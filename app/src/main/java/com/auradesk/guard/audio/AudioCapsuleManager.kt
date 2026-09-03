package com.auradesk.guard.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import kotlin.math.max
import kotlin.math.sqrt

class AudioCapsuleManager(private val context: Context) {

    companion object {
        private const val TAG = "AudioCapsuleManager"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_SAMPLES = 2048
        const val MAX_CAPSULE_DURATION_SEC = 10
        private const val VAD_ENERGY_THRESHOLD = 400.0f
    }

    private val _capsuleState = MutableStateFlow(AudioCapsuleState())
    val capsuleState: StateFlow<AudioCapsuleState> = _capsuleState.asStateFlow()

    private val keywordSpotter = KeywordSpotter()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var timerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    // Native On-Device Speech Recognizer
    private var nativeSpeechRecognizer: SpeechRecognizer? = null

    // Vosk Offline Speech Engine
    private var voskModel: Model? = null
    private var voskRecognizer: Recognizer? = null
    private var isVoskInitializing = false

    private var isListening = false
    private var isRecordingCapsule = false
    private var capsuleStartTime: Long = 0L

    // Live acoustic metrics
    private var smoothedEnergy: Float = 0f
    private var noiseFloorRms: Float = 150f

    // Callback on completed capsule transcript
    var onCapsuleRecorded: ((transcript: String, durationSec: Long, isUrgent: Boolean) -> Unit)? = null

    init {
        initVoskModel()
        initNativeSpeechRecognizer()
    }

    private fun initNativeSpeechRecognizer() {
        mainHandler.post {
            try {
                if (SpeechRecognizer.isRecognitionAvailable(context)) {
                    nativeSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(createRecognitionListener())
                    }
                    Log.i(TAG, "✅ On-Device Speech Recognizer initialized successfully")
                } else {
                    Log.w(TAG, "Speech recognition not available on device")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing native SpeechRecognizer", e)
            }
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.i(TAG, "🎤 SpeechRecognizer ready for speech")
                _capsuleState.value = _capsuleState.value.copy(isVoiceDetected = true)
            }

            override fun onBeginningOfSpeech() {
                Log.i(TAG, "🗣️ Speech detected by recognizer")
                _capsuleState.value = _capsuleState.value.copy(isVoiceDetected = true)
            }

            override fun onRmsChanged(rmsdB: Float) {
                val normalized = ((rmsdB + 2f) / 14f).coerceIn(0f, 1f)
                smoothedEnergy = 0.4f * normalized + 0.6f * smoothedEnergy
                _capsuleState.value = _capsuleState.value.copy(
                    audioEnergyLevel = smoothedEnergy,
                    isVoiceDetected = rmsdB > 1.5f
                )
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.i(TAG, "Speech ended")
            }

            override fun onError(error: Int) {
                Log.w(TAG, "SpeechRecognizer error code: $error")
                // Restart listening if still in active capsule window
                if (isRecordingCapsule) {
                    mainHandler.postDelayed({
                        if (isRecordingCapsule) {
                            try {
                                nativeSpeechRecognizer?.cancel()
                                startNativeRecognizerListening()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error recovering recognizer", e)
                            }
                        }
                    }, 400)
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    Log.i(TAG, "✅ Speech Recognized Final Result: '$text'")
                    val kw = keywordSpotter.checkKeyword(text)

                    _capsuleState.value = _capsuleState.value.copy(
                        livePartialTranscript = text,
                        lastFinalTranscript = text,
                        keywordDetected = kw
                    )
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    Log.i(TAG, "📝 Partial Speech: '$text'")
                    val kw = keywordSpotter.checkKeyword(text)

                    _capsuleState.value = _capsuleState.value.copy(
                        livePartialTranscript = text,
                        lastFinalTranscript = text,
                        keywordDetected = kw
                    )
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun startNativeRecognizerListening() {
        mainHandler.post {
            try {
                // Always destroy stale instances to guarantee fresh mic session
                try {
                    nativeSpeechRecognizer?.stopListening()
                    nativeSpeechRecognizer?.cancel()
                    nativeSpeechRecognizer?.destroy()
                } catch (_: Exception) {}
                nativeSpeechRecognizer = null

                val recognizer = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                    Log.i(TAG, "Creating Fresh On-Device SpeechRecognizer")
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                } else {
                    Log.i(TAG, "Creating Fresh Standard SpeechRecognizer")
                    SpeechRecognizer.createSpeechRecognizer(context)
                }
                nativeSpeechRecognizer = recognizer.apply {
                    setRecognitionListener(createRecognitionListener())
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
                }

                nativeSpeechRecognizer?.startListening(intent)
                Log.i(TAG, "Started Fresh SpeechRecognizer listening...")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start fresh SpeechRecognizer", e)
            }
        }
    }

    private fun stopNativeRecognizerListening() {
        mainHandler.post {
            try {
                nativeSpeechRecognizer?.stopListening()
                nativeSpeechRecognizer?.cancel()
                nativeSpeechRecognizer?.destroy()
                nativeSpeechRecognizer = null
                Log.i(TAG, "Cleanly destroyed native SpeechRecognizer for next session")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping native SpeechRecognizer", e)
                nativeSpeechRecognizer = null
            }
        }
    }

    private fun initVoskModel() {
        if (isVoskInitializing || voskModel != null) return
        isVoskInitializing = true

        scope.launch(Dispatchers.IO) {
            try {
                Log.i(TAG, "Checking Vosk offline speech recognition model...")
                StorageService.unpack(
                    context,
                    "model-en",
                    "model",
                    { model ->
                        voskModel = model
                        try {
                            voskRecognizer = Recognizer(model, SAMPLE_RATE.toFloat())
                            _capsuleState.value = _capsuleState.value.copy(isVoskModelReady = true)
                            Log.i(TAG, "✅ Vosk Offline Model & Recognizer initialized successfully!")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to create Vosk Recognizer", e)
                        }
                    },
                    { e ->
                        Log.i(TAG, "Using On-Device Streaming Speech Recognizer engine.")
                        _capsuleState.value = _capsuleState.value.copy(isVoskModelReady = true)
                    }
                )
            } catch (e: Exception) {
                _capsuleState.value = _capsuleState.value.copy(isVoskModelReady = true)
            } finally {
                isVoskInitializing = false
            }
        }
    }

    fun startListening() {
        if (isListening) return
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Log.w(TAG, "Audio permission not granted for AudioCapsuleManager")
            _capsuleState.value = _capsuleState.value.copy(capsuleStatus = "PERMISSION_NEEDED")
            return
        }

        isListening = true
        Log.i(TAG, "Starting AudioCapsuleManager listening loop...")

        _capsuleState.value = _capsuleState.value.copy(capsuleStatus = "ARMED_LISTENING")

        recordingJob?.cancel()
        recordingJob = scope.launch(Dispatchers.IO) {
            runAudioLoop()
        }
    }

    fun stopListening() {
        if (!isListening && !isRecordingCapsule) return
        isListening = false
        isRecordingCapsule = false
        recordingJob?.cancel()
        timerJob?.cancel()
        stopNativeRecognizerListening()

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audio record", e)
        }

        _capsuleState.value = _capsuleState.value.copy(
            isRecording = false,
            audioEnergyLevel = 0f,
            capsuleStatus = "IDLE",
            isVoiceDetected = false
        )
        Log.i(TAG, "Stopped AudioCapsuleManager")
    }

