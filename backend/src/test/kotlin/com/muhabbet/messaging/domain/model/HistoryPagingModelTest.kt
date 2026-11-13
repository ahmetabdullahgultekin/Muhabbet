package com.muhabbet.messaging.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Base64
import java.util.UUID

class HistoryPagingModelTest {

    private val joinedAt = Instant.parse("2026-11-13T12:00:00.000001Z")
    private val member = ConversationMember(conversationId = UUID.randomUUID(), userId = UUID.randomUUID(), joinedAt = joinedAt)

    private fun messageAt(ts: Instant) = Message(
        id = UUID.randomUUID(),
        conversationId = member.conversationId,
        senderId = UUID.randomUUID(),
        content = "x",
        serverTimestamp = ts,
        clientTimestamp = ts
    )

    /** #874 — the policy function. */
    @Nested
    inner class Window {

        @Test
        fun `should open a group from the moment this membership began`() {
            val window = HistoryWindow.of(ConversationType.GROUP, member)
            assertEquals(HistoryWindow.From(joinedAt), window)
            assertFalse(window.admits(messageAt(joinedAt.minusNanos(1_000))))
            assertTrue(window.admits(messageAt(joinedAt)), "the boundary instant belongs to the member")
            assertTrue(window.admits(messageAt(joinedAt.plusSeconds(1))))
        }

        @Test
        fun `should leave direct conversations unbounded`() {
            val window = HistoryWindow.of(ConversationType.DIRECT, member)
            assertEquals(HistoryWindow.Unbounded, window)
            assertTrue(window.admits(messageAt(joinedAt.minusSeconds(86_400))))
        }

        @Test
        fun `should leave channels unbounded so subscribers can scroll back`() {
            assertEquals(HistoryWindow.Unbounded, HistoryWindow.of(ConversationType.CHANNEL, member))
        }
    }

    /** #750 / #876 — the cursor. */
    @Nested
    inner class Cursor {

        @Test
        fun `should round-trip timestamp and id at microsecond precision`() {
            val cursor = MessageCursor(Instant.parse("2026-11-13T12:00:00.123456Z"), UUID.randomUUID())
            assertEquals(cursor, MessageCursor.decode(cursor.encode()))
        }

        @Test
        fun `should be opaque and URL-safe`() {
            val encoded = MessageCursor(Instant.now(), UUID.randomUUID()).encode()
            assertTrue(encoded.matches(Regex("[A-Za-z0-9_-]+")), encoded)
        }

        @Test
        fun `should decode a legacy timestamp cursor to the same exclusive bound`() {
            val ts = Instant.parse("2026-11-13T12:00:00Z")
            assertEquals(MessageCursor(ts, UUID(0L, 0L)), MessageCursor.decode(ts.toString()))
        }

        @Test
        fun `should refuse anything it did not mint`() {
            assertNull(MessageCursor.decode("garbage"))
            assertNull(MessageCursor.decode(""))
            val wrongVersion = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("v9|2026-11-13T12:00:00Z|${UUID.randomUUID()}".toByteArray())
            assertNull(MessageCursor.decode(wrongVersion))
            val badId = Base64.getUrlEncoder().withoutPadding().encodeToString("v1|2026-11-13T12:00:00Z|nope".toByteArray())
            assertNull(MessageCursor.decode(badId))
        }
    }

    @Nested
    inner class Position {

        private val cursor = MessageCursor(Instant.now(), UUID.randomUUID())

        @Test
        fun `should map direction and cursor onto a position`() {
            assertEquals(HistoryPosition.Latest, HistoryPosition.of(HistoryDirection.BEFORE, null))
            assertEquals(HistoryPosition.Before(cursor), HistoryPosition.of(HistoryDirection.BEFORE, cursor))
            assertEquals(HistoryPosition.After(cursor), HistoryPosition.of(HistoryDirection.AFTER, cursor))
            assertNull(HistoryPosition.of(HistoryDirection.AFTER, null))
        }

        @Test
        fun `should parse direction case-insensitively and nothing else`() {
            assertEquals(HistoryDirection.BEFORE, HistoryDirection.fromWire("before"))
            assertEquals(HistoryDirection.AFTER, HistoryDirection.fromWire("AFTER"))
            assertNull(HistoryDirection.fromWire("around"))
        }
    }
}
