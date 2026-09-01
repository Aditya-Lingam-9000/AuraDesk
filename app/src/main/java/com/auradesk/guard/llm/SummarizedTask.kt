package com.auradesk.guard.llm

enum class UrgencyLevel(val label: String, val colorHex: Long) {
    CRITICAL("CRITICAL", 0xFFDC2626),
    HIGH("HIGH", 0xFFD97706),
    NORMAL("NORMAL", 0xFF1D4ED8),
    LOW("LOW", 0xFF64748B)
}

data class SummarizedTask(
    val actionItem: String,
    val rawTranscript: String,
    val deadlineOrTime: String? = null,
    val urgencyLevel: UrgencyLevel = UrgencyLevel.NORMAL,
    val urgencyReason: String? = null,
    val targetComponent: String? = null, // e.g. "Auth / Staging", "Figma", "PR Review"
    val joviNotesPayload: String = ""
)
