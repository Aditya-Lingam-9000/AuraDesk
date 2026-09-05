package com.auradesk.guard.ui.glass

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Glass Color Palette & Pre-allocated HWUI Static Brushes ───────────────────
object GlassColors {
    val SceneBg1 = Color(0xFFCFDBF2)
    val SceneBg2 = Color(0xFFE8ECF8)

    val GlassSurface    = Color(0xCCFFFFFF)   // 80% white
    val GlassSurfaceMid = Color(0xAAFFFFFF)  // 67% white

    val HighlightTop   = Color(0xEEFFFFFF)
    val HighlightBot   = Color(0x22FFFFFF)

    val ShimmerLight   = Color(0xBBFFFFFF)
    val ShimmerMid     = Color(0x44FFFFFF)

    val GlassBorder    = Color(0xAAFFFFFF)

    val TextPrimary   = Color(0xFF0F172A)
    val TextSecondary = Color(0xFF334155)
    val TextMuted     = Color(0xFF64748B)

    // Unified Global Icon Color Palette
    val IconColor      = Color(0xFF1E293B)  // Premium deep slate for all standard icons
    val IconColorMuted = Color(0xFF64748B)

    val AccentBlue  = Color(0xFF2563EB)
    val AccentGreen = Color(0xFF059669)
    val AccentRed   = Color(0xFFDC2626)
    val AccentAmber = Color(0xFFD97706)

    val GlassGreen = Color(0x22059669)
    val GlassRed   = Color(0x22DC2626)
    val GlassAmber = Color(0x22D97706)
    val GlassBlue  = Color(0x222563EB)

    // Static Pre-allocated Brushes (Zero heap allocation during draw frames)
    val GlassSurfaceBrush = Brush.verticalGradient(
        listOf(GlassSurface, GlassSurfaceMid)
    )

    val SceneBgGradient = Brush.verticalGradient(
        listOf(Color(0xFFCFDBF2), Color(0xFFE8ECF8))
    )

    val ArmedSceneBgGradient = Brush.verticalGradient(
        listOf(Color(0xFFBBDBFF), Color(0xFFD1EBD8))
    )
}

// ── Ultra-Fast Hardware-Accelerated Glass Surface Draw (Zero Heap Allocations) ─
inline fun DrawScope.drawGlassSurface(
    cornerRadius: Float,
    tintColor: Color = Color.Transparent
) {
    val cr = CornerRadius(cornerRadius, cornerRadius)

    // 1. Frosted base (Skia native hardware round rect, 0 path allocations)
    drawRoundRect(
        brush = GlassColors.GlassSurfaceBrush,
        cornerRadius = cr
    )

    // 2. Tint overlay
    if (tintColor != Color.Transparent) {
        drawRoundRect(
            color = tintColor,
            cornerRadius = cr
        )
    }

    // 3. Top light refraction highlight
    drawLine(
        color = GlassColors.HighlightTop,
        start = Offset(cornerRadius, 1.5f),
        end = Offset(size.width - cornerRadius, 1.5f),
        strokeWidth = 1.5f,
        cap = StrokeCap.Round
    )

    // 4. Glass border
    drawRoundRect(
        color = GlassColors.GlassBorder,
        cornerRadius = cr,
        style = Stroke(width = 1.dp.toPx())
    )
}

// ── Staggered Hardware-Accelerated Entrance Card ──────────────────────────────
@Composable
fun StaggeredAnimatedCard(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (index > 0) {
            kotlinx.coroutines.delay((index * 30L).coerceAtMost(120L))
        }
        visible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing),
        label = "staggerAlpha"
    )
    val transY by animateFloatAsState(
        targetValue = if (visible) 0f else 18f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "staggerY"
    )

    Box(
        modifier = modifier.graphicsLayer {
            this.alpha = alpha
            this.translationY = transY
        }
    ) {
        content()
    }
}

