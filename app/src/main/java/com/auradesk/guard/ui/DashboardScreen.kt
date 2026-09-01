package com.auradesk.guard.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.auradesk.guard.data.InterruptionEntity
import com.auradesk.guard.data.InterruptionRepository
import com.auradesk.guard.sensors.FaceDownSensors
import com.auradesk.guard.service.GuardService
import com.auradesk.guard.ui.theme.*
import kotlinx.coroutines.launch

enum class DashboardTab(val title: String, val icon: ImageVector) {
    OVERVIEW("Overview", Icons.Default.Dashboard),
    RADAR("Radar", Icons.Default.Radar),
    VOICE_AI("Voice & AI", Icons.Default.Mic),
    PRIVACY("Privacy & Power", Icons.Default.Security)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onReplayTour: () -> Unit = {}
) {
    val context = LocalContext.current
    val isRunning by GuardService.isRunning.collectAsState()
    val isArmed by GuardService.isArmed.collectAsState()
    val sensors by GuardService.liveSensors.collectAsState()

    var selectedTab by remember { mutableStateOf(DashboardTab.OVERVIEW) }
    val coroutineScope = rememberCoroutineScope()

    // Permission launcher for Android 13+ notification and camera/mic
    val permissionsToRequest = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.RECORD_AUDIO)
    }

    var hasPermissions by remember {
        mutableStateOf(permissionsToRequest.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermissions = result.values.all { it }
    }

    Scaffold(
        containerColor = Slate50,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isArmed) StatusGreen else Slate400)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "AuraDesk",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate900
                            )
                            Text(
                                text = if (isArmed) "Focus Shield Armed" else "Standby Mode",
                                fontSize = 11.sp,
                                color = if (isArmed) StatusGreen else Slate500
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onReplayTour) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = "Quick Tour",
                            tint = Slate600
                        )
                    }
                    IconButton(onClick = {
                        val auditor = GuardService.getPrivacyAuditor(context)
                        auditor.panicPurge {
                            Toast.makeText(context, "All local records purged", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Panic Purge",
                            tint = StatusRed
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PureWhite,
                    titleContentColor = Slate900
                ),
                modifier = Modifier.border(BorderStroke(1.dp, Slate200))
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = PureWhite,
                tonalElevation = 0.dp,
                modifier = Modifier.border(BorderStroke(1.dp, Slate200))
            ) {
                DashboardTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontSize = 11.sp,
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Slate900,
                            selectedTextColor = Slate900,
                            indicatorColor = Slate100,
                            unselectedIconColor = Slate400,
                            unselectedTextColor = Slate500
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Permission Banner
            if (!hasPermissions) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = StatusAmberBg,
                    border = BorderStroke(1.dp, StatusAmberBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = StatusAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Camera & Audio permissions required",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = StatusAmber
                            )
                        }

                        Button(
                            onClick = { permissionLauncher.launch(permissionsToRequest.toTypedArray()) },
                            colors = ButtonDefaults.buttonColors(containerColor = Slate900),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("Grant Access", fontSize = 11.sp, color = PureWhite)
                        }
                    }
                }
            }

            when (selectedTab) {
                DashboardTab.OVERVIEW -> {
                    // Main Status Hero Card
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

                    // Sensor Fusion Telemetry
                    SensorFusionTelemetryCard(sensors = sensors, isRunning = isRunning)

                    // Deep Work Detection Card
                    DeepWorkFocusCard()

                    // Always-On Display Preview Card
                    AlwaysOnPreviewCard()
                }

                DashboardTab.RADAR -> {
                    // Person Radar Vision AI Card
                    PersonRadarCard()

                    // Sound & Haptic Test Card
                    SoundHapticsTestCard(context = context)
                }

                DashboardTab.VOICE_AI -> {
                    // Audio Pipeline & Vosk Speech-to-Text
                    AudioCapsuleSttCard()

                    // On-Device Action Synthesizer
                    LlmCapsuleSynthesizerCard(context = context)

                    // Interruption Capsules (Room DB)
                    InterruptionCapsulesCard(context = context)

                    // Vivo Office Kit & Jovi Notes Sync
                    VivoOfficeKitCard(context = context)
                }

                DashboardTab.PRIVACY -> {
                    // Battery & Air-Gapped Privacy Card
                    PrivacyBatteryGuardCard(context = context)

                    // Testing Instructions
                    TestingInstructionsCard()
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// -------------------------------------------------------------
// UI COMPONENTS (Clean, Flat, Professional White Theme)
// -------------------------------------------------------------

@Composable
fun GuardStatusHeroCard(
    isRunning: Boolean,
    isArmed: Boolean,
    sensors: FaceDownSensors,
    onToggleService: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = BorderStroke(1.dp, if (isArmed) StatusGreenBorder else Slate200),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = if (isArmed) StatusGreen else Slate800,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (isArmed) "Guard Active & Armed" else if (isRunning) "Guard Service Ready" else "Guard Service Inactive",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Slate900
                        )
                        Text(
                            text = if (isArmed) "Device face-down • Perimeter actively guarded" else "Place device face-down on desk to arm",
                            fontSize = 12.sp,
                            color = Slate500
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isArmed) StatusGreenBg else Slate100,
                    border = BorderStroke(1.dp, if (isArmed) StatusGreenBorder else Slate300)
                ) {
                    Text(
                        text = if (isArmed) "ARMED" else if (isRunning) "STANDBY" else "OFFLINE",
                        color = if (isArmed) StatusGreen else Slate700,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onToggleService,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) Slate800 else Slate900
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = PureWhite
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRunning) "Stop Focus Guard Service" else "Start Focus Guard Service",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PureWhite
                )
            }
        }
    }
}

