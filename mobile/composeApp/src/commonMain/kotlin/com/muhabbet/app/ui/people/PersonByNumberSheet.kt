package com.muhabbet.app.ui.people

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.muhabbet.app.data.repository.PhoneLookupResult
import com.muhabbet.app.platform.rememberShareLauncher
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.normalizeToE164
import com.muhabbet.app.util.runCatchingCancellable
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.designsystem.components.MuhabbetBottomSheet
import com.muhabbet.designsystem.components.MuhabbetButton
import com.muhabbet.designsystem.components.MuhabbetButtonRole
import com.muhabbet.designsystem.components.MuhabbetTextField
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Reaching a person by typing their phone number.
 *
 * The chrome — field, validation, busy label, and the three "nothing happened" endings — lives here
 * once; what a hit *means* is the caller's business. Starting a chat (#389) and putting somebody
 * into a group (#520) are the same lookup with two different endings, and the second was written as
 * a wall reading "grant contacts access" precisely because there was no shared piece to reuse.
 *
 * A sheet rather than a screen because it owns no navigation of its own.
 *
 * **Reports inline, never through the snackbar.** A `ModalBottomSheet` draws above the host
 * scaffold, so a snackbar raised from here appears *behind* the sheet and its scrim — the user taps,
 * nothing visibly happens, and the app has grown one more control that looks functional and is not.
 * Reporting in the sheet also means there is no spinner racing a suspending `showSnackbar` (the
 * four-second stall PR #390 removed nineteen of); the button carries its own busy label and no
 * separate progress indicator exists to leave spinning.
 *
 * @param onSubmit runs the lookup for an already-normalised E.164 number and does whatever the
 *   caller wants with a hit, returning the outcome so this sheet can report the endings that need no
 *   caller. A caller that acted on [PhoneLookupResult.Opened] or [PhoneLookupResult.Found] owns the
 *   dismissal too: navigating away cancels this sheet's scope, so anything queued after it would
 *   never run.
 * @param notice something only the caller can know about the person just found — "already in this
 *   group", above all. Without it a hit the caller declines to use is a tap where nothing at all
 *   happens, which is the failure this whole area is being fixed for.
 * @param onInputChanged called when the field is edited, so the caller can drop a [notice] that no
 *   longer describes what is typed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonByNumberSheet(
    title: String,
    actionLabel: String,
    onDismiss: () -> Unit,
    onSubmit: suspend (e164: String) -> PhoneLookupResult,
    notice: String? = null,
    onInputChanged: () -> Unit = {},
) {
    var input by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<PhoneLookupResult?>(null) }
    var failed by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val shareLauncher = rememberShareLauncher()

    // Resolved here rather than inside scope.launch: stringResource is @Composable.
    val inviteText = stringResource(Res.string.start_by_number_invite_text)

    // Derived, not stored: a second copy of the field's validity is a second thing to keep in sync,
    // and the one that goes stale is always the one the button reads.
    val normalized = normalizeToE164(input)
    val canSubmit = normalized != null && !isSearching

    MuhabbetBottomSheet(onDismiss = onDismiss) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(MuhabbetSpacing.Small))

        Text(
            text = stringResource(Res.string.start_by_number_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(MuhabbetSpacing.Large))

        MuhabbetTextField(
            value = input,
            onValueChange = {
                input = it
                // Any edit invalidates the previous answer — leaving "not on Muhabbet" under a
                // number the user has since corrected is the same lie as showing it too early.
                outcome = null
                failed = false
                onInputChanged()
            },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(Res.string.start_by_number_field_label),
            placeholder = stringResource(Res.string.start_by_number_field_placeholder),
            // Shown against the field, not as a snackbar, and only once there is something to
            // reject — an empty field the user has not filled in yet is not an error.
            error = if (input.isNotBlank() && normalized == null) {
                stringResource(Res.string.start_by_number_invalid)
            } else null,
            enabled = !isSearching,
            keyboardType = KeyboardType.Phone,
        )

        Spacer(Modifier.height(MuhabbetSpacing.Large))

        MuhabbetButton(
            text = if (isSearching) {
                stringResource(Res.string.start_by_number_searching)
            } else {
                actionLabel
            },
            onClick = {
                // Guarded as well as disabled. `enabled` governs the tap; this governs the call, and
                // the two are read at different moments.
                val number = normalized ?: return@MuhabbetButton
                outcome = null
                failed = false
                isSearching = true
                scope.launch {
                    val result = runCatchingCancellable { onSubmit(number) }
                    isSearching = false
                    result
                        .onSuccess { outcome = it }
                        .onFailure { error ->
                            // ApiClient throws on any non-2xx since #374. Surfacing it is the point:
                            // a rejected lookup rendered as "not on Muhabbet" would invite the user
                            // to invite somebody who is already here.
                            Log.e(TAG, "Phone-number lookup failed", error)
                            failed = true
                        }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            role = MuhabbetButtonRole.Primary,
            enabled = canSubmit,
        )

        if (failed) {
            Spacer(Modifier.height(MuhabbetSpacing.Large))
            Text(
                text = stringResource(Res.string.error_generic),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (notice != null) {
            Spacer(Modifier.height(MuhabbetSpacing.Large))
            Text(
                text = notice,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        when (outcome) {
            PhoneLookupResult.NotOnMuhabbet -> {
                Spacer(Modifier.height(MuhabbetSpacing.Large))
                Text(
                    text = stringResource(Res.string.start_by_number_not_registered),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(MuhabbetSpacing.Medium))
                MuhabbetButton(
                    text = stringResource(Res.string.start_by_number_invite),
                    onClick = { shareLauncher(inviteText) },
                    modifier = Modifier.fillMaxWidth(),
                    role = MuhabbetButtonRole.Secondary,
                )
            }

            PhoneLookupResult.OwnNumber -> {
                Spacer(Modifier.height(MuhabbetSpacing.Large))
                Text(
                    text = stringResource(Res.string.start_by_number_own_number),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // InvalidNumber cannot arrive here — the button is disabled until the number
            // normalises — and the two hits are the caller's to act on, which it has already done by
            // the time this recomposes. The branches are spelled out so a future result type has to
            // be handled rather than falling into a silent else.
            PhoneLookupResult.InvalidNumber,
            is PhoneLookupResult.Opened,
            is PhoneLookupResult.Found,
            null -> Unit
        }

        Spacer(Modifier.height(MuhabbetSpacing.XLarge))
    }
}

private const val TAG = "PersonByNumberSheet"
