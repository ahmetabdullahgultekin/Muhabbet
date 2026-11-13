package com.muhabbet.designsystem.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.muhabbet.designsystem.theme.LocalHaptics
import com.muhabbet.designsystem.theme.MuhabbetDepth
import com.muhabbet.designsystem.theme.MuhabbetHapticIntent
import com.muhabbet.designsystem.theme.containerColor

/**
 * Pull-to-refresh in the brand: a copper arrow on a Floating-depth disc, and a `RefreshTriggered`
 * tick at the moment the pull crosses the point where letting go will refresh.
 *
 * That tick is the whole reason this is not a bare `PullToRefreshBox`. The intent was declared in
 * [MuhabbetHapticIntent] from the start and nothing ever performed it — the one pull-to-refresh
 * in the app (the conversation list) gave no sign of having armed, so a user could not tell a pull
 * that would refresh from one that would spring back.
 *
 * When it fires is a small state machine, [PullRefreshArming], kept pure so the rules are tested
 * rather than eyeballed:
 * - once per pull, on the frame the indicator first reaches the threshold;
 * - never while a refresh is already running — M3 parks the indicator *at* the threshold during a
 *   refresh, and a programmatic `isRefreshing = true` animates it there with no finger involved;
 * - re-armed only after the indicator falls back below the threshold, so dithering at the line
 *   does not buzz on every frame.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuhabbetPullRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val state = rememberPullToRefreshState()
    val haptics = LocalHaptics.current
    val refreshing by rememberUpdatedState(isRefreshing)

    LaunchedEffect(state) {
        var arming: PullRefreshArming = PullRefreshArming.Idle
        snapshotFlow { state.distanceFraction }.collect { fraction ->
            val step = arming.next(fraction, refreshing)
            arming = step.state
            if (step.fire) haptics.perform(MuhabbetHapticIntent.RefreshTriggered)
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = MuhabbetDepth.Floating.containerColor(),
                color = MaterialTheme.colorScheme.primary
            )
        },
        content = content
    )
}

/**
 * Whether the current pull has already announced itself.
 *
 * Sealed with pure transitions (ADR 0012), because "fire once per gesture, never during a refresh,
 * re-arm below the line" is three rules that interact, and the frame-by-frame collector above is
 * the worst possible place to discover they do not.
 */
internal sealed interface PullRefreshArming {
    /** Below the threshold, or not being pulled. The next crossing will fire. */
    data object Idle : PullRefreshArming

    /** At or past the threshold, and it has already been announced (or deliberately swallowed). */
    data object Armed : PullRefreshArming

    /** The next state for an indicator at [fraction] of its threshold, and whether to buzz. */
    fun next(fraction: Float, refreshing: Boolean): PullRefreshStep = when (this) {
        Idle -> if (fraction >= 1f) PullRefreshStep(Armed, fire = !refreshing) else PullRefreshStep(Idle, fire = false)
        Armed -> if (fraction < 1f) PullRefreshStep(Idle, fire = false) else PullRefreshStep(Armed, fire = false)
    }
}

/** One transition of [PullRefreshArming]. */
internal data class PullRefreshStep(val state: PullRefreshArming, val fire: Boolean)
