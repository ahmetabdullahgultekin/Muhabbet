package com.muhabbet.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.muhabbet.designsystem.theme.LocalHaptics
import com.muhabbet.designsystem.theme.MuhabbetCorners
import com.muhabbet.designsystem.theme.MuhabbetHapticIntent
import com.muhabbet.designsystem.theme.MuhabbetMotion

/**
 * A filter chip.
 *
 * Five call sites — the conversation-list filters and the privacy dashboard's visibility choices —
 * which is over the three-caller threshold that decides whether something belongs here.
 *
 * Carries the haptic, like every other control in this module. A filter chip is a segmented choice
 * rather than an action, so it uses `SegmentAdvanced` rather than the confirm buzz a button gets;
 * and re-tapping the chip that is already selected is silent, because nothing happened.
 *
 * Shape is [MuhabbetCorners.Pill] rather than M3's default `small` (8dp) — the same fully-rounded
 * radius [MuhabbetCorners] names for "fully-rounded floating surfaces" like the reaction bar, so a
 * filter chip reads as a control you pick up and set down rather than a clipped rectangle. It presses
 * back on tap with the same spatial spring as every other pressable control, which a stock
 * `FilterChip`'s ripple-only feedback did not carry.
 *
 * The selected colours are set explicitly rather than left to `FilterChipDefaults`: M3 derives the
 * selected container from `secondaryContainer`, which under the copper palette is close enough to
 * the unselected surface that "which filter am I on" stopped being obvious at a glance.
 */
@Composable
fun MuhabbetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    val haptics = LocalHaptics.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) ChipPressedScale else 1f,
        animationSpec = MuhabbetMotion.spatialFast(),
        label = "chipPress"
    )
    FilterChip(
        selected = selected,
        onClick = {
            if (!selected) haptics.perform(MuhabbetHapticIntent.SegmentAdvanced)
            onClick()
        },
        label = { Text(label) },
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        enabled = enabled,
        leadingIcon = leadingIcon,
        shape = RoundedCornerShape(MuhabbetCorners.Pill),
        interactionSource = interactionSource,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

private const val ChipPressedScale = 0.95f
