package com.muhabbet.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.attach_document
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import org.jetbrains.compose.resources.stringResource

/**
 * A file attachment: an icon, the file name, and a tap that hands the file to whatever the platform
 * uses to open it.
 *
 * Lives in its own file for the same reason [VoiceBubble], [LocationBubble] and [PollBubble] do —
 * one bubble kind, one file — and so that giving it a working tap does not push `MessageBubble`
 * further past the 300-line composable limit it is already over.
 *
 * @param onOpen invoked with the tap; the caller owns the URL and the failure message, because the
 *   snackbar host lives on the screen, not on the bubble.
 */
@Composable
fun DocumentBubble(
    fileName: String,
    onBubbleColor: Color,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = onBubbleColor.copy(alpha = 0.1f),
        modifier = modifier.clickable { onOpen() }
    ) {
        Row(
            modifier = Modifier.padding(MuhabbetSizes.AttachmentPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Muhabbet.icons.Document,
                contentDescription = stringResource(Res.string.attach_document),
                modifier = Modifier.size(MuhabbetSizes.IconAttachment),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(MuhabbetSpacing.Small))
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodySmall,
                color = onBubbleColor,
                maxLines = 2
            )
        }
    }
}
