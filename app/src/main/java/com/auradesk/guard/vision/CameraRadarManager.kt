package com.auradesk.guard.vision

import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors

class CameraRadarManager(
    private val context: Context,
    val detector: PersonRadarDetector = PersonRadarDetector()
) {

    companion object {
        private const val TAG = "CameraRadarManager"
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var isRunning = false

    fun startCamera(lifecycleOwner: LifecycleOwner) {
        if (isRunning) return
        isRunning = true
        Log.i(TAG, "Initializing CameraX for Person Radar...")

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                // Low power 320x240 analysis stream
                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(320, 240))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    detector.processImageProxy(imageProxy)
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    imageAnalysis
                )
                Log.i(TAG, "CameraX Person Radar running at 2fps 320x240")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind CameraX lifecycle", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun stopCamera() {
        if (!isRunning) return
        isRunning = false
        try {
            cameraProvider?.unbindAll()
            detector.clear()
            Log.i(TAG, "CameraX Person Radar stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping CameraX", e)
        }
    }

    fun release() {
        stopCamera()
        cameraExecutor.shutdown()
    }
}
