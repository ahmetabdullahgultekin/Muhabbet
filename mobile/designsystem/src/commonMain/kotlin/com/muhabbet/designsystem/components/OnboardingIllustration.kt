package com.muhabbet.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.muhabbet.designsystem.theme.MuhabbetMotion
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.breathing
import kotlin.math.cos
import kotlin.math.sin

/**
 * The two drawn illustrations the first-run welcome flow (#692) is built on.
 *
 * **Drawn, not imported.** The obvious way to put "nice Lottie-style animation" on an onboarding
 * screen is to add `io.github.alexzhirkevich:compottie` and ship a JSON file per step. That is the
 * wrong trade here for three reasons, all of them already written down elsewhere in this module:
 *
 *  - The design doc's illustration rule is Canvas or vector, never a raster asset, and
 *    [MuhabbetBrandMark] names it explicitly as the rule a logo is easiest to break by accident.
 *    A Lottie JSON is not a raster, but it is an *asset*, and the debug APK is already ~82 MB.
 *  - Motion has exactly one home. `MuhabbetMotion` exists so the whole app's feel is retunable from
 *    one file, and `rawStackAnimation = 0` in the guardrails is the same rule enforced for
 *    navigation. Easing baked into a Lottie file is reachable from neither — it would be the only
 *    motion in the app that cannot be changed by editing [MuhabbetMotion].
 *  - [EmptyStateIllustration] already proves the app draws its own animated art with `Canvas` and
 *    the theme's own colours, which is also how these stay legible in light, dark and OLED without
 *    a second set of JSON files.
 *
 * Both are decorative — `clearAndSetSemantics {}`, because the step's own title and body say the
 * whole thing and a screen reader announcing the artwork first is noise.
 */

/**
 * A single 0→1 spring, released on first composition, that the drawing reads as its entry progress.
 *
 * One animation rather than one per element: springs cannot be delayed individually without a
 * `LaunchedEffect` each, and staggering is a matter of reading different windows out of the same
 * travel — see [stagger].
 */
@Composable
private fun rememberEntryProgress(): Float {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val progress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        // Spatial: things arrive and settle, and a slight overshoot is what reads as physical. Slow,
        // because the distance is large and this is the first thing a new user sees.
        animationSpec = MuhabbetMotion.spatialSlow(),
        label = "onboardingIllustrationEntry"
    )
    return progress
}

/**
 * The share of [progress] belonging to element [index] of [count], as its own 0→1 ramp.
 *
 * Each element gets the whole travel compressed into a later window, so they arrive one after
 * another out of a single spring. Coerced, so an overshooting spatial spring cannot push an alpha
 * past 1 or a scale past its target.
 */
private fun stagger(progress: Float, index: Int, count: Int): Float {
    val window = 1f / (count + 1)
    return ((progress - index * window) / (1f - index * window)).coerceIn(0f, 1f)
}

/**
 * People arriving around you: a centre mark with satellites settling onto a ring.
 *
 * Drawn for the welcome flow's contacts step, where the question being asked is "may we look at
 * your address book" and the honest picture of the answer is other people appearing, not a phone
 * book.
 */
@Composable
fun ContactsRingIllustration(modifier: Modifier = Modifier) {
    val progress = rememberEntryProgress()
    val backdrop = MaterialTheme.colorScheme.surfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    Box(
        modifier = modifier
            .size(MuhabbetSizes.OnboardingIllustration)
            .breathing()
            .clearAndSetSemantics {}
    ) {
        Canvas(modifier = Modifier.size(MuhabbetSizes.OnboardingIllustration)) {
            val centre = Offset(size.width / 2, size.height / 2)
            val ring = size.minDimension / 3f

            drawCircle(color = backdrop, radius = size.minDimension / 2.2f, center = centre)

            // The satellites first, so the centre mark sits over them where they overlap — the
            // person the ring is drawn around is the one in front.
            repeat(SatelliteCount) { index ->
                val reveal = stagger(progress, index, SatelliteCount)
                if (reveal <= 0f) return@repeat
                val angle = TwoPi * index / SatelliteCount - QuarterTurn
                // Satellites travel inward as they appear rather than simply fading: a dot that
                // fades in on the spot reads as a rendering glitch, one that arrives reads as
                // someone joining.
                val distance = ring * (SatelliteTravel - (SatelliteTravel - 1f) * reveal)
                drawCircle(
                    color = (if (index % 2 == 0) primary else secondary).copy(alpha = SatelliteAlpha * reveal),
                    radius = ring * SatelliteRadius * reveal,
                    center = Offset(
                        centre.x + cos(angle) * distance,
                        centre.y + sin(angle) * distance
                    )
                )
            }

            val centreReveal = stagger(progress, 0, SatelliteCount)
            drawCircle(
                color = primary.copy(alpha = centreReveal),
                radius = ring * CentreRadius * centreReveal,
                center = centre
            )
        }
    }
}

