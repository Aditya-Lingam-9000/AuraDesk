package com.auradesk.guard.sensors

data class FaceDownSensors(
    val proximityCm: Float = 5f,
    val lightLux: Float = 100f,
    val accelX: Float = 0f,
    val accelY: Float = 0f,
    val accelZ: Float = 0f,
    val gyroMagnitude: Float = 0f,
    val isProximityNear: Boolean = false,
    val isLightDark: Boolean = false,
    val isZDownward: Boolean = false,
    val isGyroStable: Boolean = false,
    val isFaceDown: Boolean = false,
    val stabilityDurationMs: Long = 0L,
    val proximityType: String = "Hardware",
    val lastArmedDurationSec: Long = 0L,
    val totalArmSessions: Int = 0
)
