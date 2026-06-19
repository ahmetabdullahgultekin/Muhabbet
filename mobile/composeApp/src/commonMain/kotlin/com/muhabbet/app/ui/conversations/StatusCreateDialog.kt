package com.muhabbet.app.ui.conversations

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muhabbet.app.platform.PickedImage
import com.muhabbet.app.ui.theme.MuhabbetSpacing
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * "Create status" dialog (text + optional photo). State is hoisted into [ConversationListScreen].
 */
@Composable
internal fun StatusCreateDialog(
    statusText: String,
    pickedImage: PickedImage?,
    isUploading: Boolean,
    cancelLabel: String,
    onTextChange: (String) -> Unit,
    onPickImage: () -> Unit,
    onPost: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.status_create_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = statusText,
                    onValueChange = onTextChange,
                    placeholder = { Text(stringResource(Res.string.status_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                Spacer(Modifier.height(MuhabbetSpacing.Small))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onPickImage) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(MuhabbetSpacing.XSmall))
                        Text(stringResource(Res.string.status_add_photo))
                    }
                    if (pickedImage != null) {
                        Text(
                            text = pickedImage.fileName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (isUploading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onPost,
                enabled = (statusText.isNotBlank() || pickedImage != null) && !isUploading
            ) { Text(stringResource(Res.string.status_post)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(cancelLabel) }
        }
    )
}
