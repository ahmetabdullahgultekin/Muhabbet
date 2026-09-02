package com.muhabbet.app.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #516's decision table.
 *
 * The keypress itself is not testable here — there is no emulator on this host and the composer is
 * a Compose node — so what is pinned is the part that was actually wrong: which combination of
 * setting, modifier and content sends. #514 shipped a composer that drew a send key and inserted a
 * newline, and nothing in the suite could have noticed.
 */
class EnterKeyBehaviorTest {

    @Test
    fun should_send_when_enter_to_send_is_on_and_there_is_text() {
        assertEquals(
            EnterKeyAction.Send,
            enterKeyAction(enterToSend = true, shiftPressed = false, hasSendableText = true)
        )
    }

    @Test
    fun should_insert_a_newline_when_shift_is_held_and_enter_to_send_is_on() {
        assertEquals(
            EnterKeyAction.InsertNewline,
            enterKeyAction(enterToSend = true, shiftPressed = true, hasSendableText = true)
        )
    }

    @Test
    fun should_insert_a_newline_when_enter_to_send_is_off() {
        assertEquals(
            EnterKeyAction.InsertNewline,
            enterKeyAction(enterToSend = false, shiftPressed = false, hasSendableText = true)
        )
    }

    /**
     * With the setting off, Shift+Enter must not become a second way to send — the point of turning
     * it off is that no Enter sends.
     */
    @Test
    fun should_insert_a_newline_when_enter_to_send_is_off_even_with_shift() {
        assertEquals(
            EnterKeyAction.InsertNewline,
            enterKeyAction(enterToSend = false, shiftPressed = true, hasSendableText = true)
        )
    }

    /**
     * Blank composer: not consumed. Sending nothing is not an option and neither is swallowing the
     * key, which would read as a dead keyboard.
     */
    @Test
    fun should_not_send_when_the_composer_is_blank() {
        assertEquals(
            EnterKeyAction.InsertNewline,
            enterKeyAction(enterToSend = true, shiftPressed = false, hasSendableText = false)
        )
    }
}
