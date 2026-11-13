package com.muhabbet.designsystem.components

import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * #844: the undo path of [MuhabbetSnackbarState], driven the way the host drives it.
 *
 * The result mapping is the part a caller trusts blindly — an inverted branch would roll back an
 * action the user kept, or keep one they undid — so each outcome is pinned.
 */
class MuhabbetSnackbarStateTest {

    private val state = MuhabbetSnackbarState()

    /** Waits for the snackbar the coroutine under test has just put up. */
    private suspend fun showing(): SnackbarData {
        while (state.host.currentSnackbarData == null) yield()
        return requireNotNull(state.host.currentSnackbarData)
    }

    @Test
    fun `should report undone when the user presses undo`() = runBlocking {
        val result = async { state.showUndo("Sohbet arşivlendi", "Geri al") }
        showing().performAction()
        assertEquals(MuhabbetUndoResult.Undone, result.await())
    }

    @Test
    fun `should report kept when the snackbar goes away without undo`() = runBlocking {
        val result = async { state.showUndo("Sohbet arşivlendi", "Geri al") }
        showing().dismiss()
        assertEquals(MuhabbetUndoResult.Kept, result.await())
    }

    @Test
    fun `should offer undo with its label and long enough to reach it`() = runBlocking {
        val result = async { state.showUndo("Sohbet arşivlendi", "Geri al") }
        val visuals = showing().visuals
        assertEquals("Sohbet arşivlendi", visuals.message)
        assertEquals("Geri al", visuals.actionLabel)
        assertEquals(SnackbarDuration.Long, visuals.duration)
        showing().dismiss()
        result.await()
        Unit
    }

    @Test
    fun `should show a plain message with no action`() = runBlocking {
        val shown = async { state.show("Kaydedildi") }
        val visuals = showing().visuals
        assertEquals("Kaydedildi", visuals.message)
        assertNull(visuals.actionLabel)
        assertEquals(SnackbarDuration.Short, visuals.duration)
        showing().dismiss()
        shown.await()
    }
}
