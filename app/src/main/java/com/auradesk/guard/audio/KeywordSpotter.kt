package com.auradesk.guard.audio

import android.util.Log

class KeywordSpotter {

    companion object {
        private const val TAG = "KeywordSpotter"

        private val DEFAULT_TRIGGER_KEYWORDS = listOf(
            "arjun", "veerababu", "hey", "excuse me", "quick question",
            "have a minute", "check this", "need help", "listen", "mummy", "sir"
        )
    }

    private var customKeywords = mutableSetOf<String>().apply {
        addAll(DEFAULT_TRIGGER_KEYWORDS)
    }

    fun addCustomKeyword(keyword: String) {
        if (keyword.isNotBlank()) {
            customKeywords.add(keyword.trim().lowercase())
            Log.i(TAG, "Added trigger keyword: $keyword")
        }
    }

    fun checkKeyword(text: String): String? {
        if (text.isBlank()) return null
        val normalized = text.lowercase()

        for (kw in customKeywords) {
            if (normalized.contains(kw)) {
                Log.i(TAG, "🎯 Keyword Spotter Triggered: '$kw' in '$text'")
                return kw
            }
        }
        return null
    }

    fun getRegisteredKeywords(): List<String> = customKeywords.toList()
}
