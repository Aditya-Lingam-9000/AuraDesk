package com.auradesk.guard.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
    LOGS("Capsules", Icons.Default.History)
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

    LiquidGlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                LiquidGlassTopAppBar(
                    isArmed = isArmed,
                    isRunning = isRunning,
                    onReplayTour = onReplayTour
                )
            },
            bottomBar = {
                LiquidFloatingNavBar(
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Permission Notice
                if (!hasPermissions) {
                    LiquidGlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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

                            LiquidGlassButton(
                                onClick = { permissionLauncher.launch(permissionsToRequest.toTypedArray()) },
                                isPrimary = true,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text("Grant", fontSize = 12.sp, color = PureWhite, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                when (selectedTab) {
                    DashboardTab.FOCUS -> {
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

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

// -------------------------------------------------------------
// LIQUID GLASS TOP & BOTTOM NAVIGATION
// -------------------------------------------------------------

@Composable
fun LiquidGlassTopAppBar(
    isArmed: Boolean,
    isRunning: Boolean,
    onReplayTour: () -> Unit
) {
    val glassGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xE6FFFFFF),
            Color(0xB3F8FAFC)
        )
    )
    val specularBorder = Brush.verticalGradient(
        colors = listOf(
            Color(0xF2FFFFFF),
            Color(0x66CBD5E1)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(glassGradient)
            .border(BorderStroke(1.2.dp, specularBorder), RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "AuraDesk",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = if (isArmed) "Focus Shield Active" else if (isRunning) "Service Ready" else "Standby",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isArmed) AccentGreen else TextMuted
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                LiquidGlassBadge(
                    text = if (isArmed) "ARMED" else if (isRunning) "STANDBY" else "IDLE",
                    textColor = if (isArmed) AccentGreen else TextPrimary,
                    backgroundColor = if (isArmed) AccentGreenBg else Color(0x66FFFFFF),
                    borderColor = if (isArmed) AccentGreenBorder else Color(0x80CBD5E1)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x80FFFFFF))
                        .border(1.dp, Color(0x99FFFFFF), CircleShape)
                        .liquidPressEffect { onReplayTour() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.HelpOutline,
                        contentDescription = "Help",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun LiquidFloatingNavBar(
    selectedTab: DashboardTab,
    onSelectTab: (DashboardTab) -> Unit
) {
    val glassGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xF0FFFFFF),
            Color(0xCCF1F5F9)
        )
    )
    val specularBorder = Brush.linearGradient(
        colors = listOf(
            Color(0xFFFFFFFF),
            Color(0x80CBD5E1),
            Color(0xCCFFFFFF)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(glassGradient)
                .border(BorderStroke(1.2.dp, specularBorder), RoundedCornerShape(26.dp))
                .padding(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DashboardTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab

                    val tabScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.05f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "TabScale"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .scale(tabScale)
                            .clip(RoundedCornerShape(20.dp))
                            .then(
                                if (isSelected) {
                                    Modifier.background(
                                        Brush.verticalGradient(
                                            listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                                        )
                                    )
                                } else Modifier
                            )
                            .border(
                                BorderStroke(
                                    1.dp,
                                    if (isSelected) Color(0x6694A3B8) else Color.Transparent
                                ),
                                RoundedCornerShape(20.dp)
                            )
                            .liquidPressEffect { onSelectTab(tab) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                tint = if (isSelected) PureWhite else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tab.title,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) PureWhite else TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// LIQUID GLASS CARDS & MICRO-INTERACTIONS
// -------------------------------------------------------------

@Composable
fun FocusServiceCard(
    isRunning: Boolean,
    isArmed: Boolean,
    onToggleService: () -> Unit,
    onLaunchAod: () -> Unit
) {
    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        enableGleam = isArmed
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = if (isArmed) "Focus Shield Armed" else if (isRunning) "Focus Guard Ready" else "Focus Guard Standby",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isArmed) "Face-down on desk • Real-time perimeter active" else "Flip device face-down on your desk to arm",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            LiquidGlassBadge(
                text = if (isArmed) "ARMED" else if (isRunning) "STANDBY" else "OFFLINE",
                textColor = if (isArmed) AccentGreen else TextPrimary,
                backgroundColor = if (isArmed) AccentGreenBg else Color(0x66FFFFFF),
                borderColor = if (isArmed) AccentGreenBorder else Color(0x80CBD5E1)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LiquidGlassButton(
                onClick = onToggleService,
                isPrimary = true,
                modifier = Modifier.weight(1.4f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (isRunning) "Stop Guard" else "Start Guard",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PureWhite
                )
            }

            LiquidGlassButton(
                onClick = onLaunchAod,
                isPrimary = false,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
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

@Composable
fun DeepWorkCadenceCard() {
    val deepWorkState by GuardService.liveDeepWork.collectAsState()

    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp)
    ) {
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
                    text = "Acoustic keyboard cadence & quiet study detector",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            LiquidGlassBadge(
                text = "${deepWorkState.focusScore}% FOCUS",
                textColor = if (deepWorkState.isDeepWork) AccentGreen else TextPrimary,
                backgroundColor = if (deepWorkState.isDeepWork) AccentGreenBg else Color(0x66FFFFFF),
                borderColor = if (deepWorkState.isDeepWork) AccentGreenBorder else Color(0x80CBD5E1)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LiquidGlassTile(modifier = Modifier.weight(1f)) {
                Text("Typing Cadence", fontSize = 11.sp, color = TextMuted)
                Spacer(modifier = Modifier.height(2.dp))
                Text("${String.format("%.0f", deepWorkState.typingCadenceBpm)} BPM", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            LiquidGlassTile(modifier = Modifier.weight(1f)) {
                Text("Environment Mode", fontSize = 11.sp, color = TextMuted)
                Spacer(modifier = Modifier.height(2.dp))
                Text(deepWorkState.environmentProfile.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LiquidGlassButton(
                onClick = { GuardService.simulateDeepWork(true, 92, 140f) },
                isPrimary = false,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Text("Simulate Coding", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            }

            LiquidGlassButton(
                onClick = { GuardService.simulateDeepWork(false, 15, 0f) },
                isPrimary = false,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Text("Simulate Idle", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun SensorTelemetryCard(sensors: FaceDownSensors, isRunning: Boolean) {
    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp)
    ) {
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
            LiquidSensorTile("Proximity", "${String.format("%.1f", sensors.proximityCm)} cm", sensors.isProximityNear, Modifier.weight(1f))
            LiquidSensorTile("Ambient Light", "${String.format("%.0f", sensors.lightLux)} lux", sensors.isLightDark, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LiquidSensorTile("Gravity Z", "${String.format("%.2f", sensors.accelZ)} m/s²", sensors.isZDownward, Modifier.weight(1f))
            LiquidSensorTile("Gyro Drift", "${String.format("%.3f", sensors.gyroMagnitude)} rad/s", sensors.isGyroStable, Modifier.weight(1f))
        }
    }
}

@Composable
fun LiquidSensorTile(label: String, value: String, isPassing: Boolean, modifier: Modifier = Modifier) {
    LiquidGlassTile(modifier = modifier) {
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

@Composable
fun SystemStatusBadgeCard(context: Context) {
    val powerManager = remember { GuardService.getPowerManagerGuard(context) }
    val telemetry by powerManager.telemetry.collectAsState()

    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Power Consumption", fontSize = 11.sp, color = TextMuted)
                Text("${telemetry.batteryPercent}% • ${telemetry.estimatedDrainPerHour}%/hr", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text("Security Protocol", fontSize = 11.sp, color = TextMuted)
                Text("100% Air-Gapped (0 Bytes)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
            }
        }
    }
}

@Composable
fun PerimeterRadarCard() {
    val radar by GuardService.liveRadar.collectAsState()
    var showViewfinder by remember { mutableStateOf(false) }

    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp)
    ) {
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

            LiquidGlassBadge(
                text = if (radar.isPersonDetected) "SUBJECT DETECTED" else "CLEAR",
                textColor = if (radar.isPersonDetected) AccentGreen else TextPrimary,
                backgroundColor = if (radar.isPersonDetected) AccentGreenBg else Color(0x66FFFFFF),
                borderColor = if (radar.isPersonDetected) AccentGreenBorder else Color(0x80CBD5E1)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        LiquidGlassTile(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Current Zone", fontSize = 11.sp, color = TextMuted)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(radar.zone.label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("Distance", fontSize = 11.sp, color = TextMuted)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("${String.format("%.1f", radar.distanceMeters)} meters", fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TextPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LiquidGlassButton(
            onClick = { showViewfinder = !showViewfinder },
            isPrimary = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (showViewfinder) "Close Camera Viewfinder" else "Open Camera Viewfinder", fontSize = 13.sp, color = PureWhite, fontWeight = FontWeight.SemiBold)
        }

        if (showViewfinder) {
            Spacer(modifier = Modifier.height(12.dp))
            CameraRadarViewfinder(onClose = { showViewfinder = false })
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LiquidGlassButton(
                onClick = { GuardService.simulateRadar(2.0f, true, 30.0f) },
                isPrimary = false,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Text("2.0m Approach", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            }

            LiquidGlassButton(
                onClick = { GuardService.simulateRadar(0.5f, false, 0.0f) },
                isPrimary = false,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Text("0.5m At Desk", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            }

            LiquidGlassButton(
                onClick = { GuardService.clearRadar() },
                isPrimary = false,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Text("Reset", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun HapticFeedbackCard(context: Context) {
    val feedbackManager = remember { com.auradesk.guard.utils.FeedbackManager(context) }

    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp)
    ) {
        Text("Subconscious Haptic Patterns", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
        Text("Silent vibration cues triggered through desk surfaces based on distance", fontSize = 12.sp, color = TextSecondary)

        Spacer(modifier = Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LiquidGlassButton(
                onClick = { feedbackManager.playHapticWhisperLow() },
                isPrimary = false,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Text("Low (80ms)", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            }

            LiquidGlassButton(
                onClick = { feedbackManager.playHapticWhisperMedium() },
                isPrimary = false,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Text("Mid (2m Approach)", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            }

            LiquidGlassButton(
                onClick = { feedbackManager.playHapticWhisperUrgent() },
                isPrimary = false,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Text("Urgent (0.5m)", fontSize = 11.sp, color = AccentRed, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun VoiceCaptureSynthesizerCard(context: Context) {
    val audioState by GuardService.liveAudioCapsule.collectAsState()
    val synthesizedTask by GuardService.liveSynthesizedTask.collectAsState()

    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Voice VAD & Action Synthesizer", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                Text("10s offline audio capsule with on-device action extraction", fontSize = 12.sp, color = TextSecondary)
            }

            LiquidGlassBadge(
                text = if (audioState.isRecording) "RECORDING (${audioState.remainingSeconds}s)" else audioState.capsuleStatus,
                textColor = if (audioState.isRecording) AccentRed else TextPrimary,
                backgroundColor = if (audioState.isRecording) AccentRedBg else Color(0x66FFFFFF),
                borderColor = if (audioState.isRecording) AccentRedBorder else Color(0x80CBD5E1)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        LiquidGlassTile(modifier = Modifier.fillMaxWidth()) {
            Text("Live Audio Transcript", fontSize = 11.sp, color = TextMuted)
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = audioState.livePartialTranscript.ifBlank { "No audio recorded yet" },
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }

        if (synthesizedTask != null) {
            Spacer(modifier = Modifier.height(10.dp))
            LiquidGlassTile(modifier = Modifier.fillMaxWidth()) {
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

        Spacer(modifier = Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LiquidGlassButton(
                onClick = { GuardService.startAudioCapsule(context, 10) },
                isPrimary = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(vertical = 11.dp)
            ) {
                Text("Record 10s Capsule", fontSize = 12.sp, color = PureWhite, fontWeight = FontWeight.SemiBold)
            }

            LiquidGlassButton(
                onClick = {
                    GuardService.simulateSpeechCapsule(
                        context = context,
                        speakerName = "Rahul from Backend",
                        speechText = "Hey Arjun, can you review PR 142 API schema changes before the 4 PM deployment?",
                        durationSec = 6L,
                        isUrgent = true
                    )
                },
                isPrimary = false,
                modifier = Modifier.weight(1.2f),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(vertical = 11.dp)
            ) {
                Text("Simulate Rahul Speech", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun InterruptionHistoryCard(context: Context) {
    val repository = remember { InterruptionRepository.getInstance(context) }
    val capsules by repository.allInterruptions.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp)
    ) {
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

            LiquidGlassBadge(
                text = "${capsules.size} STORED",
                textColor = TextPrimary,
                backgroundColor = Color(0x66FFFFFF),
                borderColor = Color(0x80CBD5E1)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (capsules.isEmpty()) {
            LiquidGlassTile(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
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
                    LiquidGlassTile(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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

                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .liquidPressEffect {
                                        coroutineScope.launch { repository.delete(capsule.id) }
                                    },
                                contentAlignment = Alignment.Center
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
            LiquidGlassButton(
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
                isPrimary = false,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Text("Add Sample Capsule", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            }

            LiquidGlassButton(
                onClick = { coroutineScope.launch { repository.deleteAll() } },
                isPrimary = false,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Text("Clear All", fontSize = 11.sp, color = AccentRed, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun VivoNotesSyncCard(context: Context) {
    val joviManager = remember { GuardService.getJoviNotesSyncManager(context) }
    val syncState by joviManager.syncState.collectAsState()

    LiquidGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp)
    ) {
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
                    uncheckedTrackColor = Color(0x66CBD5E1)
                )
            )
        }
    }
}
