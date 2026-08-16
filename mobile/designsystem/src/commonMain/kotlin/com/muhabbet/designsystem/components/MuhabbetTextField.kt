package com.muhabbet.designsystem.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

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
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        prefix = prefix?.let { { Text(it) } },
        isError = error != null,
        supportingText = error?.let {
            { Text(text = it, color = MaterialTheme.colorScheme.error) }
        },
        singleLine = singleLine,
        maxLines = maxLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction)
    )
}
