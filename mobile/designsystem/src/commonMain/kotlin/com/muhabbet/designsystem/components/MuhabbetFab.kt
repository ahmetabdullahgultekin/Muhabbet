package com.muhabbet.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import com.muhabbet.designsystem.theme.MuhabbetCorners
import com.muhabbet.designsystem.theme.MuhabbetDepth
import com.muhabbet.designsystem.theme.MuhabbetElevation
import com.muhabbet.designsystem.theme.MuhabbetMotion
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.designsystem.theme.depth

/**
 * The primary action on a list screen.
 *
 * Six screens each built their own out of `FloatingActionButton` plus a two-line colour block, which
 * is the usual way a control ends up with the same colours and three different shadows.
 *
 * Three things make it ours rather than Material's:
 *
 *  - **The corner is [MuhabbetCorners.Bubble], the chat bubble's own 18dp radius**, not Material's
 *    16dp `large`. Two dp is not the point; the point is that the app's biggest button and the app's
 *    most-repeated shape are now the same shape, on purpose, from one token.
 *  - **Depth is [MuhabbetDepth.Floating]**, which is the level that token was written for. Material's
 *    own elevation is switched off ([MuhabbetElevation.None]) rather than layered on top: its single
 *    flat shadow is the "sticker" look the depth scale exists to replace, it goes grey over a warm
 *    surface, and on OLED it renders as nothing at all — where [depth] draws the outline that keeps
 *    a copper disc from floating in a void.
 *  - **It presses back**, with the same spatial spring as [MuhabbetIconButton] and [MuhabbetChip].
 *
 * No haptic. A FAB opens a screen, and navigation is on the explicit do-not-buzz list — a phone that
 * vibrates every time you move between screens feels broken, not responsive.
 */
@Composable
fun MuhabbetFab(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale by rememberPressScale(interactionSource)
    val shape = RoundedCornerShape(MuhabbetCorners.Bubble)

    FloatingActionButton(
        onClick = onClick,
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .depth(MuhabbetDepth.Floating, shape),
        shape = shape,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = flatElevation(),
        interactionSource = interactionSource
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription)
    }
}

/**
 * A primary action that needs to say what it does in words.
 *
 * Same surface treatment as [MuhabbetFab], but [MuhabbetCorners.Pill] — the radius the token set
 * reserves for fully-rounded floating surfaces — because a wide button at the bubble radius reads as
 * a card someone forgot to finish.
 *
 * The icon is decorative and is not given a content description: [text] already labels the button,
 * and a screen reader announcing the label twice is worse than announcing it once.
 */
@Composable
fun MuhabbetExtendedFab(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale by rememberPressScale(interactionSource)
    val shape = RoundedCornerShape(MuhabbetCorners.Pill)

    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .depth(MuhabbetDepth.Floating, shape),
        shape = shape,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = flatElevation(),
        interactionSource = interactionSource
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null)
            Spacer(Modifier.width(MuhabbetSpacing.Medium))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Material's elevation, off at every interaction state.
 *
 * All four have to be named: leaving `pressedElevation` at its default puts a shadow back under the
 * finger, which is exactly the frame where the two treatments would be visible at once.
 */
@Composable
private fun flatElevation() = FloatingActionButtonDefaults.elevation(
    defaultElevation = MuhabbetElevation.None,
    pressedElevation = MuhabbetElevation.None,
    focusedElevation = MuhabbetElevation.None,
    hoveredElevation = MuhabbetElevation.None
)

/** Shared press physics, so the two variants cannot drift apart. */
@Composable
private fun rememberPressScale(interactionSource: MutableInteractionSource) =
    animateFloatAsState(
        targetValue = if (interactionSource.collectIsPressedAsState().value) PressedScale else 1f,
        animationSpec = MuhabbetMotion.spatialFast(),
        label = "fabPress"
    )

/** Shallower than [MuhabbetIconButton]'s 0.88: the same proportional squeeze on a 56dp target would
 *  travel four times as far in pixels and read as the button collapsing. */
private const val PressedScale = 0.94f
