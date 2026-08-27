package com.muhabbet.app.ui.communities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.designsystem.components.MuhabbetDialog
import com.muhabbet.designsystem.components.MuhabbetTextField
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import org.jetbrains.compose.resources.stringResource

/**
 * Rename a community and rewrite its description.
 *
 * The description is sent as-is, including empty: the endpoint replaces rather than merges, so a
 * user who clears the field is clearing it. A blank name is refused here as well as on the server —
 * the confirm button simply does not enable — because a round trip to be told the name is empty is
 * a worse way to learn it.
 *
 * The server can also refuse a name this account already used (#446), which is not something the
 * confirm button can predict — so [nameError] comes back from the caller after the save fails and is
 * shown against the name field. The dialog stays open in that case: the whole point is that the user
 * edits the name and tries again, and a dialog that closed first would leave them re-opening it with
 * no idea which field was wrong.
 *
 * @param nameError message to show under the name field, or `null` when there is nothing to say.
 * @param onNameEdited called when the user changes the name while [nameError] is showing, so the
 * caller can clear a complaint that no longer describes what is in the field.
 * @param onConfirm receives the new name and the new description, `null` when it was left empty.
 */
@Composable
fun EditCommunityDialog(
    initialName: String,
    initialDescription: String?,
    onConfirm: (name: String, description: String?) -> Unit,
    onDismiss: () -> Unit,
    nameError: String? = null,
    onNameEdited: () -> Unit = {}
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription.orEmpty()) }

    MuhabbetDialog(
        title = stringResource(Res.string.community_edit_title),
        onDismiss = onDismiss,
        dismissLabel = stringResource(Res.string.cancel),
        confirmLabel = stringResource(Res.string.save),
        confirmEnabled = name.isNotBlank(),
        onConfirm = { onConfirm(name.trim(), description.trim().ifBlank { null }) },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Medium)) {
                MuhabbetTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (nameError != null) onNameEdited()
                    },
                    placeholder = stringResource(Res.string.community_name_hint),
                    error = nameError,
                    singleLine = true
                )
                MuhabbetTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = stringResource(Res.string.community_description_hint),
                    singleLine = false
                )
            }
        }
    )
}
