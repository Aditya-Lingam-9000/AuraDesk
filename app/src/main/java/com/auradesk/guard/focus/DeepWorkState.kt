package com.auradesk.guard.focus

enum class EnvironmentProfile(val label: String, val sensitivityThreshold: Float) {
    AUTO_ADAPTIVE("Auto-Adaptive", 0.08f),
    QUIET_LAPTOP("Quiet Laptop / Trackpad", 0.04f),
    LIBRARY_SILENCE("Silent Study / Reading", 0.02f),
    CAFE_OFFICE("Cafe / Open Office", 0.15f)
}

data class DeepWorkState(
    val isDeepWork: Boolean = false,
    val focusScore: Int = 0, // 0 to 100%
    val typingCadenceBpm: Float = 0f, // Estimated taps/keystrokes per minute
    val acousticEnergyLevel: Float = 0f, // 0.0 to 1.0
    val noiseFloorDb: Float = 35f, // Baseline ambient dB
    val uninterruptedFocusDurationSec: Long = 0L,
    val focusStateLabel: String = "Idle Desk 💤",
    val environmentProfile: EnvironmentProfile = EnvironmentProfile.AUTO_ADAPTIVE,
    val isAudioListening: Boolean = false
)
