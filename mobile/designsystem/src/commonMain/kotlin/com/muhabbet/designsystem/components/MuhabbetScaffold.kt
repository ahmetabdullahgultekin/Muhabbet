package com.muhabbet.designsystem.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.muhabbet.designsystem.theme.MuhabbetCorners
import com.muhabbet.designsystem.theme.MuhabbetDepth
import com.muhabbet.designsystem.theme.depth

/**
 * The screen frame: app bar, snackbar host, optional FAB, and one correct inset policy.
 *
 * Wraps Material's `Scaffold` rather than replacing it, so the existing call sites keep their
 * shape — all of them already use the `padding` the content lambda is handed, so nothing changes
 * about how content is laid out. No Material type crosses this signature (#844).
 *
 * Four things it fixes by existing:
 *
 *  - **Insets live in one place.** Since `targetSdk` 36 the app draws behind the system bars whether
 *    it asks to or not, and getting that right per screen is how six screens ended up with no inset
 *    handling at all.
 *  - **Every screen gets a snackbar host.** Six of the 27 had none, and those six are *exactly* the
 *    six that fell back to rendering errors as inline `Text` — an error surface that cannot be
 *    dismissed. Supplying the host by default removes the reason that fallback existed, and
 *    [LocalMuhabbetSnackbar] lets content deep in the tree reach it.
 *  - **Every scrolling screen gets the top bar's scroll lift** (#845). The scaffold owns a
 *    [MuhabbetTopBarScroll], listens to its content's nested scroll, and provides the state to the
 *    bar slot, where [MuhabbetTopBar] picks it up. No screen wires a scroll behaviour by hand — the
 *    31 bars that could have and none that did are why this lives here.
 *  - **The snackbar has a shape and depth.** [MuhabbetDepth.Overlay]'s treatment and a 12dp corner,
 *    so the app's every error and confirmation toast shares one shape with its dialogs and sheets.
 *
 * @param snackbarState pass one in to show snackbars from outside the content lambda; omit it and a
 *   host is still created, reachable from the content through [LocalMuhabbetSnackbar].
 * @param topBarScroll only for a screen that needs to read or own its bar's scroll state; everyone
 *   else takes the default.
 */
@Composable
fun MuhabbetScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarState: MuhabbetSnackbarState = rememberMuhabbetSnackbarState(),
    topBarScroll: MuhabbetTopBarScroll = rememberMuhabbetTopBarScroll(),
    content: @Composable (PaddingValues) -> Unit
) {
    CompositionLocalProvider(
        LocalMuhabbetSnackbar provides snackbarState,
        LocalMuhabbetTopBarScroll provides topBarScroll
    ) {
        Scaffold(
            modifier = modifier.nestedScroll(topBarScroll.nestedScrollConnection),
            topBar = topBar,
            bottomBar = bottomBar,
            floatingActionButton = floatingActionButton,
            snackbarHost = {
                SnackbarHost(snackbarState.host) { data ->
                    val shape = RoundedCornerShape(MuhabbetCorners.Medium)
                    Snackbar(
                        snackbarData = data,
                        shape = shape,
                        modifier = Modifier.depth(MuhabbetDepth.Overlay, shape)
                    )
                }
            },
            content = content
        )
    }
}
