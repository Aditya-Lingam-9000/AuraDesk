package com.auradesk.guard.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Premium iOS Liquid Glass Styling & Animation Toolkit
 */

// Glass Translucencies
val GlassSurface = Color(0xD9FFFFFF) // 85% opacity white
val GlassSurfaceSubtle = Color(0xB3FFFFFF) // 70% opacity white
val GlassSurfaceSecondary = Color(0x80F1F5F9) // Frosted secondary
val GlassBorderTop = Color(0xF2FFFFFF) // 95% specular highlight
val GlassBorderBottom = Color(0x40CBD5E1) // Refractive shadow edge
val GlassGleam = Color(0x66FFFFFF) // Specular gleam shine

// Glossy Ambient Orb Colors
val OrbCyan = Color(0x3338BDF8)
val OrbViolet = Color(0x2E818CF8)
val OrbEmerald = Color(0x2E34D399)
val OrbRose = Color(0x26F43F5E)

/**
 * Animated Liquid Background with floating iridescent blur orbs
 * Gives real refraction & depth to frosted liquid glass cards
 */
@Composable
fun LiquidGlassBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "LiquidOrbs")

    val t1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Orb1Motion"
    )

    val t2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Orb2Motion"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF1F5F9)) // Soft slate pearl base
    ) {
        // Floating Fluid Orbs
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Orb 1: Cyan fluid
            val o1X = w * 0.25f + cos(t1) * 80f
            val o1Y = h * 0.2f + sin(t1) * 60f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(OrbCyan, Color.Transparent),
                    center = Offset(o1X, o1Y),
                    radius = w * 0.55f
                ),
                center = Offset(o1X, o1Y),
                radius = w * 0.55f
            )

            // Orb 2: Violet fluid
            val o2X = w * 0.75f + sin(t2) * 90f
            val o2Y = h * 0.45f + cos(t2) * 70f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(OrbViolet, Color.Transparent),
                    center = Offset(o2X, o2Y),
                    radius = w * 0.6f
                ),
                center = Offset(o2X, o2Y),
                radius = w * 0.6f
            )

            // Orb 3: Emerald fluid
            val o3X = w * 0.35f + cos(t2) * 70f
            val o3Y = h * 0.8f + sin(t2) * 80f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(OrbEmerald, Color.Transparent),
                    center = Offset(o3X, o3Y),
                    radius = w * 0.5f
                ),
                center = Offset(o3X, o3Y),
                radius = w * 0.5f
            )
        }

        content()
    }
}

/**
 * Liquid Pressable scale modifier with spring bounce feedback
 */
@Composable
fun Modifier.liquidPressEffect(
    pressedScale: Float = 0.97f,
    onClick: (() -> Unit)? = null
): Modifier {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "LiquidPressScale"
    )

    return this
        .scale(scale)
        .pointerInput(onClick) {
            detectTapGestures(
                onPress = {
                    isPressed = true
                    try {
                        awaitRelease()
                    } finally {
                        isPressed = false
                    }
                },
                onTap = {
                    onClick?.invoke()
                }
            )
        }
}

/**
 * Liquid Glass Shimmering Gleam modifier
 */
@Composable
fun Modifier.liquidGleamEffect(
    enabled: Boolean = true,
    durationMs: Int = 4500
): Modifier {
    if (!enabled) return this

    val infiniteTransition = rememberInfiniteTransition(label = "LiquidGleam")
    val gleamProgress by infiniteTransition.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "GleamProgress"
    )

    return this.drawWithContent {
        drawContent()

        val gleamX = size.width * gleamProgress
        val brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = 0.25f),
                Color.White.copy(alpha = 0.45f),
                Color.White.copy(alpha = 0.25f),
                Color.Transparent
            ),
            start = Offset(gleamX - 100f, 0f),
            end = Offset(gleamX + 100f, size.height)
        )
        drawRect(brush = brush, blendMode = BlendMode.SrcOver)
    }
}

/**
 * Standard Liquid Glass Card Container
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    isInteractive: Boolean = false,
    enableGleam: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed && (isInteractive || onClick != null)) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "CardPress"
    )

    val glassGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xE6FFFFFF), // High refraction top
            Color(0xB8FFFFFF), // Frosted mid
            Color(0xD1F8FAFC)  // Translucent pearl bottom
        )
    )

    val specularBorder = Brush.linearGradient(
        colors = listOf(
            Color(0xF0FFFFFF), // Intense top-left highlight
            Color(0x66CBD5E1), // Mid refraction
            Color(0x99FFFFFF)  // Bottom specular reflect
        ),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(glassGradient)
            .border(BorderStroke(1.2.dp, specularBorder), shape)
            .liquidGleamEffect(enabled = enableGleam)
            .then(
                if (onClick != null || isInteractive) {
                    Modifier.pointerInput(onClick) {
                        detectTapGestures(
                            onPress = {
                                isPressed = true
                                try {
                                    awaitRelease()
                                } finally {
                                    isPressed = false
                                }
                            },
                            onTap = { onClick?.invoke() }
                        )
                    }
                } else Modifier
            )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            content = content
        )
    }
}

/**
 * Liquid Glass Button with spring bounce and specular shine
 */
@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = true,
    shape: Shape = RoundedCornerShape(14.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "ButtonPress"
    )

    val backgroundBrush = if (isPrimary) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF1E293B), // Slate 800
                Color(0xFF0F172A)  // Slate 900
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xF2FFFFFF),
                Color(0xCCF1F5F9)
            )
        )
    }

    val borderBrush = if (isPrimary) {
        Brush.linearGradient(
            colors = listOf(
                Color(0x6694A3B8),
                Color(0x26475569)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color(0xF0FFFFFF),
                Color(0x66CBD5E1)
            )
        )
    }

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(backgroundBrush)
            .border(BorderStroke(1.dp, borderBrush), shape)
            .pointerInput(onClick) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        try {
                            awaitRelease()
                        } finally {
                            isPressed = false
                        }
                    },
                    onTap = { onClick() }
                )
            }
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content
        )
    }
}

/**
 * Liquid Glass Nested Tile / Metric Box
 */
@Composable
fun LiquidGlassTile(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "TilePress"
    )

    val tileGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0x80FFFFFF),
            Color(0x4DF1F5F9)
        )
    )

    val tileBorder = Brush.linearGradient(
        colors = listOf(
            Color(0xE6FFFFFF),
            Color(0x40CBD5E1)
        )
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(tileGradient)
            .border(BorderStroke(1.dp, tileBorder), shape)
            .then(
                if (onClick != null) {
                    Modifier.pointerInput(onClick) {
                        detectTapGestures(
                            onPress = {
                                isPressed = true
                                try {
                                    awaitRelease()
                                } finally {
                                    isPressed = false
                                }
                            },
                            onTap = { onClick() }
                        )
                    }
                } else Modifier
            )
            .padding(12.dp)
    ) {
        Column(content = content)
    }
}

/**
 * Liquid Glass Status Pill Badge
 */
@Composable
fun LiquidGlassBadge(
    text: String,
    textColor: Color,
    backgroundColor: Color = Color(0x66FFFFFF),
    borderColor: Color = Color(0x99FFFFFF),
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
    }
}
