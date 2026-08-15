package com.muhabbet.designsystem.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.muhabbet.designsystem.theme.MuhabbetMotion
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing

/**
 * The shimmer phase, hoisted so a list of placeholders animates as one.
 *
 * The previous implementation created a `rememberInfiniteTransition` inside each skeleton row, so
 * ten placeholder rows meant ten independent infinite animations recomposing on their own clocks —
 * both wasteful and visibly wrong, since a real shimmer sweeps across the whole list rather than
 * each row pulsing to its own beat.
 */
private val LocalShimmerPhase = staticCompositionLocalOf<Float?> { null }

/**
 * Runs one shimmer clock for everything inside.
 *
 * Wrap a list of skeletons in this; each [Modifier.shimmer] inside picks the shared phase up.
 * Using a skeleton outside it still works — it falls back to its own transition.
 */
@Composable
fun ShimmerHost(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalShimmerPhase provides rememberShimmerPhase(), content = content)
}

@Composable
private fun rememberShimmerPhase(): Float {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(MuhabbetMotion.Duration.ShimmerSweepMs),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerPhase"
    )
    return phase
}

/**
 * Paints a placeholder surface with a light sweeping across it.
 *
 * A moving highlight, not a pulsing alpha. The alpha pulse this replaces reads as "something is
 * broken and blinking"; a sweep reads as "content is on its way", which is the whole job of a
 * skeleton.
 */
@Composable
fun Modifier.shimmer(shape: Shape = MaterialTheme.shapes.small): Modifier {
    val phase = LocalShimmerPhase.current ?: rememberShimmerPhase()
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest

    // Travels well past the edges so the highlight enters and leaves rather than appearing mid-surface.
    val travel = 2000f
    val x = (phase * 2f - 0.5f) * travel
    return this
        .clip(shape)
        .background(
            Brush.linearGradient(
                colors = listOf(base, highlight, base),
                start = Offset(x - travel / 2f, 0f),
                end = Offset(x, 0f)
            )
        )
}

/** A placeholder block: use for a title line, a preview line, a thumbnail. */
@Composable
fun MuhabbetSkeletonBlock(
    modifier: Modifier = Modifier,
    height: Dp = MuhabbetSizes.SkeletonLine,
    shape: Shape = MaterialTheme.shapes.small
) {
    Box(modifier.height(height).shimmer(shape))
}

/**
 * One placeholder row shaped like an avatar + two lines of text.
 *
 * Matches the geometry of the real conversation and contact rows, so the list does not jump when
 * content arrives. The two lines are deliberately unequal widths — a skeleton of identical bars
 * reads as a loading bar, not as text.
 */
@Composable
fun MuhabbetSkeletonListRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(MuhabbetSizes.AvatarChatList).shimmer(CircleShape))
        Spacer(Modifier.width(MuhabbetSpacing.Large))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Small)
        ) {
            MuhabbetSkeletonBlock(Modifier.fillMaxWidth(0.45f))
            MuhabbetSkeletonBlock(Modifier.fillMaxWidth(0.75f), height = MuhabbetSizes.SkeletonLineSmall)
        }
    }
}

/**
 * A whole list of placeholder rows under one shimmer clock.
 *
 * @param rows enough to fill a phone screen; fewer looks like a short list rather than a loading one.
 */
@Composable
fun MuhabbetSkeletonList(modifier: Modifier = Modifier, rows: Int = 8) {
    ShimmerHost {
        Column(modifier) {
            repeat(rows) { MuhabbetSkeletonListRow() }
        }
    }
}

/** Placeholder for a paragraph of text — profile bios, message info bodies. */
@Composable
fun MuhabbetSkeletonParagraph(modifier: Modifier = Modifier, lines: Int = 3) {
    ShimmerHost {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Small)) {
            repeat(lines) { i ->
                MuhabbetSkeletonBlock(
                    Modifier.fillMaxWidth(if (i == lines - 1) 0.6f else 1f),
                    shape = MaterialTheme.shapes.extraSmall
                )
            }
        }
    }
}

