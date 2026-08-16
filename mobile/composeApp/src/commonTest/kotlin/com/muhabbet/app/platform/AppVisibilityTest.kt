package com.muhabbet.app.platform

import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The signal an open chat uses to notice the user has come back (#478).
 *
 * The `drop(1).filter { it }` shape asserted below is exactly what `ChatScreen` collects, so these
 * tests are the contract between the two: the replayed current value must not read as a return to
 * the foreground, and every later `true` must.
 */
class AppVisibilityTest {

    @Test
    fun should_assume_the_app_is_in_front_before_anything_reports_otherwise() {
        assertTrue(
            AppVisibility().isForeground.value,
            "iOS has no entry point yet, so nothing feeds this there. Defaulting to hidden would " +
                "suppress work that runs today on a platform that simply cannot answer."
        )
    }

    @Test
    fun should_report_being_away_while_the_app_is_paused() {
        val visibility = AppVisibility()

        visibility.onBackground()

        assertEquals(false, visibility.isForeground.value)
    }

    @Test
    fun should_only_signal_a_return_after_the_app_was_actually_away() =
        runTest(UnconfinedTestDispatcher()) {
            val visibility = AppVisibility()
            var returns = 0

            visibility.isForeground
                .drop(1)
                .filter { it }
                .onEach { returns++ }
                .launchIn(backgroundScope)
            runCurrent()

            assertEquals(
                0,
                returns,
                "Subscribing replays the current value. Treating that as a transition would fire a " +
                    "second receipt on every chat open, on top of the open-handler's."
            )

            visibility.onForeground()
            runCurrent()
            assertEquals(0, returns, "Already in front — a StateFlow emits nothing for the same value.")

            // runCurrent() between the two on purpose: StateFlow conflates, so a pause and a resume
            // that the collector never gets scheduled between would look like no change at all. Two
            // real Activity callbacks are always separated by the user; the test should not depend
            // on that being true of the scheduler.
            visibility.onBackground()
            runCurrent()
            visibility.onForeground()
            runCurrent()
            assertEquals(1, returns, "Locking and unlocking the phone is one return to the front.")

            // runCurrent() between the two on purpose: StateFlow conflates, so a pause and a resume
            // that the collector never gets scheduled between would look like no change at all. Two
            // real Activity callbacks are always separated by the user; the test should not depend
            // on that being true of the scheduler.
            visibility.onBackground()
            runCurrent()
            visibility.onForeground()
            runCurrent()
            assertEquals(2, returns)
        }
}
