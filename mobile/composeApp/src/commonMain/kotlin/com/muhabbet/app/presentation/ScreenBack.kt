package com.muhabbet.app.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import com.arkivanov.essenty.backhandler.BackCallback
import com.arkivanov.essenty.backhandler.BackHandler

/*
 * How back resolves, innermost first (#803).
 *
 * Android convention, and WhatsApp's: back closes the thing the user opened *inside* a screen —
 * search, a reply, an edit, a non-start tab — before it leaves the screen, and leaves the app only
 * from the start destination. Before this file nothing in the app registered a back callback at all,
 * so every one of those modes was skipped and back popped the screen (or closed the app) under it.
 *
 * The order is enforced by Essenty priorities, not by registration order, because registration
 * order is decided by when things compose and is therefore not something a reader can see:
 *
 *  1. App Lock's cover — `PRIORITY_MAX` on the root component (see RootComponent).
 *  2. An in-screen mode — registered on the *destination's own* back handler via
 *     [ScreenBackHandler]. Decompose nests that handler inside the stack's at the default priority.
 *  3. Popping the stack — the predictive-back callback, registered through [belowScreenModes] so
 *     it sits one step below (2) whatever order the two were composed in.
 *  4. Nothing enabled — the platform handles back, which on Android leaves the app.
 */

/** Priority of the stack's own pop, one below every destination's in-screen modes. */
const val STACK_POP_BACK_PRIORITY: Int = BackCallback.PRIORITY_DEFAULT - 1

/**
 * The [BackHandler] the stack's pop must register through: every callback registered here is
 * lowered to [STACK_POP_BACK_PRIORITY].
 *
 * Needed because Decompose's predictive-back animation registers its callback at the default
 * priority from inside composition, i.e. *after* the destinations' handlers, and among equal
 * priorities the last registered wins. Unwrapped, it would pop a chat that has an edit open.
 */
fun BackHandler.belowScreenModes(): BackHandler = PriorityOverridingBackHandler(this, STACK_POP_BACK_PRIORITY)

private class PriorityOverridingBackHandler(
    private val delegate: BackHandler,
    private val priority: Int
) : BackHandler by delegate {
    override fun register(callback: BackCallback) {
        callback.priority = priority
        delegate.register(callback)
    }
}

/**
 * The back handler of the destination being composed. Provided by the navigation host for every
 * child, so a screen that has not yet moved its modes into a component (ADR 0010) can still take
 * part in back resolution.
 */
val LocalScreenBackHandler = staticCompositionLocalOf<BackHandler?> { null }

/**
 * Consumes back while [enabled], calling [onBack] instead of letting the stack pop.
 *
 * For in-screen modes that still live in a composable. A screen migrated to a component registers
 * its `BackCallback` from the component instead, where it can be unit-tested without Compose.
 */
@Composable
fun ScreenBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val backHandler = LocalScreenBackHandler.current ?: return
    val currentOnBack by rememberUpdatedState(onBack)
    val callback = remember(backHandler) { BackCallback(isEnabled = enabled) { currentOnBack() } }
    SideEffect { callback.isEnabled = enabled }
    DisposableEffect(backHandler, callback) {
        backHandler.register(callback)
        onDispose { backHandler.unregister(callback) }
    }
}
