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
 * @param onConfirm receives the new name and the new description, `null` when it was left empty.
 */
@Composable
fun EditCommunityDialog(
    initialName: String,
    initialDescription: String?,
    onConfirm: (name: String, description: String?) -> Unit,
    onDismiss: () -> Unit
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
                    onValueChange = { name = it },
                    placeholder = stringResource(Res.string.community_name_hint),
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
