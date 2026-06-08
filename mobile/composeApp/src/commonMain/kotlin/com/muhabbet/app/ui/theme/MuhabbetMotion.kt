package com.muhabbet.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Muhabbet motion language — the single source of truth for animation timing, easing, and springs.
 * See `docs/design/D2-motion-spec.md`. Principle: **motion = meaning** (every animation communicates
 * a state change, nothing is mere decoration). Use these tokens instead of ad-hoc `tween(300)` /
 * `spring()` literals so the app feels coherent.
 */
object MuhabbetMotion {
    // Durations (ms) — three steps, emphasized scale.
    const val DurationQuick: Int = 120        // taps, toggles, tick state changes
    const val DurationStandard: Int = 220     // most transitions
    const val DurationEmphasized: Int = 360   // hero / entrance moments

    // Easing — M3-expressive flavored. Standard for in-place changes; emphasized for arrivals.
    val EasingStandard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EasingEmphasized: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EasingDecelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)

    // Springs — the "alive" feel.
    val PressSpring: SpringSpec<Float> =
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
    val BubbleSpring: SpringSpec<Float> =
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
    val ReactionSpring: SpringSpec<Float> =
        spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium)

    // Signature magnitudes.
    const val PressScale: Float = 0.97f        // how far a pressed element shrinks
    const val EntranceFromScale: Float = 0.94f // entrance start scale
}

/**
 * Tactile press feedback: the element gently shrinks while pressed and springs back on release.
 * **Scroll-safe** — driven by the press [interactionSource], not by appearance, so it does NOT
 * re-fire when a LazyColumn recycles items. Share the same [interactionSource] with the element's
 * `clickable`/`combinedClickable` so press state is in sync.
 */
@Composable
fun Modifier.pressBounce(interactionSource: MutableInteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) MuhabbetMotion.PressScale else 1f,
        animationSpec = MuhabbetMotion.PressSpring,
        label = "pressBounce"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Signature "lift + settle" entrance for a **newly added** message bubble: it springs up from the
 * sending side and fades in. Apply ONLY to genuinely new items (e.g. the just-sent message), NOT to
 * every list item — LazyColumn recycles items on scroll and this would re-trigger the animation.
 */
@Composable
fun Modifier.bubbleEntrance(isOwn: Boolean): Modifier {
    var appeared by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else MuhabbetMotion.EntranceFromScale,
        animationSpec = MuhabbetMotion.BubbleSpring,
        label = "bubbleEntranceScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(MuhabbetMotion.DurationStandard, easing = MuhabbetMotion.EasingStandard),
        label = "bubbleEntranceAlpha"
    )
    LaunchedEffect(Unit) { appeared = true }
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
        transformOrigin = TransformOrigin(if (isOwn) 1f else 0f, 1f)
    }
}
