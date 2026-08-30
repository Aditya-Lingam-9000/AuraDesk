package com.auradesk.guard.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.auradesk.guard.sensors.FaceDownSensors
import com.auradesk.guard.service.GuardService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen() {
    val context = LocalContext.current
    val isRunning by GuardService.isRunning.collectAsState()
    val isArmed by GuardService.isArmed.collectAsState()
    val sensors by GuardService.liveSensors.collectAsState()

    // Permission launcher for Android 13+ notification and camera/mic
    val permissionsToRequest = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.RECORD_AUDIO)
    }

    var hasAllPermissions by remember {
        mutableStateOf(permissionsToRequest.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasAllPermissions = results.values.all { it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (isArmed) Color(0xFF00E676) else Color(0xFF90CAF9)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "AuraDesk",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF1E293B)
                        ) {
                            Text(
                                text = "OFFLINE GUARD",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A)
                )
            )
        },
        containerColor = Color(0xFF090D16)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Permission Request Banner if needed
            if (!hasAllPermissions) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF332005)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFBBF24))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Permissions Needed",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFDE68A)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "AuraDesk requires notification, camera, and microphone permissions for the offline focus guard.",
                            fontSize = 13.sp,
                            color = Color(0xFFF3F4F6)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { permissionLauncher.launch(permissionsToRequest.toTypedArray()) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                        ) {
                            Text("Grant Permissions", color = Color.White)
                        }
                    }
                }
            }

            // Main Status Shield Card
            GuardStatusHeroCard(
                isRunning = isRunning,
                isArmed = isArmed,
                sensors = sensors,
                onToggleService = {
                    if (isRunning) {
                        GuardService.stopService(context)
                    } else {
                        GuardService.startService(context)
                    }
                }
            )

            // Live Sensor Fusion Telemetry
            SensorFusionTelemetryCard(sensors = sensors, isRunning = isRunning)

            // Phase 4: Deep Work Detection Card
            DeepWorkFocusCard()

            // Phase 5: Interruption Capsules & Haptic Whisper Card
            InterruptionCapsulesCard(context = context)

            // Phase 3: Person Approaching Radar Card
            PersonRadarCard()

            // Sound & Haptic Test Card
            SoundHapticsTestCard(context = context)

            // Phase 2: Always-On Display Preview Card
            AlwaysOnPreviewCard()

            // Test Verification Instructions
            TestingInstructionsCard()

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun AlwaysOnPreviewCard() {
    var showPreview by remember { mutableStateOf(false) }

    if (showPreview) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showPreview = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            GuardArmedScreen(onDisarm = { showPreview = false })
        }
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF00E676))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Phase 2: Always-On Display (AOD)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "When your phone is face-down on your desk, the screen automatically switches to the OLED Deep Black (#090D14) Always-On mode showing the pulsating green shield, live deep work timer, and return time.",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { showPreview = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F2E1E)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("👀 Preview Always-On Display", color = Color(0xFF00E676), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun GuardStatusHeroCard(
    isRunning: Boolean,
    isArmed: Boolean,
    sensors: FaceDownSensors,
    onToggleService: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            isArmed -> Color(0xFF00E676)
            isRunning -> Color(0xFF38BDF8)
            else -> Color(0xFF334155)
        }, label = "borderColor"
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, borderColor, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isArmed -> Color(0x3300E676)
                            isRunning -> Color(0x3338BDF8)
                            else -> Color(0x2264748B)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        isArmed -> Icons.Default.Shield
                        isRunning -> Icons.Default.Lock
                        else -> Icons.Default.LockOpen
                    },
                    contentDescription = null,
                    modifier = Modifier.size(38.dp),
                    tint = when {
                        isArmed -> Color(0xFF00E676)
                        isRunning -> Color(0xFF38BDF8)
                        else -> Color(0xFF94A3B8)
                    }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = when {
                    isArmed -> "🛡️ GUARD ARMED"
                    isRunning -> "STANDBY: FLIP PHONE"
                    else -> "GUARD SERVICE OFF"
                },
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = when {
                    isArmed -> Color(0xFF00E676)
                    isRunning -> Color(0xFF38BDF8)
                    else -> Color(0xFF94A3B8)
                }
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = when {
                    isArmed -> "Phone face-down on desk • Deep work protected"
                    isRunning -> "Place phone face-down on your desk to arm"
                    else -> "Turn on Guard Service to enable background protection"
                },
                fontSize = 13.sp,
                color = Color(0xFFCBD5E1)
            )

            // Arming Proof Badge
            if (sensors.totalArmSessions > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0x2200E676),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Armed ${sensors.totalArmSessions}x • Last Session: ${sensors.lastArmedDurationSec}s",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E676)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onToggleService,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) Color(0xFFEF4444) else Color(0xFF2563EB)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRunning) "Stop Guard Service" else "Start Guard Service",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun SensorFusionTelemetryCard(sensors: FaceDownSensors, isRunning: Boolean) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Sensor Fusion Telemetry",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isRunning) Color(0x3300E676) else Color(0x33EF4444)
                ) {
                    Text(
                        text = if (isRunning) "LIVE" else "PAUSED",
                        color = if (isRunning) Color(0xFF00E676) else Color(0xFFEF4444),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            SensorRow(
                title = "1. Proximity",
                value = "${String.format("%.1f", sensors.proximityCm)} cm (${if (sensors.isProximityNear) "Near" else "Far"})",
                isSatisfied = sensors.isProximityNear,
                criteria = "< 1.5 cm (${sensors.proximityType})"
            )

            SensorRow(
                title = "2. Ambient Light",
                value = "${String.format("%.1f", sensors.lightLux)} lux",
                isSatisfied = sensors.isLightDark,
                criteria = "< 2.0 lux (Dark desk)"
            )

            SensorRow(
                title = "3. Accel Z (Screen)",
                value = "${String.format("%.2f", sensors.accelZ)} m/s²",
                isSatisfied = sensors.isZDownward,
                criteria = "≤ -7.5 m/s² (Face Down)"
            )

            SensorRow(
                title = "4. Gyroscope Stability",
                value = "${String.format("%.3f", sensors.gyroMagnitude)} rad/s",
                isSatisfied = sensors.isGyroStable,
                criteria = "< 0.25 rad/s"
            )

            HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Stability Hold Time",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp
                )
                Text(
                    text = "${sensors.stabilityDurationMs} ms / 1000 ms",
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (sensors.stabilityDurationMs >= 1000L) Color(0xFF00E676) else Color(0xFFFBBF24),
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun SoundHapticsTestCard(context: Context) {
    val feedbackManager = remember { com.auradesk.guard.utils.FeedbackManager(context) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color(0xFF38BDF8))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Sound & Haptic Test Triggers",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { feedbackManager.playArmFeedback() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E676))
                ) {
                    Text("🔔 Test Arm Tone", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { feedbackManager.playDisarmFeedback() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
                ) {
                    Text("🔕 Test Disarm Tone", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun PersonRadarCard() {
    val radar by GuardService.liveRadar.collectAsState()

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Radar,
                        contentDescription = null,
                        tint = Color(radar.zone.colorHex)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Person Radar (Vision AI)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White,
                        maxLines = 1
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(radar.zone.colorHex).copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(radar.zone.colorHex))
                ) {
                    Text(
                        text = radar.zone.label,
                        color = Color(radar.zone.colorHex),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Radar Distance Rings Visualizer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF090E17)),
                contentAlignment = Alignment.Center
            ) {
                // Ring 5m
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .border(1.dp, Color(0xFF1E293B), CircleShape)
                )
                // Ring 2m
                Box(
                    modifier = Modifier
                        .size(85.dp)
                        .border(1.dp, Color(0xFF334155), CircleShape)
                )
                // Ring 0.5m (Close Interruption)
                Box(
                    modifier = Modifier
                        .size(45.dp)
                        .border(1.dp, Color(0x66EF4444), CircleShape)
                )
                // Center Desk Reference
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00E676))
                )

                // Subject Blip if detected
                if (radar.isPersonDetected) {
                    val blipSize = when (radar.zone) {
                        com.auradesk.guard.vision.RadarZone.CLOSE_05M -> 30.dp
                        com.auradesk.guard.vision.RadarZone.MID_2M -> 20.dp
                        com.auradesk.guard.vision.RadarZone.FAR_5M -> 14.dp
                        com.auradesk.guard.vision.RadarZone.NONE -> 0.dp
                    }

                    Box(
                        modifier = Modifier
                            .size(blipSize)
                            .clip(CircleShape)
                            .background(Color(radar.zone.colorHex).copy(alpha = 0.8f))
                            .border(2.dp, Color.White, CircleShape)
                    )
                } else {
                    Text("Area Clear • No Approaching Subject", fontSize = 11.sp, color = Color(0xFF475569))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Approaching Warning Banner
            if (radar.isApproaching) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0x33FBBF24),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFBBF24)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DirectionsRun, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "⚠️ Subject Approaching Desk (+${String.format("%.1f", radar.growthRatePercentPerSec)}%/s)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFDE68A)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Distance", fontSize = 11.sp, color = Color(0xFF64748B))
                    Text(
                        text = if (radar.isPersonDetected) "${String.format("%.1f", radar.distanceMeters)} m" else "--",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }

                Column {
                    Text("Box Growth Rate", fontSize = 11.sp, color = Color(0xFF64748B))
                    Text(
                        text = if (radar.isPersonDetected) "${String.format("%.1f", radar.growthRatePercentPerSec)}%/s" else "--",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (radar.isApproaching) Color(0xFFFBBF24) else Color(0xFF94A3B8),
                        fontSize = 14.sp
                    )
                }

                Column {
                    Text("AI Confidence", fontSize = 11.sp, color = Color(0xFF64748B))
                    Text(
                        text = if (radar.isPersonDetected) "${(radar.confidence * 100).toInt()}%" else "--",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF38BDF8),
                        fontSize = 14.sp
                    )
                }
            }

            var isCameraLiveOpen by remember { mutableStateOf(false) }

            // Live Camera Viewfinder Toggle Button
            Button(
                onClick = { isCameraLiveOpen = !isCameraLiveOpen },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCameraLiveOpen) Color(0xFF1E293B) else Color(0xFF0F766E)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isCameraLiveOpen) "Hide Live Camera Feed" else "📷 Open Real-Time Camera Viewfinder",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            if (isCameraLiveOpen) {
                Spacer(modifier = Modifier.height(12.dp))
                CameraRadarViewfinder(onClose = { isCameraLiveOpen = false })
            }

            HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 10.dp))

            // Simulation Trigger Chips for Testing & Demo Stage
            Text(
                text = "Simulation & Stage Demo Triggers:",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF94A3B8)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = { GuardService.simulateRadar(5.0f, isApproaching = false) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("5m Far", fontSize = 10.sp)
                }

                OutlinedButton(
                    onClick = { GuardService.simulateRadar(2.0f, isApproaching = true, growthRate = 28.5f) },
                    modifier = Modifier.weight(1.3f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFBBF24))
                ) {
                    Text("🏃 2m Approach", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { GuardService.simulateRadar(0.5f, isApproaching = true, growthRate = 65.0f) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                ) {
                    Text("🛑 0.5m", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { GuardService.clearRadar() },
                    modifier = Modifier.weight(0.8f),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Clear", fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun DeepWorkFocusCard() {
    val deepWork by GuardService.liveDeepWork.collectAsState()
    val themeColor = when {
        deepWork.isDeepWork -> Color(0xFF00E676)
        deepWork.focusScore >= 30 -> Color(0xFFFBBF24)
        else -> Color(0xFF64748B)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = themeColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Deep Work Focus Engine",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White,
                        maxLines = 1
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = themeColor.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, themeColor)
                ) {
                    Text(
                        text = if (deepWork.isDeepWork) "DEEP WORK ACTIVE" else "RAMPING UP",
                        color = themeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Focus Score Bar
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Focus Score", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    Text(
                        "${deepWork.focusScore}%",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = themeColor,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (deepWork.focusScore / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = themeColor,
                    trackColor = Color(0xFF1E293B)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Cadence", fontSize = 11.sp, color = Color(0xFF64748B))
                    Text(
                        text = "${String.format("%.0f", deepWork.typingCadenceBpm)} BPM",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }

                Column {
                    Text("Noise Floor", fontSize = 11.sp, color = Color(0xFF64748B))
                    Text(
                        text = "${String.format("%.1f", deepWork.noiseFloorDb)} dB",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.sp
                    )
                }

                Column {
                    Text("Focus Time", fontSize = 11.sp, color = Color(0xFF64748B))
                    Text(
                        text = "${deepWork.uninterruptedFocusDurationSec}s",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF38BDF8),
                        fontSize = 13.sp
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 10.dp))

            // Environment Profile Chips
            Text(
                text = "Environment Profile:",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val profiles = listOf(
                    com.auradesk.guard.focus.EnvironmentProfile.QUIET_LAPTOP to "Quiet Laptop",
                    com.auradesk.guard.focus.EnvironmentProfile.LIBRARY_SILENCE to "Silent Study",
                    com.auradesk.guard.focus.EnvironmentProfile.AUTO_ADAPTIVE to "Auto"
                )

                profiles.forEach { (profile, label) ->
                    val isSelected = deepWork.environmentProfile == profile
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) Color(0xFF0F766E) else Color(0xFF1E293B),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) Color(0xFF00E676) else Color(0xFF334155)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        Button(
                            onClick = { GuardService.setEnvironmentProfile(profile) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Simulation Triggers
            Text(
                text = "Stage Demo Simulation Triggers:",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        GuardService.simulateDeepWork(
                            isDeepWork = true,
                            score = 88,
                            cadenceBpm = 140f,
                            profile = com.auradesk.guard.focus.EnvironmentProfile.QUIET_LAPTOP
                        )
                    },
                    modifier = Modifier.weight(1.3f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E676))
                ) {
                    Text("⌨️ 140 BPM Typing", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        GuardService.simulateDeepWork(
                            isDeepWork = true,
                            score = 92,
                            cadenceBpm = 0f,
                            profile = com.auradesk.guard.focus.EnvironmentProfile.LIBRARY_SILENCE
                        )
                    },
                    modifier = Modifier.weight(1.3f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
                ) {
                    Text("📖 Silent Study", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        GuardService.simulateDeepWork(
                            isDeepWork = false,
                            score = 15,
                            cadenceBpm = 0f
                        )
                    },
                    modifier = Modifier.weight(0.8f),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Reset", fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun InterruptionCapsulesCard(context: Context) {
    val repository = remember { com.auradesk.guard.data.InterruptionRepository.getInstance(context) }
    val feedbackManager = remember { com.auradesk.guard.utils.FeedbackManager(context) }
    val interruptions by repository.allInterruptions.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Inbox,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Interruption Capsules (Room DB)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White,
                        maxLines = 1
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0x3338BDF8),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8))
                ) {
                    Text(
                        text = "${interruptions.size} LOGGED",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Capsules List
            if (interruptions.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0F172A),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "No Interruption Capsules Stored • Clean Desk",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    interruptions.take(3).forEach { capsule ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF1E293B),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (capsule.isUrgent) Color(0xFFEF4444) else Color(0xFF334155)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = capsule.personName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "(${capsule.distanceZone})",
                                            fontSize = 11.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = capsule.taskSummary,
                                        fontSize = 12.sp,
                                        color = Color(0xFFE2E8F0),
                                        maxLines = 2
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        coroutineScope.launch { repository.delete(capsule.id) }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Haptic Whisper Motors Test Row
            Text(
                text = "Haptic Whisper Patterns:",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF94A3B8)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = { feedbackManager.playHapticWhisperLow() },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Low (80ms)", fontSize = 10.sp)
                }

                OutlinedButton(
                    onClick = { feedbackManager.playHapticWhisperMedium() },
                    modifier = Modifier.weight(1.2f),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
                ) {
                    Text("Mid (2m Approached)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { feedbackManager.playHapticWhisperUrgent() },
                    modifier = Modifier.weight(1.2f),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                ) {
                    Text("🚨 Urgent (0.5m)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Simulation Triggers
            Text(
                text = "Interruption Simulation & Shake Triggers:",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF94A3B8)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            repository.insert(
                                com.auradesk.guard.data.InterruptionEntity(
                                    personName = "Rahul from Backend",
                                    taskSummary = "Review PR #142 API schema changes before 4 PM deployment",
                                    contextSnippet = "Editing auth/TokenManager.kt line 88",
                                    distanceZone = "0.5m (At Desk)",
                                    durationSec = 6L,
                                    isUrgent = true
                                )
                            )
                            feedbackManager.playHapticWhisperUrgent()
                        }
                    },
                    modifier = Modifier.weight(1.3f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("🚨 Sim Rahul Visit", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Button(
                    onClick = {
                        coroutineScope.launch {
                            repository.insert(
                                com.auradesk.guard.data.InterruptionEntity(
                                    personName = "Priya (Design Lead)",
                                    taskSummary = "Sync on Figma design tokens for dark mode theme",
                                    contextSnippet = "Editing ui/Theme.kt line 42",
                                    distanceZone = "2.0m (Approached)",
                                    durationSec = 4L,
                                    isUrgent = false
                                )
                            )
                            feedbackManager.playHapticWhisperMedium()
                        }
                    },
                    modifier = Modifier.weight(1.3f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("🔔 Sim Priya Visit", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            repository.deleteAll()
                            feedbackManager.playIncinerateFeedback()
                        }
                    },
                    modifier = Modifier.weight(0.8f),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
                ) {
                    Text("Wipe All", fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun SensorRow(
    title: String,
    value: String,
    isSatisfied: Boolean,
    criteria: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
            Text(criteria, fontSize = 11.sp, color = Color(0xFF64748B))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = Color(0xFFE2E8F0)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = if (isSatisfied) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                tint = if (isSatisfied) Color(0xFF00E676) else Color(0xFF475569),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun TestingInstructionsCard() {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Science, contentDescription = null, tint = Color(0xFF38BDF8))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Phase 3 Radar Verification Checklist",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val steps = listOf(
                "1. Test Simulation Chips: Tap '5m Far' ➔ '🏃 2m Approach' ➔ '🛑 0.5m' to verify radar distance rings, approach growth rate (+28%/s), and approaching alert banner.",
                "2. Rear Camera Live Radar: When Guard is armed, CameraX analyzes 2fps 320x240 in background with zero video storage (100% privacy).",
                "3. Continuous Flip: Flip phone down and up repeatedly to verify multi-session stability.",
                "4. Airplane Mode: Works completely offline with 0 network bytes."
            )

            steps.forEach { step ->
                Text(
                    text = step,
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(vertical = 3.dp)
                )
            }
        }
    }
}
