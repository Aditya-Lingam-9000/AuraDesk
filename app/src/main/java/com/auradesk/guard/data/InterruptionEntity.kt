package com.auradesk.guard.data

data class InterruptionEntity(
    val id: Long = 0,
    val personName: String,
    val taskSummary: String,
    val aiActionItem: String = "",
    val aiDeadline: String = "",
    val aiUrgencyReason: String = "",
    val targetComponent: String = "",
    val rawTranscript: String = "",
    val hasVoiceTranscript: Boolean = false,
    val contextSnippet: String = "Editing main.py at line 124",
    val distanceZone: String = "0.5m (At Desk)",
    val durationSec: Long = 6L,
    val timestamp: Long = System.currentTimeMillis(),
    val isUrgent: Boolean = false,
    val status: String = "NEW", // NEW, SAVED_TO_NOTES, DISMISSED
    val joviSynced: Boolean = false,
    val joviSyncTimestamp: Long = 0L
)
