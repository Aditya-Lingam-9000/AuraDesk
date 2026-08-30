package com.auradesk.guard.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.sqrt

class ShakeDetector(
    private val context: Context,
    private val onShakeDetected: () -> Unit
) : SensorEventListener {

    companion object {
        private const val TAG = "ShakeDetector"
        private const val SHAKE_THRESHOLD_GRAVITY = 2.2f // Approx 21 m/s^2 total acceleration
        private const val SHAKE_SLOP_TIME_MS = 350
        private const val SHAKE_COUNT_RESET_TIME_MS = 1500
        private const val REQUIRED_SHAKE_COUNT = 3
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var shakeTimestamp: Long = 0
    private var shakeCount: Int = 0
    private var isListening = false

    fun startListening() {
        if (isListening || accelerometer == null) return
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        isListening = true
        Log.i(TAG, "ShakeDetector started listening")
    }

    fun stopListening() {
        if (!isListening) return
        sensorManager.unregisterListener(this)
        isListening = false
        shakeCount = 0
        Log.i(TAG, "ShakeDetector stopped")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val gX = x / SensorManager.GRAVITY_EARTH
        val gY = y / SensorManager.GRAVITY_EARTH
        val gZ = z / SensorManager.GRAVITY_EARTH

        // Net g-force
        val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)

        if (gForce > SHAKE_THRESHOLD_GRAVITY) {
            val now = System.currentTimeMillis()

            // Ignore shakes too close together
            if (shakeTimestamp + SHAKE_SLOP_TIME_MS > now) {
                return
            }

            // Reset shake count if too much time has passed
            if (shakeTimestamp + SHAKE_COUNT_RESET_TIME_MS < now) {
                shakeCount = 0
            }

            shakeTimestamp = now
            shakeCount++
            Log.d(TAG, "Shake detected: count=$shakeCount")

            if (shakeCount >= REQUIRED_SHAKE_COUNT) {
                shakeCount = 0
                Log.i(TAG, "🔥 3-SHAKE GESTURE TRIGGERED! Incinerating capsule...")
                onShakeDetected()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
