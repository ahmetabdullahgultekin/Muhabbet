package com.muhabbet.designsystem.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * The screen frame: app bar, snackbar host, optional FAB, and one correct inset policy.
 *
 * Wraps Material's `Scaffold` rather than replacing it, so the 27 existing call sites keep their
 * shape — all 27 already use the `padding` the content lambda is handed, so nothing changes about
 * how content is laid out.
 *
 * Two things it fixes by existing:
 *
 *  - **Insets live in one place.** Since `targetSdk` 36 the app draws behind the system bars whether
 *    it asks to or not, and getting that right per screen is how six screens ended up with no inset
 *    handling at all.
 *  - **Every screen gets a snackbar host.** Six of the 27 had none, and those six are *exactly* the
 *    six that fall back to rendering errors as inline `Text` — an error surface that cannot be
 *    dismissed and that the screen then has no way to recover from. Supplying the host by default
 *    removes the reason that fallback existed.
 *
 * @param snackbarHostState pass one in to show snackbars; omit it and a host is still created, so a
 *   screen can adopt error reporting later without restructuring.
 */
@Composable
fun MuhabbetScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        content = content
    )
}
