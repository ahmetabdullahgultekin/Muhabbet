package com.muhabbet.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.muhabbet.designsystem.theme.LocalReducedMotion
import com.muhabbet.designsystem.theme.MuhabbetMotion
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing

/**
 * The conversation list's empty state: two chat bubbles and a heart floating in a soft circle.
 *
 * **Drawn in unit space** (#841). Every coordinate below is in units of a 120-unit-square artboard
 * and multiplied by the canvas's actual size, so the picture is identical at every density. It used
 * to be drawn in raw px inside a dp-sized canvas, which on a 3.5x phone left a tiny cluster in the
 * middle of a 120dp circle — on the first screen a new user sees.
 *
 * The float is a slow idle loop; under reduced motion it stops at rest (#842).
 */
@Composable
fun EmptyChatsIllustration(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val colors = EmptyChatsColors(
        primary = MaterialTheme.colorScheme.primary,
        // The mock "text" lines inside the drawn bubble. `onPrimary` because the bubble under them is
        // `primary` — as a hardcoded white they were invisible on the light theme's white-ish
        // variants and stayed white in dark, where the partner is near-black.
        onPrimary = MaterialTheme.colorScheme.onPrimary,
        secondary = MaterialTheme.colorScheme.secondary,
        backdrop = MaterialTheme.colorScheme.surfaceVariant
    )
    val float = rememberIllustrationFloat()

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // The float is read inside the draw lambda, so the loop redraws the canvas and recomposes
        // nothing.
        Canvas(modifier = Modifier.size(MuhabbetSizes.EmptyChatsIllustration)) {
            drawEmptyChats(colors, float.value)
        }

        Spacer(Modifier.height(MuhabbetSpacing.XLarge))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(MuhabbetSpacing.Small))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 0..[FloatAmplitude] artboard units, or a constant 0 under reduced motion. */
@Composable
private fun rememberIllustrationFloat(): State<Float> {
    if (LocalReducedMotion.current) return remember { mutableFloatStateOf(0f) }
    return rememberInfiniteTransition(label = "empty_state").animateFloat(
        initialValue = 0f,
        targetValue = FloatAmplitude,
        animationSpec = infiniteRepeatable(
            animation = tween(MuhabbetMotion.Duration.IllustrationFloatMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )
}

private class EmptyChatsColors(val primary: Color, val onPrimary: Color, val secondary: Color, val backdrop: Color)

/**
 * The artwork. [float] is in artboard units. `u` converts one artboard unit to pixels for whatever
 * size the canvas actually has.
 */
private fun DrawScope.drawEmptyChats(colors: EmptyChatsColors, float: Float) {
    val u = size.minDimension / Artboard
    val cx = size.width / 2
    val cy = size.height / 2
    fun at(x: Float, y: Float) = Offset(cx + x * u, cy + y * u)
    val corner = CornerRadius(12f * u, 12f * u)

    drawCircle(color = colors.backdrop, radius = size.minDimension / 2.2f, center = Offset(cx, cy))

    // Received bubble, with three dots.
    val bubble1Y = -14f + float
    drawRoundRect(colors.primary.copy(alpha = 0.3f), topLeft = at(-40f, bubble1Y - 16f), size = Size(50f * u, 28f * u), cornerRadius = corner)
    for (i in 0..2) {
        drawCircle(colors.primary.copy(alpha = 0.6f), radius = 3f * u, center = at(-26f + i * 12f, bubble1Y))
    }

    // Sent bubble, with two lines of "text".
    val bubble2Y = 10f - float * 0.5f
    drawRoundRect(colors.primary.copy(alpha = 0.7f), topLeft = at(-10f, bubble2Y - 14f), size = Size(48f * u, 26f * u), cornerRadius = corner)
    drawLine(colors.onPrimary.copy(alpha = 0.85f), start = at(-2f, bubble2Y - 4f), end = at(30f, bubble2Y - 4f), strokeWidth = 2.5f * u)
    drawLine(colors.onPrimary.copy(alpha = 0.6f), start = at(-2f, bubble2Y + 4f), end = at(20f, bubble2Y + 4f), strokeWidth = 2.5f * u)

    // Heart.
    drawCircle(colors.secondary.copy(alpha = 0.5f), radius = 6f * u, center = at(30f, -30f + float * 0.7f))
}

/** The artboard's side, in its own units. Every coordinate in [drawEmptyChats] is out of this. */
private const val Artboard = 120f

/** How far the bubbles drift at the top of the float, in artboard units. */
private const val FloatAmplitude = 8f
