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
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.auradesk.guard.data.InterruptionEntity
import com.auradesk.guard.data.InterruptionRepository
import com.auradesk.guard.llm.LlamaModelRunner
import com.auradesk.guard.service.GuardService
import com.auradesk.guard.utils.FeedbackManager
import com.auradesk.guard.vision.PersonRadarDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

        // Cooldown per sender during focus session (30 seconds to allow smooth multi-message testing)
        private const val SENDER_COOLDOWN_MS = 30 * 1000L

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
            "com.vivo.mms" to "Messages",
            "com.android.mms" to "Messages",
            "com.coloros.mms" to "Messages",
            "com.oplus.mms" to "Messages",
            "com.samsung.android.messaging" to "Messages",
            "com.microsoft.teams" to "Teams",
            "org.thoughtcrime.securesms" to "Signal",
            "com.facebook.orca" to "Messenger",
            "com.instagram.android" to "Instagram"
        )
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    private fun logDiagnostic(msg: String) {
        Log.i(TAG, msg)
        try {
            val logFile = File(getExternalFilesDir(null), "auradesk_runtime.log")
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            logFile.appendText("[$timestamp] $msg\n")
        } catch (e: Exception) {
            // Ignore logging file write errors
        }
    }

    private val testReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.auradesk.guard.TEST_LLM") {
                val llamaRunner = LlamaModelRunner.getInstance(this@FocusNotificationListenerService)
                val rawPrompt = intent.getStringExtra("rawPrompt")
                if (rawPrompt != null) {
                    serviceScope.launch {
                        val maxTokens = intent.getIntExtra("maxTokens", 60)
                        val out = llamaRunner.generateRaw(rawPrompt, maxTokens)
                        Log.i("LLM_TEST_RESULT", "⚡ RAW_RESULT: '$out'")
                        logDiagnostic("⚡ RAW_RESULT: '$out'")
                    }
                    return
                }

                val sender = intent.getStringExtra("sender") ?: "Rahul"
                val message = intent.getStringExtra("message") ?: "Can you review the PR?"
                serviceScope.launch {
                    val prefs = getSharedPreferences("auradesk_prefs", Context.MODE_PRIVATE)
                    val userName = prefs.getString("user_name", "")?.trim() ?: ""
                    val returnTime = llamaRunner.calculateReturnTime(45)
                    val reply = llamaRunner.generateAutoReply(sender, message, returnTime, userName)
                    Log.i("LLM_TEST_RESULT", "📥 INCOMING: '$message' => ⚡ REPLY: '$reply'")
                    logDiagnostic("📥 INCOMING: '$message' => ⚡ REPLY: '$reply'")

                    val digitalInfo = DigitalNotification(
                        senderName = sender,
                        messageText = message,
                        autoReplyText = reply,
                        appName = "WhatsApp (Test)",
                        returnTime = returnTime
                    )
                    GuardService.postDigitalNotification(digitalInfo)

                    val repo = InterruptionRepository.getInstance(this@FocusNotificationListenerService)
                    val entity = InterruptionEntity(
                        personName = sender,
                        taskSummary = reply,
                        aiActionItem = message,
                        aiDeadline = returnTime,
                        aiUrgencyReason = "Digital WhatsApp notification (Test)",
                        targetComponent = "WhatsApp Interruption",
                        rawTranscript = message,
                        hasVoiceTranscript = false,
                        contextSnippet = "Auto-Reply Generated • Test Verified",
                        distanceZone = "Digital (WhatsApp)",
                        durationSec = 0L,
                        timestamp = System.currentTimeMillis(),
                        isUrgent = false,
                        status = "NEW"
                    )
                    repo.insert(entity)
                }
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            val filter = android.content.IntentFilter("com.auradesk.guard.TEST_LLM")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(testReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(testReceiver, filter)
            }
            logDiagnostic("✅ FocusNotificationListenerService connected and registered TEST_LLM")
        } catch (e: Exception) {
            Log.w(TAG, "Receiver registration error", e)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        try {
            unregisterReceiver(testReceiver)
            logDiagnostic("⚠️ FocusNotificationListenerService disconnected")
        } catch (e: Exception) {}
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        // 1. Verify Guard mode is actively running
        val isRunning = GuardService.isRunning.value
        if (!isRunning) {
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

        // 2. Prevent self-reply echo loop
        if (rawText.startsWith("You:", ignoreCase = true) ||
            rawText.contains("in deep work", ignoreCase = true) ||
            rawText.contains("focus session", ignoreCase = true) ||
            rawText.contains("AuraDesk", ignoreCase = true) ||
            recentSentReplies.keys.any { sentSnippet -> rawText.contains(sentSnippet, ignoreCase = true) }
        ) {
            Log.d(TAG, "⏭️ Ignoring self-sent notification update echo: '$rawText'")
            return
        }

        // 3. Per-Sender Cooldown
        val normalizedSender = rawTitle.lowercase().trim()
        val now = System.currentTimeMillis()
        val lastRepliedTime = senderReplyHistory[normalizedSender] ?: 0L

        if (now - lastRepliedTime < SENDER_COOLDOWN_MS) {
            val remainingSec = (SENDER_COOLDOWN_MS - (now - lastRepliedTime)) / 1000
            logDiagnostic("⏳ Sender '$rawTitle' already replied to recently (${remainingSec}s remaining). Skipping.")
            return
        }

        // 4. Message Hash Deduplication: Never process the exact same message twice
        val msgKey = "$normalizedSender:$rawText"
        val lastMsgTime = processedMessageHashes[msgKey] ?: 0L
        if (now - lastMsgTime < SENDER_COOLDOWN_MS) {
            Log.d(TAG, "⏭️ Duplicate message key already handled: '$msgKey'")
            return
        }
        processedMessageHashes[msgKey] = now

        logDiagnostic("📩 Intercepted incoming $appName notification from '$rawTitle': '$rawText'")

        serviceScope.launch {
            processIncomingMessage(sbn, rawTitle, rawText, appName, normalizedSender)
        }
    }

    private suspend fun processIncomingMessage(
        sbn: StatusBarNotification,
        senderName: String,
        messageText: String,
        appName: String,
        normalizedSender: String
    ) {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AuraDesk:AutoReplyDispatch")
        wakeLock?.acquire(15000L) // Hold wake lock for up to 15s during inference and dispatch

        try {
            // Temporarily pause camera MLKit pose detection so LLM gets 100% CPU
            PersonRadarDetector.isPausedForLlm = true

            val prefs = getSharedPreferences("auradesk_prefs", Context.MODE_PRIVATE)
            val userName = prefs.getString("user_name", "")?.trim() ?: ""

            val llamaRunner = LlamaModelRunner.getInstance(this)
            val returnTime = llamaRunner.calculateReturnTime(focusDurationMinutes = 45)

            logDiagnostic("🦙 Generating auto-reply for $appName ($senderName)...")
            val autoReply = llamaRunner.generateAutoReply(
                senderName = senderName,
                messageText = messageText,
                returnTime = returnTime,
                userName = userName
            )

            // Store snippet in echo prevention cache
            val echoKey = if (autoReply.length > 20) autoReply.substring(0, 20) else autoReply
            recentSentReplies[echoKey] = System.currentTimeMillis()

            logDiagnostic("🤖 Generated reply for $senderName: '$autoReply'")

            // 1. Direct Notification Reply via RemoteInput
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
                            logDiagnostic("🚀 Direct auto-reply sent to $appName ($senderName) via RemoteInput!")
                            break
                        } catch (e: Exception) {
                            logDiagnostic("⚠️ Direct reply with options error, falling back: ${e.message}")
                            try {
                                action.actionIntent.send(this, 0, intent)
                                repliedViaRemoteInput = true
                                logDiagnostic("🚀 Fallback auto-reply sent to $appName ($senderName)")
                                break
                            } catch (e2: Exception) {
                                logDiagnostic("❌ Fallback reply failed: ${e2.message}")
                            }
                        }
                    }
                }
            }

            if (!repliedViaRemoteInput) {
                logDiagnostic("ℹ️ Notification for $appName did not contain direct reply RemoteInput action.")
            }

            // 2. Copy to Clipboard for instant PC / laptop paste
            // 2. Cross-Device Clipboard Sync via Vivo Office Kit (Syncs to Laptop Slack/Teams)
            try {
                com.auradesk.guard.vivo.VivoOfficeKitManager.getInstance(this)
                    .syncClipboard(autoReply, "AuraDesk Auto-Reply ($senderName)")
            } catch (e: Exception) {
                Log.w(TAG, "Clipboard copy error", e)
            }

            // 3. Save to Local SQLite Repository for Dashboard & Focus Debrief
            val repo = InterruptionRepository.getInstance(this)
            val entity = InterruptionEntity(
                personName = senderName,
                taskSummary = autoReply,
                aiActionItem = messageText,
                aiDeadline = returnTime,
                aiUrgencyReason = "Digital $appName notification (Auto-replied via Qwen2-0.5B)",
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

            // Auto-sync note to Jovi Notes / EasyShare
            com.auradesk.guard.vivo.VivoOfficeKitManager.getInstance(this)
                .syncInterruptionToJoviNotes(entity, launchActivity = false)

            // 4. Update GuardService live digital interruption for AOD Ticker & Screen
            val digitalInfo = DigitalNotification(
                senderName = senderName,
                messageText = messageText,
                autoReplyText = autoReply,
                appName = appName,
                returnTime = returnTime
            )
            GuardService.postDigitalNotification(digitalInfo)

            // 5. Subtle haptic feedback notifying user that focus shield deflected message
            try {
                FeedbackManager(this).playHapticWhisperLow()
            } catch (e: Exception) {
                // Ignore audio/haptic error
            }

            // 6. Record successful reply time for per-sender cooldown
            senderReplyHistory[normalizedSender] = System.currentTimeMillis()
            logDiagnostic("✅ Successfully processed and recorded auto-reply for $senderName")

        } catch (e: Exception) {
            logDiagnostic("❌ Error processing incoming message: ${e.message}")
        } finally {
            // Resume camera pose detection
            PersonRadarDetector.isPausedForLlm = false
            if (wakeLock?.isHeld == true) {
                wakeLock.release()
            }
        }
    }
}