// ── GlassCard (120 FPS Optimized) ─────────────────────────────────────────────
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    tintColor: Color = Color.Transparent,
    onClick: (() -> Unit)? = null,
    enable3dTouch: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val localDensity = LocalDensity.current
    val cornerPx = remember(localDensity, cornerRadius) { with(localDensity) { cornerRadius.toPx() } }

    val scale by animateFloatAsState(
        targetValue = if (enable3dTouch && isPressed) 0.975f else 1.0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "cardScale"
    )

    var cardModifier: Modifier = modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            if (enable3dTouch && isPressed) {
                translationY = 1.5f
                rotationX = -2.5f
                cameraDistance = 18f * density
            }
        }
        .drawBehind {
            drawGlassSurface(cornerRadius = cornerPx, tintColor = tintColor)
        }

    if (onClick != null) {
        cardModifier = cardModifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    }

    Box(
        modifier = cardModifier,
        content = content
    )
}

// ── GlassButton (120 FPS Optimized) ───────────────────────────────────────────
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tintColor: Color = Color.Transparent,
    textColor: Color = GlassColors.TextPrimary,
    cornerRadius: Dp = 10.dp,
    isPrimary: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val localDensity = LocalDensity.current
    val cornerPx = remember(localDensity, cornerRadius) { with(localDensity) { cornerRadius.toPx() } }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.965f else 1.0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "btnScale"
    )

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 42.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                val cr = CornerRadius(cornerPx, cornerPx)
                if (isPrimary) {
                    drawRoundRect(
                        color = GlassColors.TextPrimary,
                        cornerRadius = cr
                    )
                    drawLine(
                        color = Color(0x33FFFFFF),
                        start = Offset(cornerPx, 1.5f),
                        end = Offset(size.width - cornerPx, 1.5f),
                        strokeWidth = 1f,
                        cap = StrokeCap.Round
                    )
                    drawRoundRect(
                        color = Color(0x22FFFFFF),
                        cornerRadius = cr,
                        style = Stroke(width = 1.dp.toPx())
                    )
                } else {
                    drawGlassSurface(cornerRadius = cornerPx, tintColor = tintColor)
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Text(
            text = text,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isPrimary) Color.White else textColor,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

// ── GlassBadge (120 FPS Optimized) ────────────────────────────────────────────
@Composable
fun GlassBadge(
    text: String,
    tintColor: Color = Color.Transparent,
    textColor: Color = GlassColors.TextPrimary
) {
    val localDensity = LocalDensity.current
    val cornerPx = remember(localDensity) { with(localDensity) { 5.dp.toPx() } }

    Box(
        modifier = Modifier
            .drawBehind {
                drawGlassSurface(cornerRadius = cornerPx, tintColor = tintColor)
            }
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        androidx.compose.material3.Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            maxLines = 1,
            softWrap = false
        )
    }
}

// ── GlassMetricTile (120 FPS Optimized) ───────────────────────────────────────
@Composable
fun GlassMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tintColor: Color = Color.Transparent,
    statusText: String? = null,
    statusColor: Color = GlassColors.TextMuted
) {
    val localDensity = LocalDensity.current
    val cornerPx = remember(localDensity) { with(localDensity) { 10.dp.toPx() } }

    Box(
        modifier = modifier
            .drawBehind {
                drawGlassSurface(cornerRadius = cornerPx, tintColor = tintColor)
            }
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Text(label, fontSize = 11.sp, color = GlassColors.TextMuted)
                if (statusText != null) {
                    androidx.compose.material3.Text(
                        text = statusText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            androidx.compose.material3.Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = GlassColors.TextPrimary
            )
        }
    }
}

// ── GlassSection (120 FPS Optimized) ──────────────────────────────────────────
@Composable
fun GlassSection(
    modifier: Modifier = Modifier,
    tintColor: Color = Color.Transparent,
    cornerRadius: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val localDensity = LocalDensity.current
    val cornerPx = remember(localDensity, cornerRadius) { with(localDensity) { cornerRadius.toPx() } }

    Column(
        modifier = modifier
            .drawBehind {
                drawGlassSurface(cornerRadius = cornerPx, tintColor = tintColor)
            }
            .padding(12.dp),
        content = content
    )
}
