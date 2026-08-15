package com.muhabbet.designsystem.components

import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.muhabbet.designsystem.theme.LocalHaptics
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
    FilterChip(
        selected = selected,
        onClick = {
            if (!selected) haptics.perform(MuhabbetHapticIntent.SegmentAdvanced)
            onClick()
        },
        label = { Text(label) },
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}
