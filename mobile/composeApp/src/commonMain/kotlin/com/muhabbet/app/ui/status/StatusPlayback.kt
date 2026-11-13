package com.muhabbet.app.ui.status

import com.muhabbet.shared.dto.UserStatusGroup

/**
 * The story viewer's playback, as a sealed state with a pure reducer (#787, ADR 0012).
 *
 * It replaces a `LaunchedEffect(currentIndex, statuses.size, isPaused, confirmDelete)` that set
 * `progress = 0f` on entry. Releasing a hold re-keyed the effect, so **every pause restarted the
 * segment from zero**; and the timer started whether or not the photo had loaded, so on a slow
 * network a story advanced before anyone saw it.
 *
 * Here progress is an elapsed time carried in the state, a pause keeps it, and a segment does not
 * start until its media is ready ([StatusPlaybackEvent.MediaReady]). Time arrives as
 * [StatusPlaybackEvent.Tick] deltas from the frame clock, so every rule is testable with numbers.
 */
sealed interface StatusPlayback {
    val index: Int

    /** Waiting for segment [index]'s media. [held] remembers a press that began while loading. */
    data class Loading(override val index: Int, val held: Boolean = false) : StatusPlayback

    data class Playing(override val index: Int, val elapsedMs: Long) : StatusPlayback

    data class Paused(override val index: Int, val elapsedMs: Long) : StatusPlayback

    /** Past the last segment, or backed out of the first: the viewer closes. */
    data object Finished : StatusPlayback {
        override val index: Int get() = -1
    }
}

sealed interface StatusPlaybackEvent {
    /** Segment [index]'s media is on screen (or it has none, or it failed and will not appear). */
    data class MediaReady(val index: Int) : StatusPlaybackEvent
    data class Tick(val deltaMs: Long) : StatusPlaybackEvent
    data object Pause : StatusPlaybackEvent
    data object Resume : StatusPlaybackEvent
    data object Next : StatusPlaybackEvent
    data object Previous : StatusPlaybackEvent

    /** The list changed underneath (a status was deleted): start again from the first. */
    data object Restart : StatusPlaybackEvent
}

/**
 * @param count how many segments there are.
 * @param durationMs how long one segment plays.
 */
fun reduceStatusPlayback(
    state: StatusPlayback,
    event: StatusPlaybackEvent,
    count: Int,
    durationMs: Long
): StatusPlayback {
    fun segment(index: Int): StatusPlayback =
        if (index in 0 until count) StatusPlayback.Loading(index) else StatusPlayback.Finished

    return when (event) {
        is StatusPlaybackEvent.MediaReady -> when {
            state is StatusPlayback.Loading && state.index == event.index ->
                if (state.held) StatusPlayback.Paused(state.index, 0) else StatusPlayback.Playing(state.index, 0)
            else -> state
        }

        is StatusPlaybackEvent.Tick -> when (state) {
            is StatusPlayback.Playing -> {
                val elapsed = state.elapsedMs + event.deltaMs.coerceAtLeast(0)
                if (elapsed >= durationMs) segment(state.index + 1) else state.copy(elapsedMs = elapsed)
            }
            else -> state
        }

        StatusPlaybackEvent.Pause -> when (state) {
            is StatusPlayback.Playing -> StatusPlayback.Paused(state.index, state.elapsedMs)
            is StatusPlayback.Loading -> state.copy(held = true)
            else -> state
        }

        StatusPlaybackEvent.Resume -> when (state) {
            is StatusPlayback.Paused -> StatusPlayback.Playing(state.index, state.elapsedMs)
            is StatusPlayback.Loading -> state.copy(held = false)
            else -> state
        }

        StatusPlaybackEvent.Next ->
            if (state == StatusPlayback.Finished) state else segment(state.index + 1)

        // Back from the first segment closes the viewer, as it always has.
        StatusPlaybackEvent.Previous ->
            if (state == StatusPlayback.Finished) state else segment(state.index - 1)

        StatusPlaybackEvent.Restart -> segment(0)
    }
}

/** How far through its own segment the current status is, 0..1. */
fun StatusPlayback.segmentProgress(durationMs: Long): Float = when (this) {
    is StatusPlayback.Playing -> (elapsedMs.toFloat() / durationMs).coerceIn(0f, 1f)
    is StatusPlayback.Paused -> (elapsedMs.toFloat() / durationMs).coerceIn(0f, 1f)
    else -> 0f
}

/**
 * Whether every status in [group] has been seen, by the server's record or by this session's own
 * reports ([locallyViewed]) — the seen/unseen ring on the Updates tab (#787).
 */
fun UserStatusGroup.isFullySeen(locallyViewed: Set<String>): Boolean =
    statuses.isNotEmpty() && statuses.all { it.viewed || it.id in locallyViewed }

/**
 * Unseen authors first, then seen ones, each newest first — the order a person scans the tray in.
 * Every author used to sort by recency alone, so a story you had already watched could sit above
 * one you had not.
 */
fun List<UserStatusGroup>.orderedForTray(locallyViewed: Set<String>): List<UserStatusGroup> =
    sortedWith(
        compareBy<UserStatusGroup> { it.isFullySeen(locallyViewed) }
            .thenByDescending { group -> group.statuses.maxOfOrNull { it.createdAt } ?: 0L }
    )
