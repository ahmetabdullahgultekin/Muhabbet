package com.muhabbet.app.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Back in a chat cancels the composer's mode before it leaves the chat (#803). */
class ComposerBackTest {

    @Test
    fun should_cancelTheEdit_when_editing() {
        assertEquals(ComposerBack.CancelEdit, composerBack(isEditing = true, isReplying = false))
    }

    @Test
    fun should_cancelTheEditFirst_when_editingAndReplying() {
        assertEquals(ComposerBack.CancelEdit, composerBack(isEditing = true, isReplying = true))
    }

    @Test
    fun should_cancelTheReply_when_replying() {
        assertEquals(ComposerBack.CancelReply, composerBack(isEditing = false, isReplying = true))
    }

    @Test
    fun should_letTheChatPop_when_composerIsIdle() {
        assertNull(composerBack(isEditing = false, isReplying = false))
    }
}
