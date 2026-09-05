package com.auradesk.guard

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.auradesk.guard.data.InterruptionRepository
import com.auradesk.guard.sensors.ShakeDetector
import com.auradesk.guard.service.GuardService
import com.auradesk.guard.ui.DashboardScreen
import com.auradesk.guard.ui.GuardArmedScreen
import com.auradesk.guard.ui.InterruptionCard
import com.auradesk.guard.ui.theme.AuraDeskTheme
import com.auradesk.guard.utils.FeedbackManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var shakeDetector: ShakeDetector
    private lateinit var feedbackManager: FeedbackManager
    private lateinit var repository: InterruptionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request maximum 120Hz / 144Hz panel refresh rate
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            try {
                val display = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    display
                } else {
                    @Suppress("DEPRECATION")
                    windowManager.defaultDisplay
                }
                val modes = display?.supportedModes
                val maxRefreshMode = modes?.maxByOrNull { it.refreshRate }
                if (maxRefreshMode != null && maxRefreshMode.refreshRate >= 90f) {
                    val params = window.attributes
                    params.preferredDisplayModeId = maxRefreshMode.modeId
                    window.attributes = params
                }
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Could not set preferred display mode: ${e.message}")
            }
        }

        repository = InterruptionRepository.getInstance(this)
        feedbackManager = FeedbackManager(this)

        // Ensure GuardService instance is ready for static access
        GuardService.ensureAudioCapsuleManager(this)

        shakeDetector = ShakeDetector(this) {
            // Shake-to-delete action
            feedbackManager.playIncinerateFeedback()
            runOnUiThread {
                Toast.makeText(this, "Shake detected: All interruption capsules incinerated", Toast.LENGTH_SHORT).show()
            }
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                repository.deleteAll()
            }
        }

        setContent {
            AuraDeskTheme {
                val prefs = remember { getSharedPreferences("auradesk_prefs", MODE_PRIVATE) }
                var showOnboarding by remember {
                    mutableStateOf(!prefs.getBoolean("has_completed_onboarding", false))
                }

                val isArmed by GuardService.isArmed.collectAsState()
                val activeCapsule by repository.activeCapsule.collectAsState()
                val coroutineScope = rememberCoroutineScope()

                if (showOnboarding) {
                    com.auradesk.guard.ui.OnboardingScreen(
                        onFinish = { showOnboarding = false }
                    )
                } else {
                    // Interruption Capsule Pop-up upon lifting phone if there's a new interruption
                    if (!isArmed && activeCapsule != null) {
                        Dialog(
                            onDismissRequest = {
                                activeCapsule?.let { capsule ->
                                    coroutineScope.launch { repository.dismiss(capsule.id) }
                                }
                            },
                            properties = DialogProperties(usePlatformDefaultWidth = false)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0x800F172A))
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                activeCapsule?.let { capsule ->
                                    InterruptionCard(
                                        capsule = capsule,
                                        onSaveToNotes = { id ->
                                            coroutineScope.launch {
                                                repository.markSavedToNotes(id)
                                                Toast.makeText(this@MainActivity, "Saved to Vivo Notes", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onDismiss = { id ->
                                            coroutineScope.launch { repository.dismiss(id) }
                                        },
                                        onDelete = { id ->
                                            coroutineScope.launch { repository.delete(id) }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Crossfade(targetState = isArmed, label = "ScreenTransition") { armed ->
                        if (armed) {
                            GuardArmedScreen(
                                onDisarm = {
                                    GuardService.stopService(this@MainActivity)
                                }
                            )
                        } else {
                            DashboardScreen(
                                onReplayTour = { showOnboarding = true }
                            )
                        }
                    }
                }
            }
        }

    }

    override fun onResume() {
        super.onResume()
        shakeDetector.startListening()
    }

    override fun onPause() {
        super.onPause()
        shakeDetector.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        feedbackManager.release()
    }
}