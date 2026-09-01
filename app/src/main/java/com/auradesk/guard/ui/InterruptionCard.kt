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

    LiquidGlassCard(
        shape = RoundedCornerShape(24.dp),
        enableGleam = true,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Header Row: Avatar + Visitor info + Urgency Pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0x80FFFFFF))
                        .border(1.2.dp, Color(0xF2FFFFFF), CircleShape),
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
                    LiquidGlassBadge(
                        text = capsule.targetComponent.uppercase(),
                        textColor = TextSecondary,
                        backgroundColor = Color(0x66FFFFFF),
                        borderColor = Color(0x99FFFFFF)
                    )
                }

                LiquidGlassBadge(
                    text = if (capsule.isUrgent) "Urgent" else "Interruption",
                    textColor = if (capsule.isUrgent) AccentRed else TextPrimary,
                    backgroundColor = if (capsule.isUrgent) AccentRedBg else Color(0x66FFFFFF),
                    borderColor = if (capsule.isUrgent) AccentRedBorder else Color(0x80CBD5E1)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action Item Box
        LiquidGlassTile(modifier = Modifier.fillMaxWidth()) {
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
                    LiquidGlassBadge(
                        text = capsule.aiDeadline,
                        textColor = AccentAmber,
                        backgroundColor = AccentAmberBg,
                        borderColor = AccentAmberBorder
                    )
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

        // Raw Quote Box
        if (capsule.rawTranscript.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            LiquidGlassTile(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(2.dp),
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

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .liquidPressEffect { onDelete(capsule.id) },
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

        AnimatedVisibility(visible = showContextDetails) {
            LiquidGlassTile(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(2.dp),
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
            LiquidGlassButton(
                onClick = {
                    val joviSync = com.auradesk.guard.notes.JoviNotesSyncManager.getInstance(context)
                    joviSync.syncInterruptionToNotes(capsule, launchChooser = true)
                    onSaveToNotes(capsule.id)
                },
                isPrimary = !(capsule.status == "SAVED_TO_NOTES" || capsule.joviSynced),
                modifier = Modifier.weight(1.4f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (capsule.status == "SAVED_TO_NOTES" || capsule.joviSynced) "Saved to Notes" else "Save to Vivo Notes",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (capsule.status == "SAVED_TO_NOTES" || capsule.joviSynced) AccentGreen else PureWhite
                )
            }

            LiquidGlassButton(
                onClick = { onDismiss(capsule.id) },
                isPrimary = false,
                modifier = Modifier.weight(0.8f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Dismiss", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
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
