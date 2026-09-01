package com.auradesk.guard.ui

import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.auradesk.guard.service.GuardService
import com.auradesk.guard.ui.theme.*
import java.util.concurrent.Executors

@Composable
fun CameraRadarViewfinder(
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val radar by GuardService.liveRadar.collectAsState()
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LiquidGlassCard(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Rear Vision Radar Feed",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = TextPrimary
                )
            }

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .liquidPressEffect { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (!hasCameraPermission) {
            LiquidGlassTile(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null, tint = TextMuted, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Camera permission required for Vision Radar", fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(10.dp))
                    LiquidGlassButton(
                        onClick = { permissionLauncher.launch(android.Manifest.permission.CAMERA) },
                        isPrimary = true,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Grant Camera Access", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }
            }
        } else {
            // Viewfinder Surface Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black)
                    .border(BorderStroke(1.2.dp, Color(0x33FFFFFF)), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Live CameraX Preview Surface
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }

                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            try {
                                val cameraProvider = cameraProviderFuture.get()

                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }

                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                    .also {
                                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                                            GuardService.radarDetector.processImageProxy(imageProxy)
                                        }
                                    }

                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageAnalysis
                                )
                                Log.i("CameraRadarViewfinder", "CameraX bound successfully")
                            } catch (e: Exception) {
                                Log.e("CameraRadarViewfinder", "CameraX bind error", e)
                            }
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Bounding Box Overlay Canvas
                if (radar.isPersonDetected) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasW = size.width
                        val canvasH = size.height

                        val left = radar.leftRel * canvasW
                        val top = radar.topRel * canvasH
                        val right = radar.rightRel * canvasW
                        val bottom = radar.bottomRel * canvasH
                        val boxW = (right - left).coerceAtLeast(40f)
                        val boxH = (bottom - top).coerceAtLeast(40f)

                        val boxColor = when {
                            radar.distanceMeters <= 1.0f -> AccentRed
                            radar.distanceMeters <= 3.0f -> AccentAmber
                            else -> AccentBlue
                        }

                        drawRect(
                            color = boxColor,
                            topLeft = Offset(left, top),
                            size = Size(boxW, boxH),
                            style = Stroke(width = 2.dp.toPx())
                        )

                        drawCircle(
                            color = PureWhite,
                            radius = 4.dp.toPx(),
                            center = Offset(left + boxW / 2, top + boxH / 2)
                        )
                    }

                    // Liquid Top Distance Pill
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xCC0F172A))
                            .border(1.dp, Color(0x6694A3B8), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = PureWhite,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "${radar.zone.label} • ${String.format("%.1f", radar.distanceMeters)}m",
                                fontWeight = FontWeight.SemiBold,
                                color = PureWhite,
                                fontSize = 11.sp
                            )
                            if (radar.isApproaching) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "+${String.format("%.0f", radar.growthRatePercentPerSec)}%/s",
                                    fontWeight = FontWeight.Bold,
                                    color = AccentAmber,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 10.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xB30F172A))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Point rear camera at entrance / desk approach path",
                            color = Color(0xFFD1D5DB),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
