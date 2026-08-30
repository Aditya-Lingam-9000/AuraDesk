package com.auradesk.guard.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class FaceDownDetector(context: Context) : SensorEventListener {

    companion object {
        private const val TAG = "FaceDownDetector"
        private const val STABILITY_REQUIRED_MS = 1000L // 1.0 second sustained stability
        private const val MAX_LIGHT_LUX = 2.0f // Only dark table (0-1 lux), strictly rejects any room light
        private const val MAX_PROXIMITY_CM = 1.5f
        private const val MIN_Z_DOWNWARD = -7.5f // Facing down: z is negative on Android
        private const val MAX_GYRO_STABLE_RAD_S = 0.25f
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    // Support wake-up sensor on modern Android devices (Vivo / Snapdragon)
    private val proximitySensor: Sensor? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY, true)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    } else {
        sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    }

    private val lightSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val accelSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val _sensorState = MutableStateFlow(FaceDownSensors())
    val sensorState: StateFlow<FaceDownSensors> = _sensorState.asStateFlow()

    private val _isFaceDown = MutableStateFlow(false)
    val isFaceDown: StateFlow<Boolean> = _isFaceDown.asStateFlow()

    private var isListening = false
    private var conditionSatisfiedSince: Long = 0L
    private var armedStartTime: Long = 0L
    private var lastArmedDurationSec: Long = 0L
    private var totalArmSessions: Int = 0

    private var monitorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    // Current raw values
    private var currentProximity = proximitySensor?.maximumRange ?: 5f
    private var hasReceivedProximityEvent = false
    private var currentLight = 100f
    private var currentAccelX = 0f
    private var currentAccelY = 0f
    private var currentAccelZ = 0f
    private var currentGyroMagnitude = 0f

    fun startListening() {
        if (isListening || sensorManager == null) return
        isListening = true
        Log.i(TAG, "Starting FaceDownDetector sensor listening... Proximity: ${proximitySensor?.name ?: "None"}")

        val delay = SensorManager.SENSOR_DELAY_GAME
        try {
            proximitySensor?.let { sensorManager.registerListener(this, it, delay) }
        } catch (e: Exception) {
            Log.e(TAG, "Proximity listener registration error", e)
        }
        try {
            lightSensor?.let { sensorManager.registerListener(this, it, delay) }
        } catch (e: Exception) {
            Log.e(TAG, "Light listener registration error", e)
        }
        try {
            accelSensor?.let { sensorManager.registerListener(this, it, delay) }
        } catch (e: Exception) {
            Log.e(TAG, "Accel listener registration error", e)
        }
        try {
            gyroSensor?.let { sensorManager.registerListener(this, it, delay) }
        } catch (e: Exception) {
            Log.e(TAG, "Gyro listener registration error", e)
        }

        startStabilityMonitor()
    }

    fun stopListening() {
        if (!isListening) return
        isListening = false
        monitorJob?.cancel()
        sensorManager?.unregisterListener(this)
        conditionSatisfiedSince = 0L
        _isFaceDown.value = false
        Log.i(TAG, "Stopped FaceDownDetector")
    }

    private fun startStabilityMonitor() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (isActive) {
                evaluateFaceDownState()
                delay(80)
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> {
                hasReceivedProximityEvent = true
                currentProximity = event.values[0]
                Log.d(TAG, "Proximity event received: $currentProximity cm (max: ${proximitySensor?.maximumRange})")
            }
            Sensor.TYPE_LIGHT -> {
                currentLight = event.values[0]
            }
            Sensor.TYPE_ACCELEROMETER -> {
                currentAccelX = event.values[0]
                currentAccelY = event.values[1]
                currentAccelZ = event.values[2]
            }
            Sensor.TYPE_GYROSCOPE -> {
                val gx = event.values[0]
                val gy = event.values[1]
                val gz = event.values[2]
                currentGyroMagnitude = sqrt(gx * gx + gy * gy + gz * gz)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }

    private fun evaluateFaceDownState() {
        val maxProx = proximitySensor?.maximumRange ?: 5f
        val isLightDark = currentLight <= MAX_LIGHT_LUX // <= 2.0 lux
        // Face down means phone is resting on its screen: gravity pulls downwards, so Z acceleration is negative
        val isZDownward = currentAccelZ <= MIN_Z_DOWNWARD && Math.abs(currentAccelX) < 4.5f && Math.abs(currentAccelY) < 4.5f
        // Gyro is stable if magnitude is low (or if gyro not available, assume stable if not moving violently)
        val isGyroStable = gyroSensor == null || currentGyroMagnitude <= MAX_GYRO_STABLE_RAD_S

        // Seamless Multi-Session Re-Arming:
        // Proximity is Near if hardware sensor reports near, OR if optical occlusion (dark desk + Z downward)
        val isHardwareProxNear = hasReceivedProximityEvent && (currentProximity <= MAX_PROXIMITY_CM || (currentProximity < maxProx && maxProx <= 1f))
        val isOpticalOccluded = isLightDark && isZDownward
        val isProxNear = isHardwareProxNear || isOpticalOccluded
        val displayProx = if (isProxNear) 0.0f else (if (hasReceivedProximityEvent) currentProximity else 5.0f)
        val proxTypeLabel = if (isHardwareProxNear) "Hardware (${proximitySensor?.name?.take(12) ?: "Sensor"})" else "Optical Fused (0 lux)"

        val instantaneousArmCondition = isProxNear && isLightDark && isZDownward && isGyroStable
        val now = System.currentTimeMillis()
        var stabilityTime = 0L

        if (_isFaceDown.value) {
            // Once Armed: Hold state as long as phone is face-down on table (Z <= -5.5 m/s² and Light <= 4.0 lux)
            val isStillFaceDown = currentAccelZ <= -5.5f && currentLight <= 4.0f
            if (!isStillFaceDown) {
                if (armedStartTime > 0L) {
                    lastArmedDurationSec = (now - armedStartTime) / 1000L
                }
                Log.i(TAG, ">>> Guard Disarmed: Phone picked up / moved after ${lastArmedDurationSec}s (Z=$currentAccelZ, Light=$currentLight) <<<")
                _isFaceDown.value = false
                armedStartTime = 0L
                conditionSatisfiedSince = 0L
            }
        } else {
            // In Standby: Require 1000ms of sustained stillness while face-down to ARM
            if (instantaneousArmCondition) {
                if (conditionSatisfiedSince == 0L) {
                    conditionSatisfiedSince = now
                }
                stabilityTime = now - conditionSatisfiedSince
                if (stabilityTime >= 1000L) {
                    _isFaceDown.value = true
                    armedStartTime = now
                    totalArmSessions++
                    Log.i(TAG, ">>> Guard Armed: Phone is Face-Down on desk (Session #$totalArmSessions, stable for ${stabilityTime}ms) <<<")
                }
            } else {
                conditionSatisfiedSince = 0L
            }
        }

        _sensorState.value = FaceDownSensors(
            proximityCm = displayProx,
            lightLux = currentLight,
            accelX = currentAccelX,
            accelY = currentAccelY,
            accelZ = currentAccelZ,
            gyroMagnitude = currentGyroMagnitude,
            isProximityNear = isProxNear,
            isLightDark = isLightDark,
            isZDownward = isZDownward,
            isGyroStable = isGyroStable,
            isFaceDown = _isFaceDown.value,
            stabilityDurationMs = stabilityTime,
            proximityType = proxTypeLabel,
            lastArmedDurationSec = lastArmedDurationSec,
            totalArmSessions = totalArmSessions
        )
    }
}

