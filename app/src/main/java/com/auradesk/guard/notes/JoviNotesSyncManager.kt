package com.auradesk.guard.notes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.auradesk.guard.data.InterruptionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
        val vivoManager = com.auradesk.guard.vivo.VivoOfficeKitManager.getInstance(context)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            vivoManager.syncInterruptionToJoviNotes(capsule, launchChooser)
        }

        // Update local sync state
        val noteTitle = if (capsule.aiActionItem.isNotBlank()) capsule.aiActionItem else capsule.taskSummary
        _syncState.value = _syncState.value.copy(
            lastSyncedTaskId = capsule.id,
            lastSyncedTitle = noteTitle,
            totalSyncedCount = _syncState.value.totalSyncedCount + 1,
            lastSyncStatus = "SYNCED"
        )

        return true
    }
}