@Composable
fun SensorFusionTelemetryCard(sensors: FaceDownSensors, isRunning: Boolean) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = BorderStroke(1.dp, Slate200),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Sensors, contentDescription = null, tint = Slate800, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Optical & Inertial Telemetry", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Slate900)
                }

                Text(
                    text = if (isRunning) "Streaming (50Hz)" else "Idle",
                    fontSize = 11.sp,
                    color = if (isRunning) StatusGreen else Slate400,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SensorMetricTile("Proximity", "${String.format("%.1f", sensors.proximityCm)} cm", sensors.isProximityNear, Modifier.weight(1f))
                SensorMetricTile("Ambient Light", "${String.format("%.0f", sensors.lightLux)} lux", sensors.isLightDark, Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SensorMetricTile("Gravity Z", "${String.format("%.2f", sensors.accelZ)} m/s²", sensors.isZDownward, Modifier.weight(1f))
                SensorMetricTile("Gyro Drift", "${String.format("%.3f", sensors.gyroMagnitude)} rad/s", sensors.isGyroStable, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun SensorMetricTile(label: String, value: String, isPassing: Boolean, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Slate50,
        border = BorderStroke(1.dp, if (isPassing) StatusGreenBorder else Slate200),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, fontSize = 11.sp, color = Slate500)
                Icon(
                    imageVector = if (isPassing) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isPassing) StatusGreen else Slate300,
                    modifier = Modifier.size(12.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Slate900)
        }
    }
}

@Composable
fun DeepWorkFocusCard() {
    val deepWorkState by GuardService.liveDeepWork.collectAsState()

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = BorderStroke(1.dp, Slate200),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = Slate800, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Deep Work Focus Analysis", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Slate900)
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (deepWorkState.isDeepWork) StatusGreenBg else Slate100,
                    border = BorderStroke(1.dp, if (deepWorkState.isDeepWork) StatusGreenBorder else Slate300)
                ) {
                    Text(
                        text = "${deepWorkState.focusScore}% FOCUS",
                        color = if (deepWorkState.isDeepWork) StatusGreen else Slate700,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Monitors micro-transient acoustic typing cadence and quiet study sessions to establish focus metrics.",
                fontSize = 12.sp,
                color = Slate600
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Slate50,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Typing Cadence", fontSize = 11.sp, color = Slate500)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("${String.format("%.0f", deepWorkState.typingCadenceBpm)} BPM", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate900)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Slate50,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Environment Mode", fontSize = 11.sp, color = Slate500)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(deepWorkState.environmentProfile.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate900)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { GuardService.simulateDeepWork(true, 92, 140f) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Slate300)
                ) {
                    Text("Simulate Coding", fontSize = 11.sp, color = Slate700)
                }

                OutlinedButton(
                    onClick = { GuardService.simulateDeepWork(false, 15, 0f) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Slate300)
                ) {
                    Text("Simulate Idle", fontSize = 11.sp, color = Slate700)
                }
            }
        }
    }
}

