package com.muhabbet.designsystem.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing

/**
 * The app's motion scale.
 *
 * Before this existed there were two `tween()` calls in the entire UI layer and no `spring()` at
 * all: every state change was a hard cut. Motion is most of what separates an app that feels
 * expensive from one that feels assembled, and it needs one home or it becomes 60 ad-hoc durations.
 *
 * Two families, deliberately:
 *
 *  - **Spatial** springs move or resize things. Damping is below 1, so they overshoot slightly and
 *    settle. That overshoot is what reads as physical rather than scripted.
 *  - **Effects** springs animate colour, alpha and elevation. Damping is exactly 1: an overshooting
 *    colour animation renders a hue that is not in the palette, and an overshooting alpha clips.
 *
 * The taxonomy is borrowed from Material 3 Expressive, which gets it right. The implementation is
 * not: `MaterialExpressiveTheme` also swaps component shapes, press-morph and progress indicators
 * app-wide with no per-screen opt-out, and the result looks like a stock Google reference app. The
 * brief here is a distinct identity, so we take the structure and own the constants.
 *
 * Stiffnesses are read from the expressive scheme (they are what make it feel quick); damping is
 * raised to 0.80 from its 0.6/0.8, because a message list that bounces on every insert reads as a
 * toy rather than as premium.
 *
 * An `object`, not a CompositionLocal: nothing in this app varies motion by theme, and an object is
 * callable from non-composable code. If per-theme motion is ever needed, Compose's own
 * `LocalMotionScheme` is the mechanical migration and no call site changes.
 *
 * The one thing that *does* vary motion is the user: [LocalReducedMotion] carries the OS setting,
 * and the policy-aware members below take it as a parameter rather than reading it themselves, so
 * they stay callable from `transitionSpec` lambdas and other non-composable code (#842).
 */
object MuhabbetMotion {

    private const val SpatialDamping = 0.80f
    private const val EffectsDamping = 1f

    /** Press feedback, icon swaps, delivery-tick states — anything the finger is still touching. */
    private const val SpatialStiffFast = 800f

    /** List item enter/exit, message bubbles, sheets. The default for "something moved". */
    private const val SpatialStiffDefault = 380f

    /** Full-screen transitions and hero moves, where the distance travelled is large. */
    private const val SpatialStiffSlow = 200f

    private const val EffectsStiffFast = 3800f
    private const val EffectsStiffDefault = 1600f
    private const val EffectsStiffSlow = 800f

    fun <T> spatialFast(): SpringSpec<T> = spring(SpatialDamping, SpatialStiffFast)
    fun <T> spatialDefault(): SpringSpec<T> = spring(SpatialDamping, SpatialStiffDefault)
    fun <T> spatialSlow(): SpringSpec<T> = spring(SpatialDamping, SpatialStiffSlow)

    fun <T> effectsFast(): SpringSpec<T> = spring(EffectsDamping, EffectsStiffFast)
    fun <T> effectsDefault(): SpringSpec<T> = spring(EffectsDamping, EffectsStiffDefault)
    fun <T> effectsSlow(): SpringSpec<T> = spring(EffectsDamping, EffectsStiffSlow)

    /*
     * Typed variants for spatial units.
     *
     * `spring<T>()` without an explicit visibilityThreshold leaves it null, and for Dp / IntOffset
     * that means the animation settles later than Compose's own defaults would — visibly so on
     * small movements. Use the generic form for Float and Color; use these for anything measured in
     * pixels or dp.
     */
    fun dpSpatialDefault(): SpringSpec<Dp> =
        spring(SpatialDamping, SpatialStiffDefault, Dp.VisibilityThreshold)

    fun dpSpatialFast(): SpringSpec<Dp> =
        spring(SpatialDamping, SpatialStiffFast, Dp.VisibilityThreshold)

    fun offsetSpatialDefault(): SpringSpec<IntOffset> =
        spring(SpatialDamping, SpatialStiffDefault, IntOffset.VisibilityThreshold)

    fun offsetSpatialSlow(): SpringSpec<IntOffset> =
        spring(SpatialDamping, SpatialStiffSlow, IntOffset.VisibilityThreshold)

