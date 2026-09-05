package com.auradesk.guard.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auradesk.guard.data.InterruptionEntity
import com.auradesk.guard.llm.LlamaModelRunner
import com.auradesk.guard.ui.glass.*
import com.auradesk.guard.vivo.VivoOfficeKitManager
import kotlinx.coroutines.launch

@Composable
fun VivoOfficeKitCard(
    modifier: Modifier = Modifier,
    isArmed: Boolean
) {
    val context = LocalContext.current
    val officeKit = remember { VivoOfficeKitManager.getInstance(context) }
    val state by officeKit.state.collectAsState()
    val eventLogs by officeKit.eventLogs.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    val prefs = remember { context.getSharedPreferences("auradesk_prefs", Context.MODE_PRIVATE) }
    val userName = remember(isArmed) { prefs.getString("user_name", "")?.trim() ?: "" }
    val displayName = if (userName.isNotBlank()) userName else "Arjun"

    var testFeedbackMsg by remember { mutableStateOf<String?>(null) }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        tintColor = if (isArmed) GlassColors.GlassBlue.copy(alpha = 0.4f) else Color.Transparent
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0x12000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LaptopMac,
                            contentDescription = "Vivo Office Kit",
                            tint = GlassColors.IconColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Vivo Office Kit & PC Suite",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = GlassColors.TextPrimary
                        )
                        Text(
                            text = if (state.isEasyShareInstalled || state.isPcSuiteInstalled) "Cross-Device Connectivity Active" else "Ready (Vivo Ecosystem)",
                            fontSize = 12.sp,
                            color = GlassColors.TextSecondary
                        )
                    }
                }

                GlassBadge(
                    text = if (isArmed) "SHIELD MIRRORED" else "READY",
                    tintColor = if (isArmed) GlassColors.GlassGreen else GlassColors.GlassBlue,
                    textColor = if (isArmed) GlassColors.AccentGreen else GlassColors.AccentBlue
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4 Pillars Status Section
            GlassSection(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Pillar 1: Screen Mirroring Banner
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = GlassColors.IconColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Screen Mirroring Banner", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = GlassColors.TextPrimary)
                                Text(
                                    if (state.isMirrorBannerActive) state.currentBannerText else "Fires intent on face-down deep work",
                                    fontSize = 10.sp, color = GlassColors.TextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                        Text(
                            if (state.isMirrorBannerActive) "ACTIVE" else "STANDBY",
                            fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            color = if (state.isMirrorBannerActive) GlassColors.AccentGreen else GlassColors.TextMuted
                        )
                    }

                    HorizontalDivider(color = Color(0x14000000), thickness = 0.5.dp)

                    // Pillar 2: Laptop Notification Mute
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Icon(
                                imageVector = if (state.isLaptopMuted) Icons.Default.NotificationsOff else Icons.Default.NotificationsNone,
                                contentDescription = null,
                                tint = GlassColors.IconColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Laptop Notification Handoff", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = GlassColors.TextPrimary)
                                Text(
                                    if (state.isLaptopMuted) "DND & Audio Silenced • Laptop Muted" else if (state.isDndPolicyGranted) "DND sync ready • Normal flow" else "Normal laptop notifications",
                                    fontSize = 10.sp, color = GlassColors.TextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                        Text(
                            if (state.isLaptopMuted) "MUTED" else "UNMUTED",
                            fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            color = if (state.isLaptopMuted) GlassColors.AccentAmber else GlassColors.TextMuted
                        )
                    }

                    HorizontalDivider(color = Color(0x14000000), thickness = 0.5.dp)

                    // Pillar 3: Jovi Notes Direct Sync
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Icon(
                                imageVector = Icons.Default.EditNote,
                                contentDescription = null,
                                tint = GlassColors.IconColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Jovi Notes SQLite Sync", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = GlassColors.TextPrimary)
                                Text(
                                    if (state.lastSyncedNoteTitle.isNotBlank()) "Last: ${state.lastSyncedNoteTitle}" else "Direct content://com.vivo.notes.provider",
                                    fontSize = 10.sp, color = GlassColors.TextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                        GlassBadge(
                            text = "${state.totalNotesSynced} SYNCED",
                            tintColor = GlassColors.GlassGreen,
                            textColor = GlassColors.AccentGreen
                        )
                    }

                    HorizontalDivider(color = Color(0x14000000), thickness = 0.5.dp)

                    // Pillar 4: EasyShare File & Clipboard Sync
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = null,
                                tint = GlassColors.IconColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("EasyShare & Clipboard Sync", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = GlassColors.TextPrimary)
                                Text(
                                    if (state.lastClipboardSyncedText.isNotBlank()) state.lastClipboardSyncedText else "Copies tasks & replies for laptop paste",
                                    fontSize = 10.sp, color = GlassColors.TextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                        Text(
                            "SYNCED",
                            fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            color = GlassColors.AccentGreen
                        )
                    }
                }
            }

            if (!state.isDndPolicyGranted) {
                Spacer(modifier = Modifier.height(10.dp))
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    tintColor = GlassColors.GlassAmber.copy(alpha = 0.4f)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("DND Policy Access Needed", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GlassColors.TextPrimary)
                            Text("Enables automatic laptop & phone notification silencing", fontSize = 10.sp, color = GlassColors.TextSecondary)
                        }
                        GlassButton(
                            text = "Grant",
                            onClick = {
                                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            },
                            modifier = Modifier.width(68.dp),
                            isPrimary = true
                        )
                    }
                }
            }

            // Test Feedback Alert
            if (testFeedbackMsg != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = testFeedbackMsg ?: "",
                    fontSize = 11.sp,
                    color = GlassColors.AccentGreen,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Test Buttons (2x2 Grid for full Vivo Office Kit testing)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassButton(
                    text = if (state.isMirrorBannerActive) "Mirror Active" else "Start Mirroring",
                    onClick = {
                        val llama = LlamaModelRunner.getInstance(context)
                        val returnTime = llama.calculateReturnTime(45)
                        if (state.isMirrorBannerActive) {
                            officeKit.dismissScreenMirrorFocusBanner()
                            testFeedbackMsg = "Screen Mirroring Banner dismissed"
                        } else {
                            officeKit.sendScreenMirrorFocusBanner(displayName, returnTime, isArmed = true, autoLaunchMirroring = true)
                            testFeedbackMsg = "Screen Mirroring session initiated for $displayName ($returnTime)"
                        }
                        Toast.makeText(context, testFeedbackMsg, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    isPrimary = true
                )

                GlassButton(
                    text = "Sync Jovi Note",
                    onClick = {
                        val testCapsule = InterruptionEntity(
                            personName = "Rahul (Backend Lead)",
                            taskSummary = "Merge payment schema fix before client demo",
                            aiActionItem = "Merge payment schema fix & update staging keys",
                            aiDeadline = "Today 5:00 PM (Client Demo)",
                            aiUrgencyReason = "High Priority Demo Dependency",
                            targetComponent = "Payment Gateway",
                            rawTranscript = "Hey make sure you merge the payment schema fix before client demo at 5",
                            hasVoiceTranscript = true,
                            contextSnippet = "Editing CheckoutActivity.kt line 88",
                            distanceZone = "0.5m (At Desk)",
                            durationSec = 8L,
                            isUrgent = true,
                            status = "SAVED_TO_NOTES"
                        )
                        coroutineScope.launch {
                            officeKit.syncInterruptionToJoviNotes(testCapsule, launchActivity = true)
                        }
                        testFeedbackMsg = "Synced to Jovi Notes, EasyShare & Clipboard!"
                        Toast.makeText(context, "Synced to Jovi Notes + Clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    isPrimary = false
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassButton(
                    text = "Send EasyShare File",
                    onClick = {
                        val testContent = "# AuraDesk Focus Debrief\n\n- **Focus Mode:** Deep Work Active\n- **User:** $displayName\n- **Tasks Synced:** ${state.totalNotesSynced}\n- **Clipboard:** Ready on Laptop"
                        officeKit.shareSummaryFileViaEasyShare("AuraDesk_Focus_Summary.md", testContent)
                        testFeedbackMsg = "EasyShare file transfer broadcasted to Mac/PC"
                        Toast.makeText(context, testFeedbackMsg, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    isPrimary = false
                )

                GlassButton(
                    text = if (state.isLaptopMuted) "Unmute Laptop" else "Mute Laptop",
                    onClick = {
                        val newMute = !state.isLaptopMuted
                        officeKit.setLaptopNotificationsMuted(newMute)
                        testFeedbackMsg = if (newMute) "Laptop notifications muted via Office Kit" else "Laptop notifications unmuted"
                        Toast.makeText(context, testFeedbackMsg, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    isPrimary = false
                )
            }
        }
    }
}
