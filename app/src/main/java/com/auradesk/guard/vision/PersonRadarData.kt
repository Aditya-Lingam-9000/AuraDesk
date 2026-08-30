package com.auradesk.guard.vision

enum class RadarZone(val label: String, val colorHex: Long) {
    NONE("Clear Area", 0xFF64748B),
    FAR_5M("Far (~5m)", 0xFF38BDF8),
    MID_2M("Approaching (~2m)", 0xFFFBBF24),
    CLOSE_05M("At Desk (~0.5m)", 0xFFEF4444)
}

data class PersonRadarData(
    val isPersonDetected: Boolean = false,
    val distanceMeters: Float = 0f,
    val isApproaching: Boolean = false,
    val growthRatePercentPerSec: Float = 0f,
    val boxRelativeHeight: Float = 0f,
    val centroidX: Float = 0.5f,
    val centroidY: Float = 0.5f,
    val leftRel: Float = 0f,
    val topRel: Float = 0f,
    val rightRel: Float = 0f,
    val bottomRel: Float = 0f,
    val zone: RadarZone = RadarZone.NONE,
    val confidence: Float = 0f,
    val lastDetectionTimestamp: Long = 0L,
    val fps: Float = 10.0f
)
