package com.auradesk.guard.llm

import android.util.Log
import java.util.Locale
import java.util.regex.Pattern

class CapsuleSynthesizer {

    companion object {
        private const val TAG = "CapsuleSynthesizer"

        private val CONVERSATIONAL_FILLERS = listOf(
            "(?i)^hey\\s+([a-zA-Z]+[,\\s]*)?",
            "(?i)^hi\\s+([a-zA-Z]+[,\\s]*)?",
            "(?i)^hello\\s+([a-zA-Z]+[,\\s]*)?",
            "(?i)^excuse\\s+me[,\\s]*",
            "(?i)^quick\\s+question[,\\s]*",
            "(?i)^do\\s+you\\s+have\\s+(a\\s+minute|2\\s+mins|time)[,\\s]*",
            "(?i)^can\\s+you\\s+(please\\s+)?",
            "(?i)^could\\s+you\\s+(please\\s+)?",
            "(?i)^please\\s+",
            "(?i)^i\\s+wanted\\s+to\\s+ask\\s+(if\\s+)?"
        )

        private val DEADLINE_PATTERNS = listOf(
            Pattern.compile("(?i)\\b(before\\s+\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?(?:\\s+sprint\\s+demo|\\s+meeting)?)\\b"),
            Pattern.compile("(?i)\\b(by\\s+(?:eod|end\\s+of\\s+day|today|tomorrow|noon|lunch|\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?))\\b"),
            Pattern.compile("(?i)\\b(in\\s+\\d+\\s*(?:mins|minutes|hours|days))\\b"),
            Pattern.compile("(?i)\\b(at\\s+\\d{1,2}(?::\\d{2})?\\s*(?:am|pm))\\b"),
            Pattern.compile("(?i)\\b(today|tomorrow|asap|right\\s+now|urgent)\\b")
        )

        private val CRITICAL_KEYWORDS = listOf(
            "failing", "broken", "500 error", "crash", "urgent", "asap",
            "production", "prod", "blocker", "critical", "outage", "down"
        )

        private val HIGH_KEYWORDS = listOf(
            "before", "demo", "review", "deploy", "release", "meeting", "client", "pr", "pull request"
        )
    }

    fun synthesize(rawTranscript: String, speakerName: String = "Desk Visitor"): SummarizedTask {
        val trimmed = rawTranscript.trim()
        if (trimmed.isBlank()) {
            return SummarizedTask(
                actionItem = "Quick desk check-in / No action requested",
                rawTranscript = "",
                urgencyLevel = UrgencyLevel.LOW,
                joviNotesPayload = "• Interruption logged during focus session."
            )
        }

        Log.i(TAG, "🤖 LLM Synthesizer processing transcript: '$trimmed'")

        // 1. Extract Deadlines
        var extractedDeadline: String? = null
        for (pattern in DEADLINE_PATTERNS) {
            val matcher = pattern.matcher(trimmed)
            if (matcher.find()) {
                extractedDeadline = matcher.group(1)?.replaceFirstChar { it.uppercase() }
                break
            }
        }

        // 2. Extract Clean Action Item
        var cleanAction = trimmed
        for (fillerRegex in CONVERSATIONAL_FILLERS) {
            cleanAction = cleanAction.replaceFirst(Regex(fillerRegex), "")
        }
        cleanAction = cleanAction.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        if (cleanAction.endsWith(".")) {
            cleanAction = cleanAction.dropLast(1)
        }

        // 3. Classify Urgency Level & Reason
        val lower = trimmed.lowercase(Locale.ROOT)
        val (urgency, reason) = when {
            CRITICAL_KEYWORDS.any { lower.contains(it) } -> {
                val matched = CRITICAL_KEYWORDS.first { lower.contains(it) }
                UrgencyLevel.CRITICAL to "High priority trigger keyword detected: '$matched'"
            }
            HIGH_KEYWORDS.any { lower.contains(it) } -> {
                val matched = HIGH_KEYWORDS.first { lower.contains(it) }
                UrgencyLevel.HIGH to "Time-sensitive reference: '$matched'"
            }
            extractedDeadline != null -> {
                UrgencyLevel.HIGH to "Has explicit deadline: '$extractedDeadline'"
            }
            else -> {
                UrgencyLevel.NORMAL to "Standard task request"
            }
        }

        // 4. Detect Target Technical Component
        val targetComponent = when {
            lower.contains("auth") || lower.contains("login") || lower.contains("token") -> "Auth / Security"
            lower.contains("api") || lower.contains("endpoint") || lower.contains("backend") -> "Backend API"
            lower.contains("staging") || lower.contains("prod") || lower.contains("deploy") -> "DevOps / Staging"
            lower.contains("pr") || lower.contains("pull request") || lower.contains("review") -> "Code Review"
            lower.contains("figma") || lower.contains("design") || lower.contains("ui") -> "UI / Design"
            lower.contains("database") || lower.contains("db") || lower.contains("sql") -> "Database"
            else -> "General Task"
        }

        // 5. Build Formatted Jovi Notes Sync Payload
        val joviNotes = buildString {
            append("### 📌 Task: $cleanAction\n")
            append("- **From:** $speakerName\n")
            if (extractedDeadline != null) {
                append("- **Deadline:** $extractedDeadline\n")
            }
            append("- **Priority:** ${urgency.label}\n")
            append("- **Component:** $targetComponent\n")
            append("- **Visitor Quote:** \"$trimmed\"\n")
        }

        Log.i(TAG, "✅ Synthesized Action: '$cleanAction' [${urgency.label}] (Deadline: $extractedDeadline)")

        return SummarizedTask(
            actionItem = cleanAction.ifBlank { "Request: \"$trimmed\"" },
            rawTranscript = trimmed,
            deadlineOrTime = extractedDeadline,
            urgencyLevel = urgency,
            urgencyReason = reason,
            targetComponent = targetComponent,
            joviNotesPayload = joviNotes
        )
    }
}
