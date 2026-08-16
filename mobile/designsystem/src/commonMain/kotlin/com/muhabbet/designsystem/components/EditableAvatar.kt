package com.muhabbet.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.muhabbet.designsystem.theme.MuhabbetSizes

/**
 * An avatar that is also the control for replacing it: a camera badge over the bottom-right corner
 * that turns into a spinner while the new photo uploads.
 *
 * The click sits on the whole Box, not just the badge. A tap that landed on the badge — the part
 * that looks like the button, and the node carrying the description a screen reader is told to
 * activate — otherwise hits the Surface and does nothing at all.
 *
 * Used by both the user's own profile and a group's, which is why it lives here: the two had drifted
 * into byte-identical copies of this layout.
 *
 * @param changePhotoContentDescription what a screen reader announces for the badge. Supplied by the
 *   caller rather than read from resources here: this module has no string resources, and "change
 *   your photo" and "change the group photo" are not the same sentence.
 * @param isUploading swaps the badge for a spinner and disables the tap, so a second pick cannot
 *   race the first.
 */
@Composable
fun EditableAvatar(
    avatarUrl: String?,
    displayName: String,
    size: Dp,
    changePhotoContentDescription: String,
    onPickPhoto: () -> Unit,
    modifier: Modifier = Modifier,
    isUploading: Boolean = false,
    isGroup: Boolean = false,
    avatarContentDescription: String? = null
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.clickable(enabled = !isUploading) { onPickPhoto() }
    ) {
        UserAvatar(
            avatarUrl = avatarUrl,
            displayName = displayName,
            size = size,
            isGroup = isGroup,
            contentDescription = avatarContentDescription
        )
        Surface(
            modifier = Modifier
                .size(MuhabbetSizes.IconAttachment)
                .align(Alignment.BottomEnd),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(MuhabbetSizes.IconSmall),
                        strokeWidth = MuhabbetSizes.ProgressStrokeInline,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = changePhotoContentDescription,
                        modifier = Modifier.size(MuhabbetSizes.IconSmall),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}
