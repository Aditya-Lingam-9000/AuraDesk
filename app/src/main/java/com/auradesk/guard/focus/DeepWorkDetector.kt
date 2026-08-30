package com.auradesk.guard.focus

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

class DeepWorkDetector(private val context: Context) {

    companion object {
        private const val TAG = "DeepWorkDetector"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_SAMPLES = 1024
    }

    private val _deepWorkState = MutableStateFlow(DeepWorkState())
    val deepWorkState: StateFlow<DeepWorkState> = _deepWorkState.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private var isListening = false
    private var currentProfile = EnvironmentProfile.AUTO_ADAPTIVE

    // Tracking state
    private var sessionStartTime: Long = 0L
    private var noiseFloorRms = 200.0f
    private val tapTimestamps = ArrayDeque<Long>()
    private var smoothedEnergy = 0f
    private var smoothedScore = 0f

    fun startListening() {
        if (isListening) return
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Log.w(TAG, "Audio permission not granted for DeepWorkDetector")
            return
        }

        isListening = true
        sessionStartTime = System.currentTimeMillis()
        tapTimestamps.clear()
        smoothedEnergy = 0f
        smoothedScore = 0f
        Log.i(TAG, "Starting DeepWorkDetector listening...")

        recordingJob?.cancel()
        recordingJob = scope.launch(Dispatchers.IO) {
            runAudioLoop()
        }
    }

    fun stopListening() {
        if (!isListening) return
        isListening = false
        recordingJob?.cancel()
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audio record", e)
        }
        _deepWorkState.value = _deepWorkState.value.copy(
            isAudioListening = false,
            focusStateLabel = "Idle Desk 💤"
        )
        Log.i(TAG, "Stopped DeepWorkDetector")
    }

    fun setEnvironmentProfile(profile: EnvironmentProfile) {
        currentProfile = profile
        _deepWorkState.value = _deepWorkState.value.copy(environmentProfile = profile)
    }

    @SuppressLint("MissingPermission")
    private suspend fun runAudioLoop() {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = max(minBufferSize, BUFFER_SIZE_SAMPLES * 2)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                return
            }

            audioRecord?.startRecording()
            _deepWorkState.value = _deepWorkState.value.copy(isAudioListening = true)

            val audioBuffer = ShortArray(BUFFER_SIZE_SAMPLES)

            while (isListening && currentCoroutineContext().isActive) {
                val readCount = audioRecord?.read(audioBuffer, 0, BUFFER_SIZE_SAMPLES) ?: 0
                if (readCount > 0) {
                    processAudioChunk(audioBuffer, readCount)
                }
                delay(60)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio processing exception", e)
        }
    }

    private fun processAudioChunk(buffer: ShortArray, length: Int) {
        val now = System.currentTimeMillis()

        // Compute RMS Energy
        var sumSquares = 0.0
        var maxVal = 0
        for (i in 0 until length) {
            val sample = buffer[i].toInt()
            sumSquares += sample * sample
            val absSample = abs(sample)
            if (absSample > maxVal) maxVal = absSample
        }

        val rms = kotlin.math.sqrt(sumSquares / length).toFloat()

        // Dynamic Noise Floor Auto-Calibration (Slow exponential moving average)
        noiseFloorRms = if (rms < noiseFloorRms * 1.5f) {
            0.05f * rms + 0.95f * noiseFloorRms
        } else {
            0.01f * rms + 0.99f * noiseFloorRms
        }

        val noiseDb = (20 * log10(max(noiseFloorRms, 1.0f))).coerceIn(20f, 90f)

        // Pulse Detection for Quiet Laptop Keys / Trackpad Taps:
        // Detects transient bursts exceeding the baseline noise floor
        val thresholdMultiplier = when (currentProfile) {
            EnvironmentProfile.QUIET_LAPTOP -> 1.35f
            EnvironmentProfile.LIBRARY_SILENCE -> 1.20f
            EnvironmentProfile.CAFE_OFFICE -> 2.20f
            EnvironmentProfile.AUTO_ADAPTIVE -> 1.50f
        }

        val isTapPulse = (rms > noiseFloorRms * thresholdMultiplier) && (maxVal > 300)

        if (isTapPulse) {
            tapTimestamps.addLast(now)
        }

        // Clean up taps older than 6 seconds to compute rolling cadence
        while (tapTimestamps.isNotEmpty() && now - tapTimestamps.first() > 6000L) {
            tapTimestamps.removeFirst()
        }

        // Keystrokes / Taps per minute (BPM)
        val cadenceBpm = (tapTimestamps.size / 6.0f) * 60.0f

        // Energy ratio above baseline (0.0 to 1.0)
        val energyRatio = ((rms - noiseFloorRms) / max(noiseFloorRms, 100f)).coerceIn(0f, 1f)
        smoothedEnergy = 0.2f * energyRatio + 0.8f * smoothedEnergy

        val focusDurationSec = if (sessionStartTime > 0L) (now - sessionStartTime) / 1000L else 0L

        // Focus Score Fusion:
        // 1. Cadence Factor: Rhythmic typing/clicking (optimal 60-180 BPM)
        val cadenceScore = when {
            cadenceBpm in 40f..260f -> 1.0f
            cadenceBpm > 0f -> 0.6f
            else -> 0.0f
        }

        // 2. Focused Silence / Reading Factor: In library or quiet room, low noise floor without loud vocal bursts
        val silenceScore = if (noiseDb < 55f && cadenceBpm == 0f) 0.70f else 0.20f

        // 3. Time Duration Ramp Factor: Longer uninterrupted time increases deep work confidence
        val durationScore = min((focusDurationSec / 120.0f), 1.0f) // Max out at 2 minutes of focus

        val rawFocusScore = when (currentProfile) {
            EnvironmentProfile.LIBRARY_SILENCE -> (silenceScore * 0.5f + durationScore * 0.4f + cadenceScore * 0.1f) * 100f
            EnvironmentProfile.QUIET_LAPTOP -> (cadenceScore * 0.55f + durationScore * 0.35f + silenceScore * 0.1f) * 100f
            else -> (cadenceScore * 0.45f + durationScore * 0.35f + silenceScore * 0.2f) * 100f
        }

        smoothedScore = 0.15f * rawFocusScore + 0.85f * smoothedScore
        val finalScore = smoothedScore.toInt().coerceIn(0, 100)

        val isDeepWork = finalScore >= 55 || (focusDurationSec >= 60L && noiseDb < 60f)

        val stateLabel = when {
            isDeepWork -> "⚡ Deep Work Active • Focus Protected"
            finalScore >= 30 -> "⏳ Focus Ramping Up..."
            else -> "💤 Idle / Ambient Desk"
        }

        _deepWorkState.value = DeepWorkState(
            isDeepWork = isDeepWork,
            focusScore = finalScore,
            typingCadenceBpm = cadenceBpm,
            acousticEnergyLevel = smoothedEnergy,
            noiseFloorDb = noiseDb,
            uninterruptedFocusDurationSec = focusDurationSec,
            focusStateLabel = stateLabel,
            environmentProfile = currentProfile,
            isAudioListening = true
        )
    }

    /**
     * Interactive Simulation method for demo stage presentations and testing
     */
    fun simulate(
        isDeepWork: Boolean,
        focusScore: Int,
        cadenceBpm: Float,
        profile: EnvironmentProfile = EnvironmentProfile.QUIET_LAPTOP
    ) {
        val label = if (isDeepWork) "⚡ Deep Work Active • Focus Protected" else "💤 Idle / Ambient Desk"
        _deepWorkState.value = DeepWorkState(
            isDeepWork = isDeepWork,
            focusScore = focusScore,
            typingCadenceBpm = cadenceBpm,
            acousticEnergyLevel = if (isDeepWork) 0.65f else 0.05f,
            noiseFloorDb = 38f,
            uninterruptedFocusDurationSec = if (isDeepWork) 420L else 0L,
            focusStateLabel = label,
            environmentProfile = profile,
            isAudioListening = true
        )
        Log.i(TAG, "Simulated Deep Work State: Score=$focusScore%, Cadence=$cadenceBpm BPM, Active=$isDeepWork")
    }

    fun reset() {
        tapTimestamps.clear()
        sessionStartTime = System.currentTimeMillis()
        smoothedEnergy = 0f
        smoothedScore = 0f
        _deepWorkState.value = DeepWorkState(environmentProfile = currentProfile)
    }
}
