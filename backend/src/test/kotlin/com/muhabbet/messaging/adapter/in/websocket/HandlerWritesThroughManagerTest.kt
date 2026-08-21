package com.muhabbet.messaging.adapter.`in`.websocket

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

/**
 * #490 — the guard, rather than another example.
 *
 * The defect was not that one write was unguarded; it was that seven were, spread across ack, error,
 * call-busy, call-missed, call-room, pong and the generic error path, and that nothing said so. The
 * behavioural tests in [SessionWriteSerialisationTest] prove the manager serialises; this proves
 * nobody has quietly gone around it again, which is the shape the bug would take when it returns.
 *
 * Reading the source is a blunt instrument and deliberately chosen: the alternative is seven more
 * MockK tests that each assert one call site, and the eighth call site added next year would be
 * covered by none of them.
 */
class HandlerWritesThroughManagerTest {

    @Test
    fun `should route every reply through the session manager when the handler answers a frame`() {
        val source = File("src/main/kotlin/com/muhabbet/messaging/adapter/in/websocket/ChatWebSocketHandler.kt")
        require(source.exists()) { "handler source not found at ${source.absolutePath}" }

        val rawWrites = source.readLines()
            .withIndex()
            .filter { (_, line) -> line.contains("session.sendMessage(") }
            .map { (i, line) -> "${i + 1}: ${line.trim()}" }

        assertEquals(
            emptyList<String>(),
            rawWrites,
            "ChatWebSocketHandler must write through sessionManager.send(session, json), never to " +
                "the raw session: a write the manager does not own can collide with a broadcast or " +
                "a ping and take a healthy session down with it (#490)"
        )
    }
}
