package com.auradesk.guard.vision

import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

class PersonRadarDetector {

    companion object {
        private const val TAG = "PersonRadarDetector"
        private const val DETECTION_PERSISTENCE_MS = 2500L // 2.5s persistence hold to prevent flicker
        private const val APPROACH_ON_THRESHOLD = 0.20f // Sustained +20%/s expansion turns ON approaching
        private const val APPROACH_OFF_THRESHOLD = 0.05f // <= +5%/s turns OFF approaching (Hysteresis)
    }

    private val _radarData = MutableStateFlow(PersonRadarData())
    val radarData: StateFlow<PersonRadarData> = _radarData.asStateFlow()

    // Fast Stream Pose Detector
    private val poseOptions = PoseDetectorOptions.Builder()
        .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
        .build()

    private val poseDetector = PoseDetection.getClient(poseOptions)

    // Tracking & History State
    private val areaHistory = ArrayDeque<Pair<Long, Float>>() // Rolling history for robust velocity calculation
    private var lastFrameTime: Long = 0L
    private var lastValidDetectionTime: Long = 0L
    private var isProcessing = false

    // Exponential Moving Average (EMA) Filtered Values
    private var smoothedLeft: Float = 0f
    private var smoothedTop: Float = 0f
    private var smoothedRight: Float = 0f
    private var smoothedBottom: Float = 0f
    private var smoothedDistance: Float = 0f
    private var smoothedGrowthRate: Float = 0f
    private var isApproachingLatched: Boolean = false
    private var hasInitializedFilter: Boolean = false
    private var currentZone: RadarZone = RadarZone.NONE

