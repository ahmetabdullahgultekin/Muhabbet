package com.muhabbet.messaging.adapter.`in`.websocket

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketMessage
import org.springframework.web.socket.WebSocketSession
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * #490 — every write to a socket goes through one wrapper, so two writers cannot overlap.
 *
 * Before this, [WebSocketSessionManager] wrote under `synchronized(session)` and
 * [ChatWebSocketHandler] wrote to the same sessions in seven places without it. A monitor only half
 * the writers take protects nothing: Tomcat answers an overlap with
 * `IllegalStateException [TEXT_FULL_WRITING]`, and when the manager's write was the one that lost,
 * its catch dropped a perfectly healthy session from every map — the user disappeared from presence
 * and from every later broadcast while their socket was still open.
 *
 * The fake session below is what a raw one behaves like: it refuses a second concurrent send. If
 * the manager stops decorating, or a handler goes back to writing to the raw session, these fail.
 */
class SessionWriteSerialisationTest {

    /** Fails a send that begins while another is in progress, the way Tomcat's endpoint does. */
    private class SingleWriterSession(private val sessionId: String) : WebSocketSession by mockk(relaxed = true) {
        val writing = AtomicBoolean(false)
        val overlapped = AtomicBoolean(false)
        val sends = AtomicInteger(0)
        val started = CountDownLatch(1)
        var holdFirstSendUntil: CountDownLatch? = null

        override fun getId(): String = sessionId
        override fun isOpen(): Boolean = true

        override fun sendMessage(message: WebSocketMessage<*>) {
            if (!writing.compareAndSet(false, true)) {
                overlapped.set(true)
                throw IllegalStateException("The remote endpoint was in state [TEXT_FULL_WRITING]")
            }
            try {
                started.countDown()
                holdFirstSendUntil?.let { it.await(2, TimeUnit.SECONDS); holdFirstSendUntil = null }
                sends.incrementAndGet()
            } finally {
                writing.set(false)
            }
        }
    }

    private val manager = WebSocketSessionManager()
    private val userId = UUID.randomUUID()

    @Test
    fun `should serialise two concurrent writes when both go through the manager`() {
        val session = SingleWriterSession("s-1")
        manager.register(userId, session)
        val release = CountDownLatch(1)
        session.holdFirstSendUntil = release

        val broadcast = Thread { manager.sendToUser(userId, """{"type":"message.new"}""") }
        broadcast.start()
        assertTrue(session.started.await(2, TimeUnit.SECONDS), "the first write never began")

        // The handler's reply path, arriving while the broadcast is still writing. Raw, this is the
        // TEXT_FULL_WRITING collision; decorated, it queues.
        val replyAboutToWrite = CountDownLatch(1)
        val reply = Thread {
            replyAboutToWrite.countDown()
            manager.send(session, """{"type":"ack"}""")
        }
        reply.start()
        // Wait for the second writer to be at the door, then give it long enough to be through it.
        // Without this the release could win the race and the two writes would merely be
        // sequential, which is a test that cannot fail rather than a test that passes.
        assertTrue(replyAboutToWrite.await(2, TimeUnit.SECONDS), "the second writer never started")
        Thread.sleep(100)
        release.countDown()
        broadcast.join(3_000)
        reply.join(3_000)

        assertTrue(!session.overlapped.get(), "two writers were inside sendMessage at once")
        assertEquals(2, session.sends.get(), "one of the two frames was lost")
    }

    @Test
    fun `should keep a session online when a concurrent write would have collided`() {
        // The consequence that made this worth fixing: sendToUser's catch called forget(), so a
        // collision deleted a healthy session from all three maps.
        val session = SingleWriterSession("s-2")
        manager.register(userId, session)
        val release = CountDownLatch(1)
        session.holdFirstSendUntil = release

        val broadcast = Thread { manager.sendToUser(userId, "{}") }
        broadcast.start()
        session.started.await(2, TimeUnit.SECONDS)
        val replyAboutToWrite = CountDownLatch(1)
        val reply = Thread {
            replyAboutToWrite.countDown()
            manager.send(session, "{}")
        }
        reply.start()
        assertTrue(replyAboutToWrite.await(2, TimeUnit.SECONDS), "the second writer never started")
        Thread.sleep(100)
        release.countDown()
        broadcast.join(3_000)
        reply.join(3_000)

        assertTrue(manager.isOnline(userId), "a healthy session was dropped after a concurrent write")
    }

    @Test
    fun `should write directly when the session is not registered yet`() {
        // afterConnectionEstablished rejects a bad token before register(), so the two auth-failure
        // frames have no wrapper to go through. They must still be written.
        val session: WebSocketSession = mockk(relaxed = true)
        every { session.id } returns "unregistered"

        manager.send(session, """{"type":"error"}""")

        verify(exactly = 1) { session.sendMessage(any<TextMessage>()) }
    }
}
