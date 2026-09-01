package com.auradesk.guard.audio

data class AudioCapsuleState(
    val isRecording: Boolean = false,
    val remainingSeconds: Int = 10,
    val elapsedSeconds: Int = 0,
    val audioEnergyLevel: Float = 0f, // 0.0 to 1.0 normalized RMS for live visualizer
    val livePartialTranscript: String = "",
    val lastFinalTranscript: String = "",
    val isVoiceDetected: Boolean = false, // VAD: Speech vs silence
    val keywordDetected: String? = null,
    val capsuleStatus: String = "IDLE", // IDLE, ARMED_LISTENING, RECORDING_CAPSULE, TRANSCRIBED
    val isVoskModelReady: Boolean = false
)
