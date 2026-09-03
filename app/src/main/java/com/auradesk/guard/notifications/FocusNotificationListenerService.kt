package com.auradesk.guard.notifications

import android.app.Notification
import android.app.RemoteInput
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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

        // 1. Only intercept if AuraDesk is Armed Face-Down in Deep Work
        val isArmed = GuardService.isArmed.value
        if (!isArmed) return

        val pkgName = sbn.packageName ?: return
        val appName = SUPPORTED_PACKAGES[pkgName] ?: return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getString(Notification.EXTRA_TITLE)?.trim() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""

        if (title.isBlank() || text.isBlank()) return
        // Skip ongoing / media playback / system summary
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return

        Log.i(TAG, "📩 Intercepted incoming $appName notification from '$title': '$text'")

        serviceScope.launch {
            processIncomingMessage(sbn, title, text, appName)
        }
    }

    private suspend fun processIncomingMessage(
        sbn: StatusBarNotification,
        senderName: String,
        messageText: String,
        appName: String
    ) {
        val prefs = getSharedPreferences("auradesk_prefs", Context.MODE_PRIVATE)
        val userName = prefs.getString("user_name", "Arjun") ?: "Arjun"

        val llamaRunner = LlamaModelRunner.getInstance(this)
        val returnTime = llamaRunner.calculateReturnTime(focusDurationMinutes = 45)

        // Phase 7 Prompt A: Generate on-device Auto-Reply via Qwen2-0.5B
        val autoReply = llamaRunner.generateAutoReply(
            senderName = senderName,
            messageText = messageText,
            returnTime = returnTime,
            userName = userName
        )

        Log.i(TAG, "🤖 Generated Auto-Reply for $senderName: '$autoReply'")

        // 1. Direct Notification Reply via RemoteInput
        var repliedViaRemoteInput = false
        val actions = sbn.notification.actions
        if (actions != null) {
            for (action in actions) {
                val remoteInputs = action.remoteInputs
                if (!remoteInputs.isNullOrEmpty()) {
                    val intent = Intent()
                    val bundle = Bundle()
                    for (remoteInput in remoteInputs) {
                        bundle.putCharSequence(remoteInput.resultKey, autoReply)
                    }
                    RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)
                    try {
                        action.actionIntent.send(this, 0, intent)
                        repliedViaRemoteInput = true
                        Log.i(TAG, "🚀 Direct auto-reply sent to $appName via RemoteInput!")
                        break
                    } catch (e: Exception) {
                        Log.w(TAG, "Direct reply dispatch error", e)
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
            aiUrgencyReason = "Digital $appName notification auto-replied via Qwen2-0.5B",
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
