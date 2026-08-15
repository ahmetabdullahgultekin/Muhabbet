package com.muhabbet.app.navigation

import androidx.compose.foundation.gestures.Orientation
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.stack.animation.StackAnimation
import com.arkivanov.decompose.extensions.compose.stack.animation.predictiveback.materialPredictiveBackAnimatable
import com.arkivanov.decompose.extensions.compose.stack.animation.predictiveback.predictiveBackAnimation
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.plus
import com.arkivanov.decompose.extensions.compose.stack.animation.scale
import com.arkivanov.decompose.extensions.compose.stack.animation.slide
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.essenty.backhandler.BackHandler
import com.muhabbet.designsystem.theme.MuhabbetMotion

/*
 * The app's navigation motion — the last surface still running stock curves.
 *
 * Everything *inside* a screen moves on MuhabbetMotion springs. Between screens, all three
 * `Children` call sites used Decompose's out-of-the-box `fade()` / `slide()` with their default
 * tween. The seam was visible: a bubble settles like it has mass, then the whole screen it lives on
 * slides in like a slideshow.
 *
 * ## Why this file is in composeApp and not in :mobile:designsystem
 *
 * `StackAnimation` is a Decompose type, and the design-system module has exactly two hard rules, one
 * of which is that it knows nothing about navigation. So the split is: the *physics* is imported
 * from the library (`MuhabbetMotion`), the *plumbing* lives here. That keeps the module boundary
 * intact while still giving navigation the same springs as everything else — which was the whole
 * point.
 *
 * ## Why a file rather than two inline expressions
 *
 * Same reason `MuhabbetTopBarDefaults` exists: three call sites drifting apart is how the app ended
 * up with top bars in three different colours. `verifyUi`'s `rawStackAnimation` rule holds this at
 * zero call sites elsewhere.
 */

/**
 * Push and pop within a stack: Material's shared-axis X, expressed with our own springs.
 *
 * Three animators composed, each doing one job:
 *  - `slide` carries the spatial meaning — forward goes left, back goes right.
 *  - `fade` stops the outgoing screen from reading as a hard-edged card being dragged off. It floors
 *    at [OutgoingMinAlpha] rather than 0 so the screen behind stays legible through the whole
 *    transition; fading fully to transparent leaves a moment of bare window that reads as a flicker.
 *  - `scale` pushes the back child slightly away. This is what turns "two screens sliding" into
 *    "one screen on top of another", and it is most of the perceived expense.
 *
 * `spatialSlow` (stiffness 200) is the right family here: full-screen travel is the longest distance
 * anything moves in this app, and the faster springs arrive before the eye has followed them.
 *
 * [OutgoingMinAlpha] and [BackChildScale] are the two numbers to tune on a device. They are here,
 * once, precisely so that tuning is a one-line change rather than an archaeology exercise.
 */
fun <C : Any, T : Any> sharedAxisX(): StackAnimation<C, T> = stackAnimation(
    animator = slide(animationSpec = MuhabbetMotion.spatialSlow(), orientation = Orientation.Horizontal) +
        fade(animationSpec = MuhabbetMotion.effectsSlow(), minAlpha = OutgoingMinAlpha) +
        scale(animationSpec = MuhabbetMotion.spatialSlow(), frontFactor = 1f, backFactor = BackChildScale),
    disableInputDuringAnimation = true
)

/**
 * Auth ↔ Main. A cross-fade, deliberately — not the shared axis above.
 *
 * `RootComponent` only ever calls `replaceAll`, so these two children are not in a
 * forward/backward relationship: logging in does not take you "deeper", and logging out does not
 * take you "back". A horizontal slide would assert a hierarchy that does not exist, and the user
 * would read it as a screen they can swipe away.
 */
fun <C : Any, T : Any> rootFade(): StackAnimation<C, T> = stackAnimation(
    animator = fade(animationSpec = MuhabbetMotion.effectsSlow()),
    disableInputDuringAnimation = true
)

/**
 * [sharedAxisX], but the user can also drag it with a back gesture.
 *
 * On Android 14+ this is what makes a back swipe show the screen underneath *while the finger is
 * still down*, so the gesture is a preview you can abandon rather than a command you have already
 * issued. Where the platform does not support it — Android 13 and below, and iOS — the animation
 * falls back to plain [sharedAxisX] and nothing regresses.
 *
 * `materialPredictiveBackAnimatable` rather than `androidPredictiveBackAnimatableV1`/`V2`: those two
 * imitate the Android 13 and Android 14 system look specifically, and this app has its own. One
 * animatable, one feel, on every platform.
 *
 * Not applied to `RootComponent`. That stack is one deep by construction — it only ever calls
 * `replaceAll` — so a back gesture there should leave the app, not animate between Auth and Main.
 *
 * @param onBack invoked when the gesture completes. It, not the stack's own `handleBackButton`,
 *   performs the pop: Essenty dispatches a back event to a single callback rather than broadcasting,
 *   and this one is registered later, so it takes precedence.
 */
@OptIn(ExperimentalDecomposeApi::class)
fun <C : Any, T : Any> predictiveBack(
    backHandler: BackHandler,
    onBack: () -> Unit
): StackAnimation<C, T> = predictiveBackAnimation(
    backHandler = backHandler,
    fallbackAnimation = sharedAxisX(),
    selector = { initialBackEvent, _, _ -> materialPredictiveBackAnimatable(initialBackEvent) },
    onBack = onBack
)

/**
 * How far the leaving screen fades. Not to zero — see [sharedAxisX].
 */
private const val OutgoingMinAlpha = 0.4f

/**
 * How far the screen underneath recedes. Deliberately shallow: Decompose's own default is 0.7,
 * which at full-screen size reads as the app zooming out rather than as depth.
 */
private const val BackChildScale = 0.94f
