package com.auradesk.guard.ui.glass

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*

// ── Glass Color Palette ───────────────────────────────────────────────────────
object GlassColors {
    val SceneBg1 = Color(0xFFCFDBF2)
    val SceneBg2 = Color(0xFFE8ECF8)

    val GlassSurface   = Color(0xCCFFFFFF)   // 80% white
    val GlassSurfaceMid = Color(0xAAFFFFFF)  // 67% white

    val HighlightTop   = Color(0xEEFFFFFF)
    val HighlightBot   = Color(0x22FFFFFF)

    val ShimmerLight   = Color(0xBBFFFFFF)
    val ShimmerMid     = Color(0x44FFFFFF)

    val GlassBorder    = Color(0xAAFFFFFF)

    val TextPrimary   = Color(0xFF0F172A)
    val TextSecondary = Color(0xFF334155)
    val TextMuted     = Color(0xFF64748B)

    val AccentBlue  = Color(0xFF2563EB)
    val AccentGreen = Color(0xFF059669)
    val AccentRed   = Color(0xFFDC2626)
    val AccentAmber = Color(0xFFD97706)

    val GlassGreen = Color(0x33059669)
    val GlassRed   = Color(0x33DC2626)
    val GlassAmber = Color(0x33D97706)
    val GlassBlue  = Color(0x332563EB)
}

// ── Animated shimmer offset ───────────────────────────────────────────────────
@Composable
fun rememberShimmerOffset(): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    return infiniteTransition.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    ).value
}

// ── Spring press scale ────────────────────────────────────────────────────────
@Composable
fun rememberLiquidPressScale(pressed: Boolean): Float {
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.967f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "liquidPress"
    )
    return scale
}

// ── Draw shimmer sweep ────────────────────────────────────────────────────────
fun DrawScope.drawGlassShimmer(shimmerOffset: Float, alpha: Float = 0.35f) {
    val sw = size.width * 0.4f
    val x = (shimmerOffset * (size.width + sw)) - sw / 2
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                GlassColors.ShimmerMid.copy(alpha = alpha * 0.3f),
                GlassColors.ShimmerLight.copy(alpha = alpha),
                GlassColors.ShimmerMid.copy(alpha = alpha * 0.3f),
                Color.Transparent
            ),
            start = Offset(x - sw / 2, 0f),
            end   = Offset(x + sw / 2, size.height)
        )
    )
}

// ── Core glass surface draw ───────────────────────────────────────────────────
fun DrawScope.drawGlassSurface(
    cornerRadius: Float,
    tintColor: Color = Color.Transparent,
    shimmerOffset: Float = 0f,
    drawShimmer: Boolean = true
) {
    val rrect = RoundRect(Rect(Offset.Zero, size), CornerRadius(cornerRadius))
    val path  = Path().apply { addRoundRect(rrect) }

    // Frosted base
    drawPath(
        path  = path,
        brush = Brush.verticalGradient(
            colors = listOf(GlassColors.GlassSurface, GlassColors.GlassSurfaceMid)
        )
    )

    // Tint overlay
    if (tintColor != Color.Transparent) drawPath(path = path, color = tintColor)

    // Top highlight (light refraction)
    drawLine(
        color       = GlassColors.HighlightTop,
        start       = Offset(cornerRadius, 1.5f),
        end         = Offset(size.width - cornerRadius, 1.5f),
        strokeWidth = 1.5f,
        cap         = StrokeCap.Round
    )

    // Shimmer sweep
    if (drawShimmer) clipPath(path) { drawGlassShimmer(shimmerOffset) }

    // Border
    drawPath(path = path, color = GlassColors.GlassBorder, style = Stroke(width = 1.dp.toPx()))
}

// ── GlassCard ────────────────────────────────────────────────────────────────
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    tintColor: Color = Color.Transparent,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed  by interactionSource.collectIsPressedAsState()
    val shimmerOff = rememberShimmerOffset()
    val pressScale = rememberLiquidPressScale(isPressed)
    val density    = LocalDensity.current
    val cornerPx   = with(density) { cornerRadius.toPx() }

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .drawBehind {
                drawGlassSurface(
                    cornerRadius  = cornerPx,
                    tintColor     = tintColor,
                    shimmerOffset = shimmerOff
                )
            }
            .then(
                if (onClick != null)
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication        = null,
                        onClick           = onClick
                    )
                else Modifier
            ),
        content = content
    )
}

