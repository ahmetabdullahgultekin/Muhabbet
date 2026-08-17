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
 * When [onViewPhoto] is given, the avatar itself becomes a second, independent tap zone that opens
 * the photo full-screen (the same `MediaViewer` a chat photo gets — #615) instead of the picker. The
 * badge can no longer share the whole box the way it used to: two actions on one region can only
 * resolve arbitrarily, so the badge is carved out into its own bounded hit area first, sized to
 * [MuhabbetSizes.MinTouchTarget] even though the glyph inside it is smaller — the earlier "whole box"
 * design existed because a bare, unsized badge tap was landing on the plain Surface beneath it and
 * doing nothing; giving the badge an explicit, correctly-sized region fixes that same failure mode
 * without needing to keep the rest of the circle wired to the same handler. [onViewPhoto] is left
 * null, not called, when there is no photo — a full-screen view of the name-seeded gradient fallback
 * is not worth a navigation.
 *
 * Used by both the user's own profile and a group's, which is why it lives here: the two had drifted
 * into byte-identical copies of this layout.
 *
 * @param changePhotoContentDescription what a screen reader announces for the badge. Supplied by the
 *   caller rather than read from resources here: this module has no string resources, and "change
 *   your photo" and "change the group photo" are not the same sentence.
 * @param isUploading swaps the badge for a spinner and disables the tap, so a second pick cannot
 *   race the first.
 * @param onViewPhoto opens the current photo full-screen. Null keeps this component exactly as it
 *   was before #615 — pure "tap anywhere to change" — for any caller not yet wired to a viewer.
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
    avatarContentDescription: String? = null,
    onViewPhoto: (() -> Unit)? = null
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        UserAvatar(
            avatarUrl = avatarUrl,
            displayName = displayName,
            size = size,
            isGroup = isGroup,
            contentDescription = avatarContentDescription,
            modifier = if (onViewPhoto != null && avatarUrl != null) {
                Modifier.clickable(onClick = onViewPhoto)
            } else {
                Modifier
            }
        )
        Box(
            modifier = Modifier
                .size(MuhabbetSizes.MinTouchTarget)
                .align(Alignment.BottomEnd)
                .clickable(enabled = !isUploading) { onPickPhoto() },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.size(MuhabbetSizes.IconAttachment),
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
}
