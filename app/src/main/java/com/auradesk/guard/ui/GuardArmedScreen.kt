package com.auradesk.guard.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import com.auradesk.guard.service.GuardService
import com.auradesk.guard.ui.glass.*
import com.auradesk.guard.vision.RadarZone
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun GuardArmedScreen(onDisarm: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val deepWorkState by GuardService.liveDeepWork.collectAsState()
    val radar by GuardService.liveRadar.collectAsState()
    val latestHapticAlert by GuardService.latestHapticAlert.collectAsState()
    val audioCapsule by GuardService.liveAudioCapsule.collectAsState()
    val savedNotes by GuardService.liveInterruptions.collectAsState()
    val digitalNotif by GuardService.liveDigitalNotification.collectAsState()
    val llamaRunner = remember { com.auradesk.guard.llm.LlamaModelRunner.getInstance(context) }
    val llamaState by llamaRunner.llamaState.collectAsState()

    var elapsedSeconds by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) { delay(1000); elapsedSeconds++ }
    }

    // Temporary vibration banner timer: shows for 3.5s then auto-disappears
    var showHapticBanner by remember { mutableStateOf(false) }
    LaunchedEffect(latestHapticAlert) {
        if (latestHapticAlert != null) {
            showHapticBanner = true
            delay(3500)
            showHapticBanner = false
        }
    }

    val returnTime = remember {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MINUTE, 45)
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(cal.time)
    }

    val formattedTime = remember(elapsedSeconds) {
        val m = elapsedSeconds / 60; val s = elapsedSeconds % 60
        String.format("%02d:%02d", m, s)
    }

    // Ambient breathing glow
    val infiniteTransition = rememberInfiniteTransition(label = "aodBreath")
    val breathAlpha by infiniteTransition.animateFloat(
        initialValue = 0.04f, targetValue = 0.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .padding(24.dp)
    ) {
        // Ambient glow canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1E3A5F).copy(alpha = breathAlpha),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.width * 0.65f
                )
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Top
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Text(
                    text = "AURADESK",
                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = Color(0xFF6B7280), letterSpacing = 3.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (deepWorkState.isDeepWork) "Deep Work Protected" else "Focus Sanctuary Active",
                    fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    color = Color(0xFFE5E7EB)
                )

                when (llamaState) {
                    is com.auradesk.guard.llm.LlamaState.Loading -> {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Mounting On-Device LLM into RAM...",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFFBBF24)
                        )
                    }
                    is com.auradesk.guard.llm.LlamaState.Ready -> {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Qwen2-0.5B INT4 Resident in RAM",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF10B981)
                        )
                    }
                    else -> {}
                }

                // Phase 7: Subtle OLED Digital Notification Ticker
                digitalNotif?.let { notif ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF111827))
                            .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${notif.appName} • ${notif.senderName}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF60A5FA)
                                )
                                Text(
                                    text = "Auto-Replied",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF34D399)
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "\"${notif.autoReplyText}\"",
                                fontSize = 11.sp,
                                color = Color(0xFFD1D5DB),
                                maxLines = 2
                            )
                        }
                    }
                }
            }

            // Center: Big Timer + Focus Status + Live Radar Status + Temporary Vibration Banner
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = formattedTime,
                    fontSize = 60.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFFFFFFF), letterSpacing = (-1).sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Till $returnTime", fontSize = 13.sp, color = Color(0xFF9CA3AF))
                    Text("•", fontSize = 13.sp, color = Color(0xFF4B5563))
                    Text(
                        "Focus ${deepWorkState.focusScore}%",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF10B981)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Live Perimeter Vision Radar Status Pill
                val (zoneText, zoneColor, zoneBg) = when (radar.zone) {
                    RadarZone.CLOSE_05M -> Triple(
                        "At Desk • ${String.format("%.1f", radar.distanceMeters)}m",
                        Color(0xFFEF4444),
                        Color(0x33EF4444)
                    )
                    RadarZone.MID_2M -> Triple(
                        "Approaching • ${String.format("%.1f", radar.distanceMeters)}m",
                        Color(0xFFFBBF24),
                        Color(0x33FBBF24)
                    )
                    RadarZone.FAR_5M -> Triple(
                        "Far Perimeter • ${String.format("%.1f", radar.distanceMeters)}m",
                        Color(0xFF38BDF8),
                        Color(0x3338BDF8)
                    )
                    RadarZone.NONE -> Triple(
                        "Perimeter Clear • Radar Active",
                        Color(0xFF94A3B8),
                        Color(0x1A94A3B8)
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(zoneBg)
                        .border(1.dp, zoneColor.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(zoneColor)
                    )
                    Text(
                        text = zoneText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = zoneColor,
                        letterSpacing = 0.4.sp
                    )
                }

                // Live Mic Recording Pill (Active during 10s capsule capture)
                AnimatedVisibility(
                    visible = audioCapsule.isRecording,
                    enter = fadeIn(tween(200)) + expandVertically(),
                    exit = fadeOut(tween(250)) + shrinkVertically()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0x33EF4444))
                                .border(1.2.dp, Color(0xFFEF4444).copy(alpha = 0.8f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444))
                            )
                            Text(
                                text = "Recording Voice Capsule (${audioCapsule.remainingSeconds}s) • Speak Now",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFCA5A5),
                                letterSpacing = 0.3.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Temporary Vibration Alert Tag (Appears only on haptic trigger, auto-vanishes after 3.5s)
                AnimatedVisibility(
                    visible = showHapticBanner && latestHapticAlert != null,
                    enter = fadeIn(tween(250)) + slideInVertically(initialOffsetY = { 30 }),
                    exit = fadeOut(tween(300)) + slideOutVertically(targetOffsetY = { 30 })
                ) {
                    latestHapticAlert?.let { alert ->
                        val alertColor = when (alert.zone) {
                            RadarZone.CLOSE_05M -> Color(0xFFEF4444)
                            RadarZone.MID_2M -> Color(0xFFFBBF24)
                            else -> Color(0xFF38BDF8)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF141417))
                                .border(1.5.dp, alertColor.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(alertColor.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Vibration,
                                    contentDescription = null,
                                    tint = alertColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = alert.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = alertColor
                                )
                                Text(
                                    text = alert.detail,
                                    fontSize = 11.sp,
                                    color = Color(0xFFCBD5E1)
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Section: Captured Notes List + Glass Disarm Button
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // List of Saved Notes (shows one by one as they are captured)
                if (savedNotes.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CAPTURED NOTES (${savedNotes.size})",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8),
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = "OLED Silent Sanctuary",
                            fontSize = 10.sp,
                            color = Color(0xFF475569)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 150.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        savedNotes.take(4).forEach { note ->
                            val noteColor = if (note.isUrgent) Color(0xFFEF4444) else Color(0xFF38BDF8)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF0D0D11))
                                    .border(1.dp, noteColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(noteColor)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        val displayAction = if (note.aiActionItem.isNotBlank()) {
                                            note.aiActionItem
                                        } else {
                                            note.taskSummary
                                        }
                                        Text(
                                            text = displayAction,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFFF1F5F9),
                                            maxLines = 2
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${note.personName} • ${note.distanceZone}",
                                                fontSize = 10.sp,
                                                color = Color(0xFF94A3B8)
                                            )
                                            if (note.aiDeadline.isNotBlank()) {
                                                Text(
                                                    text = "Due: ${note.aiDeadline}",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFFBBF24)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Disarm Button (Clean solid OLED dark finish)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF18181B))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                        .clickable(onClick = onDisarm),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = Color(0xFFE5E7EB),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "Disarm Focus Guard",
                            color = Color(0xFFE5E7EB),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
