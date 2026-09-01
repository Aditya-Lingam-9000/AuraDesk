package com.auradesk.guard.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auradesk.guard.ui.theme.liquidGleamEffect
import com.auradesk.guard.ui.theme.liquidPressEffect
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun GuardArmedScreen(
    onDisarm: () -> Unit
) {
    val deepWorkState by com.auradesk.guard.service.GuardService.liveDeepWork.collectAsState()

    var elapsedSeconds by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            elapsedSeconds++
        }
    }

    val returnTime = remember {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MINUTE, 45)
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(calendar.time)
    }

    val formattedTime = remember(elapsedSeconds) {
        val minutes = elapsedSeconds / 60
        val seconds = elapsedSeconds % 60
        String.format("%02d:%02d", minutes, seconds)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000)) // Pure Pitch Black OLED
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(32.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Status Label
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(
                    text = "AURADESK",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6B7280),
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x2610B981))
                        .border(1.dp, Color(0x4D10B981), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (deepWorkState.isDeepWork) "Deep Work Protected" else "Focus Sanctuary Active",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF34D399)
                    )
                }
            }

            // Center: Digital Monospace Timer
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formattedTime,
                    fontSize = 68.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFFFFFFF),
                    letterSpacing = (-1.5).sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Scheduled till $returnTime",
                        fontSize = 13.sp,
                        color = Color(0xFF9CA3AF)
                    )
                    Text(
                        text = "•",
                        fontSize = 13.sp,
                        color = Color(0xFF4B5563)
                    )
                    Text(
                        text = "Focus ${deepWorkState.focusScore}%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF10B981)
                    )
                }
            }

            // Bottom Liquid Glass Disarm Pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF27272A), Color(0xFF18181B))
                        )
                    )
                    .border(
                        BorderStroke(
                            1.2.dp,
                            Brush.linearGradient(
                                listOf(Color(0x6671717A), Color(0x263F3F46))
                            )
                        ),
                        RoundedCornerShape(18.dp)
                    )
                    .liquidPressEffect { onDisarm() }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = Color(0xFFE5E7EB),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Disarm Focus Guard",
                        color = Color(0xFFE5E7EB),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
