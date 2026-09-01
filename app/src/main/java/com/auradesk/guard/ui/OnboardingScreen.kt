package com.auradesk.guard.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.auradesk.guard.ui.glass.*

data class OnboardingStep(
    val title: String,
    val subtitle: String,
    val description: String,
    val icon: ImageVector,
    val badgeLabel: String
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    var currentStepIndex by remember { mutableStateOf(0) }

    val steps = remember {
        listOf(
            OnboardingStep(
                title = "Autonomous Focus Protection",
                subtitle = "OPTICAL & INERTIAL SENSOR FUSION",
                description = "Place your device face-down at the edge of your desk. AuraDesk activates low-power optical sensors to automatically establish and guard your deep work focus zone.",
                icon = Icons.Default.Shield,
                badgeLabel = "STEP 1 OF 3"
            ),
            OnboardingStep(
                title = "Proximity Radar & Haptics",
                subtitle = "SILENT PERIMETER DETECTION",
                description = "Monitors approaching visitors within a 2-meter radius using on-device vision, delivering silent subconscious haptic cues before your concentration is interrupted.",
                icon = Icons.Default.Radar,
                badgeLabel = "STEP 2 OF 3"
            ),
            OnboardingStep(
                title = "Offline Action Synthesis",
                subtitle = "AIR-GAPPED ON-DEVICE INTELLIGENCE",
                description = "Captures brief offline audio capsules, synthesizes actionable tasks using local intelligence, and synchronizes with Vivo Notes — zero network exposure.",
                icon = Icons.Default.Psychology,
                badgeLabel = "STEP 3 OF 3"
            )
        )
    }

    val currentStep = steps[currentStepIndex]

    val permissionsToRequest = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
        }
    }

    var permissionsGranted by remember {
        mutableStateOf(permissionsToRequest.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> permissionsGranted = result.values.all { it } }

    // Scene background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFCFDBF2), Color(0xFFE8ECF8))
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AURADESK",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = GlassColors.TextPrimary, letterSpacing = 1.5.sp
                )
                GlassBadge(text = currentStep.badgeLabel)
            }

            // Central Showcase
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                // Animated icon in glass circle
                GlassCard(
                    modifier = Modifier.size(100.dp),
                    cornerRadius = 50.dp
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = currentStep.icon,
                            contentDescription = null,
                            tint = GlassColors.TextPrimary,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = currentStep.subtitle,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = GlassColors.TextMuted, letterSpacing = 1.2.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = currentStep.title,
                    fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    color = GlassColors.TextPrimary, textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = currentStep.description,
                    fontSize = 14.sp, color = GlassColors.TextSecondary,
                    textAlign = TextAlign.Center, lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                // Permissions Card on Final Step
                if (currentStepIndex == 2) {
                    Spacer(modifier = Modifier.height(24.dp))
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        tintColor = if (permissionsGranted) GlassColors.GlassGreen.copy(alpha = 0.4f)
                                    else GlassColors.GlassAmber.copy(alpha = 0.4f)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = GlassColors.TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Hardware Permissions", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = GlassColors.TextPrimary)
                                }
                                GlassBadge(
                                    text = if (permissionsGranted) "Granted" else "Required",
                                    tintColor = if (permissionsGranted) GlassColors.GlassGreen else GlassColors.GlassAmber,
                                    textColor = if (permissionsGranted) GlassColors.AccentGreen else GlassColors.AccentAmber
                                )
                            }

                            if (!permissionsGranted) {
                                Spacer(modifier = Modifier.height(12.dp))
                                GlassButton(
                                    text = "Grant Camera & Mic Access",
                                    onClick = { permissionLauncher.launch(permissionsToRequest.toTypedArray()) },
                                    modifier = Modifier.fillMaxWidth(),
                                    isPrimary = true
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Navigation Row
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                // Step indicator dots
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    steps.indices.forEach { index ->
                        val isSelected = index == currentStepIndex
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (isSelected) 20.dp else 6.dp, 6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (isSelected) GlassColors.TextPrimary
                                    else Color(0x88000000)
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (currentStepIndex > 0) {
                        GlassButton(
                            text = "Back",
                            onClick = { currentStepIndex-- },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    GlassButton(
                        text = if (currentStepIndex < steps.size - 1) "Continue" else "Enter Dashboard",
                        onClick = {
                            if (currentStepIndex < steps.size - 1) {
                                currentStepIndex++
                            } else {
                                val prefs = context.getSharedPreferences("auradesk_prefs", Context.MODE_PRIVATE)
                                prefs.edit().putBoolean("has_completed_onboarding", true).apply()
                                onFinish()
                            }
                        },
                        modifier = Modifier.weight(if (currentStepIndex > 0) 1.5f else 1f),
                        isPrimary = true
                    )
                }
            }
        }
    }
}
