package com.muhabbet.designsystem.components

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The badge animates when what it *shows* changes, not when the number behind it does. Above the cap
 * 150 → 160 is "99+" → "99+", and sliding the same text out and back in is motion that says
 * something changed when, to the user, nothing did.
 *
 * Measured by how many frames are alive a few ticks after the change, with the test owning the
 * clock: two means one is sliding out while the other slides in; one means nothing moved. The
 * second test is the control, so the measurement can tell the two apart.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CountBadgeAnimationTest {

    @get:Rule
    val rule = createComposeRule()

    /** How many badge frames are composed shortly after changing [from] → [to]. */
    private fun framesAliveAfterChange(from: Int, to: Int): Int {
        var count by mutableIntStateOf(from)
        var alive = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val label = formatBadgeCount(count).orEmpty()
            CountBadgeContent(label = label, count = count) {
                DisposableEffect(Unit) {
                    alive++
                    onDispose { alive-- }
                }
            }
        }
        rule.mainClock.advanceTimeBy(SettleMillis)
        assertEquals("one frame when settled", 1, alive)

        count = to
        // The write happens outside composition; publish it before the clock moves, or the frames
        // below run against the old value.
        Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeBy(MidTransitionMillis)
        return alive
    }

    @Test
    fun `should not animate when a count above the cap changes`() {
        assertEquals("99+ → 99+ must stay still", 1, framesAliveAfterChange(150, 160))
    }

    @Test
    fun `should animate when the shown number changes`() {
        assertEquals("5 → 6 must slide one out and one in", 2, framesAliveAfterChange(5, 6))
    }

    private companion object {
        const val SettleMillis = 2_000L
        const val MidTransitionMillis = 64L
    }
}
