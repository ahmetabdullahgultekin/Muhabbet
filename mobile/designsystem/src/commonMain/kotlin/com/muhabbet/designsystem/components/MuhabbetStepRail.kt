package com.muhabbet.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.muhabbet.designsystem.theme.MuhabbetMotion
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing

/**
 * How far through a short flow the user is.
 *
 * Sign-up is three screens with no indication that it is three screens — a phone number, then a
 * code, then a name, each arriving without warning. Telling someone the end is close is most of what
 * makes a multi-step flow feel short.
 *
 * Segments animate their colour, not their width: a width animation on a bar that only ever moves
 * forward reads as a loading indicator, which is the wrong promise.
 *
 * @param current 1-based. Values outside `1..total` simply leave every segment inactive rather than
 *   crashing, since this is decoration and must never be the thing that takes a screen down.
 */
@Composable
fun MuhabbetStepRail(
    current: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    Row(
        // Decorative: the step is already stated by the screen's own title and subtitle, so
        // announcing "1 of 3" as a separate node would just be a second thing to swipe past.
        modifier = modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Small)
    ) {
        repeat(total) { index ->
            val isActive = index < current
            val color by animateColorAsState(
                targetValue = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                animationSpec = MuhabbetMotion.effectsDefault(),
                label = "stepRailSegment$index"
            )
            Row(
                modifier = Modifier
                    .width(MuhabbetSizes.StepRailSegmentWidth)
                    .height(MuhabbetSizes.StepRailSegmentHeight)
                    .clip(RoundedCornerShape(MuhabbetSizes.StepRailSegmentHeight / 2))
                    .background(color)
            ) {}
        }
    }
}
