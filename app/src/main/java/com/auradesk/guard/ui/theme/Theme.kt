package com.auradesk.guard.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val CleanLightColorScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = PureWhite,
    primaryContainer = BorderSubtle,
    onPrimaryContainer = TextPrimary,
    secondary = TextSecondary,
    onSecondary = PureWhite,
    secondaryContainer = BorderSubtle,
    onSecondaryContainer = TextPrimary,
    tertiary = AccentBlue,
    onTertiary = PureWhite,
    background = AppBg,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = AppBg,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    outlineVariant = BorderStrong
)

@Composable
fun AuraDeskTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = CleanLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = PureWhite.toArgb()
                window.navigationBarColor = PureWhite.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = true
                insetsController.isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}