@Composable
fun PersonRadarCard() {
    val radar by GuardService.liveRadar.collectAsState()
    var showViewfinder by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = BorderStroke(1.dp, Slate200),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Radar, contentDescription = null, tint = Slate800, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Person Approaching Radar (Vision AI)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Slate900)
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (radar.isPersonDetected) StatusGreenBg else Slate100,
                    border = BorderStroke(1.dp, if (radar.isPersonDetected) StatusGreenBorder else Slate300)
                ) {
                    Text(
                        text = if (radar.isPersonDetected) "SUBJECT DETECTED" else "NO SUBJECT",
                        color = if (radar.isPersonDetected) StatusGreen else Slate600,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Tracks human proximity via ML Kit biometric span inverse geometry (5m perimeter to 0.5m desk arrival).",
                fontSize = 12.sp,
                color = Slate600
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Radar Metric Row
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Slate50,
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Current Zone", fontSize = 11.sp, color = Slate500)
                        Text(radar.zone.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate900)
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text("Estimated Distance", fontSize = 11.sp, color = Slate500)
                        Text("${String.format("%.1f", radar.distanceMeters)} m", fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Slate900)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Viewfinder Toggle
            Button(
                onClick = { showViewfinder = !showViewfinder },
                colors = ButtonDefaults.buttonColors(containerColor = Slate900),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = PureWhite
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (showViewfinder) "Close Camera Feed" else "Open Camera Viewfinder", fontSize = 12.sp, color = PureWhite)
            }

            if (showViewfinder) {
                Spacer(modifier = Modifier.height(12.dp))
                CameraRadarViewfinder(onClose = { showViewfinder = false })
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Simulation Controls
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { GuardService.simulateRadar(2.0f, true, 32.0f) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Slate300)
                ) {
                    Text("Simulate 2m Approach", fontSize = 10.sp, color = Slate700)
                }

                OutlinedButton(
                    onClick = { GuardService.simulateRadar(0.5f, false, 0.0f) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Slate300)
                ) {
                    Text("Simulate 0.5m Desk", fontSize = 10.sp, color = Slate700)
                }

                OutlinedButton(
                    onClick = { GuardService.clearRadar() },
                    modifier = Modifier.weight(0.7f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Slate300)
                ) {
                    Text("Clear", fontSize = 10.sp, color = Slate700)
                }
            }
        }
    }
}

@Composable
fun AudioCapsuleSttCard() {
    val audioState by GuardService.liveAudioCapsule.collectAsState()
    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = BorderStroke(1.dp, Slate200),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Mic, contentDescription = null, tint = Slate800, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Voice VAD & Offline Transcription", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Slate900)
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (audioState.isRecording) StatusRedBg else Slate100,
                    border = BorderStroke(1.dp, if (audioState.isRecording) StatusRedBorder else Slate300)
                ) {
                    Text(
                        text = if (audioState.isRecording) "RECORDING (${audioState.remainingSeconds}s)" else audioState.capsuleStatus,
                        color = if (audioState.isRecording) StatusRed else Slate700,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Autonomous 10-second capsule recording with keyword spotter (Arjun, Hey, Excuse me) and local transcription.",
                fontSize = 12.sp,
                color = Slate600
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Transcript Box
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Slate50,
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Latest Transcript", fontSize = 11.sp, color = Slate500)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = audioState.livePartialTranscript.ifBlank { "No audio transcript captured" },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate900
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { GuardService.startAudioCapsule(context, 10) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Slate900),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(14.dp), tint = PureWhite)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Record 10s Capsule", fontSize = 11.sp, color = PureWhite)
                }

                OutlinedButton(
                    onClick = {
                        GuardService.simulateSpeechCapsule(
                            context = context,
                            speakerName = "Rahul from Backend",
                            speechText = "Hey Arjun, can you review PR 142 before the 4 PM deployment sprint demo?",
                            durationSec = 6L,
                            isUrgent = true
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Slate300)
                ) {
                    Text("Simulate Rahul Speech", fontSize = 11.sp, color = Slate700)
                }
            }
        }
    }
}

