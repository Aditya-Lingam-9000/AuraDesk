package com.auradesk.guard.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    LOGS("Interruption Log", Icons.Default.History)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onReplayTour: () -> Unit = {}) {
    val context   = LocalContext.current
    val isRunning by GuardService.isRunning.collectAsState()
    val isArmed   by GuardService.isArmed.collectAsState()
    val sensors   by GuardService.liveSensors.collectAsState()

    val prefs = remember { context.getSharedPreferences("auradesk_prefs", Context.MODE_PRIVATE) }
    var activeName by remember { mutableStateOf(prefs.getString("user_name", "Arjun") ?: "Arjun") }
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
            title = { Text("Update Call-Sign", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("The bodyguard triggers priority capture when a visitor calls this name.", fontSize = 13.sp, color = GlassColors.TextSecondary)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        singleLine = true,
                        placeholder = { Text("e.g. Arjun") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val finalName = editedName.trim().ifBlank { "Arjun" }
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

    val sceneBackground = Brush.verticalGradient(
        listOf(
            if (isArmed) Color(0xFFBBDBFF) else Color(0xFFCFDBF2),
            if (isArmed) Color(0xFFD1EBD8) else Color(0xFFE8ECF8)
        )
    )

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
                                text = "Callsign: $activeName",
                                tintColor = GlassColors.GlassGreen,
                                textColor = GlassColors.AccentGreen
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = onReplayTour) {
                            Icon(Icons.Default.HelpOutline, contentDescription = "Help", tint = GlassColors.TextSecondary)
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
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor   = GlassColors.TextPrimary,
                                selectedTextColor   = GlassColors.TextPrimary,
                                indicatorColor      = Color(0x44FFFFFF),
                                unselectedIconColor = GlassColors.TextMuted,
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
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Permission Banner
                if (!hasPermissions) {
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

                when (selectedTab) {
                    DashboardTab.FOCUS -> {
                        FocusServiceCard(
                            isRunning = isRunning, isArmed = isArmed,
                            onToggleService = {
                                if (isRunning) GuardService.stopService(context) else GuardService.startService(context)
                            },
                            onLaunchAod = { showAodPreview = true }
                        )
                        DeepWorkCadenceCard()
                        SensorTelemetryCard(sensors = sensors, isRunning = isRunning)
                        SystemStatusBadgeCard(context = context)
                    }
                    DashboardTab.RADAR -> {
                        PerimeterRadarCard()
                        HapticFeedbackCard(context = context)
                    }
                    DashboardTab.LOGS -> {
                        VoiceCaptureSynthesizerCard(context = context)
                        InterruptionHistoryCard(context = context)
                        VivoNotesSyncCard(context = context)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
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
        Column(modifier = Modifier.padding(18.dp)) {
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

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton(
                    text = if (isRunning) "Stop Guard" else "Start Guard",
                    onClick = onToggleService, modifier = Modifier.weight(1.4f), isPrimary = true
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
        Column(modifier = Modifier.padding(18.dp)) {
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
                GlassButton("Simulate Coding Focus", onClick = { GuardService.simulateDeepWork(true, 92, 140f) }, modifier = Modifier.weight(1f))
                GlassButton("Simulate Idle Desk", onClick = { GuardService.simulateDeepWork(false, 15, 0f) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun SensorTelemetryCard(sensors: FaceDownSensors, isRunning: Boolean) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
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
        Column(modifier = Modifier.padding(18.dp)) {
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
        Column(modifier = Modifier.padding(18.dp)) {
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
        Column(modifier = Modifier.padding(18.dp)) {
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
                GlassButton("Record 10s Capsule", onClick = { GuardService.startAudioCapsule(context, 10) },
                    modifier = Modifier.weight(1f), isPrimary = true)
                GlassButton("Simulate Rahul Speech", onClick = {
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
        Column(modifier = Modifier.padding(18.dp)) {
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
                                            text = "⏰ ${capsule.aiDeadline}",
                                            fontSize = 11.sp, color = GlassColors.AccentAmber, fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { coroutineScope.launch { repository.delete(capsule.id) } },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete",
                                        tint = GlassColors.AccentRed, modifier = Modifier.size(18.dp))
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
fun VivoNotesSyncCard(context: Context) {
    val joviManager = remember { GuardService.getJoviNotesSyncManager(context) }
    val syncState by joviManager.syncState.collectAsState()

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(18.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Vivo Office Kit & Notes", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = GlassColors.TextPrimary)
                Text("Direct Markdown task handoff into Vivo Notes", fontSize = 12.sp, color = GlassColors.TextSecondary)
            }
            Switch(
                checked = syncState.isAutoSyncEnabled,
                onCheckedChange = { joviManager.setAutoSyncEnabled(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor  = Color.White,
                    checkedTrackColor  = GlassColors.TextPrimary.copy(alpha = 0.8f),
                    uncheckedThumbColor = GlassColors.TextMuted,
                    uncheckedTrackColor = Color(0x44000000)
                )
            )
        }
    }
}
