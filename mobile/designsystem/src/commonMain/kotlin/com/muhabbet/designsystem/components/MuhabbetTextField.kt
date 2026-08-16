package com.muhabbet.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.muhabbet.designsystem.theme.MuhabbetCorners
import com.muhabbet.designsystem.theme.MuhabbetMotion
import com.muhabbet.designsystem.theme.MuhabbetSizes

/**
 * A single-line text field with a label or a placeholder and, when something is wrong, a message.
 *
 * Scoped on purpose. Twenty-eight `OutlinedTextField` call sites vary enough that one wrapper for
 * all of them would need a dozen parameters and would be the god-component this library is supposed
 * to avoid — the OTP boxes, the chat composer and the search fields are each doing something
 * genuinely their own, and they keep doing it. This covers the ordinary case: a labelled field in a
 * form or a dialog.
 *
 * Two things it fixes across those sites:
 *
 * @param error one parameter instead of `isError` plus `supportingText`. Only three of the
 *   twenty-eight wired both, and setting one without the other gives you either a red outline with
 *   no explanation or an explanation with nothing marked — the same half-a-pairing bug the export
 *   row and the status composer each had.
 * @param imeAction defaults to [ImeAction.Done] on a single-line field. Half the fields set no
 *   keyboard options at all, which leaves the return key inserting a newline into a field that
 *   cannot show one. A multi-line field keeps [ImeAction.Default] — there the newline is the point,
 *   and taking it away would be the same mistake in reverse.
 * @param prefix fixed text shown before the value and **not part of it**. The login screen used to
 *   seed the field with `"+90"` as ordinary content, so tapping to the left of it put the caret at
 *   position 0 and typing produced `5000000001+90` — a nonsense number that still looked plausible
 *   because the `+90` was visibly there (#439). A prefix cannot be selected, deleted or typed
 *   before.
 *
 * It was also, until #433, the only thing setting it apart from a bare `OutlinedTextField`: no
 * shape, no colour and no motion of its own, despite being the most-touched control in the app —
 * every one of those twenty-eight call sites rendered the identical Material 3 default every other
 * Android app ships. Three additions, all restrained rather than decorative:
 *
 * - **Its own corner radius** — [MuhabbetCorners.Small], not the ambient theme's `extraSmall` it
 *   would otherwise inherit silently, and distinct from the chat composer's `Pill` field a few
 *   layouts away.
 * - **A focus ring the field draws itself**, not the passive fact that `colorScheme.primary`
 *   happens to already be copper. It fades in on focus (or the instant [error] becomes non-null)
 *   using an Effects spring — damping `1`, so it settles into the ring's colour rather than
 *   overshooting past it into a tone that is not in the palette — and its footprint is reserved at
 *   all times so nothing resizes when focus changes, only the ring's opacity.
 * - **An error state that names its own semantic role.** `errorBorderColor` and `errorCursorColor`
 *   are set explicitly from `colorScheme.error` rather than left to whatever M3's own default
 *   happens to resolve to — the same role the ring and the supporting text already read, so a
 *   future change to the app's error hue has exactly one place to take effect.
 */
@Composable
fun MuhabbetTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
    prefix: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val isError = error != null
    val accentColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    val ringAlpha by animateFloatAsState(
        targetValue = if (enabled && (focused || isError)) FocusRingAlpha else 0f,
        animationSpec = MuhabbetMotion.effectsDefault(),
        label = "textFieldFocusRing"
    )

    Box(
        modifier = modifier
            .border(
                width = MuhabbetSizes.BorderActive,
                color = accentColor.copy(alpha = ringAlpha),
                shape = RoundedCornerShape(MuhabbetCorners.Small + MuhabbetSizes.TextFieldFocusRingSpread)
            )
            .padding(MuhabbetSizes.TextFieldFocusRingSpread)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            label = label?.let { { Text(it) } },
            placeholder = placeholder?.let { { Text(it) } },
            prefix = prefix?.let { { Text(it) } },
            isError = isError,
            supportingText = error?.let {
                { Text(text = it, color = MaterialTheme.colorScheme.error) }
            },
            singleLine = singleLine,
            maxLines = maxLines,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            shape = RoundedCornerShape(MuhabbetCorners.Small),
            interactionSource = interactionSource,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                cursorColor = MaterialTheme.colorScheme.primary,
                errorBorderColor = MaterialTheme.colorScheme.error,
                errorCursorColor = MaterialTheme.colorScheme.error
            )
        )
    }
}

/** How visible the focus ring gets at its most opaque — present, not shouting. */
private const val FocusRingAlpha = 0.35f
