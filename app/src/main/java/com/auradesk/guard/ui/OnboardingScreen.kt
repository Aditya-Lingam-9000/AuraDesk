package com.auradesk.guard.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

    // Hardware Permission Launcher
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

    Scaffold(
        containerColor = PureWhite
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Slate900)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AURADESK",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900,
                        letterSpacing = 1.5.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Slate100,
                    border = BorderStroke(1.dp, Slate200)
                ) {
                    Text(
                        text = currentStep.badgeLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate700,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Central Showcase
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 24.dp)
            ) {
                // Flat Solid Icon Container
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(Slate100)
                        .border(1.dp, Slate300, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = currentStep.icon,
                        contentDescription = null,
                        tint = Slate900,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = currentStep.subtitle,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate500,
                    letterSpacing = 1.2.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = currentStep.title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate900,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = currentStep.description,
                    fontSize = 14.sp,
                    color = Slate600,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Permissions Card on Final Step
                if (currentStepIndex == 2) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Slate50,
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
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
                                        tint = Slate700,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Required Device Permissions",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Slate900
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (permissionsGranted) StatusGreenBg else StatusAmberBg,
                                    border = BorderStroke(1.dp, if (permissionsGranted) StatusGreenBorder else StatusAmberBorder)
                                ) {
                                    Text(
                                        text = if (permissionsGranted) "Granted" else "Required",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (permissionsGranted) StatusGreen else StatusAmber,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            if (!permissionsGranted) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { permissionLauncher.launch(permissionsToRequest.toTypedArray()) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Slate900),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(vertical = 10.dp)
                                ) {
                                    Text(
                                        "Grant Camera & Microphone Access",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PureWhite
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Navigation Row
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                // Page Indicator Dots
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    steps.indices.forEach { index ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (index == currentStepIndex) 20.dp else 6.dp, 6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (index == currentStepIndex) Slate900 else Slate200)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (currentStepIndex > 0) {
                        OutlinedButton(
                            onClick = { currentStepIndex-- },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Slate300),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate700)
                        ) {
                            Text("Back", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Button(
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
                        colors = ButtonDefaults.buttonColors(containerColor = Slate900),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text(
                            text = if (currentStepIndex < steps.size - 1) "Continue" else "Open Dashboard",
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
