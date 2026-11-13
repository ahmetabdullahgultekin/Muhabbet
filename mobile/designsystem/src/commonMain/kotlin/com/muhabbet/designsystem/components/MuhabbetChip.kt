package com.muhabbet.designsystem.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.muhabbet.designsystem.modifier.MuhabbetPress
import com.muhabbet.designsystem.modifier.pressScale
import com.muhabbet.designsystem.theme.LocalHaptics
import com.muhabbet.designsystem.theme.LocalSemanticColors
import com.muhabbet.designsystem.theme.MuhabbetCorners
import com.muhabbet.designsystem.theme.MuhabbetHapticIntent

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
 * back on tap with [MuhabbetPress.Standard], like every other control its size, which a stock
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
    val selectedColors = LocalSemanticColors.current.selected
    FilterChip(
        selected = selected,
        onClick = {
            if (!selected) haptics.perform(MuhabbetHapticIntent.SegmentAdvanced)
            onClick()
        },
        label = { Text(label) },
        modifier = modifier.pressScale(interactionSource, MuhabbetPress.Standard),
        enabled = enabled,
        leadingIcon = leadingIcon,
        shape = RoundedCornerShape(MuhabbetCorners.Pill),
        interactionSource = interactionSource,
        // One pair, read once. Naming the container and the two foregrounds separately is how
        // three lines that must agree end up disagreeing.
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = selectedColors.container,
            selectedLabelColor = selectedColors.content,
            selectedLeadingIconColor = selectedColors.content
        )
    )
}
