package com.muhabbet.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import com.muhabbet.designsystem.theme.MuhabbetDepth
import com.muhabbet.designsystem.theme.MuhabbetIcons
import com.muhabbet.designsystem.theme.MuhabbetMotion
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.designsystem.theme.containerColor

/**
 * Wraps [content] in a one-shot fade-and-rise on first composition, using the shared enter pair
 * every other "something just appeared" moment in the app uses.
 *
 * A loading, empty or error state is usually the first thing a screen shows — a hard cut into it
 * reads as unstyled in exactly the way a subtle rise does not. There is no matching exit: the state
 * leaves because the screen recomposes into real content, not because this component hides itself.
 */
@Composable
private fun AppearingColumn(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit
) {
    val visibleState = remember { MutableTransitionState(false) }.apply { targetState = true }
    AnimatedVisibility(visibleState = visibleState, enter = MuhabbetMotion.enterFadeUp, modifier = modifier) {
        Column(horizontalAlignment = horizontalAlignment, verticalArrangement = verticalArrangement, content = content)
    }
}

/**
 * An icon sitting in a soft circular backdrop rather than floating bare on the page — the same
 * "icon gets a considered container" move [SettingsRow] makes for its own leading icons, so an
 * empty inbox and a failed load read as the same family of surface instead of an unrelated icon.
 */
@Composable
private fun StateIconBadge(icon: ImageVector, contentDescription: String?, tint: Color, containerColor: Color) {
    Box(
        modifier = Modifier.size(MuhabbetSizes.StateIconBadge).background(containerColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(MuhabbetSizes.IconEmptyState),
            tint = tint
        )
    }
}

/**
 * A screen that is loading its content.
 *
 * Covers the 24 sites that were each a centred `CircularProgressIndicator` in a `fillMaxSize` Box,
 * in four slightly different spellings. Prefer [MuhabbetSkeletonList] or
 * [MuhabbetSkeletonConversation] behind a [MuhabbetSkeletonGate] for anything that resolves into a
 * list — a skeleton tells the user what is coming, a spinner only says "wait" — and keep this for
 * waits with no predictable shape.
 *
 * Two cases stay a spinner and are not defects: an **in-place action** the user just started (a
 * send in flight, a photo uploading, a conversation being created from a tapped contact) has no
 * shape to promise and is owned by the tap rather than by the page; and a busy button, which stays
 * an inline indicator sized to the button.
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
        AppearingColumn(verticalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Large)) {
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
 * The icon's badge takes [MuhabbetDepth.Floating]'s container tone — the same quiet "sits above the
 * page, does not compete with the copper accent" step a FAB or the reaction bar uses, which is a
 * better fit for "nothing here yet" than either the old faded icon or a full brand colour would be.
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
        AppearingColumn(verticalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Medium)) {
            if (icon != null) {
                StateIconBadge(
                    icon = icon,
                    // Usually decorative: the title beneath already says what is missing. Callers
                    // pass a description only when the icon carries meaning the text does not.
                    contentDescription = iconContentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    containerColor = MuhabbetDepth.Floating.containerColor()
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
 *
 * The retry action is a [MuhabbetButton] rather than a bare `TextButton` now, so trying again gets
 * the same press spring and haptic every other confirming tap in the app carries.
 */
@Composable
fun MuhabbetErrorState(
    message: String,
    modifier: Modifier = Modifier,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null
) {
    Box(modifier.fillMaxSize().padding(MuhabbetSpacing.XLarge), contentAlignment = Alignment.Center) {
        AppearingColumn(verticalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Medium)) {
            StateIconBadge(
                icon = MuhabbetIcons.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            if (onRetry != null && retryLabel != null) {
                MuhabbetButton(text = retryLabel, onClick = onRetry, role = MuhabbetButtonRole.Text)
            }
        }
    }
}
