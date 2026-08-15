package com.muhabbet.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.muhabbet.designsystem.theme.LocalHaptics
import com.muhabbet.designsystem.theme.MuhabbetMotion
import com.muhabbet.designsystem.theme.MuhabbetSizes

/**
 * An icon that can be pressed.
 *
 * 38 of the app's 56 `IconButton`s wrapped nothing but a bare `Icon`, each repeating the same
 * four lines. Collapsing them is worth it on its own, but the real gain is that press feedback and
 * the minimum touch target now come for free: previously every screen had to remember, and several
 * did not.
 *
 * The press scale is a spatial spring rather than a duration, so an interrupted press settles
 * rather than snapping.
 *
 * @param contentDescription what a screen reader announces. Pass null only when an adjacent label
 *   already says it — the icon is then hidden from accessibility rather than announced as unlabelled.
 */
@Composable
fun MuhabbetIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = LocalContentColor.current
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PressedScale else 1f,
        animationSpec = MuhabbetMotion.spatialFast(),
        label = "iconButtonPress"
    )

    IconButton(
        onClick = onClick,
        modifier = modifier
            .sizeIn(minWidth = MuhabbetSizes.MinTouchTarget, minHeight = MuhabbetSizes.MinTouchTarget)
            .graphicsLayer { scaleX = scale; scaleY = scale },
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

private const val PressedScale = 0.88f
