package com.auradesk.guard.notifications

import android.app.ActivityOptions
import android.app.Notification
import android.app.RemoteInput
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.auradesk.guard.data.InterruptionEntity
import com.auradesk.guard.data.InterruptionRepository
import com.auradesk.guard.llm.LlamaModelRunner
import com.auradesk.guard.service.GuardService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

data class DigitalNotification(
    val senderName: String,
    val messageText: String,
    val autoReplyText: String,
    val appName: String,
    val returnTime: String,
    val timestamp: Long = System.currentTimeMillis()
)

class FocusNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "FocusNotificationListener"

        // Send at most ONE auto-reply per sender every 20 minutes during deep work
        private const val SENDER_COOLDOWN_MS = 20 * 60 * 1000L

        private val senderReplyHistory = ConcurrentHashMap<String, Long>()
        private val recentSentReplies = ConcurrentHashMap<String, Long>()
        private val processedMessageHashes = ConcurrentHashMap<String, Long>()

        fun clearCooldowns() {
            senderReplyHistory.clear()
            recentSentReplies.clear()
            processedMessageHashes.clear()
            Log.i(TAG, "🔄 Cleared notification auto-reply cooldowns on disarm/reset.")
        }

        private val SUPPORTED_PACKAGES = mapOf(
            "com.whatsapp" to "WhatsApp",
            "com.whatsapp.w4b" to "WhatsApp Business",
            "com.slack" to "Slack",
            "org.telegram.messenger" to "Telegram",
            "org.telegram.plus" to "Telegram",
            "com.google.android.apps.messaging" to "Messages",
            "com.microsoft.teams" to "Teams",
            "org.thoughtcrime.securesms" to "Signal"
        )
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        // 1. Strictly verify the device is actively running AND face-down armed
        val isRunning = GuardService.isRunning.value
        val isArmed = GuardService.isArmed.value
        if (!isRunning || !isArmed) {
            return
        }

        val pkgName = sbn.packageName ?: return
        val appName = SUPPORTED_PACKAGES[pkgName] ?: return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val rawTitle = extras.getString(Notification.EXTRA_TITLE)?.trim() ?: ""
        val rawText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""

        if (rawTitle.isBlank() || rawText.isBlank()) return
        // Skip ongoing events (e.g. active call, media playback, file download)
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return

        // 2. Prevent self-reply echo loop: WhatsApp updates notifications after RemoteInput dispatch
        if (rawText.startsWith("You:", ignoreCase = true) ||
            rawText.contains("in deep work", ignoreCase = true) ||
            rawText.contains("focus session", ignoreCase = true) ||
            rawText.contains("AuraDesk", ignoreCase = true) ||
            recentSentReplies.keys.any { sentSnippet -> rawText.contains(sentSnippet, ignoreCase = true) }
        ) {
            Log.d(TAG, "⏭️ Ignoring self-sent notification update echo: '$rawText'")
            return
        }

        // 3. Per-Sender Cooldown: Send strictly ONE reply per sender every 20 minutes
        val normalizedSender = rawTitle.lowercase().trim()
        val now = System.currentTimeMillis()
        val lastRepliedTime = senderReplyHistory[normalizedSender] ?: 0L

        if (now - lastRepliedTime < SENDER_COOLDOWN_MS) {
            val remainingSec = (SENDER_COOLDOWN_MS - (now - lastRepliedTime)) / 1000
            Log.i(TAG, "⏳ Sender '$rawTitle' already replied to recently. Cooldown active for ${remainingSec}s more. Dropping duplicate message.")
            return
        }

        // 4. Message Hash Deduplication: Never process the exact same message twice
        val msgKey = "$normalizedSender:$rawText"
        val lastMsgTime = processedMessageHashes[msgKey] ?: 0L
        if (now - lastMsgTime < SENDER_COOLDOWN_MS) {
            Log.d(TAG, "⏭️ Duplicate message key already handled: '$msgKey'")
            return
        }

        // Mark as handled immediately to block any rapid concurrent notification bursts
        senderReplyHistory[normalizedSender] = now
        processedMessageHashes[msgKey] = now

        Log.i(TAG, "📩 Intercepted incoming $appName notification from '$rawTitle': '$rawText' (Single Reply Scheduled)")

        serviceScope.launch {
            processIncomingMessage(sbn, rawTitle, rawText, appName)
        }
    }

    private suspend fun processIncomingMessage(
        sbn: StatusBarNotification,
        senderName: String,
        messageText: String,
        appName: String
    ) {
        // Double-check armed state: if the user lifted the phone while coroutine was starting, ABORT immediately!
        if (!GuardService.isRunning.value || !GuardService.isArmed.value) {
            Log.i(TAG, "🚫 Phone was lifted or guard disarmed. Aborting auto-reply to $senderName.")
            return
        }

        val prefs = getSharedPreferences("auradesk_prefs", Context.MODE_PRIVATE)
        val userName = prefs.getString("user_name", "Arjun") ?: "Arjun"

        val llamaRunner = LlamaModelRunner.getInstance(this)
        val returnTime = llamaRunner.calculateReturnTime(focusDurationMinutes = 45)

        // Generate single on-device auto-reply via Qwen2-0.5B
        val autoReply = llamaRunner.generateAutoReply(
            senderName = senderName,
            messageText = messageText,
            returnTime = returnTime,
            userName = userName
        )

        // Store snippet in echo prevention cache
        val echoKey = if (autoReply.length > 20) autoReply.substring(0, 20) else autoReply
        recentSentReplies[echoKey] = System.currentTimeMillis()

        // Double-check armed state once more before actual dispatch!
        if (!GuardService.isRunning.value || !GuardService.isArmed.value) {
            Log.i(TAG, "🚫 Device is no longer face-down. Cancelling dispatch to $senderName.")
            return
        }

        Log.i(TAG, "🤖 Generated Single Auto-Reply for $senderName: '$autoReply'")

        // 1. Direct Notification Reply via RemoteInput (EXACTLY ONCE)
        var repliedViaRemoteInput = false
        val actions = sbn.notification.actions
        if (actions != null) {
            for (action in actions) {
                val remoteInputs = action.remoteInputs
                if (!remoteInputs.isNullOrEmpty()) {
                    val intent = Intent().apply {
                        addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    }
                    val bundle = Bundle()
                    for (remoteInput in remoteInputs) {
                        bundle.putCharSequence(remoteInput.resultKey, autoReply)
                    }
                    RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        RemoteInput.setResultsSource(intent, RemoteInput.SOURCE_FREE_FORM_INPUT)
                    }

                    val options = ActivityOptions.makeBasic().apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            setPendingIntentBackgroundActivityStartMode(
                                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                            )
                        }
                    }

                    try {
                        action.actionIntent.send(this, 0, intent, null, null, null, options.toBundle())
                        repliedViaRemoteInput = true
                        Log.i(TAG, "🚀 Single direct auto-reply sent to $appName ($senderName) via RemoteInput with instant network dispatch!")
                        break
                    } catch (e: Exception) {
                        Log.w(TAG, "Direct reply with options error, falling back", e)
                        try {
                            action.actionIntent.send(this, 0, intent)
                            repliedViaRemoteInput = true
                            Log.i(TAG, "🚀 Fallback auto-reply sent to $appName ($senderName)")
                            break
                        } catch (e2: Exception) {
                            Log.e(TAG, "Fallback reply failed", e2)
                        }
                    }
                }
            }
        }

        // 2. Copy to Clipboard for instant PC / laptop paste
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("AuraDesk Auto-Reply", autoReply)
            clipboard.setPrimaryClip(clip)
        } catch (e: Exception) {
            Log.w(TAG, "Clipboard copy error", e)
        }

        // 3. Save to Local SQLite Repository
        val repo = InterruptionRepository.getInstance(this)
        val entity = InterruptionEntity(
            personName = senderName,
            taskSummary = autoReply,
            aiActionItem = messageText,
            aiDeadline = returnTime,
            aiUrgencyReason = "Digital $appName notification (Single auto-reply sent via Qwen2-0.5B)",
            targetComponent = "$appName Interruption",
            rawTranscript = messageText,
            hasVoiceTranscript = false,
            contextSnippet = if (repliedViaRemoteInput) "Direct Auto-Replied • Copied to Clipboard" else "Auto-Reply Copied to Clipboard",
            distanceZone = "Digital ($appName)",
            durationSec = 0L,
            timestamp = System.currentTimeMillis(),
            isUrgent = messageText.contains("urgent", ignoreCase = true) || messageText.contains("asap", ignoreCase = true),
            status = "NEW"
        )
        repo.insert(entity)

        // 4. Update GuardService live digital interruption for AOD Ticker
        val digitalInfo = DigitalNotification(
            senderName = senderName,
            messageText = messageText,
            autoReplyText = autoReply,
            appName = appName,
            returnTime = returnTime
        )
        GuardService.postDigitalNotification(digitalInfo)
    }
}