@Composable
fun LlmCapsuleSynthesizerCard(context: Context) {
    val synthesizedTask by GuardService.liveSynthesizedTask.collectAsState()

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = BorderStroke(1.dp, Slate200),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Psychology, contentDescription = null, tint = Slate800, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("On-Device LLM Action Synthesizer", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Slate900)
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Slate100,
                    border = BorderStroke(1.dp, Slate300)
                ) {
                    Text(
                        text = "ON-DEVICE NPU",
                        color = Slate700,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Distills raw conversational speech into structured action items, deadlines, priority levels, and system components.",
                fontSize = 12.sp,
                color = Slate600
            )

            if (synthesizedTask != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Slate50,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Extracted Action Item", fontSize = 11.sp, color = Slate500)
                            Text(synthesizedTask!!.urgencyLevel.label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate700)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(synthesizedTask!!.actionItem, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate900)

                        if (synthesizedTask!!.deadlineOrTime != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Deadline: ${synthesizedTask!!.deadlineOrTime}", fontSize = 12.sp, color = StatusAmber, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        GuardService.synthesizePrompt(
                            context = context,
                            rawText = "Excuse me Arjun, production auth login is failing with 500 error, please check immediately!",
                            speakerName = "Priya Tech Lead"
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Slate300)
                ) {
                    Text("Test Critical Outage Prompt", fontSize = 10.sp, color = Slate700)
                }

                OutlinedButton(
                    onClick = {
                        GuardService.synthesizePrompt(
                            context = context,
                            rawText = "Hi, can you update the Figma dashboard design by tomorrow morning?",
                            speakerName = "Ananya Designer"
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Slate300)
                ) {
                    Text("Test Design Task Prompt", fontSize = 10.sp, color = Slate700)
                }
            }
        }
    }
}

@Composable
fun InterruptionCapsulesCard(context: Context) {
    val repository = remember { InterruptionRepository.getInstance(context) }
    val capsules by repository.allInterruptions.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = BorderStroke(1.dp, Slate200),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, contentDescription = null, tint = Slate800, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Interruption Capsules (Room DB)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Slate900)
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Slate100,
                    border = BorderStroke(1.dp, Slate300)
                ) {
                    Text(
                        text = "${capsules.size} LOGGED",
                        color = Slate700,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (capsules.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Slate50,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusGreen, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("No Interruption Capsules Stored", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Slate800)
                        Text("Desk sanctuary clean • 100% on-device local storage", fontSize = 11.sp, color = Slate500)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    capsules.take(4).forEach { capsule ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Slate50,
                            border = BorderStroke(1.dp, if (capsule.isUrgent) StatusRedBorder else Slate200),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${capsule.personName} (${capsule.distanceZone})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Slate900
                                    )
                                    Text(
                                        text = if (capsule.aiActionItem.isNotBlank()) capsule.aiActionItem else capsule.taskSummary,
                                        fontSize = 12.sp,
                                        color = Slate600,
                                        maxLines = 2
                                    )
                                }

                                IconButton(
                                    onClick = { coroutineScope.launch { repository.delete(capsule.id) } },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = StatusRed, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            repository.insert(
                                InterruptionEntity(
                                    personName = "Rahul from Backend",
                                    taskSummary = "Review PR 142 API schema changes before 4 PM deployment",
                                    aiActionItem = "Review PR 142 API schema changes",
                                    aiDeadline = "Before 4 PM",
                                    aiUrgencyReason = "Deployment blocker",
                                    targetComponent = "Backend API",
                                    rawTranscript = "Hey, please review PR 142 API schema changes before 4 PM deployment",
                                    hasVoiceTranscript = true,
                                    contextSnippet = "Editing auth/TokenManager.kt line 88",
                                    distanceZone = "0.5m (At Desk)",
                                    durationSec = 6L,
                                    isUrgent = true
                                )
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Slate300)
                ) {
                    Text("Add Sample Capsule", fontSize = 11.sp, color = Slate700)
                }

                OutlinedButton(
                    onClick = { coroutineScope.launch { repository.deleteAll() } },
                    modifier = Modifier.weight(0.7f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, StatusRedBorder)
                ) {
                    Text("Clear All", fontSize = 11.sp, color = StatusRed)
                }
            }
        }
    }
}

