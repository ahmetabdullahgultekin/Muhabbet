package com.muhabbet.app.ui.status

import com.muhabbet.shared.dto.StatusResponse
import com.muhabbet.shared.dto.UserStatusGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #787: the story viewer's playback rules, with time as plain numbers.
 *
 * The two bugs these pin: releasing a hold restarted the segment from zero (the effect that drove
 * progress re-keyed on `isPaused` and reset `progress = 0f`), and a photo segment's clock ran before
 * the photo had loaded.
 */
class StatusPlaybackTest {

    private val duration = 5_000L

    private fun StatusPlayback.after(count: Int, vararg events: StatusPlaybackEvent): StatusPlayback =
        events.fold(this) { s, e -> reduceStatusPlayback(s, e, count, duration) }

    @Test
    fun should_resumeFromWhereItPaused_when_aHoldIsReleased() {
        val at40 = StatusPlayback.Loading(0).after(
            3,
            StatusPlaybackEvent.MediaReady(0),
            StatusPlaybackEvent.Tick(2_000),
        )
        assertEquals(0.4f, at40.segmentProgress(duration))

        val paused = at40.after(3, StatusPlaybackEvent.Pause)
        // Time passing while held changes nothing.
        val stillPaused = paused.after(3, StatusPlaybackEvent.Tick(10_000))
        assertEquals(0.4f, stillPaused.segmentProgress(duration))

        val resumed = stillPaused.after(3, StatusPlaybackEvent.Resume)
        assertEquals(StatusPlayback.Playing(0, 2_000), resumed)
        assertEquals(0.4f, resumed.segmentProgress(duration))
        assertEquals(StatusPlayback.Playing(0, 2_500), resumed.after(3, StatusPlaybackEvent.Tick(500)))
    }

    @Test
    fun should_notStartTheClock_until_theMediaIsReady() {
        val loading = StatusPlayback.Loading(0)
        // A slow photo: the frame clock keeps ticking, the segment does not move.
        assertEquals(loading, loading.after(3, StatusPlaybackEvent.Tick(60_000)))
        assertEquals(0f, loading.segmentProgress(duration))

        assertEquals(StatusPlayback.Playing(0, 0), loading.after(3, StatusPlaybackEvent.MediaReady(0)))
    }

    @Test
    fun should_ignoreAReadySignalForAnotherSegment() {
        // A late callback for the photo the user already tapped past must not start this one.
        assertEquals(StatusPlayback.Loading(1), StatusPlayback.Loading(1).after(3, StatusPlaybackEvent.MediaReady(0)))
    }

    @Test
    fun should_staySpaused_when_heldWhileTheMediaWasLoading() {
        val held = StatusPlayback.Loading(0).after(3, StatusPlaybackEvent.Pause, StatusPlaybackEvent.MediaReady(0))
        assertEquals(StatusPlayback.Paused(0, 0), held)
        assertEquals(StatusPlayback.Playing(0, 0), held.after(3, StatusPlaybackEvent.Resume))
    }

    @Test
    fun should_advanceToTheNextSegmentsLoading_when_theSegmentRunsOut() {
        val playing = StatusPlayback.Playing(0, 4_900)
        assertEquals(StatusPlayback.Loading(1), playing.after(3, StatusPlaybackEvent.Tick(100)))
    }

    @Test
    fun should_finish_when_theLastSegmentRunsOut() {
        assertEquals(StatusPlayback.Finished, StatusPlayback.Playing(2, 4_999).after(3, StatusPlaybackEvent.Tick(1)))
    }

    @Test
    fun should_stepWithTaps_andCloseFromEitherEnd() {
        assertEquals(StatusPlayback.Loading(1), StatusPlayback.Playing(0, 3_000).after(3, StatusPlaybackEvent.Next))
        assertEquals(StatusPlayback.Loading(0), StatusPlayback.Paused(1, 1_000).after(3, StatusPlaybackEvent.Previous))
        assertEquals(StatusPlayback.Finished, StatusPlayback.Playing(0, 0).after(3, StatusPlaybackEvent.Previous))
        assertEquals(StatusPlayback.Finished, StatusPlayback.Loading(2).after(3, StatusPlaybackEvent.Next))
    }

    @Test
    fun should_restartFromTheFirstSegment_when_theListChanges() {
        assertEquals(StatusPlayback.Loading(0), StatusPlayback.Playing(2, 1_000).after(3, StatusPlaybackEvent.Restart))
        assertEquals(StatusPlayback.Finished, StatusPlayback.Playing(0, 0).after(0, StatusPlaybackEvent.Restart))
    }

    private fun status(id: String, createdAt: Long, viewed: Boolean = false) =
        StatusResponse(id = id, userId = "u", createdAt = createdAt, expiresAt = createdAt + 86_400_000, viewed = viewed)

    private fun group(userId: String, vararg statuses: StatusResponse) =
        UserStatusGroup(userId = userId, statuses = statuses.toList())

    @Test
    fun should_countAGroupAsSeen_onlyWhenEveryStatusInItIsSeen() {
        val g = group("a", status("s1", 1, viewed = true), status("s2", 2))
        assertFalse(g.isFullySeen(emptySet()))
        // A view reported this session counts before the server has echoed it back.
        assertTrue(g.isFullySeen(setOf("s2")))
        assertFalse(group("empty").isFullySeen(emptySet()))
    }

    @Test
    fun should_listUnseenAuthorsFirst_thenNewestFirst() {
        val seenNewest = group("seen", status("a", 300, viewed = true))
        val unseenOld = group("old", status("b", 100))
        val unseenNew = group("new", status("c", 200))

        val ordered = listOf(seenNewest, unseenOld, unseenNew).orderedForTray(emptySet())

        assertEquals(listOf("new", "old", "seen"), ordered.map { it.userId })
    }
}
