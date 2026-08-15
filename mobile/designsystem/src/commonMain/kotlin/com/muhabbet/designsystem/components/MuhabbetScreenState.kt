package com.muhabbet.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import com.muhabbet.designsystem.theme.MuhabbetIcons
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing

/**
 * A screen that is loading its content.
 *
 * Covers the 24 sites that were each a centred `CircularProgressIndicator` in a `fillMaxSize` Box,
 * in four slightly different spellings. Prefer [MuhabbetSkeletonList] for anything that resolves
 * into a list — a skeleton tells the user what is coming, a spinner only says "wait" — and keep
 * this for waits with no predictable shape.
 *
 * Not to be used for a busy button: that stays an inline indicator sized to the button.
 *
 * @param label optional; two screens showed "Syncing contacts…" under the spinner, which is worth
 *   keeping when the wait has a name.
 */
@Composable
fun MuhabbetLoadingState(
    modifier: Modifier = Modifier,
    label: String? = null
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Large)
        ) {
            CircularProgressIndicator()
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * A screen with nothing to show yet.
 *
 * There were 19 of these: 10 an icon plus a line of text, 7 a bare `Text`, and one already using
 * the illustrated component. The 10 disagreed on icon size (64/48/40dp), on tint (some at 40%
 * alpha, some full), on text style (titleMedium vs bodyLarge), and 9 of 10 left the icon with no
 * content description.
 *
 * More importantly, three of them displayed a *screen title* as the empty message — "Events",
 * "Broadcast Lists", "Communities" — because there was no obvious place for a real one. Making
 * [title] a required parameter is what forces that mistake out.
 *
 * @param action optional call to action. Empty is often a dead end the user could get out of —
 *   "no contacts" wants a sync button — and nothing offered one before.
 */
@Composable
fun MuhabbetEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconContentDescription: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    Box(modifier.fillMaxSize().padding(MuhabbetSpacing.XLarge), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Medium)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    // Usually decorative: the title beneath already says what is missing. Callers
                    // pass a description only when the icon carries meaning the text does not.
                    contentDescription = iconContentDescription,
                    modifier = Modifier.size(MuhabbetSizes.IconEmptyState),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = IconAlpha)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            if (action != null) action()
        }
    }
}

/**
 * A screen whose content failed to load.
 *
 * The ten inline error `Text`s this replaces were all dead ends: none offered a way to try again,
 * so a failed load left the screen permanently empty with a red sentence on it. [onRetry] is
 * optional only because a few failures genuinely are not retryable.
 */
@Composable
fun MuhabbetErrorState(
    message: String,
    modifier: Modifier = Modifier,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null
) {
    Box(modifier.fillMaxSize().padding(MuhabbetSpacing.XLarge), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Medium)
        ) {
            Icon(
                imageVector = MuhabbetIcons.Info,
                contentDescription = null,
                modifier = Modifier.size(MuhabbetSizes.IconEmptyState),
                tint = MaterialTheme.colorScheme.error.copy(alpha = IconAlpha)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            if (onRetry != null && retryLabel != null) {
                TextButton(onClick = onRetry) { Text(retryLabel) }
            }
        }
    }
}


private const val IconAlpha = 0.4f