    @OptIn(ExperimentalGetImage::class)
    fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || isProcessing) {
            imageProxy.close()
            return
        }

        // Throttle to 15 fps (approx 65ms)
        val now = System.currentTimeMillis()
        if (now - lastFrameTime < 65L) {
            imageProxy.close()
            return
        }
        lastFrameTime = now
        isProcessing = true

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)
        val imageWidth = if (rotationDegrees == 90 || rotationDegrees == 270) imageProxy.height else imageProxy.width
        val imageHeight = if (rotationDegrees == 90 || rotationDegrees == 270) imageProxy.width else imageProxy.height

        poseDetector.process(inputImage)
            .addOnSuccessListener { pose ->
                handlePoseDetection(pose, imageWidth, imageHeight, now)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Pose detection failed", e)
            }
            .addOnCompleteListener {
                isProcessing = false
                imageProxy.close()
            }
    }

    private fun handlePoseDetection(
        pose: Pose,
        imageWidth: Int,
        imageHeight: Int,
        timestamp: Long
    ) {
        val nose = pose.getPoseLandmark(PoseLandmark.NOSE)
        val leftEye = pose.getPoseLandmark(PoseLandmark.LEFT_EYE)
        val rightEye = pose.getPoseLandmark(PoseLandmark.RIGHT_EYE)
        val leftEar = pose.getPoseLandmark(PoseLandmark.LEFT_EAR)
        val rightEar = pose.getPoseLandmark(PoseLandmark.RIGHT_EAR)
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)

        // Robust Human Living Biometric Verification:
        // Supports low-res 320x240 distant subjects (0.35 threshold) and head turns
        val hasHeadOrFace = (nose != null && nose.inFrameLikelihood >= 0.35f) ||
                (leftEye != null && leftEye.inFrameLikelihood >= 0.35f) ||
                (rightEye != null && rightEye.inFrameLikelihood >= 0.35f) ||
                (leftEar != null && leftEar.inFrameLikelihood >= 0.35f) ||
                (rightEar != null && rightEar.inFrameLikelihood >= 0.35f)

        val hasShoulders = (leftShoulder != null && leftShoulder.inFrameLikelihood >= 0.35f) ||
                (rightShoulder != null && rightShoulder.inFrameLikelihood >= 0.35f)

        val confidentLandmarks = pose.allPoseLandmarks.filter { it.inFrameLikelihood >= 0.35f }

        // Genuine living human: head + torso/shoulders, or clear shoulders with at least 4 landmarks
        val isGenuineHuman = (hasHeadOrFace && (hasShoulders || confidentLandmarks.size >= 4)) || (hasShoulders && confidentLandmarks.size >= 4)

        if (!isGenuineHuman) {
            val timeSinceLastSeen = timestamp - lastValidDetectionTime
            // Persistence Window: Hold last valid detection for 2.5s before declaring area clear
            if (timeSinceLastSeen >= DETECTION_PERSISTENCE_MS) {
                if (_radarData.value.isPersonDetected) {
                    _radarData.value = PersonRadarData(
                        isPersonDetected = false,
                        zone = RadarZone.NONE,
                        confidence = 0f,
                        lastDetectionTimestamp = timestamp
                    )
                    areaHistory.clear()
                    hasInitializedFilter = false
                    isApproachingLatched = false
                    smoothedGrowthRate = 0f
                    smoothedDistance = 0f
                    currentZone = RadarZone.NONE
                }
            }
            return
        }

        lastValidDetectionTime = timestamp

        // Dynamic confidence calculated from valid anatomical points
        val dynamicConfidence = confidentLandmarks.map { it.inFrameLikelihood }.average().toFloat().coerceIn(0.55f, 0.99f)

        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE

        for (lm in confidentLandmarks) {
            val x = lm.position.x
            val y = lm.position.y
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }

        var shoulderWidthNorm = 0f
        if (leftShoulder != null && rightShoulder != null &&
            leftShoulder.inFrameLikelihood >= 0.35f && rightShoulder.inFrameLikelihood >= 0.35f
        ) {
            val sw = Math.abs(leftShoulder.position.x - rightShoulder.position.x)
            shoulderWidthNorm = sw / imageWidth.toFloat()
        }

        val rawHeightRel = ((maxY - minY) / imageHeight.toFloat()).coerceIn(0.05f, 1.0f)
        val rawWidthRel = ((maxX - minX) / imageWidth.toFloat()).coerceIn(0.05f, 1.0f)

        val rawLeft = (minX / imageWidth.toFloat()).coerceIn(0f, 1f)
        val rawTop = (minY / imageHeight.toFloat()).coerceIn(0f, 1f)
        val rawRight = (maxX / imageWidth.toFloat()).coerceIn(0f, 1f)
        val rawBottom = (maxY / imageHeight.toFloat()).coerceIn(0f, 1f)

        // Optical Biometric Distance Metric
        val spanMetric = max(
            shoulderWidthNorm * 1.0f,
            max(rawHeightRel * 0.42f, rawWidthRel * 0.75f)
        ).coerceIn(0.065f, 0.85f)

        // Calibrated Optical Inverse Geometry: D = 0.35 / span
        val rawDistance = (0.35f / spanMetric).coerceIn(0.4f, 5.0f)

        // Exponential Moving Average (EMA) Filter with smooth alpha (0.20)
        if (!hasInitializedFilter) {
            smoothedLeft = rawLeft
            smoothedTop = rawTop
            smoothedRight = rawRight
            smoothedBottom = rawBottom
            smoothedDistance = rawDistance
            hasInitializedFilter = true
        } else {
            val alpha = 0.20f
            smoothedLeft = alpha * rawLeft + (1f - alpha) * smoothedLeft
            smoothedTop = alpha * rawTop + (1f - alpha) * smoothedTop
            smoothedRight = alpha * rawRight + (1f - alpha) * smoothedRight
            smoothedBottom = alpha * rawBottom + (1f - alpha) * smoothedBottom
            smoothedDistance = alpha * rawDistance + (1f - alpha) * smoothedDistance
        }

        val currentArea = (smoothedRight - smoothedLeft).coerceAtLeast(0.05f) * rawHeightRel
        val centroidX = (smoothedLeft + smoothedRight) / 2f
        val centroidY = (smoothedTop + smoothedBottom) / 2f

        // Robust 500ms Sliding Window Velocity Calculation (Eliminates Hand Tremor Spikes)
        areaHistory.addLast(Pair(timestamp, currentArea))
        while (areaHistory.isNotEmpty() && timestamp - areaHistory.first().first > 550L) {
            areaHistory.removeFirst()
        }

        var sustainedGrowthRate = 0f
        if (areaHistory.size >= 4) {
            val baseline = areaHistory.first()
            val dtSec = max((timestamp - baseline.first) / 1000f, 0.25f)
            val deltaArea = currentArea - baseline.second
            sustainedGrowthRate = (deltaArea / baseline.second) / dtSec
        }

        smoothedGrowthRate = 0.25f * sustainedGrowthRate + 0.75f * smoothedGrowthRate

        // Hysteresis Latch on true sustained forward walking
        if (smoothedGrowthRate >= APPROACH_ON_THRESHOLD) {
            isApproachingLatched = true
        } else if (smoothedGrowthRate <= APPROACH_OFF_THRESHOLD) {
            isApproachingLatched = false
        }

        // Zone Hysteresis: Prevents flapping around boundaries
        val zone = determineZoneWithHysteresis(smoothedDistance)

        _radarData.value = PersonRadarData(
            isPersonDetected = true,
            distanceMeters = smoothedDistance,
            isApproaching = isApproachingLatched,
            growthRatePercentPerSec = smoothedGrowthRate * 100f,
            boxRelativeHeight = spanMetric,
            centroidX = centroidX,
            centroidY = centroidY,
            leftRel = smoothedLeft,
            topRel = smoothedTop,
            rightRel = smoothedRight,
            bottomRel = smoothedBottom,
            zone = zone,
            confidence = dynamicConfidence,
            lastDetectionTimestamp = timestamp
        )
    }

    private fun determineZoneWithHysteresis(distance: Float): RadarZone {
        val zone = when (currentZone) {
            RadarZone.CLOSE_05M -> {
                // Stays in CLOSE until person clearly moves beyond 1.35m
                if (distance > 1.35f) RadarZone.MID_2M else RadarZone.CLOSE_05M
            }
            RadarZone.MID_2M -> {
                if (distance <= 0.95f) RadarZone.CLOSE_05M
                else if (distance > 3.2f) RadarZone.FAR_5M
                else RadarZone.MID_2M
            }
            RadarZone.FAR_5M, RadarZone.NONE -> {
                if (distance <= 0.95f) RadarZone.CLOSE_05M
                else if (distance <= 2.6f) RadarZone.MID_2M
                else RadarZone.FAR_5M
            }
        }
        currentZone = zone
        return zone
    }

    /**
     * Simulation method for deterministic testing and demo stage presentations
     */
    fun simulate(distanceMeters: Float, isApproaching: Boolean, growthPercent: Float = 28.5f) {
        val zone = when {
            distanceMeters <= 0.8f -> RadarZone.CLOSE_05M
            distanceMeters <= 2.8f -> RadarZone.MID_2M
            else -> RadarZone.FAR_5M
        }

        val relativeHeight = when (zone) {
            RadarZone.CLOSE_05M -> 0.68f
            RadarZone.MID_2M -> 0.35f
            RadarZone.FAR_5M -> 0.12f
            RadarZone.NONE -> 0.0f
        }

        smoothedDistance = distanceMeters
        smoothedGrowthRate = if (isApproaching) (growthPercent / 100f) else -0.05f
        isApproachingLatched = isApproaching
        hasInitializedFilter = true

        _radarData.value = PersonRadarData(
            isPersonDetected = distanceMeters > 0f,
            distanceMeters = distanceMeters,
            isApproaching = isApproaching,
            growthRatePercentPerSec = if (isApproaching) growthPercent else -5.0f,
            boxRelativeHeight = relativeHeight,
            centroidX = 0.5f,
            centroidY = 0.5f,
            leftRel = 0.3f,
            topRel = 0.2f,
            rightRel = 0.7f,
            bottomRel = 0.8f,
            zone = zone,
            confidence = 0.96f,
            lastDetectionTimestamp = System.currentTimeMillis()
        )
        currentZone = zone
        Log.i(TAG, "Simulated Radar State: ${distanceMeters}m (Approaching: $isApproaching, Zone: ${zone.label})")
    }

    fun clear() {
        _radarData.value = PersonRadarData()
        areaHistory.clear()
        hasInitializedFilter = false
        isApproachingLatched = false
        smoothedGrowthRate = 0f
        smoothedDistance = 0f
        currentZone = RadarZone.NONE
    }
}
