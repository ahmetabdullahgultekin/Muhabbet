package com.muhabbet.messaging.adapter.`in`.websocket

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.PingMessage
import org.springframework.web.socket.WebSocketSession
import java.io.IOException
import java.util.UUID

/**
 * Liveness reaping (#468). The defect these cover: `isOnline` kept answering true for a phone whose
 * network had gone, because `WebSocketSession.isOpen` stays true until TCP eventually gives up.
 * Every message sent in that window took the local-delivery branch and its push was never sent.
 */
class WebSocketSessionManagerTest {

    private lateinit var manager: WebSocketSessionManager

    private val userId = UUID.randomUUID()

    /** Three missed client pings; anything past this is treated as dead. */
    private val staleThresholdMs = 90_000L

    @BeforeEach
    fun setUp() {
        manager = WebSocketSessionManager()
    }

    private fun openSession(): WebSocketSession = mockk(relaxed = true) {
        every { id } returns UUID.randomUUID().toString()
        every { isOpen } returns true
    }

    @Nested
    inner class Reaping {

        @Test
        fun `should drop a session that is still open but has not sent a frame within the threshold`() {
            val session = openSession()
            manager.register(userId, session)
            assertTrue(manager.isOnline(userId), "sanity: a freshly registered user is online")

            // The socket never fails and never closes — exactly the case the old cleanup missed.
            manager.reapStaleSessions(now = System.currentTimeMillis() + staleThresholdMs + 1_000)

            assertFalse(manager.isOnline(userId), "a peer that has been silent past the threshold is not online")
            assertEquals(0, manager.getOnlineUserCount())
            assertNull(manager.getUserId(session), "sessionToUser must be cleaned up too, not just sessions")
        }

        @Test
        fun `should close a stale session before dropping it`() {
            val session = openSession()
            manager.register(userId, session)

            manager.reapStaleSessions(now = System.currentTimeMillis() + staleThresholdMs + 1_000)

            verify { session.close(CloseStatus.GOING_AWAY) }
        }

        @Test
        fun `should keep a session that has been silent for less than the threshold`() {
            val session = openSession()
            manager.register(userId, session)

            // Two thirds of the way to the threshold: one missed ping is not a dead phone.
            manager.reapStaleSessions(now = System.currentTimeMillis() + staleThresholdMs - 30_000)

            assertTrue(manager.isOnline(userId))
            verify(exactly = 0) { session.close(any()) }
        }

        @Test
        fun `should keep a session whose last inbound frame was recent even if it registered long ago`() {
            val session = openSession()
            manager.register(userId, session)

            val muchLater = System.currentTimeMillis() + 10 * staleThresholdMs
            manager.touch(session, now = muchLater)
            manager.reapStaleSessions(now = muchLater + 1_000)

            assertTrue(manager.isOnline(userId), "touch must reset the staleness clock")
        }

        @Test
        fun `should drop a session whose ping frame cannot be written`() {
            val session = openSession()
            every { session.sendMessage(any()) } throws IOException("Broken pipe")
            manager.register(userId, session)

            // Not stale by the clock — the failed write is the only evidence the peer is gone.
            manager.reapStaleSessions()

            assertFalse(manager.isOnline(userId))
            assertNull(manager.getUserId(session))
        }

        @Test
        fun `should ping healthy sessions so a dead socket surfaces on the write`() {
            val session = openSession()
            manager.register(userId, session)

            manager.reapStaleSessions()

            verify { session.sendMessage(ofType(PingMessage::class)) }
            assertTrue(manager.isOnline(userId))
        }

        @Test
        fun `should drop an already closed session and its reverse lookup entry`() {
            val session = openSession()
            manager.register(userId, session)
            every { session.isOpen } returns false

            manager.reapStaleSessions()

            assertFalse(manager.isOnline(userId))
            assertNull(manager.getUserId(session), "the old cleanup task leaked sessionToUser entries")
        }

        @Test
        fun `should keep a users other device when only one of its sessions is stale`() {
            val phone = openSession()
            val tablet = openSession()
            manager.register(userId, phone)
            manager.register(userId, tablet)

            val later = System.currentTimeMillis() + staleThresholdMs + 1_000
            manager.touch(tablet, now = later)
            manager.reapStaleSessions(now = later)

            assertTrue(manager.isOnline(userId), "the live device keeps the user online")
            assertNull(manager.getUserId(phone))
            assertEquals(userId, manager.getUserId(tablet))
        }
    }

    @Nested
    inner class Registration {

        @Test
        fun `should not track a session that was never registered when touched`() {
            val stranger = openSession()

            manager.touch(stranger)

            assertNull(manager.getUserId(stranger))
            assertEquals(0, manager.getOnlineUserCount())
        }

        @Test
        fun `should forget both maps on unregister`() {
            val session = openSession()
            manager.register(userId, session)

            manager.unregister(session)

            assertFalse(manager.isOnline(userId))
            assertNull(manager.getUserId(session))
        }

        @Test
        fun `should drop a session from both maps when a send to it fails`() {
            val session = openSession()
            every { session.sendMessage(any()) } throws IOException("Broken pipe")
            manager.register(userId, session)

            manager.sendToUser(userId, "{}")

            assertFalse(manager.isOnline(userId))
            assertNull(manager.getUserId(session))
        }
    }
}
