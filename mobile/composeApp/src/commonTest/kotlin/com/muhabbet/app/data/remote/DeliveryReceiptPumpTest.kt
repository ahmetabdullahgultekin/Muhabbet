package com.muhabbet.app.data.remote

import com.muhabbet.app.data.local.FakeMessageCache
import com.muhabbet.app.data.local.FakeTokenStorage
import com.muhabbet.shared.model.ContentType
import com.muhabbet.shared.model.MessageStatus
import com.muhabbet.shared.protocol.WsMessage
import com.muhabbet.shared.protocol.wsJson
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Persist, then acknowledge (#753, ADR 0013 P4).
 *
 * A DELIVERED receipt is what turns the sender's one tick into two. It used to be sent the moment a
 * `message.new` frame arrived, while nothing at all wrote the message to the device: the frame went
 * into a non-replaying flow, a screen drew it if one happened to be listening, and the process could
 * die a second later. The sender then saw "delivered" for a message that existed nowhere on the
 * phone it was delivered to.
 *
 * These pin the order — the cache holds the message before a receipt is asked for — and the failure
 * direction: a write that does not land sends no receipt, because an unacknowledged message is
 * redelivered and an acknowledged one is not.
 */
class DeliveryReceiptPumpTest {

    private val frames = MutableSharedFlow<WsMessage>(extraBufferCapacity = 16)

    @Test
    fun `should hold the message in the cache before the receipt is sent when a message arrives`() = runTest {
        val cache = FakeMessageCache()
        val storedWhenAcked = mutableListOf<List<String>>()
        val pump = DeliveryReceiptPump(frames, cache, sendAck = { _ ->
            storedWhenAcked += cache.stored.map { it.id }
            true
        }, storageContext = EmptyCoroutineContext)
        backgroundScope.launch { pump.run(currentUserId = ME) }
        runCurrent()

        frames.emit(incoming(MESSAGE))
        runCurrent()

        assertEquals(listOf(listOf(MESSAGE)), storedWhenAcked, "the receipt must follow the write, never precede it")
        assertEquals(MessageStatus.DELIVERED, cache.stored.single().status)
    }

    @Test
    fun `should send no receipt when the cache write fails`() = runTest {
        val acks = mutableListOf<WsMessage.AckMessage>()
        val pump = DeliveryReceiptPump(
            frames,
            FakeMessageCache(failOnUpsert = true),
            sendAck = { acks += it; true },
            storageContext = EmptyCoroutineContext
        )
        backgroundScope.launch { pump.run(currentUserId = ME) }
        runCurrent()

        frames.emit(incoming(MESSAGE))
        runCurrent()

        assertTrue(acks.isEmpty(), "two ticks for a message the phone could not keep is the bug")
    }

    @Test
    fun `should keep pumping after a failed write when the next message arrives`() = runTest {
        // One bad write must not end the app-wide pump: it is the only thing sending receipts.
        var failNext = true
        val cache = object : com.muhabbet.app.data.local.MessageCache {
            val inner = FakeMessageCache()
            override fun getMessagesByPage(conversationId: String, limit: Int) = inner.getMessagesByPage(conversationId, limit)
            override fun upsertMessages(messages: List<com.muhabbet.shared.model.Message>) {
                if (failNext) { failNext = false; throw IllegalStateException("disk full") }
                inner.upsertMessages(messages)
            }
        }
        val acks = mutableListOf<String>()
        val pump = DeliveryReceiptPump(frames, cache, sendAck = { acks += it.messageId; true }, storageContext = EmptyCoroutineContext)
        backgroundScope.launch { pump.run(currentUserId = ME) }
        runCurrent()

        frames.emit(incoming("msg-a"))
        frames.emit(incoming("msg-b"))
        runCurrent()

        assertEquals(listOf("msg-b"), acks)
    }

    @Test
    fun `should neither store nor acknowledge a message when this user sent it`() = runTest {
        val cache = FakeMessageCache()
        val acks = mutableListOf<WsMessage.AckMessage>()
        val pump = DeliveryReceiptPump(frames, cache, sendAck = { acks += it; true }, storageContext = EmptyCoroutineContext)
        backgroundScope.launch { pump.run(currentUserId = ME) }
        runCurrent()

        frames.emit(incoming(MESSAGE, senderId = ME))
        runCurrent()

        assertTrue(acks.isEmpty())
        assertTrue(cache.stored.isEmpty())
    }

    @Test
    fun `should put exactly one DELIVERED on the wire per message when a frame arrives twice`() = runTest {
        // End to end over the real client: the frame comes off a socket, through the duplicate
        // filter, into the pump, and the receipt goes back out on the same socket. #346 was two
        // collectors each acking every message; there is now one, and this counts what it sends.
        val sessions = mutableListOf<FakeWebSocketSession>()
        val ws = WsClient(
            apiClient = ApiClient(FakeTokenStorage(), MockEngine { respond("", HttpStatusCode.OK) }),
            tokenProvider = { "a-token" },
            scope = backgroundScope,
            openSession = { FakeWebSocketSession().also { sessions.add(it) } }
        )
        val cache = FakeMessageCache()
        val pump = DeliveryReceiptPump(ws.incoming, cache, ws::sendAck, storageContext = EmptyCoroutineContext)
        backgroundScope.launch { pump.run(currentUserId = ME) }
        ws.connect()
        runCurrent()

        val text = wsJson.encodeToString(WsMessage.serializer(), incoming(MESSAGE))
        sessions.single().deliver(text)
        sessions.single().deliver(text)
        runCurrent()

        val delivered = sessions.single().writtenText()
            .map { wsJson.decodeFromString(WsMessage.serializer(), it) }
            .filterIsInstance<WsMessage.AckMessage>()
            .filter { it.status == MessageStatus.DELIVERED }
        assertEquals(listOf(MESSAGE), delivered.map { it.messageId })
        assertEquals(listOf(MESSAGE), cache.stored.map { it.id })
    }

    private fun incoming(id: String, senderId: String = "user-b") = WsMessage.NewMessage(
        messageId = id,
        conversationId = CONVERSATION,
        senderId = senderId,
        senderName = null,
        content = "hello",
        contentType = ContentType.TEXT,
        serverTimestamp = 1_000L
    )

    private companion object {
        const val ME = "user-a"
        const val MESSAGE = "msg-1"
        const val CONVERSATION = "conv-1"
    }
}
