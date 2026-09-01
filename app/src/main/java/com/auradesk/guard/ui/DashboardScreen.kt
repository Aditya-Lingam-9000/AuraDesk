package com.auradesk.guard.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
    FOCUS("Focus", Icons.Default.HourglassTop),
    RADAR("Radar", Icons.Default.Radar),
    LOGS("Interruption Log", Icons.Default.History)
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

    var selectedTab by remember { mutableStateOf(DashboardTab.FOCUS) }
    var showAodPreview by remember { mutableStateOf(false) }

    // Back Navigation Handler
    BackHandler(enabled = selectedTab != DashboardTab.FOCUS || showAodPreview) {
        if (showAodPreview) {
            showAodPreview = false
        } else if (selectedTab != DashboardTab.FOCUS) {
            selectedTab = DashboardTab.FOCUS
        }
    }

    // Permission launcher
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

    if (showAodPreview) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showAodPreview = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            GuardArmedScreen(onDisarm = { showAodPreview = false })
        }
    }

    Scaffold(
        containerColor = AppBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "AuraDesk",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (isArmed) "Focus Shield Armed" else if (isRunning) "Service Ready" else "Standby",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isArmed) AccentGreen else TextMuted
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onReplayTour) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = "Help & Tour",
                            tint = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PureWhite,
                    titleContentColor = TextPrimary
                ),
                modifier = Modifier.border(BorderStroke(1.dp, BorderSubtle))
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = PureWhite,
                tonalElevation = 0.dp,
                modifier = Modifier.border(BorderStroke(1.dp, BorderSubtle))
            ) {
                DashboardTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = TextPrimary,
                            selectedTextColor = TextPrimary,
                            indicatorColor = BorderSubtle,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
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
            // Permission Notice
            if (!hasPermissions) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentAmberBg,
                    border = BorderStroke(1.dp, AccentAmberBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Camera & Audio Permissions Needed",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Required for desk guard and voice notes",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }

                        Button(
                            onClick = { permissionLauncher.launch(permissionsToRequest.toTypedArray()) },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text("Grant", fontSize = 12.sp, color = PureWhite, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            when (selectedTab) {
                DashboardTab.FOCUS -> {
                    // Main Service Control Card
                    FocusServiceCard(
                        isRunning = isRunning,
                        isArmed = isArmed,
                        onToggleService = {
                            if (isRunning) {
                                GuardService.stopService(context)
                            } else {
                                GuardService.startService(context)
                            }
                        },
                        onLaunchAod = { showAodPreview = true }
                    )

                    // Deep Work Cadence
                    DeepWorkCadenceCard()

                    // Optical & Gravity Sensors
                    SensorTelemetryCard(sensors = sensors, isRunning = isRunning)

                    // Air-Gapped & Power Status
                    SystemStatusBadgeCard(context = context)
                }

                DashboardTab.RADAR -> {
                    // Person Approaching Radar
                    PerimeterRadarCard()

                    // Subconscious Haptic Whisper Cues
                    HapticFeedbackCard(context = context)
                }

                DashboardTab.LOGS -> {
                    // On-Device LLM & Voice Capture Trigger
                    VoiceCaptureSynthesizerCard(context = context)

                    // Stored Interruption Capsules
                    InterruptionHistoryCard(context = context)

                    // Vivo Notes Handoff Settings
                    VivoNotesSyncCard(context = context)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// -------------------------------------------------------------
// POLISHED HIGH-CONTRAST UI CARDS
// -------------------------------------------------------------

@Composable
fun FocusServiceCard(
    isRunning: Boolean,
    isArmed: Boolean,
    onToggleService: () -> Unit,
    onLaunchAod: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, if (isArmed) AccentGreenBorder else BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = if (isArmed) "Focus Shield Armed" else if (isRunning) "Focus Guard Ready" else "Focus Guard Inactive",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isArmed) "Device face-down on desk • Monitoring active" else "Flip device face-down on desk to arm",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isArmed) AccentGreenBg else BorderSubtle,
                    border = BorderStroke(1.dp, if (isArmed) AccentGreenBorder else BorderStrong)
                ) {
                    Text(
                        text = if (isArmed) "ARMED" else if (isRunning) "STANDBY" else "OFFLINE",
                        color = if (isArmed) AccentGreen else TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onToggleService,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1.4f),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text(
                        text = if (isRunning) "Stop Guard" else "Start Guard",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PureWhite
                    )
                }

                OutlinedButton(
                    onClick = onLaunchAod,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, BorderStrong),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text(
                        text = "AOD Clock",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun DeepWorkCadenceCard() {
    val deepWorkState by GuardService.liveDeepWork.collectAsState()

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "Deep Work Focus Index",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "Keyboard cadence & quiet study detector",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (deepWorkState.isDeepWork) AccentGreenBg else BorderSubtle,
                    border = BorderStroke(1.dp, if (deepWorkState.isDeepWork) AccentGreenBorder else BorderStrong)
                ) {
                    Text(
                        text = "${deepWorkState.focusScore}% FOCUS",
                        color = if (deepWorkState.isDeepWork) AccentGreen else TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AppBg,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Typing Cadence", fontSize = 12.sp, color = TextMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("${String.format("%.0f", deepWorkState.typingCadenceBpm)} BPM", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AppBg,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Environment Mode", fontSize = 12.sp, color = TextMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(deepWorkState.environmentProfile.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { GuardService.simulateDeepWork(true, 92, 140f) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, BorderStrong)
                ) {
                    Text("Simulate Coding Focus", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                }

                OutlinedButton(
                    onClick = { GuardService.simulateDeepWork(false, 15, 0f) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, BorderStrong)
                ) {
                    Text("Simulate Idle Desk", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun SensorTelemetryCard(sensors: FaceDownSensors, isRunning: Boolean) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Sensor Fusion State", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                    Text("Optical occlusion and gravity alignment", fontSize = 12.sp, color = TextSecondary)
                }

                Text(
                    text = if (isRunning) "Active (50Hz)" else "Standby",
                    fontSize = 11.sp,
                    color = if (isRunning) AccentGreen else TextMuted,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SensorBlock("Proximity", "${String.format("%.1f", sensors.proximityCm)} cm", sensors.isProximityNear, Modifier.weight(1f))
                SensorBlock("Ambient Light", "${String.format("%.0f", sensors.lightLux)} lux", sensors.isLightDark, Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SensorBlock("Gravity Z", "${String.format("%.2f", sensors.accelZ)} m/s²", sensors.isZDownward, Modifier.weight(1f))
                SensorBlock("Gyro Drift", "${String.format("%.3f", sensors.gyroMagnitude)} rad/s", sensors.isGyroStable, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun SensorBlock(label: String, value: String, isPassing: Boolean, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = AppBg,
        border = BorderStroke(1.dp, if (isPassing) AccentGreenBorder else BorderSubtle),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, fontSize = 11.sp, color = TextMuted)
                Text(
                    text = if (isPassing) "Pass" else "Wait",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPassing) AccentGreen else TextMuted
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TextPrimary)
        }
    }
}

@Composable
fun SystemStatusBadgeCard(context: Context) {
    val powerManager = remember { GuardService.getPowerManagerGuard(context) }
    val telemetry by powerManager.telemetry.collectAsState()

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Power Consumption", fontSize = 12.sp, color = TextMuted)
                Text("${telemetry.batteryPercent}% • ${telemetry.estimatedDrainPerHour}%/hr", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text("Security Protocol", fontSize = 12.sp, color = TextMuted)
                Text("100% Air-Gapped (0 Bytes)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
            }
        }
    }
}

@Composable
fun PerimeterRadarCard() {
    val radar by GuardService.liveRadar.collectAsState()
    var showViewfinder by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header Row with robust spacing
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "Perimeter Vision Radar",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "Real-time 5m to 0.5m desk proximity tracking",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (radar.isPersonDetected) AccentGreenBg else BorderSubtle,
                    border = BorderStroke(1.dp, if (radar.isPersonDetected) AccentGreenBorder else BorderStrong)
                ) {
                    Text(
                        text = if (radar.isPersonDetected) "SUBJECT DETECTED" else "CLEAR",
                        color = if (radar.isPersonDetected) AccentGreen else TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Metric Box
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = AppBg,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Current Zone", fontSize = 12.sp, color = TextMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(radar.zone.label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text("Distance", fontSize = 12.sp, color = TextMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("${String.format("%.1f", radar.distanceMeters)} meters", fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TextPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = { showViewfinder = !showViewfinder },
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 11.dp)
            ) {
                Text(if (showViewfinder) "Close Camera Viewfinder" else "Open Camera Viewfinder", fontSize = 13.sp, color = PureWhite, fontWeight = FontWeight.SemiBold)
            }

            if (showViewfinder) {
                Spacer(modifier = Modifier.height(12.dp))
                CameraRadarViewfinder(onClose = { showViewfinder = false })
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Balanced Symmetrical Simulation Buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { GuardService.simulateRadar(2.0f, true, 30.0f) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, BorderStrong)
                ) {
                    Text("2.0m Approach", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                }

                OutlinedButton(
                    onClick = { GuardService.simulateRadar(0.5f, false, 0.0f) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, BorderStrong)
                ) {
                    Text("0.5m At Desk", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                }

                OutlinedButton(
                    onClick = { GuardService.clearRadar() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, BorderStrong)
                ) {
                    Text("Reset", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun HapticFeedbackCard(context: Context) {
    val feedbackManager = remember { com.auradesk.guard.utils.FeedbackManager(context) }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text("Subconscious Haptic Patterns", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
            Text("Silent vibration cues triggered through desk surfaces based on distance", fontSize = 12.sp, color = TextSecondary)

            Spacer(modifier = Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { feedbackManager.playHapticWhisperLow() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, BorderStrong)
                ) {
                    Text("Low (80ms)", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                }

                OutlinedButton(
                    onClick = { feedbackManager.playHapticWhisperMedium() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, BorderStrong)
                ) {
                    Text("Mid (2m Approach)", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                }

                OutlinedButton(
                    onClick = { feedbackManager.playHapticWhisperUrgent() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, AccentRedBorder)
                ) {
                    Text("Urgent (0.5m)", fontSize = 11.sp, color = AccentRed, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun VoiceCaptureSynthesizerCard(context: Context) {
    val audioState by GuardService.liveAudioCapsule.collectAsState()
    val synthesizedTask by GuardService.liveSynthesizedTask.collectAsState()

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Voice VAD & Action Synthesizer", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                    Text("10s offline audio capsule with on-device action extraction", fontSize = 12.sp, color = TextSecondary)
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (audioState.isRecording) AccentRedBg else BorderSubtle,
                    border = BorderStroke(1.dp, if (audioState.isRecording) AccentRedBorder else BorderStrong)
                ) {
                    Text(
                        text = if (audioState.isRecording) "RECORDING (${audioState.remainingSeconds}s)" else audioState.capsuleStatus,
                        color = if (audioState.isRecording) AccentRed else TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Transcript Box
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = AppBg,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Live Audio Transcript", fontSize = 11.sp, color = TextMuted)
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = audioState.livePartialTranscript.ifBlank { "No audio recorded yet" },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
            }

            if (synthesizedTask != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AppBg,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Extracted Action Item", fontSize = 11.sp, color = TextMuted)
                            Text(synthesizedTask!!.urgencyLevel.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(synthesizedTask!!.actionItem, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

                        if (synthesizedTask!!.deadlineOrTime != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Deadline: ${synthesizedTask!!.deadlineOrTime}", fontSize = 12.sp, color = AccentAmber, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { GuardService.startAudioCapsule(context, 10) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(vertical = 11.dp)
                ) {
                    Text("Record 10s Capsule", fontSize = 12.sp, color = PureWhite, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = {
                        GuardService.simulateSpeechCapsule(
                            context = context,
                            speakerName = "Rahul from Backend",
                            speechText = "Hey Arjun, can you review PR 142 API schema changes before the 4 PM deployment?",
                            durationSec = 6L,
                            isUrgent = true
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, BorderStrong),
                    contentPadding = PaddingValues(vertical = 11.dp)
                ) {
                    Text("Simulate Rahul Speech", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun InterruptionHistoryCard(context: Context) {
    val repository = remember { InterruptionRepository.getInstance(context) }
    val capsules by repository.allInterruptions.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header Row with robust spacing
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "Interruption History",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "Local SQLite storage with automatic expiry",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = BorderSubtle,
                    border = BorderStroke(1.dp, BorderStrong)
                ) {
                    Text(
                        text = "${capsules.size} STORED",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (capsules.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AppBg,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No Interruption Capsules Stored", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Desk sanctuary is clean • 100% on-device storage", fontSize = 12.sp, color = TextSecondary)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    capsules.take(4).forEach { capsule ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AppBg,
                            border = BorderStroke(1.dp, if (capsule.isUrgent) AccentRedBorder else BorderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = "${capsule.personName} (${capsule.distanceZone})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (capsule.aiActionItem.isNotBlank()) capsule.aiActionItem else capsule.taskSummary,
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        maxLines = 2
                                    )
                                }

                                IconButton(
                                    onClick = { coroutineScope.launch { repository.delete(capsule.id) } },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete",
                                        tint = AccentRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

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
                    border = BorderStroke(1.dp, BorderStrong)
                ) {
                    Text("Add Sample Capsule", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                }

                OutlinedButton(
                    onClick = { coroutineScope.launch { repository.deleteAll() } },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, AccentRedBorder)
                ) {
                    Text("Clear All", fontSize = 11.sp, color = AccentRed, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun VivoNotesSyncCard(context: Context) {
    val joviManager = remember { GuardService.getJoviNotesSyncManager(context) }
    val syncState by joviManager.syncState.collectAsState()

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Vivo Office Kit & Notes", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                    Text("Direct Markdown task handoff into Vivo Notes", fontSize = 12.sp, color = TextSecondary)
                }

                Switch(
                    checked = syncState.isAutoSyncEnabled,
                    onCheckedChange = { joviManager.setAutoSyncEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PureWhite,
                        checkedTrackColor = BrandPrimary,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = BorderSubtle
                    )
                )
            }
        }
    }
}
