package com.muhabbet.designsystem.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * What became of a [MuhabbetSnackbarState.showUndo] offer.
 *
 * Two outcomes, named for what the caller must do next rather than for what the snackbar did:
 * Material's `ActionPerformed`/`Dismissed` describe a widget, and every caller then has to translate
 * them back into "roll it back" or "keep it" — which is where an inverted `if` hides.
 */
enum class MuhabbetUndoResult {
    /** The user pressed Undo before the snackbar went away. Reverse the action. */
    Undone,

    /** The snackbar timed out, was swiped away or was replaced. The action stands. */
    Kept,
}

/**
 * The design system's snackbar channel — a screen's one way to say something transient (#844).
 *
 * Owns Material's `SnackbarHostState` instead of handing it to every screen: the 26 screens that
 * built their own had no way to offer an action, and an app with no undo makes every destructive
 * action a confirmation dialog. [showUndo] is the undo-first half of the API; [show] is a plain
 * message. Neither takes a duration — that is a property of what is being said (an undo offer must
 * stay long enough to reach), not something each caller should tune.
 *
 * Get one from [rememberMuhabbetSnackbarState] and hand it to [MuhabbetScaffold], or, inside a
 * scaffold's content, read the scaffold's own through [LocalMuhabbetSnackbar].
 */
@Stable
class MuhabbetSnackbarState internal constructor() {

    /** Rendered by [MuhabbetScaffold]'s host. Never exposed: its type is Material's. */
    internal val host = SnackbarHostState()

    /** Shows [message] and suspends until it is dismissed. */
    suspend fun show(message: String) {
        host.showSnackbar(message = message, duration = SnackbarDuration.Short)
    }

    /**
     * Shows [message] with an [undoLabel] action and suspends until it goes away.
     *
     * Long rather than short: an undo the user cannot reach in time is not an undo. Reverse the
     * action on [MuhabbetUndoResult.Undone]; do nothing on [MuhabbetUndoResult.Kept].
     */
    suspend fun showUndo(message: String, undoLabel: String): MuhabbetUndoResult {
        val result = host.showSnackbar(
            message = message,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Long
        )
        return if (result == SnackbarResult.ActionPerformed) MuhabbetUndoResult.Undone else MuhabbetUndoResult.Kept
    }
}

/** A [MuhabbetSnackbarState] for one screen. */
@Composable
fun rememberMuhabbetSnackbarState(): MuhabbetSnackbarState = remember { MuhabbetSnackbarState() }

/**
 * The enclosing [MuhabbetScaffold]'s snackbar, so a section several composables deep can report
 * without the state being threaded through every signature in between.
 *
 * Reading it outside a scaffold is a programming error, and fails loudly rather than handing back
 * a host nothing renders — a message shown into the void is the silent failure this exists to end.
 */
val LocalMuhabbetSnackbar = staticCompositionLocalOf<MuhabbetSnackbarState> {
    error("LocalMuhabbetSnackbar read outside a MuhabbetScaffold")
}