/**
 * Two message bubbles arriving in sequence — the welcome flow's closing step, where what is being
 * described is the thing the app is for.
 */
@Composable
fun ChatStartIllustration(modifier: Modifier = Modifier) {
    val progress = rememberEntryProgress()
    val backdrop = MaterialTheme.colorScheme.surfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary

    Box(
        modifier = modifier
            .size(MuhabbetSizes.OnboardingIllustration)
            .breathing()
            .clearAndSetSemantics {}
    ) {
        Canvas(modifier = Modifier.size(MuhabbetSizes.OnboardingIllustration)) {
            val centre = Offset(size.width / 2, size.height / 2)
            val unit = size.minDimension / BubbleUnitDivisor

            drawCircle(color = backdrop, radius = size.minDimension / 2.2f, center = centre)

            // Received first, then sent: a conversation starts with somebody else, which is the
            // point the step's copy is making.
            drawBubble(
                reveal = stagger(progress, 0, BubbleCount),
                topLeft = Offset(centre.x - unit * 3.4f, centre.y - unit * 2.4f),
                size = Size(unit * 4.6f, unit * 2.4f),
                fill = primary.copy(alpha = ReceivedBubbleAlpha),
                lineColor = primary,
                unit = unit
            )
            drawBubble(
                reveal = stagger(progress, 1, BubbleCount),
                topLeft = Offset(centre.x - unit * 1.2f, centre.y + unit * 0.4f),
                size = Size(unit * 4.6f, unit * 2.4f),
                fill = primary,
                lineColor = onPrimary,
                unit = unit
            )
        }
    }
}

/**
 * One bubble plus its two mock text lines, drawn at [reveal] of its final size and opacity.
 *
 * The lines take their colour from the caller rather than a constant: the sent bubble is filled
 * with `primary`, so white lines on it were legible only by luck in one of the three schemes —
 * the same defect [EmptyStateIllustration] records having had.
 */
private fun DrawScope.drawBubble(
    reveal: Float,
    topLeft: Offset,
    size: Size,
    fill: Color,
    lineColor: Color,
    unit: Float
) {
    if (reveal <= 0f) return
    // Bubbles rise into place. `unit` rather than a fixed pixel count so the travel scales with the
    // illustration instead of being tuned to one density.
    val lift = unit * (1f - reveal)
    val origin = Offset(topLeft.x, topLeft.y + lift)

    drawRoundRect(
        color = fill.copy(alpha = fill.alpha * reveal),
        topLeft = origin,
        size = Size(size.width, size.height),
        cornerRadius = CornerRadius(unit, unit)
    )
    repeat(2) { line ->
        val y = origin.y + size.height * (if (line == 0) 0.35f else 0.65f)
        drawLine(
            color = lineColor.copy(alpha = (if (line == 0) 0.9f else 0.6f) * reveal),
            start = Offset(origin.x + unit * 0.5f, y),
            end = Offset(origin.x + size.width - unit * (if (line == 0) 0.5f else 1.4f), y),
            strokeWidth = unit * 0.22f
        )
    }
}

private const val SatelliteCount = 6
private const val BubbleCount = 2
private const val TwoPi = 6.2831855f
private const val QuarterTurn = 1.5707964f

/** How far out the satellites start, as a multiple of the ring they settle onto. */
private const val SatelliteTravel = 1.45f
private const val SatelliteRadius = 0.24f
private const val SatelliteAlpha = 0.85f
private const val CentreRadius = 0.5f
private const val ReceivedBubbleAlpha = 0.28f

/** The illustration's own grid: a bubble is a few of these across, so nothing is tuned in pixels. */
private const val BubbleUnitDivisor = 11f
