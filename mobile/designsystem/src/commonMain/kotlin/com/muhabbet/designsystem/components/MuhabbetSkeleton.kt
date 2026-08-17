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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.muhabbet.designsystem.theme.MuhabbetCorners
import com.muhabbet.designsystem.theme.MuhabbetDurations
import com.muhabbet.designsystem.theme.MuhabbetMotion
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.delay

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
 * The same shape backs the conversation list, the contact picker, communities and channels: all four
 * are an avatar with a name over a secondary line, so all four share this rather than each growing
 * its own near-identical copy. If a fifth list needs a visibly different row, give it its own
 * composable here — do not add a mode flag to this one.
 *
 * @param loadingLabel what a screen reader should announce, e.g. "Loading chats". Supplied by the
 *   caller because this module carries no strings; see [skeletonSemantics] for why the rows
 *   themselves are silent.
 * @param rows enough to fill a phone screen; fewer looks like a short list rather than a loading one.
 */
@Composable
fun MuhabbetSkeletonList(
    modifier: Modifier = Modifier,
    loadingLabel: String? = null,
    rows: Int = 8
) {
    ShimmerHost {
        Column(modifier.skeletonSemantics(loadingLabel)) {
            repeat(rows) { MuhabbetSkeletonListRow() }
        }
    }
}

/**
 * A chat's worth of placeholder bubbles: alternating sides, varying widths, bottom-aligned.
 *
 * All three properties are load-bearing. A chat is the one screen where the user already knows the
 * shape of what is coming, so a centred spinner there is the most jarring in the app — but a column
 * of identical bars would be no better, because a conversation is visibly two people taking turns.
 * Bottom-aligned because a message list opens scrolled to its end, so this is where the real bubbles
 * will actually be; top-aligning it would move everything up the screen the moment content landed.
 *
 * The pattern is a fixed table rather than randomised widths: a random width is re-rolled on
 * recomposition, so the placeholder would visibly resize itself while waiting.
 */
@Composable
fun MuhabbetSkeletonConversation(
    modifier: Modifier = Modifier,
    loadingLabel: String? = null
) {
    ShimmerHost {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = MuhabbetSpacing.Medium, vertical = MuhabbetSpacing.Small)
                .skeletonSemantics(loadingLabel),
            verticalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Small, Alignment.Bottom)
        ) {
            SkeletonBubbles.forEach { SkeletonBubble(it) }
        }
    }
}

/** One placeholder bubble, sized and cornered like the real [MuhabbetCorners.Bubble] surface. */
@Composable
private fun SkeletonBubble(spec: SkeletonBubbleSpec) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (spec.isOwn) Arrangement.End else Arrangement.Start
    ) {
        Box(
            Modifier
                .fillMaxWidth(spec.widthFraction)
                // The real bubble is capped, not fractioned (see MuhabbetSizes.BubbleMaxWidth), so
                // on a tablet the placeholder stops where a message would rather than stretching.
                .widthIn(max = MuhabbetSizes.BubbleMaxWidth)
                .height(if (spec.tall) MuhabbetSizes.SkeletonBubbleTall else MuhabbetSizes.SkeletonBubbleShort)
                .shimmer(SkeletonBubbleShape)
        )
    }
}

private data class SkeletonBubbleSpec(val isOwn: Boolean, val widthFraction: Float, val tall: Boolean)

/**
 * Seven bubbles: enough to fill a phone, uneven enough to read as a conversation.
 *
 * Two consecutive bubbles from the same side appear twice on purpose — real chats are not strict
 * alternation, and a perfect zigzag is the tell that gives a placeholder away.
 */
private val SkeletonBubbles = listOf(
    SkeletonBubbleSpec(isOwn = false, widthFraction = 0.62f, tall = true),
    SkeletonBubbleSpec(isOwn = true, widthFraction = 0.44f, tall = false),
    SkeletonBubbleSpec(isOwn = false, widthFraction = 0.36f, tall = false),
    SkeletonBubbleSpec(isOwn = true, widthFraction = 0.70f, tall = true),
    SkeletonBubbleSpec(isOwn = true, widthFraction = 0.30f, tall = false),
    SkeletonBubbleSpec(isOwn = false, widthFraction = 0.55f, tall = false),
    SkeletonBubbleSpec(isOwn = false, widthFraction = 0.48f, tall = true)
)

