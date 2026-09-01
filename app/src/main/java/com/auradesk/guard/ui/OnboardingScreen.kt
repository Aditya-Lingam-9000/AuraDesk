package com.auradesk.guard.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.auradesk.guard.ui.theme.*

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
                description = "Captures brief offline audio capsules, synthesizes actionable tasks using local intelligence, and synchronizes with Vivo Notes with zero network exposure.",
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
    ) { result ->
        permissionsGranted = result.values.all { it }
    }

    LiquidGlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AURADESK",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = 1.5.sp
                )

                LiquidGlassBadge(
                    text = currentStep.badgeLabel,
                    textColor = TextPrimary,
                    backgroundColor = Color(0x66FFFFFF),
                    borderColor = Color(0x80CBD5E1)
                )
            }

            // Central Liquid Glass Showcase
            LiquidGlassCard(
                shape = RoundedCornerShape(28.dp),
                enableGleam = true,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                                )
                            )
                            .border(1.5.dp, Color(0x6694A3B8), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = currentStep.icon,
                            contentDescription = null,
                            tint = PureWhite,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = currentStep.subtitle,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.2.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = currentStep.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = currentStep.description,
                        fontSize = 13.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    // Permissions Card on Step 3
                    if (currentStepIndex == 2) {
                        Spacer(modifier = Modifier.height(16.dp))
                        LiquidGlassTile(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Hardware Permissions",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                LiquidGlassBadge(
                                    text = if (permissionsGranted) "Granted" else "Required",
                                    textColor = if (permissionsGranted) AccentGreen else AccentAmber,
                                    backgroundColor = if (permissionsGranted) AccentGreenBg else AccentAmberBg,
                                    borderColor = if (permissionsGranted) AccentGreenBorder else AccentAmberBorder
                                )
                            }

                            if (!permissionsGranted) {
                                Spacer(modifier = Modifier.height(10.dp))
                                LiquidGlassButton(
                                    onClick = { permissionLauncher.launch(permissionsToRequest.toTypedArray()) },
                                    isPrimary = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(vertical = 10.dp)
                                ) {
                                    Text("Grant Camera & Mic Access", fontSize = 12.sp, color = PureWhite, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Navigation Row
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                // Step Indicator Dots
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    steps.indices.forEach { index ->
                        val isCurrent = index == currentStepIndex
                        val dotWidth by animateDpAsState(
                            targetValue = if (isCurrent) 24.dp else 6.dp,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "DotWidth"
                        )

                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(dotWidth, 6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (isCurrent) BrandPrimary else Color(0x6694A3B8)
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (currentStepIndex > 0) {
                        LiquidGlassButton(
                            onClick = { currentStepIndex-- },
                            isPrimary = false,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Back", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        }
                    }

                    LiquidGlassButton(
                        onClick = {
                            if (currentStepIndex < steps.size - 1) {
                                currentStepIndex++
                            } else {
                                val prefs = context.getSharedPreferences("auradesk_prefs", Context.MODE_PRIVATE)
                                prefs.edit().putBoolean("has_completed_onboarding", true).apply()
                                onFinish()
                            }
                        },
                        isPrimary = true,
                        modifier = Modifier.weight(if (currentStepIndex > 0) 1.5f else 1f),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text(
                            text = if (currentStepIndex < steps.size - 1) "Continue" else "Enter Sanctuary",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PureWhite
                        )
                    }
                }
            }
        }
    }
}
