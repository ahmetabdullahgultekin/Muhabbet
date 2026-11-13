package com.muhabbet.designsystem.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.muhabbet.designsystem.modifier.MuhabbetPress
import com.muhabbet.designsystem.modifier.pressScale
import com.muhabbet.designsystem.theme.LocalHaptics
import com.muhabbet.designsystem.theme.MuhabbetHapticIntent
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.performingHaptic

/**
 * An icon that can be pressed.
 *
 * 38 of the app's 56 `IconButton`s wrapped nothing but a bare `Icon`, each repeating the same
 * four lines. Collapsing them is worth it on its own, but the real gain is that press feedback and
 * the minimum touch target now come for free: previously every screen had to remember, and several
 * did not.
 *
 * The press is [MuhabbetPress.Strong] — the deepest squeeze, because a 48dp target is the smallest
 * thing in the app that presses.
 *
 * @param contentDescription what a screen reader announces. Pass null only when an adjacent label
 *   already says it — the icon is then hidden from accessibility rather than announced as unlabelled.
 * @param haptic silent by default: most icon buttons open something or navigate, which is on the
 *   do-not-buzz list. Name a UI intent when the tap itself is the event (declining a call, say). A
 *   send button does *not* pass `MessageSent` — that fires when the server acknowledges the message.
 */
@Composable
fun MuhabbetIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = LocalContentColor.current,
    haptic: MuhabbetHapticIntent? = null
) {
    val interactionSource = remember { MutableInteractionSource() }

    IconButton(
        onClick = LocalHaptics.current.performingHaptic(haptic, onClick),
        modifier = modifier
            .sizeIn(minWidth = MuhabbetSizes.MinTouchTarget, minHeight = MuhabbetSizes.MinTouchTarget)
            .pressScale(interactionSource, MuhabbetPress.Strong),
        enabled = enabled,
        interactionSource = interactionSource
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            // A null description means "an adjacent label already covers this". Clearing the
            // semantics is stronger than leaving it null: it removes the node rather than leaving
            // an unlabelled one for a screen reader to stumble over.
            modifier = if (contentDescription == null) Modifier.clearAndSetSemantics {} else Modifier
        )
    }
}
