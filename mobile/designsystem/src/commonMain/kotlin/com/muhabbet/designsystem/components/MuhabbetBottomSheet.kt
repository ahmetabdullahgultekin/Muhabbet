package com.muhabbet.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.muhabbet.designsystem.theme.MuhabbetCorners
import com.muhabbet.designsystem.theme.MuhabbetDepth
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.designsystem.theme.containerColor
import com.muhabbet.designsystem.theme.depth

/**
 * The one bottom sheet.
 *
 * Four call sites each remembered their own sheet state and three of them opened with a byte-identical
 * `ModalBottomSheet(onDismissRequest, sheetState) { Column(Modifier.padding(XLarge)) { … } }`.
 * The state and the padding live here now, which also gives the sheet's insets, shape and drag
 * handle a single place to be decided when the brand lands.
 *
 * Top corners are [MuhabbetCorners.Large] — a little tighter than M3's own 28dp default, matching
 * [MuhabbetDialog]'s corner so the app's two modal surfaces read as one family — and the container
 * takes [MuhabbetDepth.Overlay]'s tone and edge treatment, the same level [MuhabbetDepth] itself
 * already names bottom sheets as an example of. Its own slide-in stays the platform default: unlike
 * a `Surface`, `ModalBottomSheet` owns its drag/anchor animation internally, and there is no seam to
 * hook a [com.muhabbet.designsystem.theme.MuhabbetMotion] spring into without reimplementing
 * anchored dragging from scratch — a materially bigger, riskier change for a surface this host
 * cannot render to verify.
 *
 * @param controller only worth passing when the caller drives the sheet itself — the GIF picker and
 *   the What's-new sheet animate it closed before reporting, so they hold their own. Everyone else
 *   takes the default.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuhabbetBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(MuhabbetSpacing.XLarge),
    controller: MuhabbetSheetController = rememberMuhabbetSheetController(),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(topStart = MuhabbetCorners.Large, topEnd = MuhabbetCorners.Large)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = controller.state,
        modifier = modifier.depth(MuhabbetDepth.Overlay, shape),
        shape = shape,
        containerColor = MuhabbetDepth.Overlay.containerColor()
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/**
 * A handle on an open [MuhabbetBottomSheet], for the callers that close it themselves (#844).
 *
 * Wraps Material's `SheetState` rather than exposing it, so a screen can animate its sheet away
 * without naming a Material type — and without reaching for `show`, `expand` or the anchor API,
 * none of which any sheet in the app has a use for.
 */
@Stable
class MuhabbetSheetController @OptIn(ExperimentalMaterial3Api::class) internal constructor(
    internal val state: SheetState
) {
    /**
     * Animates the sheet off screen and returns once it is gone. Report the outcome *after* this,
     * not instead of it: removing the sheet from the composition skips the animation, and the sheet
     * vanishes while its scrim is still fading.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    suspend fun hide() = state.hide()
}

/**
 * @param skipPartiallyExpanded for a sheet with a fixed-height body, where a half-open state would
 *   just clip it. The GIF picker is the only one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberMuhabbetSheetController(skipPartiallyExpanded: Boolean = false): MuhabbetSheetController {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded)
    return remember(state) { MuhabbetSheetController(state) }
}