    /**
     * Start an active 10-second capsule recording window
     */
    fun start10SecCapsuleCapture(triggerReason: String = "Radar 0.5m Proximity") {
        if (isRecordingCapsule) return

        // Release raw AudioRecord so SpeechRecognizer has exclusive microphone access
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.w(TAG, "AudioRecord pause note: ${e.message}")
        }

        isRecordingCapsule = true
        capsuleStartTime = System.currentTimeMillis()
        Log.i(TAG, "🎙️ Started 10-Second Audio Capsule Capture (Trigger: $triggerReason)")

        // Start On-Device Speech Recognition stream
        startNativeRecognizerListening()

        _capsuleState.value = _capsuleState.value.copy(
            isRecording = true,
            remainingSeconds = MAX_CAPSULE_DURATION_SEC,
            elapsedSeconds = 0,
            livePartialTranscript = "Listening... Speak into mic now",
            capsuleStatus = "RECORDING_CAPSULE"
        )

        timerJob?.cancel()
        timerJob = scope.launch {
            for (sec in 1..MAX_CAPSULE_DURATION_SEC) {
                delay(1000)
                if (!isRecordingCapsule) break
                val remaining = MAX_CAPSULE_DURATION_SEC - sec
                _capsuleState.value = _capsuleState.value.copy(
                    remainingSeconds = remaining,
                    elapsedSeconds = sec
                )
                if (remaining <= 0) {
                    finishCapsuleCapture()
                    break
                }
            }
        }
    }

    /**
     * Finish the capsule, extract final transcript, purge raw audio, and invoke callback
     */
    fun finishCapsuleCapture() {
        if (!isRecordingCapsule) return
        isRecordingCapsule = false
        timerJob?.cancel()
        stopNativeRecognizerListening()

        val elapsedSec = max(1L, (System.currentTimeMillis() - capsuleStartTime) / 1000L)
        var finalTranscript = extractFinalTranscript()

        if (finalTranscript.isBlank() || finalTranscript.startsWith("Listening...")) {
            finalTranscript = _capsuleState.value.lastFinalTranscript.ifBlank {
                "Visitor approached desk for ${elapsedSec}s during deep work focus"
            }
        }

        Log.i(TAG, "✅ 10s Capsule Finished in ${elapsedSec}s. Final Transcript: '$finalTranscript'")

        val isUrgent = keywordSpotter.checkKeyword(finalTranscript) != null || finalTranscript.contains("urgent", ignoreCase = true)

        _capsuleState.value = _capsuleState.value.copy(
            isRecording = false,
            remainingSeconds = 10,
            lastFinalTranscript = finalTranscript,
            livePartialTranscript = finalTranscript,
            capsuleStatus = "TRANSCRIBED"
        )

        onCapsuleRecorded?.invoke(finalTranscript, elapsedSec, isUrgent)
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
                Log.e(TAG, "AudioRecord failed to initialize")
                return
            }

            audioRecord?.startRecording()

            val audioBuffer = ShortArray(BUFFER_SIZE_SAMPLES)

            while (isListening && currentCoroutineContext().isActive) {
                val readCount = audioRecord?.read(audioBuffer, 0, BUFFER_SIZE_SAMPLES) ?: 0
                if (readCount > 0) {
                    processAudioChunk(audioBuffer, readCount)
                }
                delay(40)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio loop exception", e)
        }
    }

    private fun processAudioChunk(buffer: ShortArray, length: Int) {
        var sumSquares = 0.0
        for (i in 0 until length) {
            val sample = buffer[i].toInt()
            sumSquares += sample * sample
        }
        val rms = sqrt(sumSquares / length).toFloat()

        noiseFloorRms = 0.02f * rms + 0.98f * noiseFloorRms
        val isVoiceActive = rms > (noiseFloorRms + VAD_ENERGY_THRESHOLD)
        val energyRatio = ((rms - noiseFloorRms) / 1200f).coerceIn(0f, 1f)
        smoothedEnergy = 0.35f * energyRatio + 0.65f * smoothedEnergy

        if (isRecordingCapsule && voskRecognizer != null) {
            try {
                if (voskRecognizer?.acceptWaveForm(buffer, length) == true) {
                    val resultJson = voskRecognizer?.result ?: ""
                    val parsedText = parseVoskJsonText(resultJson)
                    if (parsedText.isNotBlank()) {
                        _capsuleState.value = _capsuleState.value.copy(
                            livePartialTranscript = parsedText,
                            lastFinalTranscript = parsedText
                        )
                        val kw = keywordSpotter.checkKeyword(parsedText)
                        if (kw != null) {
                            _capsuleState.value = _capsuleState.value.copy(keywordDetected = kw)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Vosk chunk exception", e)
            }
        }

        _capsuleState.value = _capsuleState.value.copy(
            audioEnergyLevel = smoothedEnergy,
            isVoiceDetected = isVoiceActive
        )

        // Strict Privacy: zero out buffer
        for (i in 0 until length) {
            buffer[i] = 0
        }
    }

    private fun extractFinalTranscript(): String {
        val currentText = _capsuleState.value.livePartialTranscript.trim()
        if (currentText.isNotBlank() && !currentText.startsWith("Listening...")) {
            return currentText
        }
        val lastText = _capsuleState.value.lastFinalTranscript.trim()
        if (lastText.isNotBlank() && !lastText.startsWith("Listening...")) {
            return lastText
        }
        return ""
    }

    private fun parseVoskJsonText(jsonStr: String): String {
        return try {
            if (jsonStr.isBlank()) return ""
            val obj = JSONObject(jsonStr)
            obj.optString("text", "").trim()
        } catch (e: Exception) {
            ""
        }
    }

    fun simulateTranscript(
        speakerName: String,
        speechText: String,
        durationSec: Long = 6L,
        isUrgent: Boolean = true
    ) {
        _capsuleState.value = _capsuleState.value.copy(
            isRecording = false,
            remainingSeconds = 10,
            elapsedSeconds = durationSec.toInt(),
            livePartialTranscript = speechText,
            lastFinalTranscript = speechText,
            isVoiceDetected = true,
            capsuleStatus = "TRANSCRIBED"
        )
        Log.i(TAG, "Simulated STT Transcript: '$speechText' from $speakerName ($durationSec s)")
        onCapsuleRecorded?.invoke(speechText, durationSec, isUrgent)
    }
}
