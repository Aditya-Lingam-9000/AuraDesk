package com.auradesk.guard.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import com.auradesk.guard.service.GuardService
import com.auradesk.guard.ui.glass.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun GuardArmedScreen(onDisarm: () -> Unit) {
    val deepWorkState by GuardService.liveDeepWork.collectAsState()

    var elapsedSeconds by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) { delay(1000); elapsedSeconds++ }
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

    val shimmerOffset = rememberShimmerOffset()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .padding(32.dp)
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 24.dp)
            ) {
                androidx.compose.material3.Text(
                    text = "AURADESK",
                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = Color(0xFF6B7280), letterSpacing = 3.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                androidx.compose.material3.Text(
                    text = if (deepWorkState.isDeepWork) "Deep Work Protected" else "Focus Sanctuary Active",
                    fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    color = Color(0xFFE5E7EB)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.Text(
                    text = formattedTime,
                    fontSize = 64.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFFFFFFF), letterSpacing = (-1).sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Text("Till $returnTime", fontSize = 13.sp, color = Color(0xFF9CA3AF))
                    androidx.compose.material3.Text("•", fontSize = 13.sp, color = Color(0xFF4B5563))
                    androidx.compose.material3.Text(
                        "Focus ${deepWorkState.focusScore}%",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF10B981)
                    )
                }
            }

            // Glass disarm button (on pure black background)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .height(48.dp)
                    .drawBehind {
                        val cornerPx = 10.dp.toPx()
                        val rrect = RoundRect(Rect(Offset.Zero, size), CornerRadius(cornerPx))
                        val path  = Path().apply { addRoundRect(rrect) }
                        drawPath(path, color = Color(0xFF18181B))
                        drawLine(
                            color = Color(0x55FFFFFF),
                            start = Offset(cornerPx, 1.5f),
                            end   = Offset(size.width - cornerPx, 1.5f),
                            strokeWidth = 1f, cap = StrokeCap.Round
                        )
                        clipPath(path) { drawGlassShimmer(shimmerOffset, alpha = 0.15f) }
                        drawPath(path, color = Color(0x33FFFFFF), style = Stroke(width = 1.dp.toPx()))
                    }
                    .clickable(onClick = onDisarm),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = Color(0xFFE5E7EB),
                        modifier = Modifier.size(16.dp)
                    )
                    androidx.compose.material3.Text(
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
