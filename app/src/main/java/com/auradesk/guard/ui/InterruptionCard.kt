package com.auradesk.guard.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auradesk.guard.data.InterruptionEntity
import com.auradesk.guard.notes.JoviNotesSyncManager
import com.auradesk.guard.ui.glass.*
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
    val context = LocalContext.current
    var showContextDetails by remember { mutableStateOf(false) }

    val formattedTime = remember(capsule.timestamp) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(capsule.timestamp))
    }

    val isSaved = capsule.status == "SAVED_TO_NOTES" || capsule.joviSynced

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        tintColor = if (capsule.isUrgent) GlassColors.GlassRed.copy(alpha = 0.3f) else Color.Transparent
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: avatar + name + badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Initial avatar
                    GlassCard(
                        modifier = Modifier.size(40.dp),
                        cornerRadius = 20.dp
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = capsule.personName.firstOrNull()?.toString()?.uppercase() ?: "V",
                                fontWeight = FontWeight.Bold, fontSize = 16.sp,
                                color = GlassColors.TextPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(capsule.personName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = GlassColors.TextPrimary)
                        Text(
                            "$formattedTime • ${capsule.durationSec}s visit (${capsule.distanceZone})",
                            fontSize = 12.sp, color = GlassColors.TextSecondary
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (capsule.targetComponent.isNotBlank()) {
                        GlassBadge(text = capsule.targetComponent.uppercase())
                    }
                    GlassBadge(
                        text = if (capsule.isUrgent) "Urgent" else "Interruption",
                        tintColor = if (capsule.isUrgent) GlassColors.GlassRed else Color.Transparent,
                        textColor = if (capsule.isUrgent) GlassColors.AccentRed else GlassColors.TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Item Box
            GlassSection(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ACTION ITEM", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = GlassColors.TextMuted, letterSpacing = 0.8.sp)
                    if (capsule.aiDeadline.isNotBlank()) {
                        GlassBadge(
                            text = capsule.aiDeadline,
                            tintColor = GlassColors.GlassAmber,
                            textColor = GlassColors.AccentAmber
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                val displayAction = if (capsule.aiActionItem.isNotBlank()) capsule.aiActionItem else capsule.taskSummary
                Text(displayAction, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    color = GlassColors.TextPrimary, lineHeight = 22.sp)
                if (capsule.aiUrgencyReason.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Context: ${capsule.aiUrgencyReason}", fontSize = 12.sp, color = GlassColors.TextSecondary)
                }
            }

            // Raw Quote
            if (capsule.rawTranscript.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                GlassSection(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "\"${capsule.rawTranscript}\"",
                        fontSize = 12.sp, color = GlassColors.TextSecondary,
                        fontStyle = FontStyle.Italic
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Work context toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { showContextDetails = !showContextDetails },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Text(
                        text = if (showContextDetails) "Hide Pre-Interruption Context" else "View Pre-Interruption Context",
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = GlassColors.TextPrimary
                    )
                }
                IconButton(onClick = { onDelete(capsule.id) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete",
                        tint = GlassColors.IconColor, modifier = Modifier.size(18.dp))
                }
            }

            AnimatedVisibility(visible = showContextDetails) {
                GlassSection(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) {
                    Text(
                        text = "Location: ${capsule.contextSnippet}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp, color = GlassColors.TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton(
                    text = if (isSaved) "Saved to Notes" else "Save to Vivo Notes",
                    onClick = {
                        val joviSync = JoviNotesSyncManager.getInstance(context)
                        joviSync.syncInterruptionToNotes(capsule, launchChooser = true)
                        onSaveToNotes(capsule.id)
                    },
                    modifier = Modifier.weight(1.4f),
                    tintColor = if (isSaved) GlassColors.GlassGreen else Color.Transparent,
                    textColor = if (isSaved) GlassColors.AccentGreen else GlassColors.TextPrimary,
                    isPrimary = !isSaved
                )
                GlassButton(
                    text = "Dismiss",
                    onClick = { onDismiss(capsule.id) },
                    modifier = Modifier.weight(0.8f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text("Shake device 3× to incinerate all records", fontSize = 11.sp, color = GlassColors.TextMuted)
            }
        }
    }
}
