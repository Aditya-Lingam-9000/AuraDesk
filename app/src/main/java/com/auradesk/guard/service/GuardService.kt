package com.auradesk.guard.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.auradesk.guard.AuraDeskApp
import com.auradesk.guard.MainActivity
import com.auradesk.guard.R
import com.auradesk.guard.sensors.FaceDownDetector
import com.auradesk.guard.sensors.FaceDownSensors
import com.auradesk.guard.utils.FeedbackManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HapticAlert(
    val title: String,
    val detail: String,
    val zone: com.auradesk.guard.vision.RadarZone = com.auradesk.guard.vision.RadarZone.NONE,
    val timestamp: Long = System.currentTimeMillis()
)

class GuardService : LifecycleService() {

    companion object {
        private const val TAG = "GuardService"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START_GUARD"
        const val ACTION_STOP = "ACTION_STOP_GUARD"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _isArmed = MutableStateFlow(false)
        val isArmed: StateFlow<Boolean> = _isArmed.asStateFlow()

        private val _latestHapticAlert = MutableStateFlow<HapticAlert?>(null)
        val latestHapticAlert: StateFlow<HapticAlert?> = _latestHapticAlert.asStateFlow()

        fun postHapticAlert(title: String, detail: String, zone: com.auradesk.guard.vision.RadarZone) {
            _latestHapticAlert.value = HapticAlert(title, detail, zone, System.currentTimeMillis())
        }

        private val _liveSensors = MutableStateFlow(FaceDownSensors())
        val liveSensors: StateFlow<FaceDownSensors> = _liveSensors.asStateFlow()

        val radarDetector = com.auradesk.guard.vision.PersonRadarDetector()
        val liveRadar: StateFlow<com.auradesk.guard.vision.PersonRadarData> = radarDetector.radarData

        private val _liveDeepWork = MutableStateFlow(com.auradesk.guard.focus.DeepWorkState())
        val liveDeepWork: StateFlow<com.auradesk.guard.focus.DeepWorkState> = _liveDeepWork.asStateFlow()

        private val _liveAudioCapsule = MutableStateFlow(com.auradesk.guard.audio.AudioCapsuleState())
        val liveAudioCapsule: StateFlow<com.auradesk.guard.audio.AudioCapsuleState> = _liveAudioCapsule.asStateFlow()

        val capsuleSynthesizer = com.auradesk.guard.llm.CapsuleSynthesizer()
        private val _liveSynthesizedTask = MutableStateFlow<com.auradesk.guard.llm.SummarizedTask?>(null)
        val liveSynthesizedTask: StateFlow<com.auradesk.guard.llm.SummarizedTask?> = _liveSynthesizedTask.asStateFlow()

        private val _liveInterruptions = MutableStateFlow<List<com.auradesk.guard.data.InterruptionEntity>>(emptyList())
        val liveInterruptions: StateFlow<List<com.auradesk.guard.data.InterruptionEntity>> = _liveInterruptions.asStateFlow()

        private var joviNotesSyncManagerInstance: com.auradesk.guard.notes.JoviNotesSyncManager? = null

        fun getJoviNotesSyncManager(context: Context): com.auradesk.guard.notes.JoviNotesSyncManager {
            if (joviNotesSyncManagerInstance == null) {
                joviNotesSyncManagerInstance = com.auradesk.guard.notes.JoviNotesSyncManager.getInstance(context.applicationContext)
            }
            return joviNotesSyncManagerInstance!!
        }

        private var powerManagerGuardInstance: com.auradesk.guard.privacy.PowerManagerGuard? = null

        fun getPowerManagerGuard(context: Context): com.auradesk.guard.privacy.PowerManagerGuard {
            if (powerManagerGuardInstance == null) {
                powerManagerGuardInstance = com.auradesk.guard.privacy.PowerManagerGuard.getInstance(context.applicationContext)
            }
            return powerManagerGuardInstance!!
        }

        private var privacyAuditorInstance: com.auradesk.guard.privacy.PrivacyAuditor? = null

        fun getPrivacyAuditor(context: Context): com.auradesk.guard.privacy.PrivacyAuditor {
            if (privacyAuditorInstance == null) {
                privacyAuditorInstance = com.auradesk.guard.privacy.PrivacyAuditor.getInstance(context.applicationContext)
            }
            return privacyAuditorInstance!!
        }

        private var deepWorkDetectorInstance: com.auradesk.guard.focus.DeepWorkDetector? = null
        private var audioCapsuleManagerInstance: com.auradesk.guard.audio.AudioCapsuleManager? = null

        fun ensureAudioCapsuleManager(context: Context): com.auradesk.guard.audio.AudioCapsuleManager {
            if (audioCapsuleManagerInstance == null) {
                val appContext = context.applicationContext
                val manager = com.auradesk.guard.audio.AudioCapsuleManager(appContext)
                audioCapsuleManagerInstance = manager
                val repo = com.auradesk.guard.data.InterruptionRepository.getInstance(appContext)
                val joviSync = getJoviNotesSyncManager(appContext)

                manager.onCapsuleRecorded = { transcript, durationSec, isUrgent ->
                    val synthesized = capsuleSynthesizer.synthesize(transcript, "Desk Visitor")
                    _liveSynthesizedTask.value = synthesized

                    val summaryTitle = if (synthesized.actionItem.isNotBlank()) {
                        synthesized.actionItem
                    } else if (transcript.isNotBlank() && transcript.length > 5) {
                        "\"$transcript\""
                    } else {
                        "Desk visitor for ${durationSec}s"
                    }

                    val entity = com.auradesk.guard.data.InterruptionEntity(
                        personName = "Desk Visitor",
                        taskSummary = summaryTitle,
                        aiActionItem = synthesized.actionItem,
                        aiDeadline = synthesized.deadlineOrTime ?: "",
                        aiUrgencyReason = synthesized.urgencyReason ?: "",
                        targetComponent = synthesized.targetComponent ?: "",
                        rawTranscript = transcript,
                        hasVoiceTranscript = transcript.isNotBlank(),
                        contextSnippet = "Editing main.py at line 124",
                        distanceZone = "0.5m (At Desk)",
                        durationSec = durationSec,
                        isUrgent = isUrgent || synthesized.urgencyLevel == com.auradesk.guard.llm.UrgencyLevel.CRITICAL,
                        status = "NEW"
                    )

                    CoroutineScope(Dispatchers.IO).launch {
                        val id = repo.insert(entity)
                        if (joviSync.syncState.value.isAutoSyncEnabled && (entity.isUrgent || entity.aiActionItem.isNotBlank())) {
                            joviSync.syncInterruptionToNotes(entity.copy(id = id))
                            repo.markJoviSynced(id)
                        }
                    }
                }

                CoroutineScope(Dispatchers.Main).launch {
                    manager.capsuleState.collect {
                        _liveAudioCapsule.value = it
                    }
                }
            }
            return audioCapsuleManagerInstance!!
        }

        fun simulateRadar(distance: Float, isApproaching: Boolean, growthRate: Float = 28.5f) {
            radarDetector.simulate(distance, isApproaching, growthRate)
        }

        fun clearRadar() {
            radarDetector.clear()
        }

        fun simulateDeepWork(
            isDeepWork: Boolean,
            score: Int,
            cadenceBpm: Float,
            profile: com.auradesk.guard.focus.EnvironmentProfile = com.auradesk.guard.focus.EnvironmentProfile.QUIET_LAPTOP
        ) {
            deepWorkDetectorInstance?.simulate(isDeepWork, score, cadenceBpm, profile)
                ?: run {
                    _liveDeepWork.value = com.auradesk.guard.focus.DeepWorkState(
                        isDeepWork = isDeepWork,
                        focusScore = score,
                        typingCadenceBpm = cadenceBpm,
                        acousticEnergyLevel = if (isDeepWork) 0.65f else 0.05f,
                        focusStateLabel = if (isDeepWork) "⚡ Deep Work Active • Focus Protected" else "💤 Idle / Ambient Desk",
                        environmentProfile = profile,
                        isAudioListening = true
                    )
                }
        }

        fun setEnvironmentProfile(profile: com.auradesk.guard.focus.EnvironmentProfile) {
            deepWorkDetectorInstance?.setEnvironmentProfile(profile)
        }

        fun startAudioCapsule(context: Context? = null, durationSec: Int = 10) {
            deepWorkDetectorInstance?.pauseListening()
            if (context != null) {
                val manager = ensureAudioCapsuleManager(context)
                manager.start10SecCapsuleCapture("Manual / Test Trigger")
            } else {
                audioCapsuleManagerInstance?.start10SecCapsuleCapture("Manual / Test Trigger")
            }
        }

        fun simulateSpeechCapsule(
            context: Context? = null,
            speakerName: String,
            speechText: String,
            durationSec: Long = 6L,
            isUrgent: Boolean = true
        ) {
            if (context != null) {
                val manager = ensureAudioCapsuleManager(context)
                val repo = com.auradesk.guard.data.InterruptionRepository.getInstance(context)
                val synthesized = capsuleSynthesizer.synthesize(speechText, speakerName)
                _liveSynthesizedTask.value = synthesized

                manager.simulateTranscript(speakerName, speechText, durationSec, isUrgent)
                CoroutineScope(Dispatchers.IO).launch {
                    repo.insert(
                        com.auradesk.guard.data.InterruptionEntity(
                            personName = speakerName,
                            taskSummary = synthesized.actionItem,
                            aiActionItem = synthesized.actionItem,
                            aiDeadline = synthesized.deadlineOrTime ?: "",
                            aiUrgencyReason = synthesized.urgencyReason ?: "",
                            targetComponent = synthesized.targetComponent ?: "",
                            rawTranscript = speechText,
                            hasVoiceTranscript = true,
                            contextSnippet = "Editing main.py at line 124",
                            distanceZone = "0.5m (At Desk)",
                            durationSec = durationSec,
                            isUrgent = isUrgent || synthesized.urgencyLevel == com.auradesk.guard.llm.UrgencyLevel.CRITICAL,
                            status = "NEW"
                        )
                    )
                }
            } else {
                audioCapsuleManagerInstance?.simulateTranscript(speakerName, speechText, durationSec, isUrgent)
            }
        }

        fun synthesizePrompt(context: Context, rawText: String, speakerName: String = "Desk Visitor") {
            val synthesized = capsuleSynthesizer.synthesize(rawText, speakerName)
            _liveSynthesizedTask.value = synthesized
            val repo = com.auradesk.guard.data.InterruptionRepository.getInstance(context)
            CoroutineScope(Dispatchers.IO).launch {
                repo.insert(
                    com.auradesk.guard.data.InterruptionEntity(
                        personName = speakerName,
                        taskSummary = synthesized.actionItem,
                        aiActionItem = synthesized.actionItem,
                        aiDeadline = synthesized.deadlineOrTime ?: "",
                        aiUrgencyReason = synthesized.urgencyReason ?: "",
                        targetComponent = synthesized.targetComponent ?: "",
                        rawTranscript = rawText,
                        hasVoiceTranscript = true,
                        contextSnippet = "Editing main.py at line 124",
                        distanceZone = "0.5m (At Desk)",
                        durationSec = 5L,
                        isUrgent = synthesized.urgencyLevel == com.auradesk.guard.llm.UrgencyLevel.CRITICAL,
                        status = "NEW"
                    )
                )
            }
        }

        fun startService(context: Context) {
            val intent = Intent(context, GuardService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, GuardService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private lateinit var faceDownDetector: FaceDownDetector
    private lateinit var deepWorkDetector: com.auradesk.guard.focus.DeepWorkDetector
    private lateinit var audioCapsuleManager: com.auradesk.guard.audio.AudioCapsuleManager
    private lateinit var cameraRadarManager: com.auradesk.guard.vision.CameraRadarManager
    private lateinit var feedbackManager: FeedbackManager
    private lateinit var repository: com.auradesk.guard.data.InterruptionRepository
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var sensorCollectJob: Job? = null
    private var stateCollectJob: Job? = null
    private var deepWorkCollectJob: Job? = null
    private var audioCapsuleCollectJob: Job? = null
    private var radarCollectJob: Job? = null
    private var lastArmedState = false

    // Real-time visit tracking state
    private var visitStartTime: Long = 0L
    private var absentSinceTime: Long = 0L
    private var hasCapturedCapsuleInCurrentVisit: Boolean = false
    private var lastZoneVibrated: com.auradesk.guard.vision.RadarZone = com.auradesk.guard.vision.RadarZone.NONE
    private var lastVibrationTime: Long = 0L
    private var closestZoneInVisit: com.auradesk.guard.vision.RadarZone = com.auradesk.guard.vision.RadarZone.NONE

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "GuardService onCreate")
        faceDownDetector = FaceDownDetector(this)
        feedbackManager = FeedbackManager(this)
        deepWorkDetector = com.auradesk.guard.focus.DeepWorkDetector(this)
        deepWorkDetectorInstance = deepWorkDetector
        audioCapsuleManager = com.auradesk.guard.audio.AudioCapsuleManager(this)
        audioCapsuleManagerInstance = audioCapsuleManager
        cameraRadarManager = com.auradesk.guard.vision.CameraRadarManager(this, detector = radarDetector)
        repository = com.auradesk.guard.data.InterruptionRepository.getInstance(this)
        serviceScope.launch {
            repository.allInterruptions.collect { list ->
                _liveInterruptions.value = list
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                startGuard()
            }
            ACTION_STOP -> {
                stopGuard()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startGuard() {
        if (_isRunning.value) return
        _isRunning.value = true
        Log.i(TAG, "GuardService started in foreground")

        val notification = createNotification("AuraDesk Guard Ready", "Place phone face-down to arm guard")
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                startForeground(NOTIFICATION_ID, notification, serviceType)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                startForeground(NOTIFICATION_ID, notification, serviceType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Foreground service start error, falling back to standard startForeground", e)
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (e2: Exception) {
                Log.e(TAG, "Fatal startForeground fallback error", e2)
            }
        }

        faceDownDetector.startListening()

        sensorCollectJob?.cancel()
        sensorCollectJob = serviceScope.launch {
            faceDownDetector.sensorState.collect { sensors ->
                _liveSensors.value = sensors
            }
        }

        deepWorkCollectJob?.cancel()
        deepWorkCollectJob = serviceScope.launch {
            deepWorkDetector.deepWorkState.collect { focusState ->
                _liveDeepWork.value = focusState
            }
        }

        audioCapsuleCollectJob?.cancel()
        audioCapsuleCollectJob = serviceScope.launch {
            audioCapsuleManager.capsuleState.collect { audioState ->
                _liveAudioCapsule.value = audioState
            }
        }

        // Automatic Interruption Capsule Storage Bridge
        audioCapsuleManager.onCapsuleRecorded = { transcript, durationSec, isUrgent ->
            deepWorkDetector.resumeListening()
            hasCapturedCapsuleInCurrentVisit = true

            val isRealSpeech = transcript.isNotBlank() &&
                    !transcript.startsWith("Visitor approached desk") &&
                    !transcript.startsWith("Listening...")

            val synthesized = if (isRealSpeech) {
                capsuleSynthesizer.synthesize(transcript, "Desk Visitor")
            } else {
                com.auradesk.guard.llm.SummarizedTask(
                    actionItem = "Desk visitor for ${durationSec}s",
                    rawTranscript = "",
                    urgencyLevel = if (isUrgent) com.auradesk.guard.llm.UrgencyLevel.CRITICAL else com.auradesk.guard.llm.UrgencyLevel.LOW
                )
            }
            _liveSynthesizedTask.value = synthesized

            val distanceStr = if (closestZoneInVisit == com.auradesk.guard.vision.RadarZone.CLOSE_05M) "0.5m (At Desk)" else "2.0m (Approached)"
            val summaryTitle = if (isRealSpeech && synthesized.actionItem.isNotBlank()) {
                synthesized.actionItem
            } else if (isRealSpeech) {
                "\"$transcript\""
            } else {
                "Desk visitor at $distanceStr for ${durationSec}s"
            }

            serviceScope.launch(Dispatchers.IO) {
                repository.insert(
                    com.auradesk.guard.data.InterruptionEntity(
                        personName = "Desk Visitor",
                        taskSummary = summaryTitle,
                        aiActionItem = synthesized.actionItem,
                        aiDeadline = synthesized.deadlineOrTime ?: "",
                        aiUrgencyReason = synthesized.urgencyReason ?: "",
                        targetComponent = synthesized.targetComponent ?: "",
                        rawTranscript = transcript,
                        hasVoiceTranscript = isRealSpeech,
                        contextSnippet = "Editing main.py at line 124",
                        distanceZone = distanceStr,
                        durationSec = durationSec,
                        isUrgent = isUrgent || synthesized.urgencyLevel == com.auradesk.guard.llm.UrgencyLevel.CRITICAL,
                        status = "NEW"
                    )
                )
            }
        }

        // Real-Time Radar Haptic Whisper & Auto-Capsule Bridge
        radarCollectJob?.cancel()
        radarCollectJob = serviceScope.launch {
            liveRadar.collect { radar ->
                val now = System.currentTimeMillis()
                if (radar.isPersonDetected) {
                    absentSinceTime = 0L // Reset absence debounce timer
                    if (visitStartTime == 0L) {
                        visitStartTime = now
                        closestZoneInVisit = radar.zone
                        Log.i(TAG, "Real-time visit started in zone: ${radar.zone.label}")
                    }

                    if (radar.zone == com.auradesk.guard.vision.RadarZone.CLOSE_05M) {
                        closestZoneInVisit = com.auradesk.guard.vision.RadarZone.CLOSE_05M
                        if (lastZoneVibrated != com.auradesk.guard.vision.RadarZone.CLOSE_05M || now - lastVibrationTime > 6000L) {
                            Log.i(TAG, "🚨 Triggering Real-Time Urgent Haptic Whisper (0.5m)")
                            // If actively recording speech, soften vibration to avoid acoustic mic rattle
                            if (!audioCapsuleManager.capsuleState.value.isRecording) {
                                feedbackManager.playHapticWhisperUrgent()
                            } else {
                                feedbackManager.playHapticWhisperLow()
                            }
                            lastZoneVibrated = com.auradesk.guard.vision.RadarZone.CLOSE_05M
                            lastVibrationTime = now
                            _latestHapticAlert.value = HapticAlert(
                                title = "Urgent Desk Alert",
                                detail = "Subject at desk • 0.5m",
                                zone = com.auradesk.guard.vision.RadarZone.CLOSE_05M,
                                timestamp = now
                            )
                        }

                        // Autonomous 10s Capsule Trigger on 0.5m Approach (if not already recording)
                        if (!audioCapsuleManager.capsuleState.value.isRecording && !hasCapturedCapsuleInCurrentVisit) {
                            deepWorkDetector.pauseListening()
                            hasCapturedCapsuleInCurrentVisit = true
                            audioCapsuleManager.start10SecCapsuleCapture("Radar 0.5m Proximity")
                        }
                    } else if (radar.zone == com.auradesk.guard.vision.RadarZone.MID_2M && radar.isApproaching) {
                        if (closestZoneInVisit != com.auradesk.guard.vision.RadarZone.CLOSE_05M) {
                            closestZoneInVisit = com.auradesk.guard.vision.RadarZone.MID_2M
                        }
                        if (lastZoneVibrated == com.auradesk.guard.vision.RadarZone.NONE || now - lastVibrationTime > 7000L) {
                            Log.i(TAG, "🔔 Triggering Real-Time Medium Haptic Whisper (2.0m approaching)")
                            if (!audioCapsuleManager.capsuleState.value.isRecording) {
                                feedbackManager.playHapticWhisperMedium()
                            }
                            lastZoneVibrated = com.auradesk.guard.vision.RadarZone.MID_2M
                            lastVibrationTime = now
                            _latestHapticAlert.value = HapticAlert(
                                title = "Approaching Alert",
                                detail = "Subject approaching • 2.0m",
                                zone = com.auradesk.guard.vision.RadarZone.MID_2M,
                                timestamp = now
                            )
                        }

                        // Autonomous 10s Capsule Trigger on 2.0m Approach (if not already recording)
                        if (!audioCapsuleManager.capsuleState.value.isRecording && !hasCapturedCapsuleInCurrentVisit) {
                            deepWorkDetector.pauseListening()
                            hasCapturedCapsuleInCurrentVisit = true
                            audioCapsuleManager.start10SecCapsuleCapture("Radar 2.0m Approach")
                        }
                    }
                } else if (visitStartTime > 0L) {
                    // Person not visible in current frame: require 3.0s continuous absence before concluding visit
                    if (absentSinceTime == 0L) {
                        absentSinceTime = now
                    }
                    if (now - absentSinceTime >= 3000L) {
                        // DO NOT interrupt active recording capsule in flight!
                        if (audioCapsuleManager.capsuleState.value.isRecording) {
                            return@collect
                        }

                        val durationSec = kotlin.math.max(1L, (now - visitStartTime - 3000L) / 1000L)
                        Log.i(TAG, "Real-time visit ended: duration=${durationSec}s, closestZone=${closestZoneInVisit.label}")

                        if (durationSec >= 2L && !hasCapturedCapsuleInCurrentVisit) {
                            val distanceStr = if (closestZoneInVisit == com.auradesk.guard.vision.RadarZone.CLOSE_05M) "0.5m (At Desk)" else "2.0m (Approached)"
                            val taskDesc = if (closestZoneInVisit == com.auradesk.guard.vision.RadarZone.CLOSE_05M) {
                                "Visitor arrived at desk for ${durationSec}s during focus session"
                            } else {
                                "Subject approached desk perimeter for ${durationSec}s"
                            }

                            serviceScope.launch(Dispatchers.IO) {
                                repository.insert(
                                    com.auradesk.guard.data.InterruptionEntity(
                                        personName = "Desk Visitor",
                                        taskSummary = taskDesc,
                                        contextSnippet = "Editing main.py at line 124",
                                        distanceZone = distanceStr,
                                        durationSec = durationSec,
                                        isUrgent = closestZoneInVisit == com.auradesk.guard.vision.RadarZone.CLOSE_05M
                                    )
                                )
                            }
                        }

                        visitStartTime = 0L
                        absentSinceTime = 0L
                        hasCapturedCapsuleInCurrentVisit = false
                        lastZoneVibrated = com.auradesk.guard.vision.RadarZone.NONE
                        closestZoneInVisit = com.auradesk.guard.vision.RadarZone.NONE
                    }
                }
            }
        }


        stateCollectJob?.cancel()
        stateCollectJob = serviceScope.launch {
            faceDownDetector.isFaceDown.collect { faceDown ->
                if (faceDown != lastArmedState) {
                    if (faceDown) {
                        Log.i(TAG, "Triggering Arm Audio/Haptic Feedback")
                        feedbackManager.playArmFeedback()
                        deepWorkDetector.startListening()
                        audioCapsuleManager.startListening()
                        if (ContextCompat.checkSelfPermission(this@GuardService, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            cameraRadarManager.startCamera(this@GuardService)
                        }
                    } else if (lastArmedState) {
                        Log.i(TAG, "Triggering Disarm Audio/Haptic Feedback")
                        feedbackManager.playDisarmFeedback()
                        deepWorkDetector.stopListening()
                        audioCapsuleManager.stopListening()
                        cameraRadarManager.stopCamera()
                    }
                    lastArmedState = faceDown
                }

                _isArmed.value = faceDown
                val title = if (faceDown) "AuraDesk Guard Armed" else "AuraDesk Guard Standby"
                val text = if (faceDown) "Focus Shield Active • Power <3%/hr" else "Place device face-down to arm"
                updateNotification(title, text)
            }
        }
    }

    private fun stopGuard() {
        _isRunning.value = false
        _isArmed.value = false
        lastArmedState = false
        faceDownDetector.stopListening()
        deepWorkDetector.stopListening()
        audioCapsuleManager.stopListening()
        cameraRadarManager.stopCamera()
        deepWorkDetectorInstance = null
        audioCapsuleManagerInstance = null
        sensorCollectJob?.cancel()
        stateCollectJob?.cancel()
        deepWorkCollectJob?.cancel()
        audioCapsuleCollectJob?.cancel()
        radarCollectJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.i(TAG, "GuardService stopped")
    }

    private fun createNotification(title: String, text: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, AuraDeskApp.CHANNEL_GUARD_SERVICE)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        notificationManager?.notify(NOTIFICATION_ID, createNotification(title, text))
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopGuard()
        cameraRadarManager.release()
        feedbackManager.release()
    }
}
