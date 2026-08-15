package com.muhabbet.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.muhabbet.designsystem.theme.MuhabbetSpacing

/**
 * The one bottom sheet.
 *
 * Four call sites each remembered their own sheet state and three of them opened with a byte-identical
 * `ModalBottomSheet(onDismissRequest, sheetState) { Column(Modifier.padding(XLarge)) { … } }`.
 * The state and the padding live here now, which also gives the sheet's insets, shape and drag
 * handle a single place to be decided when the brand lands.
 *
 * @param skipPartiallyExpanded for a sheet with a fixed-height body, where a half-open state would
 *   just clip it. The GIF picker is the only one, and it says so at the call site instead of
 *   reaching for `rememberModalBottomSheetState` itself.
 * @param sheetState only worth passing when the caller drives the sheet itself — the GIF picker
 *   animates it closed before reporting a pick, so it holds its own. Everyone else takes the
 *   default and never names the type.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuhabbetBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    skipPartiallyExpanded: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(MuhabbetSpacing.XLarge),
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded),
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}