// ── GlassButton ──────────────────────────────────────────────────────────────
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
    val isPressed  by interactionSource.collectIsPressedAsState()
    val pressScale = rememberLiquidPressScale(isPressed)
    val shimmerOff = rememberShimmerOffset()
    val density    = LocalDensity.current
    val cornerPx   = with(density) { cornerRadius.toPx() }

    Box(
        modifier = modifier
            .height(44.dp)
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .drawBehind {
                val rrect = RoundRect(Rect(Offset.Zero, size), CornerRadius(cornerPx))
                val path  = Path().apply { addRoundRect(rrect) }
                if (isPrimary) {
                    drawPath(path = path, color = GlassColors.TextPrimary.copy(alpha = 0.85f))
                    drawLine(
                        color       = Color(0x55FFFFFF),
                        start       = Offset(cornerPx, 1.5f),
                        end         = Offset(size.width - cornerPx, 1.5f),
                        strokeWidth = 1.5f,
                        cap         = StrokeCap.Round
                    )
                    clipPath(path) { drawGlassShimmer(shimmerOff, alpha = 0.20f) }
                } else {
                    drawGlassSurface(
                        cornerRadius  = cornerPx,
                        tintColor     = tintColor,
                        shimmerOffset = shimmerOff
                    )
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Text(
            text       = text,
            fontSize   = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color      = if (isPrimary) Color.White else textColor,
            maxLines   = 1,
            softWrap   = false
        )
    }
}

// ── GlassBadge ───────────────────────────────────────────────────────────────
@Composable
fun GlassBadge(
    text: String,
    tintColor: Color = Color.Transparent,
    textColor: Color = GlassColors.TextPrimary
) {
    val shimmerOff = rememberShimmerOffset()
    val density    = LocalDensity.current
    val cornerPx   = with(density) { 5.dp.toPx() }

    Box(
        modifier = Modifier
            .drawBehind {
                drawGlassSurface(
                    cornerRadius  = cornerPx,
                    tintColor     = tintColor,
                    shimmerOffset = shimmerOff,
                    drawShimmer   = false
                )
            }
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        androidx.compose.material3.Text(
            text       = text,
            fontSize   = 11.sp,
            fontWeight = FontWeight.Bold,
            color      = textColor,
            maxLines   = 1,
            softWrap   = false
        )
    }
}

// ── GlassMetricTile ──────────────────────────────────────────────────────────
@Composable
fun GlassMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tintColor: Color = Color.Transparent,
    statusText: String? = null,
    statusColor: Color = GlassColors.TextMuted
) {
    val shimmerOff = rememberShimmerOffset()
    val density    = LocalDensity.current
    val cornerPx   = with(density) { 10.dp.toPx() }

    Box(
        modifier = modifier
            .drawBehind {
                drawGlassSurface(
                    cornerRadius  = cornerPx,
                    tintColor     = tintColor,
                    shimmerOffset = shimmerOff
                )
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
                        text       = statusText,
                        fontSize   = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color      = statusColor
                    )
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            androidx.compose.material3.Text(
                text       = value,
                fontSize   = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color      = GlassColors.TextPrimary
            )
        }
    }
}

// ── GlassSection ─────────────────────────────────────────────────────────────
@Composable
fun GlassSection(
    modifier: Modifier = Modifier,
    tintColor: Color = Color.Transparent,
    cornerRadius: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shimmerOff = rememberShimmerOffset()
    val density    = LocalDensity.current
    val cornerPx   = with(density) { cornerRadius.toPx() }

    Column(
        modifier = modifier
            .drawBehind {
                drawGlassSurface(
                    cornerRadius  = cornerPx,
                    tintColor     = tintColor,
                    shimmerOffset = shimmerOff,
                    drawShimmer   = false
                )
            }
            .padding(12.dp),
        content = content
    )
}