@Composable
fun VivoOfficeKitCard(context: Context) {
    val joviManager = remember { GuardService.getJoviNotesSyncManager(context) }
    val syncState by joviManager.syncState.collectAsState()

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = BorderStroke(1.dp, Slate200),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Sync, contentDescription = null, tint = Slate800, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Vivo Office Kit & Jovi Notes Sync", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Slate900)
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (syncState.isAutoSyncEnabled) StatusGreenBg else Slate100,
                    border = BorderStroke(1.dp, if (syncState.isAutoSyncEnabled) StatusGreenBorder else Slate300)
                ) {
                    Text(
                        text = if (syncState.isAutoSyncEnabled) "AUTO-SYNC ON" else "OFF",
                        color = if (syncState.isAutoSyncEnabled) StatusGreen else Slate600,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Exports interruption task payloads directly to Vivo Notes (com.vivo.notes) and system clipboard.",
                fontSize = 12.sp,
                color = Slate600
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Enable Automatic Jovi Sync", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Slate800)
                Switch(
                    checked = syncState.isAutoSyncEnabled,
                    onCheckedChange = { joviManager.setAutoSyncEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PureWhite,
                        checkedTrackColor = Slate900,
                        uncheckedThumbColor = Slate400,
                        uncheckedTrackColor = Slate200
                    )
                )
            }
        }
    }
}

@Composable
fun PrivacyBatteryGuardCard(context: Context) {
    val powerManager = remember { GuardService.getPowerManagerGuard(context) }
    val telemetry by powerManager.telemetry.collectAsState()
    val auditor = remember { GuardService.getPrivacyAuditor(context) }
    val auditReport by auditor.auditReport.collectAsState()

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = BorderStroke(1.dp, Slate200),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = Slate800, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Battery & Air-Gapped Privacy", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Slate900)
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = StatusGreenBg,
                    border = BorderStroke(1.dp, StatusGreenBorder)
                ) {
                    Text(
                        text = "0 BYTES NETWORK",
                        color = StatusGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Zero outgoing internet capabilities. Operates 100% offline in airplane mode with under 3% per hour drain.",
                fontSize = 12.sp,
                color = Slate600
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Slate50,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Battery Level", fontSize = 11.sp, color = Slate500)
                        Text("${telemetry.batteryPercent}% (${telemetry.estimatedDrainPerHour}%/hr)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate900)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Slate50,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Network Sockets", fontSize = 11.sp, color = Slate500)
                        Text("0 Active (Air-Gapped)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = StatusGreen)
                    }
                }
            }
        }
    }
}

@Composable
fun SoundHapticsTestCard(context: Context) {
    val feedbackManager = remember { com.auradesk.guard.utils.FeedbackManager(context) }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = BorderStroke(1.dp, Slate200),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Vibration, contentDescription = null, tint = Slate800, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Subconscious Haptic Motor Cues", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Slate900)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Precision vibration motor rhythms communicate approach distance silently through desk surfaces.",
                fontSize = 12.sp,
                color = Slate600
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { feedbackManager.playHapticWhisperLow() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Slate300)
                ) {
                    Text("Low (80ms)", fontSize = 11.sp, color = Slate700)
                }

                OutlinedButton(
                    onClick = { feedbackManager.playHapticWhisperMedium() },
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Slate300)
                ) {
                    Text("Mid (2m Approach)", fontSize = 11.sp, color = Slate700)
                }

                OutlinedButton(
                    onClick = { feedbackManager.playHapticWhisperUrgent() },
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, StatusRedBorder)
                ) {
                    Text("Urgent (0.5m)", fontSize = 11.sp, color = StatusRed)
                }
            }
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
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = BorderStroke(1.dp, Slate200),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Visibility, contentDescription = null, tint = Slate800, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Always-On Focus Screen (AOD)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Slate900)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "When face-down, the display switches to the minimalist OLED black mode to conserve energy while displaying the live session timer.",
                fontSize = 12.sp,
                color = Slate600
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { showPreview = true },
                colors = ButtonDefaults.buttonColors(containerColor = Slate900),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Launch AOD Screen Preview", fontSize = 12.sp, color = PureWhite)
            }
        }
    }
}

@Composable
fun TestingInstructionsCard() {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = BorderStroke(1.dp, Slate200),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = Slate800, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Verification & Stage Demo Instructions", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Slate900)
            }

            Spacer(modifier = Modifier.height(10.dp))

            val steps = listOf(
                "1. Face-down flip: Place device face-down to arm guard and start AOD timer.",
                "2. Distance radar: Walk toward device from 5m to trigger 2m mid-haptic and 0.5m urgent buzz.",
                "3. Voice capture: Speak task during 10s arrival window for local speech extraction.",
                "4. Lift device: View synthesized action item and previous code context snippet.",
                "5. Shake gesture: Give device 3 rapid shakes to instantly incinerate stored records."
            )

            steps.forEach { step ->
                Text(step, fontSize = 12.sp, color = Slate700, lineHeight = 18.sp)
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}
