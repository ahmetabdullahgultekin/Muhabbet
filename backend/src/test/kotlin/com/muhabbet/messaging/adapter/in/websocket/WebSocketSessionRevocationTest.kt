package com.muhabbet.messaging.adapter.`in`.websocket

import com.muhabbet.auth.domain.model.SessionRevocationReason
import com.muhabbet.messaging.adapter.out.external.WebSocketSessionRevocationAdapter
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketSession
import java.time.Instant
import java.util.UUID

/**
 * #747: a WebSocket authenticated once, at connect, and then outlived its token, a logout, the
 * revocation of its device and the erasure of its account. These pin the closes that fix that.
 */
class WebSocketSessionRevocationTest {

    private lateinit var manager: WebSocketSessionManager

    private val userId = UUID.randomUUID()
    private val phone = UUID.randomUUID()
    private val laptop = UUID.randomUUID()

    /** Comfortably in the future, so no test here is closed by expiry unless it asks to be. */
    private val farFuture: Instant = Instant.now().plusSeconds(3_600)

    @BeforeEach
    fun setUp() {
        manager = WebSocketSessionManager()
    }

    @AfterEach
    fun tearDown() {
        manager.stopCleanup()
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    private fun openSession(): WebSocketSession = mockk(relaxed = true) {
        every { id } returns UUID.randomUUID().toString()
        every { isOpen } returns true
    }

    private fun revoked(reason: SessionRevocationReason) =
        CloseStatus(WebSocketSessionManager.CLOSE_CODE_SESSION_REVOKED, reason.name)

    @Nested
    inner class CloseForDevice {

        @Test
        fun `should close only the revoked device's sessions when one device is revoked`() {
            val phoneSession = openSession()
            val laptopSession = openSession()
            manager.register(userId, phoneSession, phone, farFuture)
            manager.register(userId, laptopSession, laptop, farFuture)

            val closed = manager.closeForDevice(laptop, revoked(SessionRevocationReason.DEVICE_REVOKED))

            assertEquals(1, closed)
            verify { laptopSession.close(revoked(SessionRevocationReason.DEVICE_REVOKED)) }
            verify(exactly = 0) { phoneSession.close(any()) }
            assertNull(manager.getUserId(laptopSession))
            assertEquals(userId, manager.getUserId(phoneSession))
            assertTrue(manager.isOnline(userId), "the user's other device keeps its socket")
        }

        @Test
        fun `should close nothing when the device has no open session`() {
            val phoneSession = openSession()
            manager.register(userId, phoneSession, phone, farFuture)

            assertEquals(0, manager.closeForDevice(laptop, revoked(SessionRevocationReason.LOGOUT)))
            verify(exactly = 0) { phoneSession.close(any()) }
        }

        @Test
        fun `should not find a device session again once it has been unregistered`() {
            val laptopSession = openSession()
            manager.register(userId, laptopSession, laptop, farFuture)
            manager.unregister(laptopSession)

            assertEquals(0, manager.closeForDevice(laptop, revoked(SessionRevocationReason.LOGOUT)))
        }
    }

    @Nested
    inner class CloseForUser {

        @Test
        fun `should close every device's session when the account is deleted`() {
            val phoneSession = openSession()
            val laptopSession = openSession()
            val strangerSession = openSession()
            val stranger = UUID.randomUUID()
            manager.register(userId, phoneSession, phone, farFuture)
            manager.register(userId, laptopSession, laptop, farFuture)
            manager.register(stranger, strangerSession, UUID.randomUUID(), farFuture)

            val closed = manager.closeForUser(userId, revoked(SessionRevocationReason.ACCOUNT_DELETED))

            assertEquals(2, closed)
            verify { phoneSession.close(revoked(SessionRevocationReason.ACCOUNT_DELETED)) }
            verify { laptopSession.close(revoked(SessionRevocationReason.ACCOUNT_DELETED)) }
            verify(exactly = 0) { strangerSession.close(any()) }
            assertFalse(manager.isOnline(userId))
            assertTrue(manager.isOnline(stranger))
        }
    }

    @Nested
    inner class TokenExpiry {

        @Test
        fun `should close a session with 4401 when its token has expired`() {
            val session = openSession()
            val expiresAt = Instant.now().plusSeconds(600)
            manager.register(userId, session, phone, expiresAt)

            val closed = manager.closeExpiredSessions(now = expiresAt.toEpochMilli() + 1)

            assertEquals(1, closed)
            verify { session.close(WebSocketSessionManager.TOKEN_EXPIRED) }
            assertEquals(WebSocketSessionManager.CLOSE_CODE_TOKEN_EXPIRED, WebSocketSessionManager.TOKEN_EXPIRED.code)
            assertFalse(manager.isOnline(userId))
        }

        @Test
        fun `should keep a session whose token is still valid`() {
            val session = openSession()
            val expiresAt = Instant.now().plusSeconds(600)
            manager.register(userId, session, phone, expiresAt)

            assertEquals(0, manager.closeExpiredSessions(now = expiresAt.toEpochMilli() - 1))
            verify(exactly = 0) { session.close(any()) }
            assertTrue(manager.isOnline(userId))
        }

        @Test
        fun `should close a session by itself when its token expires while it is open`() {
            val session = openSession()
            manager.register(userId, session, phone, Instant.now().plusMillis(300))

            // No sweep, no manual call: the one-shot task scheduled at register() does this.
            verify(timeout = 5_000) { session.close(WebSocketSessionManager.TOKEN_EXPIRED) }
        }

        @Test
        fun `should drop the cancelled expiry task from the scheduler queue when the session disconnects first`() {
            // Without removeOnCancelPolicy a cancelled task stays queued until its original delay —
            // up to the token lifetime — so the queue grew by one entry per reconnect.
            val sessions = List(20) { openSession() }
            sessions.forEach { manager.register(userId, it, phone, farFuture) }
            assertEquals(20, manager.pendingExpiryTaskCount())

            sessions.forEach { manager.unregister(it) }

            assertEquals(0, manager.pendingExpiryTaskCount())
        }

        @Test
        fun `should close expired sessions during the periodic sweep as a backstop`() {
            val session = openSession()
            val expiresAt = Instant.now().plusSeconds(600)
            manager.register(userId, session, phone, expiresAt)

            manager.reapStaleSessions(now = expiresAt.toEpochMilli() + 1)

            verify { session.close(WebSocketSessionManager.TOKEN_EXPIRED) }
        }
    }

    @Nested
    inner class RevocationAdapter {

        @Test
        fun `should close the device's socket within 5 seconds when the device is revoked outside a transaction`() {
            val adapter = WebSocketSessionRevocationAdapter(manager)
            val laptopSession = openSession()
            manager.register(userId, laptopSession, laptop, farFuture)

            val started = System.nanoTime()
            adapter.revokeDeviceSessions(userId, laptop, SessionRevocationReason.DEVICE_REVOKED)

            verify(timeout = 5_000) { laptopSession.close(revoked(SessionRevocationReason.DEVICE_REVOKED)) }
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            assertTrue(elapsedMs <= 5_000, "revocation must close the socket within 5 s, took $elapsedMs ms")
        }

        @Test
        fun `should wait for the commit before closing when a transaction is active`() {
            val adapter = WebSocketSessionRevocationAdapter(manager)
            val phoneSession = openSession()
            manager.register(userId, phoneSession, phone, farFuture)
            TransactionSynchronizationManager.initSynchronization()

            adapter.revokeUserSessions(userId, SessionRevocationReason.ACCOUNT_DELETED)

            verify(exactly = 0) { phoneSession.close(any()) }
            TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
            verify { phoneSession.close(revoked(SessionRevocationReason.ACCOUNT_DELETED)) }
        }

        @Test
        fun `should not close anything when the transaction rolls back`() {
            val adapter = WebSocketSessionRevocationAdapter(manager)
            val phoneSession = openSession()
            manager.register(userId, phoneSession, phone, farFuture)
            TransactionSynchronizationManager.initSynchronization()

            adapter.revokeDeviceSessions(userId, phone, SessionRevocationReason.LOGOUT)
            // A rollback runs afterCompletion only; afterCommit never fires.
            TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCompletion(1) }

            verify(exactly = 0) { phoneSession.close(any()) }
            assertTrue(manager.isOnline(userId))
        }
    }
}
