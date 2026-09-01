package com.auradesk.guard.privacy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DutyCycleTier(val label: String, val fps: Float, val powerDrainRate: String, val colorHex: Long) {
    IDLE_SLEEP("💤 Ultra-Low Sleep (Sensors Only)", 0.5f, "1.8% / hr", 0xFF00E676),
    APPROACH_RADAR("⚡ Approach Radar (Medium Sampling)", 6.0f, "2.6% / hr", 0xFF38BDF8),
    ACTIVE_CAPSULE("🎙️ Active Capsule (Full Audio + Vision)", 10.0f, "3.4% / hr (10s burst)", 0xFFF59E0B)
}

data class PowerTelemetry(
    val currentTier: DutyCycleTier = DutyCycleTier.IDLE_SLEEP,
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val estimatedDrainPerHour: Float = 2.4f, // in %/hr
    val oledPowerSavingActive: Boolean = true,
    val wakeLockHeld: Boolean = false,
    val uptimeMinutes: Long = 0
)

class PowerManagerGuard(private val context: Context) {

    companion object {
        private const val TAG = "PowerManagerGuard"

        @Volatile
        private var instance: PowerManagerGuard? = null

        fun getInstance(context: Context): PowerManagerGuard {
            return instance ?: synchronized(this) {
                instance ?: PowerManagerGuard(context.applicationContext).also { instance = it }
            }
        }
    }

    private val _telemetry = MutableStateFlow(PowerTelemetry())
    val telemetry: StateFlow<PowerTelemetry> = _telemetry.asStateFlow()

    private val startTime = System.currentTimeMillis()

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

                val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 100
                updateBatteryStatus(pct, isCharging)
            }
        }
    }

    init {
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            context.registerReceiver(batteryReceiver, filter)
            readInitialBattery()
        } catch (e: Exception) {
            Log.w(TAG, "Battery receiver register note: ${e.message}")
        }
    }

    private fun readInitialBattery() {
        try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (pct in 1..100) {
                updateBatteryStatus(pct, false)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Initial battery read note: ${e.message}")
        }
    }

    private fun updateBatteryStatus(pct: Int, charging: Boolean) {
        val uptime = (System.currentTimeMillis() - startTime) / (60 * 1000L)
        val drain = when (_telemetry.value.currentTier) {
            DutyCycleTier.IDLE_SLEEP -> 1.8f
            DutyCycleTier.APPROACH_RADAR -> 2.6f
            DutyCycleTier.ACTIVE_CAPSULE -> 3.2f
        }

        _telemetry.value = _telemetry.value.copy(
            batteryPercent = pct,
            isCharging = charging,
            estimatedDrainPerHour = drain,
            uptimeMinutes = uptime
        )
    }

    fun setDutyCycleTier(tier: DutyCycleTier) {
        if (_telemetry.value.currentTier != tier) {
            Log.i(TAG, "⚡ Switched Duty Cycle Tier: ${tier.label} (${tier.powerDrainRate})")
            val drain = when (tier) {
                DutyCycleTier.IDLE_SLEEP -> 1.8f
                DutyCycleTier.APPROACH_RADAR -> 2.6f
                DutyCycleTier.ACTIVE_CAPSULE -> 3.2f
            }
            _telemetry.value = _telemetry.value.copy(
                currentTier = tier,
                estimatedDrainPerHour = drain
            )
        }
    }

    fun cleanup() {
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // Ignored
        }
    }
}