private val SkeletonBubbleShape = RoundedCornerShape(MuhabbetCorners.Bubble)

/**
 * Makes a placeholder inert to accessibility and gives the wait a single spoken name.
 *
 * A skeleton is scaffolding, not content. Left alone, eight placeholder rows are eight focusable
 * nodes a TalkBack user must swipe through to discover that none of them says anything —
 * measurably worse than the spinner it replaces. [clearAndSetSemantics] collapses the whole subtree
 * into one node, and the label makes that node say "Loading chats" once, as a polite live region so
 * it is announced when it appears without interrupting whatever is being read.
 *
 * With no label the block is silent, which is right for a placeholder inside an already-announced
 * screen and wrong for a whole-screen load — so every screen-level call site passes one.
 */
private fun Modifier.skeletonSemantics(label: String?): Modifier = clearAndSetSemantics {
    if (label != null) {
        contentDescription = label
        liveRegion = LiveRegionMode.Polite
    }
}

/**
 * Shows [skeleton] while [isLoading], but only for loads slow enough to be worth explaining.
 *
 * The rule this enforces is that a skeleton is either absent or legible. Between the load starting
 * and [MuhabbetDurations.SkeletonAppearAfter] nothing at all is drawn — not the skeleton, and not
 * [content], because content mid-load is an empty list, which every screen here renders as "you have
 * no chats". A blank moment is honest; a false empty state is not.
 *
 * @param skeleton the placeholder. Named rather than trailing so [content] keeps the trailing slot.
 */
@Composable
fun MuhabbetSkeletonGate(
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    skeleton: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    val showSkeleton = rememberSkeletonVisible(isLoading)
    Box(modifier) {
        when {
            showSkeleton -> skeleton()
            isLoading -> Unit
            else -> content()
        }
    }
}

/**
 * Whether a skeleton should be on screen, given whether the screen is still loading.
 *
 * Lives here, once, rather than at each call site: the two thresholds only work as a pair, and a
 * screen that copied the delay but forgot the hold would flash exactly as badly as one with neither.
 * [MuhabbetSkeletonGate] is the usual way in; call this directly only when the surrounding layout
 * cannot be expressed as "skeleton or content" — the chat screen, for instance, keeps its wallpaper
 * painted underneath both.
 *
 * A load that finishes before [appearAfter] elapses never sets [isLoading] long enough for the
 * effect to survive its own `delay`, so nothing is shown and nothing needs unwinding.
 */
@Composable
fun rememberSkeletonVisible(
    isLoading: Boolean,
    appearAfter: Duration = MuhabbetDurations.SkeletonAppearAfter,
    minimumVisible: Duration = MuhabbetDurations.SkeletonMinimumVisible
): Boolean {
    var visible by remember { mutableStateOf(false) }
    // Written and read only inside the effect below, never during composition — this records when
    // the skeleton went up so the hold can be measured from that moment rather than from the start
    // of the load, which is a different and much longer interval.
    var shownAt by remember { mutableStateOf<TimeMark?>(null) }

    LaunchedEffect(isLoading) {
        if (isLoading) {
            delay(appearAfter)
            shownAt = TimeSource.Monotonic.markNow()
            visible = true
        } else {
            shownAt?.let { mark ->
                val remaining = minimumVisible - mark.elapsedNow()
                if (remaining.isPositive()) delay(remaining)
            }
            visible = false
            shownAt = null
        }
    }
    return visible
}

/** Placeholder for a paragraph of text — profile bios, message info bodies. */
@Composable
fun MuhabbetSkeletonParagraph(modifier: Modifier = Modifier, lines: Int = 3) {
    ShimmerHost {
        Column(
            modifier.skeletonSemantics(label = null),
            verticalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Small)
        ) {
            repeat(lines) { i ->
                MuhabbetSkeletonBlock(
                    Modifier.fillMaxWidth(if (i == lines - 1) 0.6f else 1f),
                    shape = MaterialTheme.shapes.extraSmall
                )
            }
        }
    }
}

