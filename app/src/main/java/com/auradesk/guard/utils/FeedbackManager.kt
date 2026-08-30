package com.auradesk.guard.utils

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

class FeedbackManager(private val context: Context) {

    companion object {
        private const val TAG = "FeedbackManager"
    }

    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            // Use STREAM_MUSIC with max volume so tones are always clearly audible
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ToneGenerator", e)
        }
    }

    /**
     * Feedback when Guard is ARMED (Face-Down on desk)
     * Plays ascending double-tone + double crisp haptic buzz
     */
    fun playArmFeedback() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 350)
        } catch (e: Exception) {
            Log.e(TAG, "Tone error on arm", e)
        }
        vibrate(longArrayOf(0, 120, 80, 160), intArrayOf(0, 220, 0, 255))
    }

    /**
     * Feedback when Guard is DISARMED (Phone picked up / moved)
     * Plays distinct descending release tone + solid vibration
     */
    fun playDisarmFeedback() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 300)
        } catch (e: Exception) {
            Log.e(TAG, "Tone error on disarm", e)
        }
        vibrate(longArrayOf(0, 180), intArrayOf(0, 220))
    }

    /**
     * Haptic Whisper: Low Priority (Far subject / Subtle ping)
     * Pattern: Short-Short pulse (80ms x 2)
     */
    fun playHapticWhisperLow() {
        vibrate(longArrayOf(0, 80, 60, 80), intArrayOf(0, 140, 0, 140))
    }

    /**
     * Haptic Whisper: Medium Priority (Teammate approaching 2m)
     * Pattern: Short-Long-Short pulse (100ms - 260ms - 100ms)
     */
    fun playHapticWhisperMedium() {
        vibrate(longArrayOf(0, 100, 80, 260, 80, 100), intArrayOf(0, 160, 0, 220, 0, 160))
    }

    /**
     * Haptic Whisper: Urgent / High Priority (Interruption at desk 0.5m)
     * Pattern: Long-Long-Long pulse (200ms x 3)
     */
    fun playHapticWhisperUrgent() {
        vibrate(longArrayOf(0, 200, 100, 200, 100, 200), intArrayOf(0, 255, 0, 255, 0, 255))
    }

    /**
     * Incinerate / Shake-to-Delete Confirmation
     * Rapid triple buzz confirming instant wipe
     */
    fun playIncinerateFeedback() {
        vibrate(longArrayOf(0, 60, 40, 60, 40, 160), intArrayOf(0, 180, 0, 200, 0, 255))
    }

    private fun vibrate(timings: LongArray, amplitudes: IntArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                vibratorManager?.vibrate(CombinedVibration.createParallel(effect))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                    vibrator?.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(timings, -1)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed", e)
        }
    }

    fun release() {
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {
            Log.e(TAG, "Tone release error", e)
        }
    }
}
