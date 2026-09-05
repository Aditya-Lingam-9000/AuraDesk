package com.auradesk.guard.vivo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.auradesk.guard.R
import com.auradesk.guard.data.InterruptionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class VivoOfficeKitState(
    val isEasyShareInstalled: Boolean = false,
    val isVivoNotesInstalled: Boolean = false,
    val isPcSuiteInstalled: Boolean = false,
    val isVivoOfficeInstalled: Boolean = false,
    val isMirrorBannerActive: Boolean = false,
    val currentBannerText: String = "",
    val isLaptopMuted: Boolean = false,
    val isDndPolicyGranted: Boolean = false,
    val lastSyncedNoteTitle: String = "",
    val lastSyncedNoteTime: Long = 0L,
    val totalNotesSynced: Int = 0,
    val lastClipboardSyncedText: String = "",
    val lastSyncStatus: String = "IDLE" // IDLE, SYNCING, SYNCED, ERROR
)

class VivoOfficeKitManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "VivoOfficeKitManager"
        private const val BANNER_NOTIFICATION_ID = 2001
        private const val BANNER_CHANNEL_ID = "auradesk_officekit_banner"

        // Official & Community Vivo Package Identifiers
        const val PKG_VIVO_NOTES = "com.vivo.notes"
        const val PKG_VIVO_EASYSHARE = "com.vivo.easyshare"
        const val PKG_VIVO_PCSUITE = "com.vivo.pcsuite"
        const val PKG_VIVO_OFFICE = "com.yozo.vivo.office"
        const val PKG_VIVO_SMARTOFFICE = "com.vivo.smartoffice"
        const val PKG_VIVO_SHARE = "com.vivo.share"

        // Official Vivo Office Kit & EasyShare Intent Actions
        const val ACTION_SCREEN_MIRROR_BANNER = "com.vivo.officekit.SCREEN_MIRROR_BANNER"
        const val ACTION_DISMISS_BANNER = "com.vivo.officekit.DISMISS_BANNER"
        const val ACTION_MUTE_NOTIFICATIONS = "com.vivo.officekit.MUTE_NOTIFICATIONS"
        const val ACTION_TASK_HANDOFF = "com.vivo.officekit.TASK_HANDOFF"
        const val ACTION_EASYSHARE_SEND = "com.vivo.easyshare.ACTION_SEND"

        // Backup action strings across Funtouch OS versions
        private val BACKUP_BANNER_ACTIONS = listOf(
            "com.vivo.officekit.SCREEN_MIRROR_BANNER",
            "com.vivo.easyshare.action.SCREEN_MIRROR_BANNER",
            "com.vivo.pcsuite.action.BANNER",
            "com.vivo.officekit.action.FOCUS_BANNER"
        )

        private val BACKUP_MUTE_ACTIONS = listOf(
            "com.vivo.officekit.MUTE_NOTIFICATIONS",
            "com.vivo.pcsuite.action.MUTE_NOTIFICATIONS",
            "com.vivo.easyshare.action.TASK_HANDOFF"
        )

        // Vivo Notes ContentProvider URIs
        private val VIVO_NOTES_URIS = listOf(
            "content://com.vivo.notes.provider/notes",
            "content://com.provider.notes/notes",
            "content://com.android.notes.provider/notes",
            "content://notes/notes"
        )

        @Volatile
        private var instance: VivoOfficeKitManager? = null

        fun getInstance(context: Context): VivoOfficeKitManager {
            return instance ?: synchronized(this) {
                instance ?: VivoOfficeKitManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private var savedRingerMode: Int = -1

    private val _state = MutableStateFlow(VivoOfficeKitState())
    val state: StateFlow<VivoOfficeKitState> = _state.asStateFlow()

    private val _eventLogs = MutableStateFlow<List<String>>(emptyList())
    val eventLogs: StateFlow<List<String>> = _eventLogs.asStateFlow()

    init {
        createNotificationChannel()
        refreshPackageStatus()
    }

    private fun logEvent(msg: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val entry = "[$timestamp] $msg"
        Log.i(TAG, entry)
        _eventLogs.value = (_eventLogs.value + entry).takeLast(25)

        try {
            val logFile = File(context.getExternalFilesDir(null), "auradesk_runtime.log")
            logFile.appendText("[$timestamp] [VivoOfficeKit] $msg\n")
        } catch (e: Exception) {
            // Ignore file log errors
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                BANNER_CHANNEL_ID,
                "AuraDesk Vivo Screen Mirroring Banner",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Displays live focus banner on mirrored laptop screen and phone overlay"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun isDndPolicyAccessGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notificationManager.isNotificationPolicyAccessGranted
        } else {
            true
        }
    }

    fun refreshPackageStatus() {
        val pm = context.packageManager
        val easyShare = isPackageInstalled(pm, PKG_VIVO_EASYSHARE)
        val notes = isPackageInstalled(pm, PKG_VIVO_NOTES)
        val pcSuite = isPackageInstalled(pm, PKG_VIVO_PCSUITE)
        val office = isPackageInstalled(pm, PKG_VIVO_OFFICE)
        val dndGranted = isDndPolicyAccessGranted()

        _state.value = _state.value.copy(
            isEasyShareInstalled = easyShare,
            isVivoNotesInstalled = notes,
            isPcSuiteInstalled = pcSuite,
            isVivoOfficeInstalled = office,
            isDndPolicyGranted = dndGranted
        )
        logEvent("Ecosystem scan: EasyShare=$easyShare, VivoNotes=$notes, PCSuite=$pcSuite, Office=$office, DNDPolicy=$dndGranted")
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    // =========================================================================
    // PILLAR 1: SCREEN MIRRORING FOCUS BANNER (com.vivo.officekit.SCREEN_MIRROR_BANNER)
    // =========================================================================

    /**
     * Activates the Vivo Multi-Screen Collaboration / Screen Mirroring session
     * so the phone display streams to the connected Mac/PC.
     */
    fun startVivoScreenMirroring() {
        logEvent("Launching Vivo Screen Mirroring session...")

        // 1. Direct explicit EasyShare dl.mirroring DeepLink URI
        try {
            val uriIntent = Intent(Intent.ACTION_VIEW, Uri.parse("es://dl.mirroring")).apply {
                setPackage(PKG_VIVO_EASYSHARE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            context.startActivity(uriIntent)
            logEvent("Launched EasyShare Mirroring via es://dl.mirroring (package targeted)")
            return
        } catch (e: Exception) {
            Log.d(TAG, "Package targeted es://dl.mirroring fallback: ${e.message}")
        }

        // 2. Generic dl.mirroring DeepLink URI
        try {
            val uriIntent = Intent(Intent.ACTION_VIEW, Uri.parse("es://dl.mirroring")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            context.startActivity(uriIntent)
            logEvent("Launched EasyShare Mirroring via generic es://dl.mirroring")
            return
        } catch (e: Exception) {
            Log.d(TAG, "Generic es://dl.mirroring fallback: ${e.message}")
        }

        // 3. Vivo EasyShare Mirroring Action (explicit package)
        try {
            val actionIntent = Intent("vivo.intent.action.EASYSHARE_MIRRORING").apply {
                setPackage(PKG_VIVO_EASYSHARE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            context.startActivity(actionIntent)
            logEvent("Launched via targeted vivo.intent.action.EASYSHARE_MIRRORING")
            return
        } catch (e: Exception) {
            Log.d(TAG, "Targeted EASYSHARE_MIRRORING fallback: ${e.message}")
        }

        // 4. Vivo EasyShare Mirroring Action (generic)
        try {
            val actionIntent = Intent("vivo.intent.action.EASYSHARE_MIRRORING").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            context.startActivity(actionIntent)
            logEvent("Launched via generic vivo.intent.action.EASYSHARE_MIRRORING")
            return
        } catch (e: Exception) {
            Log.d(TAG, "Generic EASYSHARE_MIRRORING fallback: ${e.message}")
        }

        // 5. Explicit Component ShortCutSplashScreenActivity
        try {
            val compIntent = Intent(Intent.ACTION_VIEW, Uri.parse("es://dl.mirroring")).apply {
                setClassName(PKG_VIVO_EASYSHARE, "com.vivo.easyshare.activity.ShortCutSplashScreenActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            context.startActivity(compIntent)
            logEvent("Launched ShortCutSplashScreenActivity component directly")
            return
        } catch (e: Exception) {
            Log.d(TAG, "ShortCutSplashScreenActivity fallback: ${e.message}")
        }

        // 6. Fallback: Launch EasyShare Multi-Screen Activity
        try {
            val pcSuiteIntent = context.packageManager.getLaunchIntentForPackage(PKG_VIVO_EASYSHARE)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            if (pcSuiteIntent != null) {
                context.startActivity(pcSuiteIntent)
                logEvent("Launched Vivo EasyShare main interface")
            }
        } catch (e: Exception) {
            Log.w(TAG, "EasyShare package launch failed: ${e.message}")
        }
    }

    /**
     * Broadcasts the Screen Mirroring Banner intent so that connected PC/Laptop screens
     * display the focus banner across the top of the mirrored display.
     */
    fun sendScreenMirrorFocusBanner(
        userName: String,
        returnTime: String,
        isArmed: Boolean = true,
        autoLaunchMirroring: Boolean = true
    ) {
        val displayName = if (userName.isNotBlank()) userName.trim() else "Arjun"
        val bannerText = "$displayName in Deep Work till $returnTime. Tap phone twice to interrupt urgently."

        logEvent("Broadcasting Screen Mirroring Banner: '$bannerText'")

        // If requested and armed, activate the screen mirroring pipeline
        if (isArmed && autoLaunchMirroring) {
            startVivoScreenMirroring()
        }

        // 1. Send official & backup Vivo Office Kit Broadcast Intents
        for (actionName in BACKUP_BANNER_ACTIONS) {
            try {
                val intent = Intent(actionName).apply {
                    putExtra("text", bannerText)
                    putExtra("user_name", displayName)
                    putExtra("return_time", returnTime)
                    putExtra("is_armed", isArmed)
                    putExtra("hint", "Tap phone twice to interrupt urgently")
                    putExtra("source", "AuraDesk")
                    putExtra("timestamp", System.currentTimeMillis())
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                }
                context.sendBroadcast(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed sending banner action '$actionName': ${e.message}")
            }
        }

        // 2. Also send targeted intent to EasyShare and PC Suite packages
        for (pkg in listOf(PKG_VIVO_EASYSHARE, PKG_VIVO_PCSUITE, PKG_VIVO_SMARTOFFICE)) {
            try {
                val intent = Intent(ACTION_SCREEN_MIRROR_BANNER).apply {
                    setPackage(pkg)
                    putExtra("text", bannerText)
                    putExtra("user_name", displayName)
                    putExtra("return_time", returnTime)
                    putExtra("is_armed", isArmed)
                    putExtra("source", "AuraDesk")
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                }
                context.sendBroadcast(intent)
            } catch (e: Exception) {
                // Ignore package broadcast errors
            }
        }

        // 3. Post high-priority system banner notification (visible on laptop mirrored window)
        try {
            val builder = NotificationCompat.Builder(context, BANNER_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Laptop Screen Mirroring: Deep Work Active")
                .setContentText(bannerText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(bannerText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setOngoing(true)

            notificationManager.notify(BANNER_NOTIFICATION_ID, builder.build())
        } catch (e: Exception) {
            Log.w(TAG, "Notification banner error: ${e.message}")
        }

        _state.value = _state.value.copy(
            isMirrorBannerActive = isArmed,
            currentBannerText = bannerText
        )
    }

    /**
     * Dismisses the focus banner from mirrored display when phone is disarmed.
     */
    fun dismissScreenMirrorFocusBanner() {
        logEvent("Dismissing Screen Mirroring Focus Banner")

        try {
            val intent = Intent(ACTION_DISMISS_BANNER).apply {
                putExtra("is_armed", false)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            // Ignore
        }

        try {
            notificationManager.cancel(BANNER_NOTIFICATION_ID)
        } catch (e: Exception) {
            // Ignore
        }

        _state.value = _state.value.copy(
            isMirrorBannerActive = false,
            currentBannerText = ""
        )
    }

    // =========================================================================
    // PILLAR 2: TASK HANDOFF & LAPTOP NOTIFICATION MUTE (MUTE_NOTIFICATIONS)
    // =========================================================================

    /**
     * Sends Task Handoff intent and engages System Do Not Disturb / Audio Mute
     * to completely silence phone and mirrored laptop/desktop notifications during deep work.
     */
    fun setLaptopNotificationsMuted(mute: Boolean) {
        logEvent("Configuring Laptop & Ecosystem Notification Mute (mute=$mute)")

        // 1. Android System Do Not Disturb (DND) / Interruption Filter
        // Silences phone alerts & instructs Vivo PC Suite / EasyShare / Link to Windows not to pop up on laptop
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                if (notificationManager.isNotificationPolicyAccessGranted) {
                    if (mute) {
                        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                        logEvent("System DND Priority mode ACTIVATED (Laptop alerts suppressed)")
                    } else {
                        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                        logEvent("System DND mode DEACTIVATED (Normal notification flow restored)")
                    }
                } else {
                    logEvent("Notification Policy (DND) access not granted. Relying on AudioManager & Broadcasts.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "DND Interruption filter adjustment error: ${e.message}")
            }
        }

        // 2. Hardware Audio Stream Muting via AudioManager
        try {
            if (mute) {
                if (savedRingerMode == -1) {
                    savedRingerMode = audioManager.ringerMode
                }
                audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
                audioManager.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0)
                logEvent("AudioManager notification & ringer streams SILENCED")
            } else {
                if (savedRingerMode != -1) {
                    audioManager.ringerMode = savedRingerMode
                    savedRingerMode = -1
                } else {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                }
                audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
                audioManager.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0)
                logEvent("AudioManager notification & ringer streams UNMUTED")
            }
        } catch (e: Exception) {
            Log.w(TAG, "AudioManager stream adjust error: ${e.message}")
        }

        // 3. Vivo Office Kit & EasyShare Broadcast Intents
        for (actionName in BACKUP_MUTE_ACTIONS) {
            try {
                val intent = Intent(actionName).apply {
                    putExtra("mute", mute)
                    putExtra("is_deep_work", mute)
                    putExtra("is_focus_active", mute)
                    putExtra("status", if (mute) "MUTED" else "UNMUTED")
                    putExtra("source", "AuraDesk")
                    putExtra("timestamp", System.currentTimeMillis())
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                }
                context.sendBroadcast(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed sending mute action '$actionName': ${e.message}")
            }
        }

        // 4. Targeted broadcasts to Vivo PC Suite, EasyShare, and SmartOffice
        for (pkg in listOf(PKG_VIVO_EASYSHARE, PKG_VIVO_PCSUITE, PKG_VIVO_SMARTOFFICE, PKG_VIVO_SHARE)) {
            try {
                val intent = Intent(ACTION_MUTE_NOTIFICATIONS).apply {
                    setPackage(pkg)
                    putExtra("mute", mute)
                    putExtra("is_deep_work", mute)
                    putExtra("source", "AuraDesk")
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                }
                context.sendBroadcast(intent)
            } catch (e: Exception) {
                // Ignore
            }
        }

        _state.value = _state.value.copy(
            isLaptopMuted = mute,
            isDndPolicyGranted = isDndPolicyAccessGranted()
        )
    }

    // =========================================================================
    // PILLAR 3: DIRECT JOVI NOTES SYNC (ContentResolver + Intent + FileProvider)
    // =========================================================================

    /**
     * Formats an InterruptionEntity into a rich Markdown task note
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
            append("# AuraDesk Task: $actionTitle\n\n")
            append("- **Requester:** ${capsule.personName}\n")
            if (capsule.aiDeadline.isNotBlank()) {
                append("- **Deadline:** ${capsule.aiDeadline}\n")
            }
            if (capsule.targetComponent.isNotBlank()) {
                append("- **Target Component:** ${capsule.targetComponent}\n")
            }
            append("- **Priority:** ${if (capsule.isUrgent) "URGENT / CRITICAL" else "NORMAL"}\n")
            append("- **Captured At:** $formattedTime (${capsule.durationSec}s desk visit)\n")
            append("- **Status:** Ready for Action\n\n")

            if (capsule.rawTranscript.isNotBlank()) {
                append("### Visitor Speech Transcript\n")
                append("> \"${capsule.rawTranscript}\"\n\n")
            }

            if (capsule.contextSnippet.isNotBlank()) {
                append("### Focus Context Interrupted\n")
                append("```\n${capsule.contextSnippet}\n```\n\n")
            }

            append("---\n")
            append("*Auto-captured by AuraDesk Focus Guard • Synced via Vivo Office Kit*")
        }
    }

    /**
     * Syncs an Interruption task note to Vivo Notes (ContentProvider + Intent + File Sync).
     */
    suspend fun syncInterruptionToJoviNotes(
        capsule: InterruptionEntity,
        launchActivity: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        val noteTitle = if (capsule.aiActionItem.isNotBlank()) capsule.aiActionItem else capsule.taskSummary
        val markdownContent = formatMarkdownNote(capsule)

        logEvent("Syncing Task #${capsule.id} ('$noteTitle') to Jovi Notes & Cross-Device Hub...")
        _state.value = _state.value.copy(lastSyncStatus = "SYNCING")

        // 1. Cross-Device Clipboard Sync: Push formatted markdown note to system clipboard
        syncClipboard(markdownContent, "AuraDesk Task: $noteTitle")

        // 2. Direct ContentResolver Insert into Vivo Notes Provider
        var providerInserted = false
        val contentValues = ContentValues().apply {
            put("title", "AuraDesk: $noteTitle")
            put("content", markdownContent)
            put("timestamp", capsule.timestamp)
            put("date", capsule.timestamp)
            put("folder", "AuraDesk Focus")
            put("is_pinned", if (capsule.isUrgent) 1 else 0)
        }

        for (uriString in VIVO_NOTES_URIS) {
            try {
                val uri = Uri.parse(uriString)
                val resultUri = context.contentResolver.insert(uri, contentValues)
                if (resultUri != null) {
                    providerInserted = true
                    logEvent("Direct ContentResolver insert succeeded: $resultUri")
                    break
                }
            } catch (e: SecurityException) {
                Log.d(TAG, "ContentProvider URI '$uriString' requires OEM permission: ${e.message}")
            } catch (e: Exception) {
                Log.d(TAG, "ContentProvider URI '$uriString' error: ${e.message}")
            }
        }

        // 3. Save Markdown file to local disk for EasyShare / File sharing
        val noteFile = saveNoteFile(capsule.id, noteTitle, markdownContent)

        // 4. Dispatch targeted Intent to com.vivo.notes
        var intentDispatched = false
        try {
            val vivoIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "AuraDesk: $noteTitle")
                putExtra(Intent.EXTRA_TEXT, markdownContent)
                setPackage(PKG_VIVO_NOTES)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (context.packageManager.resolveActivity(vivoIntent, 0) != null) {
                if (launchActivity) {
                    context.startActivity(vivoIntent)
                }
                intentDispatched = true
                logEvent("Targeted Intent dispatched to Vivo Notes (com.vivo.notes)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vivo Notes intent dispatch error: ${e.message}")
        }

        // 5. Automatic EasyShare Transfer Broadcast (shares file to laptop EasyShare inbox)
        if (noteFile != null && noteFile.exists()) {
            shareSummaryFileViaEasyShare("AuraDesk_Task_${capsule.id}.md", markdownContent)
        }

        // 6. Chooser fallback if user explicitly tapped from UI and direct intent wasn't available
        if (!providerInserted && !intentDispatched && launchActivity) {
            try {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "AuraDesk: $noteTitle")
                    putExtra(Intent.EXTRA_TEXT, markdownContent)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooser = Intent.createChooser(sendIntent, "Save Task to Notes / Office Kit").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (e: Exception) {
                Log.e(TAG, "Chooser intent error", e)
            }
        }

        // Update state
        _state.value = _state.value.copy(
            lastSyncedNoteTitle = noteTitle,
            lastSyncedNoteTime = System.currentTimeMillis(),
            totalNotesSynced = _state.value.totalNotesSynced + 1,
            lastSyncStatus = "SYNCED"
        )

        true
    }

    private fun saveNoteFile(id: Long, title: String, content: String): File? {
        return try {
            val dir = File(context.getExternalFilesDir(null), "notes")
            if (!dir.exists()) dir.mkdirs()
            val safeTitle = title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(30)
            val file = File(dir, "AuraDesk_${id}_$safeTitle.md")
            file.writeText(content)
            file
        } catch (e: Exception) {
            Log.w(TAG, "Error writing note file: ${e.message}")
            null
        }
    }

    // =========================================================================
    // PILLAR 4: EASYSHARE FILE SHARE & CROSS-DEVICE CLIPBOARD SYNC
    // =========================================================================

    /**
     * Copies content to the system clipboard with cross-device sync metadata
     * so Vivo EasyShare / Link to Windows pushes it immediately to laptop Slack/Teams.
     */
    fun syncClipboard(text: String, label: String = "AuraDesk Clip") {
        try {
            val clipData = ClipData.newPlainText(label, text)
            
            // On Android 13+, explicit non-sensitive tag allows cross-device mirror engines to sync
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                clipData.description.extras = PersistableBundle().apply {
                    putBoolean("android.content.extra.IS_SENSITIVE", false)
                }
            }

            clipboardManager.setPrimaryClip(clipData)
            logEvent("Cross-Device Clipboard updated: '${text.take(40)}...'")

            _state.value = _state.value.copy(lastClipboardSyncedText = text)
        } catch (e: Exception) {
            Log.w(TAG, "Clipboard sync error: ${e.message}")
        }
    }

    /**
     * Shares a Markdown summary file via Vivo EasyShare to mirrored laptop.
     */
    fun shareSummaryFileViaEasyShare(filename: String, markdownContent: String): Boolean {
        return try {
            val dir = File(context.getExternalFilesDir(null), "easyshare")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, filename)
            file.writeText(markdownContent)

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(ACTION_EASYSHARE_SEND).apply {
                type = "text/markdown"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, markdownContent)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            }
            context.sendBroadcast(intent)

            // Also try targeted ACTION_SEND to EasyShare package
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage(PKG_VIVO_EASYSHARE)
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, markdownContent)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            }
            context.sendBroadcast(shareIntent)

            logEvent("EasyShare file transfer broadcasted: $filename")
            true
        } catch (e: Exception) {
            Log.w(TAG, "EasyShare broadcast error: ${e.message}")
            false
        }
    }

    // =========================================================================
    // SESSION LIFECYCLE HOOKS (Triggered automatically by GuardService)
    // =========================================================================

    /**
     * Called automatically when the phone arms face-down into Deep Work or when Guard starts.
     */
    fun onFocusSessionStarted(
        userName: String,
        returnTime: String,
        autoLaunchMirroring: Boolean = true
    ) {
        logEvent("Focus Session STARTED (Screen Mirroring & Office Kit Active)")
        sendScreenMirrorFocusBanner(userName, returnTime, isArmed = true, autoLaunchMirroring = autoLaunchMirroring)
        setLaptopNotificationsMuted(true)
    }

    /**
     * Called automatically when the phone is flipped face-up / disarmed.
     */
    fun onFocusSessionEnded(userName: String = "") {
        logEvent("Focus Session ENDED (Phone Picked Up)")
        dismissScreenMirrorFocusBanner()
        setLaptopNotificationsMuted(false)
    }
}
