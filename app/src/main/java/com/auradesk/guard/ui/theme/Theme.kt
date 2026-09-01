package com.auradesk.guard.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val GlassLightColorScheme = lightColorScheme(
    primary               = Color(0xFF0F172A),
    onPrimary             = Color(0xFFFFFFFF),
    primaryContainer      = Color(0xAAFFFFFF),
    onPrimaryContainer    = Color(0xFF0F172A),
    secondary             = Color(0xFF334155),
    onSecondary           = Color(0xFFFFFFFF),
    secondaryContainer    = Color(0xAAFFFFFF),
    onSecondaryContainer  = Color(0xFF0F172A),
    background            = Color(0xFFCFDBF2),
    onBackground          = Color(0xFF0F172A),
    surface               = Color(0xCCFFFFFF),
    onSurface             = Color(0xFF0F172A),
    surfaceVariant        = Color(0xAAFFFFFF),
    onSurfaceVariant      = Color(0xFF334155),
    outline               = Color(0xAAFFFFFF),
    outlineVariant        = Color(0x44FFFFFF)
)

@Composable
fun AuraDeskTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = GlassLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = Color(0xCCFFFFFF).toArgb()
                window.navigationBarColor = Color(0xBBFFFFFF).toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = true
                insetsController.isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}