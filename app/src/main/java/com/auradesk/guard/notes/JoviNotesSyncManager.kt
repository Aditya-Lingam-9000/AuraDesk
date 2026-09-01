package com.auradesk.guard.notes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.auradesk.guard.data.InterruptionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class JoviSyncState(
    val lastSyncedTaskId: Long? = null,
    val lastSyncedTitle: String = "",
    val totalSyncedCount: Int = 0,
    val isAutoSyncEnabled: Boolean = true,
    val lastSyncStatus: String = "IDLE" // IDLE, SYNCED, ERROR
)

class JoviNotesSyncManager(private val context: Context) {

    companion object {
        private const val TAG = "JoviNotesSync"
        const val VIVO_NOTES_PACKAGE = "com.vivo.notes"
        const val VIVO_OFFICE_PACKAGE = "com.yozo.vivo.office"

        @Volatile
        private var instance: JoviNotesSyncManager? = null

        fun getInstance(context: Context): JoviNotesSyncManager {
            return instance ?: synchronized(this) {
                instance ?: JoviNotesSyncManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val _syncState = MutableStateFlow(JoviSyncState())
    val syncState: StateFlow<JoviSyncState> = _syncState.asStateFlow()

    fun setAutoSyncEnabled(enabled: Boolean) {
        _syncState.value = _syncState.value.copy(isAutoSyncEnabled = enabled)
        Log.i(TAG, "Auto-sync to Jovi Notes set to: $enabled")
    }

    /**
     * Format an InterruptionEntity into a rich Markdown task note
     */
    fun formatMarkdownNote(capsule: InterruptionEntity): String {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
        val formattedTime = dateFormat.format(Date(capsule.timestamp))

        val actionTitle = if (capsule.aiActionItem.isNotBlank()) {
            capsule.aiActionItem
        } else {
            capsule.taskSummary
        }

        return buildString {
            append("**TASK: $actionTitle**\n\n")
            append("- **Requester:** ${capsule.personName}\n")
            if (capsule.aiDeadline.isNotBlank()) {
                append("- **Deadline:** ${capsule.aiDeadline}\n")
            }
            if (capsule.targetComponent.isNotBlank()) {
                append("- **Target Component:** ${capsule.targetComponent}\n")
            }
            append("- **Priority:** ${if (capsule.isUrgent) "URGENT / CRITICAL" else "NORMAL"}\n")
            append("- **Captured At:** $formattedTime (${capsule.durationSec}s desk visit)\n\n")

            if (capsule.rawTranscript.isNotBlank()) {
                append("**Visitor Speech Quote:**\n")
                append("> \"${capsule.rawTranscript}\"\n\n")
            }

            append("**Focus Context Interrupted:**\n")
            append("```\n${capsule.contextSnippet}\n```\n\n")
            append("— *Auto-captured by AuraDesk Focus Guard*")
        }
    }

    /**
     * Sync task to Vivo Notes or system share target
     */
    fun syncInterruptionToNotes(capsule: InterruptionEntity, launchChooser: Boolean = false): Boolean {
        val formattedContent = formatMarkdownNote(capsule)
        val noteTitle = if (capsule.aiActionItem.isNotBlank()) capsule.aiActionItem else capsule.taskSummary

        Log.i(TAG, "📝 Syncing task #${capsule.id} to Vivo Notes / Office Kit: '$noteTitle'")

        // 1. Always copy formatted markdown to clipboard for instant pasting anywhere
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("AuraDesk Task", formattedContent)
            clipboard.setPrimaryClip(clip)
        } catch (e: Exception) {
            Log.w(TAG, "Clipboard copy exception: ${e.message}")
        }

        // 2. Try direct Vivo Notes Intent
        var dispatched = false
        try {
            val vivoIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, noteTitle)
                putExtra(Intent.EXTRA_TEXT, formattedContent)
                setPackage(VIVO_NOTES_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (context.packageManager.resolveActivity(vivoIntent, 0) != null) {
                if (launchChooser) {
                    context.startActivity(vivoIntent)
                }
                dispatched = true
                Log.i(TAG, "✅ Dispatched directly to Vivo Notes (com.vivo.notes)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vivo Notes direct intent failed: ${e.message}")
        }

        // 3. Adaptive Fallback: General Share / Notes Chooser if explicitly tapped
        if (!dispatched && launchChooser) {
            try {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "AuraDesk Task: $noteTitle")
                    putExtra(Intent.EXTRA_TEXT, formattedContent)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooserIntent = Intent.createChooser(sendIntent, "Save Task to Notes / Jovi Office").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooserIntent)
                dispatched = true
            } catch (e: Exception) {
                Log.e(TAG, "Chooser intent failed", e)
            }
        }

        // Update sync state
        _syncState.value = _syncState.value.copy(
            lastSyncedTaskId = capsule.id,
            lastSyncedTitle = noteTitle,
            totalSyncedCount = _syncState.value.totalSyncedCount + 1,
            lastSyncStatus = "SYNCED"
        )

        return true
    }
}
