package com.auradesk.guard.audio

import android.content.Context
import android.util.Log

class KeywordSpotter(private val context: Context? = null) {

    companion object {
        private const val TAG = "KeywordSpotter"

        private val DEFAULT_TRIGGER_KEYWORDS = listOf(
            "arjun", "veerababu", "hey", "excuse me", "quick question",
            "have a minute", "check this", "need help", "listen", "mummy", "sir", "boss"
        )
    }

    private var activeUserName: String = "Arjun"
    private var customKeywords = mutableSetOf<String>().apply {
        addAll(DEFAULT_TRIGGER_KEYWORDS)
    }

    init {
        context?.let { ctx ->
            try {
                val prefs = ctx.getSharedPreferences("auradesk_prefs", Context.MODE_PRIVATE)
                val savedName = prefs.getString("user_name", "Arjun") ?: "Arjun"
                setUserName(savedName)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load saved user name", e)
            }
        }
    }

    fun setUserName(name: String) {
        if (name.isBlank()) return
        activeUserName = name.trim()
        val lower = activeUserName.lowercase()
        customKeywords.add(lower)
        customKeywords.add("hey $lower")
        customKeywords.add("hi $lower")
        customKeywords.add("$lower's")
        Log.i(TAG, "👤 Configured user keyword spotter for name: '$activeUserName'")
    }

    fun getUserName(): String = activeUserName

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

    fun isUserNameCalled(text: String): Boolean {
        if (text.isBlank()) return false
        val lower = text.lowercase()
        return lower.contains(activeUserName.lowercase())
    }

    fun getRegisteredKeywords(): List<String> = customKeywords.toList()
}
