package com.muhabbet.app.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #572: the codes the backend takes care to keep distinct must reach the user as distinct sentences.
 *
 * The stand-ins below are the localized strings the screen resolves; what matters here is that no
 * two causes select the same one, and that a code nobody has taught this mapping about still lands
 * somewhere honest instead of being guessed at.
 */
class SendFailureMessagesTest {

    private val messages = SendFailureMessages(
        generic = "generic",
        tooLong = "too-long",
        notMember = "not-member",
        announcementOnly = "announcement-only",
        rateLimited = "rate-limited",
    )

    @Test
    fun should_name_the_cause_for_each_refusal_the_sender_can_act_on() {
        assertEquals("too-long", messages.forCode("MSG_CONTENT_TOO_LONG"))
        assertEquals("not-member", messages.forCode("MSG_NOT_MEMBER"))
        assertEquals("announcement-only", messages.forCode("MSG_ANNOUNCEMENT_ONLY"))
        // #725: the refusal that reached the user as silence until the server started answering it
        // on the ack.
        assertEquals("rate-limited", messages.forCode("RATE_LIMITED"))
    }

    @Test
    fun should_give_a_different_sentence_to_every_cause_it_recognises() {
        // The whole of #572 in one assertion: a user whose message was too long and a user who was
        // removed from the group used to be told the same thing.
        val rendered = listOf("MSG_CONTENT_TOO_LONG", "MSG_NOT_MEMBER", "MSG_ANNOUNCEMENT_ONLY", "RATE_LIMITED")
            .map { messages.forCode(it) }
        assertEquals(rendered.size, rendered.toSet().size, "each recognised code must render differently")
        assertFalse("generic" in rendered, "a recognised code must not fall back to the generic sentence")
    }

    @Test
    fun should_fall_back_rather_than_invent_a_cause_it_cannot_explain() {
        // A server fault, a code from a newer backend, and an ack with no code at all. None of them
        // has a sentence that helps somebody holding a phone, so all three get the plain one.
        assertEquals("generic", messages.forCode("INTERNAL_ERROR"))
        assertEquals("generic", messages.forCode("MSG_SOME_CODE_THIS_BUILD_PREDATES"))
        assertEquals("generic", messages.forCode(null))
        // On the send path this means the app put a malformed id in its own frame — a bug in this
        // build, not something the sender did or can undo.
        assertEquals("generic", messages.forCode("VALIDATION_ERROR"))
    }

    @Test
    fun should_treat_a_duplicate_as_the_acceptance_it_is() {
        // MSG_DUPLICATE is the server recognising a messageId it already stored, which is what the
        // offline queue draining after a reconnect looks like from its side. The message went;
        // reporting a failure invites the user to send it again.
        assertTrue(serverAlreadyHasMessage("MSG_DUPLICATE"))
        assertFalse(serverAlreadyHasMessage("MSG_NOT_MEMBER"))
        assertFalse(serverAlreadyHasMessage("INTERNAL_ERROR"))
        assertFalse(serverAlreadyHasMessage(null))
    }
}