    /**
     * Shared enter/exit pairs, so screens do not each invent their own.
     *
     * Content rises a fraction of its own height rather than a fixed dp: the same spec then reads
     * correctly on a bubble and on a bottom sheet.
     *
     * **[reduced] is required, not defaulted** (#842). These used to be plain `val`s with no notion
     * of the user's Reduce Motion / Remove animations preference, so every caller slid content
     * regardless. A defaulted parameter would let a new call site forget the policy just as easily;
     * a required one makes it pass [LocalReducedMotion] or say `false` on purpose. Reduced keeps the
     * fade — a fade is not motion in the vestibular sense, and dropping it too would make content
     * pop in, which is its own small shock — and removes the travel.
     */
    fun enterFadeUp(reduced: Boolean): EnterTransition =
        if (reduced) fadeIn(effectsFast()) else fadeIn(effectsFast()) + slideInVertically(offsetSpatialDefault()) { it / 8 }

    fun exitFadeDown(reduced: Boolean): ExitTransition =
        if (reduced) fadeOut(effectsFast()) else fadeOut(effectsFast()) + slideOutVertically(offsetSpatialDefault()) { it / 8 }

    /** For things that originate from a point — a sent message, a reaction bar, a menu. */
    fun enterPop(reduced: Boolean): EnterTransition =
        if (reduced) fadeIn(effectsFast()) else fadeIn(effectsFast()) + scaleIn(spatialDefault(), initialScale = 0.92f)

    /**
     * The policy-aware spatial spring: under reduced motion a position change *snaps* rather than
     * travels. Effects specs (colour, alpha) have no reduced variant — changing a colour is not
     * motion, and snapping it would only make state changes harsher.
     */
    fun <T> spatialDefault(reduced: Boolean): FiniteAnimationSpec<T> =
        if (reduced) snap() else spatialDefault()

    /**
     * Duration-based specs, only where a spring genuinely cannot express the intent — a crossfade
     * has no physical analogue, and a shimmer sweep is a loop rather than a settle.
     */
    object Duration {
        const val ContentSwapMs: Int = 220
        const val ShimmerSweepMs: Int = 1200
        const val ThemeCrossfadeMs: Int = 300

        /** One half-cycle of the empty-chats illustration's float. Slow enough to read as idle. */
        const val IllustrationFloatMs: Int = 2000
    }

    /** Entering content decelerates hard; leaving content accelerates away. */
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}

/**
 * Whether the user asked the OS for reduced motion (#842). Provided by [MuhabbetTheme] from the
 * platform setting — Android's *Remove animations* (animator duration scale 0), iOS Reduce Motion —
 * and read by every design-system component that moves things or runs a loop.
 *
 * Static, not dynamic: it changes only when the user flips an OS setting, and when it does every
 * animated component genuinely needs to re-decide.
 *
 * Defaults to `false` outside a theme so a component in a bare preview still animates.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/**
 * A slow scale pulse, for something that is waiting on someone else.
 *
 * Used on the avatar while a call rings or connects. The point is not decoration: a completely still
 * screen during the seconds between dialling and connecting gives no signal that anything is
 * happening, and that is exactly when a user starts wondering whether to hang up and try again.
 *
 * Deliberately small (a few percent) and slow. A pulse you consciously notice is worse than none —
 * it competes with the two buttons that matter. There is no haptic attached: repeating haptics are
 * the fastest way to make a phone feel broken, and this repeats for as long as the call rings.
 *
 * Inert under [LocalReducedMotion].
 *
 * @param enabled when false the modifier is inert, so a caller can stop the pulse the moment the
 *   call connects without swapping modifier chains.
 */
@Composable
fun Modifier.breathing(enabled: Boolean = true): Modifier {
    // Reduced motion: still. The pulse is a "waiting" signal, but the call screen also says so in
    // words, and a looping scale is precisely what a vestibular-sensitive user turned motion off for.
    if (!enabled || LocalReducedMotion.current) return this
    val transition = rememberInfiniteTransition(label = "breathing")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = BreathingPeak,
        animationSpec = infiniteRepeatable(
            animation = tween(BreathingPeriodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingScale"
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

private const val BreathingPeak = 1.04f
private const val BreathingPeriodMs = 1400
