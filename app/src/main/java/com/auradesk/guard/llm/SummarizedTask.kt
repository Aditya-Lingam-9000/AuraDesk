package com.auradesk.guard.llm

enum class UrgencyLevel(val label: String, val colorHex: Long) {
    CRITICAL("🚨 CRITICAL", 0xFFEF4444),
    HIGH("⚠️ HIGH", 0xFFF59E0B),
    NORMAL("ℹ️ NORMAL", 0xFF38BDF8),
    LOW("💤 LOW", 0xFF64748B)
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
