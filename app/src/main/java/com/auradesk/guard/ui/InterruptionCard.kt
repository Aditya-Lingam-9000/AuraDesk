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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = BorderStroke(
            1.dp,
            if (capsule.isUrgent) StatusRed else Slate200
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
                            .background(Slate100)
                            .border(1.dp, Slate300, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = capsule.personName.firstOrNull()?.toString()?.uppercase() ?: "V",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Slate800
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = capsule.personName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Slate900
                        )
                        Text(
                            text = "$formattedTime • ${capsule.durationSec}s visit (${capsule.distanceZone})",
                            fontSize = 12.sp,
                            color = Slate500
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (capsule.targetComponent.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Slate100,
                            border = BorderStroke(1.dp, Slate200)
                        ) {
                            Text(
                                text = capsule.targetComponent.uppercase(),
                                color = Slate700,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (capsule.isUrgent) StatusRedBg else Slate100,
                        border = BorderStroke(
                            1.dp,
                            if (capsule.isUrgent) StatusRedBorder else Slate200
                        )
                    ) {
                        Text(
                            text = if (capsule.isUrgent) "Urgent" else "Interruption",
                            color = if (capsule.isUrgent) StatusRed else Slate700,
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
                shape = RoundedCornerShape(8.dp),
                color = Slate50,
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = Slate700,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "SYNTHESIZED ACTION ITEM",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate700,
                                letterSpacing = 0.8.sp
                            )
                        }

                        if (capsule.aiDeadline.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = StatusAmberBg,
                                border = BorderStroke(1.dp, StatusAmberBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = StatusAmber,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = capsule.aiDeadline,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = StatusAmber
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val displayAction = if (capsule.aiActionItem.isNotBlank()) {
                        capsule.aiActionItem
                    } else {
                        capsule.taskSummary
                    }

                    Text(
                        text = displayAction,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900,
                        lineHeight = 22.sp
                    )

                    if (capsule.aiUrgencyReason.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Context: ${capsule.aiUrgencyReason}",
                            fontSize = 12.sp,
                            color = Slate500
                        )
                    }
                }
            }

            // Raw Visitor Quote Box
            if (capsule.rawTranscript.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Slate50,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatQuote,
                            contentDescription = null,
                            tint = Slate500,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "\"${capsule.rawTranscript}\"",
                            fontSize = 12.sp,
                            color = Slate700,
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
                    Icon(
                        imageVector = if (showContextDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = Slate700,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (showContextDetails) "Hide Work Context" else "View Pre-Interruption Context",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate700
                    )
                }

                IconButton(
                    onClick = { onDelete(capsule.id) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = StatusRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(visible = showContextDetails) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Slate100,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = Slate700,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Location: ${capsule.contextSnippet}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Slate800
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
                        containerColor = if (capsule.status == "SAVED_TO_NOTES" || capsule.joviSynced) StatusGreenBg else Slate900
                    ),
                    shape = RoundedCornerShape(6.dp),
                    border = if (capsule.status == "SAVED_TO_NOTES" || capsule.joviSynced) BorderStroke(1.dp, StatusGreenBorder) else null
                ) {
                    Icon(
                        imageVector = if (capsule.status == "SAVED_TO_NOTES" || capsule.joviSynced) Icons.Default.Check else Icons.Default.NoteAdd,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (capsule.status == "SAVED_TO_NOTES" || capsule.joviSynced) StatusGreen else PureWhite
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (capsule.status == "SAVED_TO_NOTES" || capsule.joviSynced) "Saved to Notes" else "Save to Vivo Notes",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (capsule.status == "SAVED_TO_NOTES" || capsule.joviSynced) StatusGreen else PureWhite
                    )
                }

                OutlinedButton(
                    onClick = { onDismiss(capsule.id) },
                    modifier = Modifier.weight(0.8f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Slate300),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate700)
                ) {
                    Text("Dismiss", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Shake-to-delete indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Vibration,
                    contentDescription = null,
                    tint = Slate400,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Shake device 3 times to incinerate",
                    fontSize = 11.sp,
                    color = Slate400
                )
            }
        }
    }
}
