package com.muhabbet.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.lerp
import com.muhabbet.designsystem.theme.LocalHaptics
import com.muhabbet.designsystem.theme.MuhabbetComponentSizes
import com.muhabbet.designsystem.theme.MuhabbetCorners
import com.muhabbet.designsystem.theme.MuhabbetHapticIntent
import com.muhabbet.designsystem.theme.MuhabbetMotion
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import kotlinx.coroutines.launch

/**
 * A row of tabs whose copper indicator slides between them on a spatial spring.
 *
 * Use this overload when the tab change is a state change (a filter, a section switch). For tabs
 * that sit over a swipeable [androidx.compose.foundation.pager.HorizontalPager], use the
 * [PagerState] overload: its indicator tracks the finger mid-swipe rather than jumping when the
 * page settles.
 *
 * Tapping a different tab fires `TabSwitched`, the same tick the bottom bar uses; re-tapping the
 * current tab is silent, and so is a pager swipe — the finger already felt that one. Each tab reports `Role.Tab` and its `selected` state.
 *
 * The indicator is a short capsule the width of the tab's *label*, not the whole cell — the same
 * "you are here" mark as the bottom bar's rail and the sign-up step rail.
 *
 * Fixed tabs only: labels share the width equally. Two to four labels is what every tab row in the
 * app has; a scrollable row would be a different component.
 */
@Composable
fun MuhabbetTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val position by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = MuhabbetMotion.spatialDefault(),
        label = "tabIndicator"
    )
    TabRowImpl(
        tabs = tabs,
        selectedIndex = selectedIndex,
        indicatorPosition = { position },
        onSelect = onSelect,
        modifier = modifier
    )
}

/**
 * Tabs driving, and driven by, a pager. Tapping a tab animates the pager there; swiping the pager
 * moves the indicator continuously with it.
 */
@Composable
fun MuhabbetTabs(
    tabs: List<String>,
    pagerState: PagerState,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    TabRowImpl(
        tabs = tabs,
        // targetPage, not currentPage: the label highlights where the user is going, which is the
        // page they asked for, not the one the pager happens to be passing over.
        selectedIndex = pagerState.targetPage,
        indicatorPosition = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
        onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
        modifier = modifier
    )
}

@Composable
private fun TabRowImpl(
    tabs: List<String>,
    selectedIndex: Int,
    indicatorPosition: () -> Float,
    onSelect: (Int) -> Unit,
    modifier: Modifier
) {
    val haptics = LocalHaptics.current
    val scheme = MaterialTheme.colorScheme
    val safeIndex = selectedIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
    PrimaryTabRow(
        selectedTabIndex = safeIndex,
        modifier = modifier,
        containerColor = scheme.surface,
        contentColor = scheme.onSurface,
        indicator = {
            Box(
                Modifier
                    .tabIndicatorLayout { measurable, constraints, positions ->
                        val (left, width) = indicatorBounds(
                            positions.map { TabSpan(it.left, it.width, it.contentWidth) },
                            indicatorPosition()
                        )
                        val widthPx = width.roundToPx()
                        val heightPx = MuhabbetComponentSizes.TabIndicatorHeight.roundToPx()
                        val placeable = measurable.measure(Constraints.fixed(widthPx, heightPx))
                        layout(constraints.maxWidth, heightPx) {
                            placeable.place(left.roundToPx(), 0)
                        }
                    }
                    .background(scheme.primary, RoundedCornerShape(MuhabbetCorners.Pill))
            )
        },
        divider = { MuhabbetDivider() }
    ) {
        tabs.forEachIndexed { index, label ->
            Tab(
                selected = index == safeIndex,
                onClick = {
                    if (index != safeIndex) haptics.perform(MuhabbetHapticIntent.TabSwitched)
                    onSelect(index)
                },
                selectedContentColor = scheme.primary,
                unselectedContentColor = scheme.onSurfaceVariant,
                text = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

/**
 * Where the indicator sits for a fractional [position] — `1.4` is 40% of the way from tab 1 to
 * tab 2 — as (left edge, width) in the row's coordinates. Centred under each label and as wide as
 * the label's content, interpolated linearly in between.
 */
internal fun indicatorBounds(positions: List<TabSpan>, position: Float): Pair<Dp, Dp> {
    if (positions.isEmpty()) return MuhabbetSpacing.None to MuhabbetSpacing.None
    val clamped = position.coerceIn(0f, (positions.size - 1).toFloat())
    val from = positions[clamped.toInt()]
    val to = positions[(clamped.toInt() + 1).coerceAtMost(positions.size - 1)]
    val fraction = clamped - clamped.toInt()
    fun TabSpan.contentLeft(): Dp = left + (width - contentWidth) / 2
    val width = lerp(from.contentWidth, to.contentWidth, fraction)
    val left = lerp(from.contentLeft(), to.contentLeft(), fraction)
    return left to width
}

/** M3's `TabPosition` without its internal constructor, so [indicatorBounds] can be tested. */
internal data class TabSpan(val left: Dp, val width: Dp, val contentWidth: Dp)
