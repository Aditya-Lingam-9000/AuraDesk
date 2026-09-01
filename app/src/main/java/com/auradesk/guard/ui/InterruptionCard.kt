package com.auradesk.guard.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auradesk.guard.data.InterruptionEntity
import com.auradesk.guard.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InterruptionCard(
    capsule: InterruptionEntity,
    onSaveToNotes: (Long) -> Unit,
    onDismiss: (Long) -> Unit,
    onDelete: (Long) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showContextDetails by remember { mutableStateOf(false) }

    val formattedTime = remember(capsule.timestamp) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(capsule.timestamp))
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = BorderStroke(
            1.dp,
            if (capsule.isUrgent) AccentRedBorder else BorderSubtle
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header Row: Avatar + Visitor info + Urgency Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(AppBg)
                            .border(1.dp, BorderStrong, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = capsule.personName.firstOrNull()?.toString()?.uppercase() ?: "V",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = capsule.personName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "$formattedTime • ${capsule.durationSec}s visit (${capsule.distanceZone})",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (capsule.targetComponent.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = AppBg,
                            border = BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Text(
                                text = capsule.targetComponent.uppercase(),
                                color = TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (capsule.isUrgent) AccentRedBg else BorderSubtle,
                        border = BorderStroke(
                            1.dp,
                            if (capsule.isUrgent) AccentRedBorder else BorderStrong
                        )
                    ) {
                        Text(
                            text = if (capsule.isUrgent) "Urgent" else "Interruption",
                            color = if (capsule.isUrgent) AccentRed else TextPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Item Box
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = AppBg,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ACTION ITEM",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 0.8.sp
                        )

                        if (capsule.aiDeadline.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = AccentAmberBg,
                                border = BorderStroke(1.dp, AccentAmberBorder)
                            ) {
                                Text(
                                    text = capsule.aiDeadline,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AccentAmber,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val displayAction = if (capsule.aiActionItem.isNotBlank()) {
                        capsule.aiActionItem
                    } else {
                        capsule.taskSummary
                    }

                    Text(
                        text = displayAction,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        lineHeight = 22.sp
                    )

                    if (capsule.aiUrgencyReason.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Context: ${capsule.aiUrgencyReason}",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Raw Quote Box
            if (capsule.rawTranscript.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AppBg,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "\"${capsule.rawTranscript}\"",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Work Context Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { showContextDetails = !showContextDetails },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = if (showContextDetails) "Hide Pre-Interruption Context" else "View Pre-Interruption Context",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }

                IconButton(
                    onClick = { onDelete(capsule.id) },
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

            AnimatedVisibility(visible = showContextDetails) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AppBg,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Location: ${capsule.contextSnippet}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val joviSync = com.auradesk.guard.notes.JoviNotesSyncManager.getInstance(context)
                        joviSync.syncInterruptionToNotes(capsule, launchChooser = true)
                        onSaveToNotes(capsule.id)
                    },
                    modifier = Modifier.weight(1.4f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (capsule.status == "SAVED_TO_NOTES" || capsule.joviSynced) AccentGreenBg else BrandPrimary
                    ),
                    shape = RoundedCornerShape(6.dp),
                    border = if (capsule.status == "SAVED_TO_NOTES" || capsule.joviSynced) BorderStroke(1.dp, AccentGreenBorder) else null
                ) {
                    Text(
                        text = if (capsule.status == "SAVED_TO_NOTES" || capsule.joviSynced) "Saved to Notes" else "Save to Vivo Notes",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (capsule.status == "SAVED_TO_NOTES" || capsule.joviSynced) AccentGreen else PureWhite
                    )
                }

                OutlinedButton(
                    onClick = { onDismiss(capsule.id) },
                    modifier = Modifier.weight(0.8f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, BorderStrong),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                ) {
                    Text("Dismiss", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Shake hint
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Shake device 3 times to incinerate",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }
    }
}
