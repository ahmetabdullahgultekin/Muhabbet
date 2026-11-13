package com.muhabbet.designsystem.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import com.muhabbet.designsystem.theme.LocalSemanticColors
import com.muhabbet.designsystem.theme.MuhabbetComponentSizes
import com.muhabbet.designsystem.theme.MuhabbetCorners
import com.muhabbet.designsystem.theme.MuhabbetIcons
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * The one search field: a pill with a search glyph, a clear button once there is something to clear,
 * an optional working spinner, and the debounce / minimum-length policy built in.
 *
 * Five screens hand-rolled this (#847), and each re-implemented the policy slightly differently —
 * one searched on every keystroke, one waited for two characters but not after trimming, one never
 * cleared its results when the field was emptied. The policy is now [effectiveSearchQuery] plus
 * the debounce below, in one place:
 *
 * - [onQueryChange] fires on **every** keystroke — the field is controlled, the text is the
 *   caller's state.
 * - [onSearch] fires with the **effective** query: trimmed, or `""` when it is shorter than
 *   [minQueryLength]. It is debounced by [debounce], and only fires when the effective query
 *   actually changes — typing a trailing space does not refetch.
 * - Going *to* `""` (cleared, or backspaced under the minimum) fires immediately: there is nothing
 *   to fetch, and stale results should go now, not 300ms from now.
 * - The keyboard's search key fires immediately, bypassing the debounce.
 * - Nothing fires on first composition. Opening a search screen with a restored query is not a
 *   new search.
 *
 * @param placeholder what the empty field says. Caller-supplied: the module carries no strings.
 * @param clearContentDescription TalkBack label for the clear button. Required rather than
 *   defaulted, because a default would have to be a string.
 * @param loading shows a small spinner in the trailing slot while results are being fetched.
 * @param autoFocus request focus on first composition — for a screen whose only job is search.
 */
@Composable
fun MuhabbetSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    placeholder: String,
    clearContentDescription: String,
    modifier: Modifier = Modifier,
    minQueryLength: Int = 0,
    debounce: Duration = DefaultSearchDebounce,
    loading: Boolean = false,
    enabled: Boolean = true,
    autoFocus: Boolean = false
) {
    val currentOnSearch by rememberUpdatedState(onSearch)
    var lastEmitted by remember { mutableStateOf(effectiveSearchQuery(query, minQueryLength)) }
    val effective = effectiveSearchQuery(query, minQueryLength)

    LaunchedEffect(effective) {
        if (effective == lastEmitted) return@LaunchedEffect
        if (effective.isNotEmpty()) delay(debounce)
        // Re-checked after the wait: the search key may have already sent this exact query while
        // the debounce was running, and sending it twice is a duplicate request.
        if (effective == lastEmitted) return@LaunchedEffect
        lastEmitted = effective
        currentOnSearch(effective)
    }

    val focusRequester = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }

    val field = LocalSemanticColors.current.inputField
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MuhabbetComponentSizes.SearchFieldMinHeight)
            .focusRequester(focusRequester),
        enabled = enabled,
        singleLine = true,
        placeholder = { Text(placeholder, maxLines = 1) },
        leadingIcon = { Icon(MuhabbetIcons.Search, contentDescription = null) },
        trailingIcon = if (query.isEmpty() && !loading) {
            null
        } else {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (loading) MuhabbetSpinner(size = MuhabbetSpinnerSize.Small)
                    if (query.isNotEmpty()) {
                        MuhabbetIconButton(
                            icon = MuhabbetIcons.Close,
                            contentDescription = clearContentDescription,
                            onClick = { onQueryChange("") },
                            enabled = enabled
                        )
                    }
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = keyboardActionsFor(ImeAction.Search) {
            lastEmitted = effective
            currentOnSearch(effective)
        },
        shape = RoundedCornerShape(MuhabbetCorners.Pill),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = field.container,
            unfocusedContainerColor = field.container,
            disabledContainerColor = field.container,
            focusedTextColor = field.content,
            unfocusedTextColor = field.content,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

/**
 * The query a search should actually run for [raw]: trimmed, or `""` ("no search") when the trimmed
 * text is shorter than [minLength].
 *
 * Pure and public so a screen's own non-field search trigger (a deep link, a restored state) applies
 * the identical rule instead of a sixth copy of it.
 */
fun effectiveSearchQuery(raw: String, minLength: Int): String {
    val trimmed = raw.trim()
    return if (trimmed.length < minLength.coerceAtLeast(1)) "" else trimmed
}

/** Long enough that a typist's burst is one request; short enough that a pause feels answered. */
val DefaultSearchDebounce: Duration = 300.milliseconds
