package com.auradesk.guard.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
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
import com.auradesk.guard.ui.glass.*
import kotlinx.coroutines.launch

enum class DashboardTab(val title: String, val icon: ImageVector) {
    FOCUS("Focus", Icons.Default.HourglassTop),
    RADAR("Radar", Icons.Default.Radar),
    OFFICE_KIT("Office Kit", Icons.Default.LaptopMac),
    LOGS_AI("AI & Logs", Icons.Default.Psychology)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onReplayTour: () -> Unit = {}) {
    val context   = LocalContext.current
    val isRunning by GuardService.isRunning.collectAsState()
    val isArmed   by GuardService.isArmed.collectAsState()

    val prefs = remember { context.getSharedPreferences("auradesk_prefs", Context.MODE_PRIVATE) }
    var activeName by remember { mutableStateOf(prefs.getString("user_name", "")?.trim() ?: "") }
    var showNameDialog by remember { mutableStateOf(false) }
    var editedName by remember { mutableStateOf(activeName) }

    var selectedTab    by remember { mutableStateOf(DashboardTab.FOCUS) }
    var showAodPreview by remember { mutableStateOf(false) }

    BackHandler(enabled = selectedTab != DashboardTab.FOCUS || showAodPreview) {
        if (showAodPreview) showAodPreview = false
        else if (selectedTab != DashboardTab.FOCUS) selectedTab = DashboardTab.FOCUS
    }

    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text(if (activeName.isNotBlank()) "Update Name / Call-Sign" else "Set Your Name", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Your name is used for on-device voice detection and auto-replies. If left empty, 'the user' will be used.", fontSize = 13.sp, color = GlassColors.TextSecondary)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        singleLine = true,
                        placeholder = { Text("Enter your name (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val finalName = editedName.trim()
                    prefs.edit().putString("user_name", finalName).apply()
                    activeName = finalName
                    GuardService.ensureAudioCapsuleManager(context).setUserName(finalName)
                    showNameDialog = false
                }) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val permissionsToRequest = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
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
    ) { result -> hasPermissions = result.values.all { it } }

    if (showAodPreview) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showAodPreview = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            GuardArmedScreen(onDisarm = { showAodPreview = false })
        }
    }

    val sceneBackground = if (isArmed) GlassColors.ArmedSceneBgGradient else GlassColors.SceneBgGradient

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = sceneBackground)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("AuraDesk", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = GlassColors.TextPrimary)
                            Text(
                                text = if (isArmed) "Focus Shield Armed" else if (isRunning) "Service Ready" else "Standby",
                                fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                color = if (isArmed) GlassColors.AccentGreen else GlassColors.TextMuted
                            )
                        }
                    },
                    actions = {
                        Box(
                            modifier = Modifier.clickable {
                                editedName = activeName
                                showNameDialog = true
                            }
                        ) {
                            GlassBadge(
                                text = if (activeName.isNotBlank()) "Callsign: $activeName" else "Set Name",
                                tintColor = if (activeName.isNotBlank()) GlassColors.GlassGreen else GlassColors.GlassBlue,
                                textColor = if (activeName.isNotBlank()) GlassColors.AccentGreen else GlassColors.AccentBlue
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = onReplayTour) {
                            Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Help", tint = GlassColors.IconColor)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xCCFFFFFF),
                        titleContentColor = GlassColors.TextPrimary
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = Color(0xBBFFFFFF),
                    tonalElevation = 0.dp
                ) {
                    DashboardTab.values().forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick  = { selectedTab = tab },
                            icon = {
                                Icon(tab.icon, contentDescription = tab.title, modifier = Modifier.size(22.dp))
                            },
                            label = {
                                Text(
                                    text = tab.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor   = GlassColors.IconColor,
                                selectedTextColor   = GlassColors.TextPrimary,
                                indicatorColor      = Color(0x44FFFFFF),
                                unselectedIconColor = GlassColors.IconColorMuted,
                                unselectedTextColor = GlassColors.TextMuted
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
                    .padding(horizontal = 16.dp)
            ) {
                // Permission Banner
                if (!hasPermissions) {
                    Spacer(modifier = Modifier.height(12.dp))
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        tintColor = GlassColors.GlassAmber.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("Camera & Audio Permissions Needed", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = GlassColors.TextPrimary)
                                Text("Required for desk guard and voice notes", fontSize = 12.sp, color = GlassColors.TextSecondary)
                            }
                            GlassButton(
                                text = "Grant",
                                onClick = { permissionLauncher.launch(permissionsToRequest.toTypedArray()) },
                                isPrimary = true,
                                modifier = Modifier.width(80.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        val forward = targetState.ordinal > initialState.ordinal
                        (slideInHorizontally(
                            animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                            initialOffsetX = { if (forward) it / 3 else -it / 3 }
                        ) + fadeIn(
                            animationSpec = tween(220, easing = LinearOutSlowInEasing)
                        )).togetherWith(
                            slideOutHorizontally(
                                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                                targetOffsetX = { if (forward) -it / 3 else it / 3 }
                            ) + fadeOut(
                                animationSpec = tween(180, easing = FastOutLinearInEasing)
                            )
                        )
                    },
                    label = "tabContentTransition",
                    modifier = Modifier.weight(1f)
                ) { tab ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        when (tab) {
                            DashboardTab.FOCUS -> {
                                StaggeredAnimatedCard(index = 0) {
                                    FocusServiceCard(
                                        isRunning = isRunning, isArmed = isArmed,
                                        onToggleService = {
                                            if (isRunning) GuardService.stopService(context) else GuardService.startService(context)
                                        },
                                        onLaunchAod = { showAodPreview = true }
                                    )
                                }
                                StaggeredAnimatedCard(index = 1) {
                                    DeepWorkCadenceCard()
                                }
                                StaggeredAnimatedCard(index = 2) {
                                    SensorTelemetryCard(isRunning = isRunning)
                                }
                                StaggeredAnimatedCard(index = 3) {
                                    SystemStatusBadgeCard(context = context)
                                }
                            }
                            DashboardTab.RADAR -> {
                                StaggeredAnimatedCard(index = 0) {
                                    PerimeterRadarCard()
                                }
                                StaggeredAnimatedCard(index = 1) {
                                    HapticFeedbackCard(context = context)
                                }
                            }
                            DashboardTab.OFFICE_KIT -> {
                                StaggeredAnimatedCard(index = 0) {
                                    VivoOfficeKitCard(isArmed = isArmed)
                                }
                            }
                            DashboardTab.LOGS_AI -> {
                                StaggeredAnimatedCard(index = 0) {
                                    OnDeviceLlamaCard(context = context)
                                }
                                StaggeredAnimatedCard(index = 1) {
                                    VoiceCaptureSynthesizerCard(context = context)
                                }
                                StaggeredAnimatedCard(index = 2) {
                                    InterruptionHistoryCard(context = context)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

// ─── FOCUS TAB ────────────────────────────────────────────────────────────────

@Composable
fun FocusServiceCard(
    isRunning: Boolean,
    isArmed: Boolean,
    onToggleService: () -> Unit,
    onLaunchAod: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        tintColor = if (isArmed) GlassColors.GlassGreen else Color.Transparent
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = if (isArmed) "Focus Shield Armed" else if (isRunning) "Focus Guard Ready" else "Focus Guard Inactive",
                        fontWeight = FontWeight.Bold, fontSize = 16.sp, color = GlassColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isArmed) "Device face-down on desk • Monitoring active" else "Flip device face-down on desk to arm",
                        fontSize = 12.sp, color = GlassColors.TextSecondary
                    )
                }
                GlassBadge(
                    text = if (isArmed) "ARMED" else if (isRunning) "STANDBY" else "OFFLINE",
                    tintColor = if (isArmed) GlassColors.GlassGreen else Color.Transparent,
                    textColor = if (isArmed) GlassColors.AccentGreen else GlassColors.TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton(
                    text = if (isRunning) "Stop Guard" else "Start Guard",
                    onClick = onToggleService, modifier = Modifier.weight(1.3f), isPrimary = true
                )
                GlassButton(text = "AOD Clock", onClick = onLaunchAod, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun DeepWorkCadenceCard() {
    val deepWorkState by GuardService.liveDeepWork.collectAsState()

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Deep Work Focus Index", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = GlassColors.TextPrimary)
                    Text("Keyboard cadence & quiet study detector", fontSize = 12.sp, color = GlassColors.TextSecondary)
                }
                GlassBadge(
                    text = "${deepWorkState.focusScore}% FOCUS",
                    tintColor = if (deepWorkState.isDeepWork) GlassColors.GlassGreen else Color.Transparent,
                    textColor = if (deepWorkState.isDeepWork) GlassColors.AccentGreen else GlassColors.TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassMetricTile("Typing Cadence", "${String.format("%.0f", deepWorkState.typingCadenceBpm)} BPM", modifier = Modifier.weight(1f))
                GlassMetricTile("Environment Mode", deepWorkState.environmentProfile.label, modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton("Simulate Focus", onClick = { GuardService.simulateDeepWork(true, 92, 140f) }, modifier = Modifier.weight(1f))
                GlassButton("Simulate Idle", onClick = { GuardService.simulateDeepWork(false, 15, 0f) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun SensorTelemetryCard(isRunning: Boolean) {
    val sensors by GuardService.liveSensors.collectAsState()

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Sensor Fusion State", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = GlassColors.TextPrimary)
                    Text("Optical occlusion and gravity alignment", fontSize = 12.sp, color = GlassColors.TextSecondary)
                }
                Text(
                    text = if (isRunning) "Active (50Hz)" else "Standby",
                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = if (isRunning) GlassColors.AccentGreen else GlassColors.TextMuted
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassMetricTile("Proximity", "${String.format("%.1f", sensors.proximityCm)} cm",
                    modifier = Modifier.weight(1f),
                    tintColor = if (sensors.isProximityNear) GlassColors.GlassGreen else Color.Transparent,
                    statusText = if (sensors.isProximityNear) "Pass" else "Wait",
                    statusColor = if (sensors.isProximityNear) GlassColors.AccentGreen else GlassColors.TextMuted)
                GlassMetricTile("Ambient Light", "${String.format("%.0f", sensors.lightLux)} lux",
                    modifier = Modifier.weight(1f),
                    tintColor = if (sensors.isLightDark) GlassColors.GlassGreen else Color.Transparent,
                    statusText = if (sensors.isLightDark) "Pass" else "Wait",
                    statusColor = if (sensors.isLightDark) GlassColors.AccentGreen else GlassColors.TextMuted)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassMetricTile("Gravity Z", "${String.format("%.2f", sensors.accelZ)} m/s²",
                    modifier = Modifier.weight(1f),
                    tintColor = if (sensors.isZDownward) GlassColors.GlassGreen else Color.Transparent,
                    statusText = if (sensors.isZDownward) "Pass" else "Wait",
                    statusColor = if (sensors.isZDownward) GlassColors.AccentGreen else GlassColors.TextMuted)
                GlassMetricTile("Gyro Drift", "${String.format("%.3f", sensors.gyroMagnitude)} rad/s",
                    modifier = Modifier.weight(1f),
                    tintColor = if (sensors.isGyroStable) GlassColors.GlassGreen else Color.Transparent,
                    statusText = if (sensors.isGyroStable) "Pass" else "Wait",
                    statusColor = if (sensors.isGyroStable) GlassColors.AccentGreen else GlassColors.TextMuted)
            }
        }
    }
}

@Composable
fun SystemStatusBadgeCard(context: Context) {
    val powerManager = remember { GuardService.getPowerManagerGuard(context) }
    val telemetry by powerManager.telemetry.collectAsState()

    GlassCard(modifier = Modifier.fillMaxWidth(), tintColor = GlassColors.GlassGreen.copy(alpha = 0.3f)) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Power Consumption", fontSize = 12.sp, color = GlassColors.TextMuted)
                Text("${telemetry.batteryPercent}% • ${telemetry.estimatedDrainPerHour}%/hr", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = GlassColors.TextPrimary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Security Protocol", fontSize = 12.sp, color = GlassColors.TextMuted)
                Text("100% Air-Gapped (0 Bytes)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = GlassColors.AccentGreen)
            }
        }
    }
}

// ─── RADAR TAB ────────────────────────────────────────────────────────────────

@Composable
fun PerimeterRadarCard() {
    val radar by GuardService.liveRadar.collectAsState()
    var showViewfinder by remember { mutableStateOf(false) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Perimeter Vision Radar", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = GlassColors.TextPrimary)
                    Text("Real-time 5m to 0.5m desk proximity tracking", fontSize = 12.sp, color = GlassColors.TextSecondary)
                }
                GlassBadge(
                    text = if (radar.isPersonDetected) "SUBJECT DETECTED" else "CLEAR",
                    tintColor = if (radar.isPersonDetected) GlassColors.GlassGreen else Color.Transparent,
                    textColor = if (radar.isPersonDetected) GlassColors.AccentGreen else GlassColors.TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            GlassSection(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Current Zone", fontSize = 12.sp, color = GlassColors.TextMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(radar.zone.label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = GlassColors.TextPrimary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Distance", fontSize = 12.sp, color = GlassColors.TextMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("${String.format("%.1f", radar.distanceMeters)} meters",
                            fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace, color = GlassColors.TextPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            GlassButton(
                text = if (showViewfinder) "Close Camera Viewfinder" else "Open Camera Viewfinder",
                onClick = { showViewfinder = !showViewfinder },
                modifier = Modifier.fillMaxWidth(),
                isPrimary = true
            )

            if (showViewfinder) {
                Spacer(modifier = Modifier.height(12.dp))
                CameraRadarViewfinder(onClose = { showViewfinder = false })
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton("2.0m Approach", onClick = { GuardService.simulateRadar(2.0f, true, 30.0f) }, modifier = Modifier.weight(1f))
                GlassButton("0.5m At Desk", onClick = { GuardService.simulateRadar(0.5f, false, 0.0f) }, modifier = Modifier.weight(1f))
                GlassButton("Reset", onClick = { GuardService.clearRadar() }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun HapticFeedbackCard(context: Context) {
    val feedbackManager = remember { com.auradesk.guard.utils.FeedbackManager(context) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Subconscious Haptic Patterns", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = GlassColors.TextPrimary)
            Text("Silent vibration cues based on proximity distance", fontSize = 12.sp, color = GlassColors.TextSecondary)

            Spacer(modifier = Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton("Low (80ms)", onClick = {
                    feedbackManager.playHapticWhisperLow()
                    GuardService.postHapticAlert("Low Priority Ping", "Double 80ms Subtle Pulse", com.auradesk.guard.vision.RadarZone.FAR_5M)
                }, modifier = Modifier.weight(1f))
                GlassButton("Mid (2m)", onClick = {
                    feedbackManager.playHapticWhisperMedium()
                    GuardService.postHapticAlert("Approaching Alert", "Subject at 2.0m • Mid Pulse", com.auradesk.guard.vision.RadarZone.MID_2M)
                }, modifier = Modifier.weight(1f))
                GlassButton("Urgent (0.5m)", onClick = {
                    feedbackManager.playHapticWhisperUrgent()
                    GuardService.postHapticAlert("Urgent Desk Alert", "Subject at desk • 0.5m Triple Buzz", com.auradesk.guard.vision.RadarZone.CLOSE_05M)
                },
                    modifier = Modifier.weight(1f),
                    tintColor = GlassColors.GlassRed, textColor = GlassColors.AccentRed)
            }
        }
    }
}

// ─── INTERRUPTION LOG TAB ─────────────────────────────────────────────────────

@Composable
fun VoiceCaptureSynthesizerCard(context: Context) {
    val audioState       by GuardService.liveAudioCapsule.collectAsState()
    val synthesizedTask  by GuardService.liveSynthesizedTask.collectAsState()

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Voice VAD & Action Synthesizer", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = GlassColors.TextPrimary)
                    Text("10s offline audio capsule with on-device action extraction", fontSize = 12.sp, color = GlassColors.TextSecondary)
                }
                GlassBadge(
                    text = if (audioState.isRecording) "RECORDING (${audioState.remainingSeconds}s)" else audioState.capsuleStatus,
                    tintColor = if (audioState.isRecording) GlassColors.GlassRed else Color.Transparent,
                    textColor = if (audioState.isRecording) GlassColors.AccentRed else GlassColors.TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            GlassSection(modifier = Modifier.fillMaxWidth()) {
                Text("Live Audio Transcript", fontSize = 11.sp, color = GlassColors.TextMuted)
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = audioState.livePartialTranscript.ifBlank { "No audio recorded yet" },
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = GlassColors.TextPrimary
                )
            }

            if (synthesizedTask != null) {
                Spacer(modifier = Modifier.height(10.dp))
                GlassSection(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Extracted Action Item", fontSize = 11.sp, color = GlassColors.TextMuted)
                        Text(synthesizedTask!!.urgencyLevel.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GlassColors.TextPrimary)
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(synthesizedTask!!.actionItem, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GlassColors.TextPrimary)
                    if (synthesizedTask!!.deadlineOrTime != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Deadline: ${synthesizedTask!!.deadlineOrTime}", fontSize = 12.sp, color = GlassColors.AccentAmber, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton("Record 10s", onClick = { GuardService.startAudioCapsule(context, 10) },
                    modifier = Modifier.weight(1f), isPrimary = true)
                GlassButton("Simulate Rahul", onClick = {
                    GuardService.simulateSpeechCapsule(
                        context = context, speakerName = "Rahul from Backend",
                        speechText = "Hey Arjun, can you review PR 142 API schema changes before the 4 PM deployment?",
                        durationSec = 6L, isUrgent = true
                    )
                }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun InterruptionHistoryCard(context: Context) {
    val repository  = remember { InterruptionRepository.getInstance(context) }
    val capsules    by repository.allInterruptions.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Interruption History", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = GlassColors.TextPrimary)
                    Text("Local SQLite storage with automatic expiry", fontSize = 12.sp, color = GlassColors.TextSecondary)
                }
                GlassBadge(text = "${capsules.size} STORED")
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (capsules.isEmpty()) {
                GlassSection(modifier = Modifier.fillMaxWidth()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("No Interruption Capsules Stored", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GlassColors.TextPrimary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Desk sanctuary is clean", fontSize = 12.sp, color = GlassColors.TextSecondary)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    capsules.take(4).forEach { capsule ->
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            tintColor = if (capsule.isUrgent) GlassColors.GlassRed.copy(alpha = 0.3f) else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = "${capsule.personName} (${capsule.distanceZone})",
                                        fontWeight = FontWeight.Bold, fontSize = 13.sp, color = GlassColors.TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    val actionText = if (capsule.aiActionItem.isNotBlank()) capsule.aiActionItem else capsule.taskSummary
                                    Text(
                                        text = actionText,
                                        fontSize = 12.sp, color = GlassColors.TextSecondary, maxLines = 2
                                    )
                                    if (capsule.aiDeadline.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Due: ${capsule.aiDeadline}",
                                            fontSize = 11.sp, color = GlassColors.AccentAmber, fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { coroutineScope.launch { repository.delete(capsule.id) } },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete",
                                        tint = GlassColors.IconColor, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton("Add Sample Capsule", onClick = {
                    coroutineScope.launch {
                        repository.insert(
                            InterruptionEntity(
                                personName = "Rahul from Backend",
                                taskSummary = "Review PR 142 API schema changes before 4 PM deployment",
                                aiActionItem = "Review PR 142 API schema changes",
                                aiDeadline = "Before 4 PM", aiUrgencyReason = "Deployment blocker",
                                targetComponent = "Backend API",
                                rawTranscript = "Hey, please review PR 142 API schema changes before 4 PM deployment",
                                hasVoiceTranscript = true, contextSnippet = "Editing auth/TokenManager.kt line 88",
                                distanceZone = "0.5m (At Desk)", durationSec = 6L, isUrgent = true
                            )
                        )
                    }
                }, modifier = Modifier.weight(1f))
                GlassButton("Clear All", onClick = {
                    coroutineScope.launch { repository.deleteAll() }
                }, modifier = Modifier.weight(1f), tintColor = GlassColors.GlassRed, textColor = GlassColors.AccentRed)
            }
        }
    }
}

@Composable
fun OnDeviceLlamaCard(context: Context) {
    val llamaRunner = remember { com.auradesk.guard.llm.LlamaModelRunner.getInstance(context) }
    val llamaState by llamaRunner.llamaState.collectAsState()
    val lastReply by llamaRunner.lastGeneratedReply.collectAsState()

    var showTestDialog by remember { mutableStateOf(false) }
    var testSender by remember { mutableStateOf("Rahul (Tech Lead)") }
    var testMessage by remember { mutableStateOf("Hey Arjun, can you review the payment auth PR before deployment?") }
    var testGeneratedResult by remember { mutableStateOf("") }
    var isGeneratingTest by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val isNotifAccessGranted = remember {
        val flat = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        flat != null && flat.contains(context.packageName)
    }

    if (showTestDialog) {
        AlertDialog(
            onDismissRequest = { showTestDialog = false },
            title = { Text("Test Qwen2-0.5B Auto-Reply", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Simulate an incoming notification to test on-device LLM auto-reply generation.", fontSize = 12.sp, color = GlassColors.TextSecondary)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Sender Name:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GlassColors.TextPrimary)
                    OutlinedTextField(
                        value = testSender,
                        onValueChange = { testSender = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Incoming Message:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GlassColors.TextPrimary)
                    OutlinedTextField(
                        value = testMessage,
                        onValueChange = { testMessage = it },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                    if (testGeneratedResult.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Generated Auto-Reply (Prompt A):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GlassColors.AccentGreen)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x15059669))
                                .padding(10.dp)
                        ) {
                            Text(testGeneratedResult, fontSize = 12.sp, color = GlassColors.TextPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isGeneratingTest = true
                        coroutineScope.launch {
                            val prefs = context.getSharedPreferences("auradesk_prefs", Context.MODE_PRIVATE)
                            val userName = prefs.getString("user_name", "")?.trim() ?: ""
                            val returnTime = llamaRunner.calculateReturnTime(45)
                            val res = llamaRunner.generateAutoReply(testSender, testMessage, returnTime, userName)
                            testGeneratedResult = res
                            isGeneratingTest = false
                        }
                    },
                    enabled = !isGeneratingTest
                ) {
                    Text(if (isGeneratingTest) "Generating..." else "Run LLM Inference", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTestDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("On-Device LLM (Qwen2-0.5B)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = GlassColors.TextPrimary)
                    Text("Phase 7 Native llama.cpp INT4 Auto-Reply", fontSize = 12.sp, color = GlassColors.TextSecondary)
                }
                when (val st = llamaState) {
                    is com.auradesk.guard.llm.LlamaState.Ready -> {
                        GlassBadge("INT4 in RAM", tintColor = GlassColors.GlassGreen, textColor = GlassColors.AccentGreen)
                    }
                    is com.auradesk.guard.llm.LlamaState.Unloaded -> {
                        GlassBadge("Unloaded (Disk)", tintColor = GlassColors.GlassBlue, textColor = GlassColors.AccentBlue)
                    }
                    is com.auradesk.guard.llm.LlamaState.Downloading -> {
                        GlassBadge("${st.progressPercent}%", tintColor = GlassColors.GlassBlue, textColor = GlassColors.AccentBlue)
                    }
                    is com.auradesk.guard.llm.LlamaState.Loading -> {
                        GlassBadge("Mounting...", tintColor = GlassColors.GlassAmber, textColor = GlassColors.AccentAmber)
                    }
                    is com.auradesk.guard.llm.LlamaState.NotDownloaded -> {
                        GlassBadge("352 MB Needed", tintColor = GlassColors.GlassAmber, textColor = GlassColors.AccentAmber)
                    }
                    is com.auradesk.guard.llm.LlamaState.Error -> {
                        GlassBadge("Error", tintColor = GlassColors.GlassRed, textColor = GlassColors.AccentRed)
                    }
                    else -> {}
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (val st = llamaState) {
                is com.auradesk.guard.llm.LlamaState.Ready -> {
                    Text(
                        text = "Native ARM NEON kernels resident in RAM. Generates context-aware replies in ~1.1s under airplane mode zero-bytes.",
                        fontSize = 12.sp, color = GlassColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton(
                            text = "Test LLM Auto-Reply",
                            onClick = { showTestDialog = true },
                            modifier = Modifier.weight(1.3f),
                            isPrimary = true
                        )
                        GlassButton(
                            text = "Eject from RAM",
                            onClick = { llamaRunner.unloadModel() },
                            modifier = Modifier.weight(1f),
                            tintColor = GlassColors.GlassRed,
                            textColor = GlassColors.AccentRed
                        )
                    }
                    if (!isNotifAccessGranted) {
                        Spacer(modifier = Modifier.height(8.dp))
                        GlassButton(
                            text = "Grant Notification Access",
                            onClick = {
                                context.startActivity(android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                is com.auradesk.guard.llm.LlamaState.Unloaded -> {
                    Text(
                        text = "Qwen2-0.5B-Instruct INT4 is verified on disk (~352MB). Model is unloaded from RAM to save battery. Load into memory to test or arm focus mode.",
                        fontSize = 12.sp, color = GlassColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton(
                            text = "Mount / Load into RAM",
                            onClick = { coroutineScope.launch { llamaRunner.loadModel() } },
                            modifier = Modifier.weight(1.2f),
                            isPrimary = true
                        )
                        if (!isNotifAccessGranted) {
                            GlassButton(
                                text = "Notif Access",
                                onClick = {
                                    context.startActivity(android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                is com.auradesk.guard.llm.LlamaState.Downloading -> {
                    Text(
                        text = "Downloading Qwen2-0.5B-Instruct INT4 (~352MB) directly to device storage...",
                        fontSize = 12.sp, color = GlassColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { st.progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = GlassColors.AccentBlue,
                        trackColor = Color(0x22000000)
                    )
                }
                is com.auradesk.guard.llm.LlamaState.Loading -> {
                    Text("Memory-mapping INT4 tensor weights into RAM (~1.1s)...", fontSize = 12.sp, color = GlassColors.TextSecondary)
                }
                is com.auradesk.guard.llm.LlamaState.NotDownloaded -> {
                    Text(
                        text = "Qwen2-0.5B-Instruct INT4 GGUF model (~352MB) enables 100% offline auto-reply generation.",
                        fontSize = 12.sp, color = GlassColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    GlassButton(
                        text = "Download Qwen2-0.5B Model (352MB)",
                        onClick = { llamaRunner.downloadModel() },
                        modifier = Modifier.fillMaxWidth(),
                        isPrimary = true
                    )
                }
                is com.auradesk.guard.llm.LlamaState.Error -> {
                    Text("Model status: ${st.message}", fontSize = 12.sp, color = GlassColors.AccentRed)
                    Spacer(modifier = Modifier.height(8.dp))
                    GlassButton(
                        text = "Retry",
                        onClick = { llamaRunner.checkModelStatus() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                else -> {}
            }
        }
    }
}
