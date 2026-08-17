package com.muhabbet.messaging.adapter.out

import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.messaging.adapter.`in`.websocket.WebSocketSessionManager
import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.PushNotificationPort
import com.muhabbet.messaging.domain.port.out.UserDirectoryPort
import com.muhabbet.messaging.domain.service.PushNotificationComposer
import com.muhabbet.shared.protocol.WsMessage
import com.muhabbet.shared.protocol.wsJson
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * The live delivery path for a view-once photo.
 *
 * A recipient with the chat already open builds their bubble from this frame and nothing else, so
 * everything the seal depends on has to be in it — and, just as importantly, the blob URL has to not
 * be. Before #515 `WsMessage.NewMessage` had no `viewOnce` field at all: the flag reached the
 * database and stopped, and the recipient was handed the photo in full with a working URL.
 */
class WebSocketMessageBroadcasterViewOnceTest {

    private val sessionManager: WebSocketSessionManager = mockk(relaxed = true)
    private val deviceRepository: DeviceRepository = mockk(relaxed = true)
    private val pushNotificationPort: PushNotificationPort = mockk(relaxed = true)
    private val userDirectory: UserDirectoryPort = mockk(relaxed = true)
    private val conversationRepository: ConversationRepository = mockk(relaxed = true)
    private val pushComposer: PushNotificationComposer = mockk(relaxed = true)

    private val broadcaster = WebSocketMessageBroadcaster(
        sessionManager = sessionManager,
        deviceRepository = deviceRepository,
        pushNotificationPort = pushNotificationPort,
        userDirectory = userDirectory,
        conversationRepository = conversationRepository,
        pushComposer = pushComposer
    )

    private fun photo(viewOnce: Boolean) = Message(
        id = UUID.randomUUID(),
        conversationId = UUID.randomUUID(),
        senderId = UUID.randomUUID(),
        contentType = ContentType.IMAGE,
        content = "Photo",
        mediaUrl = "https://cdn.example/blob.jpg?X-Amz-Signature=deadbeef",
        thumbnailUrl = "https://cdn.example/thumb.jpg?X-Amz-Signature=deadbeef",
        viewOnce = viewOnce,
        clientTimestamp = Instant.now(),
        serverTimestamp = Instant.now()
    )

    private fun broadcastAndDecode(message: Message): WsMessage.NewMessage {
        val recipient = UUID.randomUUID()
        every { sessionManager.isOnline(recipient) } returns true
        val payload = slot<String>()
        every { sessionManager.sendToUser(recipient, capture(payload)) } returns Unit

        broadcaster.broadcastMessage(message, listOf(recipient))

        verify { sessionManager.sendToUser(recipient, any()) }
        return wsJson.decodeFromString<WsMessage>(payload.captured) as WsMessage.NewMessage
    }

    @Test
    fun `should tell the recipient a photo is view-once`() {
        val frame = broadcastAndDecode(photo(viewOnce = true))

        assertTrue(frame.viewOnce)
    }

    @Test
    fun `should withhold the blob url from the view-once frame`() {
        val frame = broadcastAndDecode(photo(viewOnce = true))

        // Presigned: anyone holding this string can fetch the full-resolution image with no
        // credential. Shipping it alongside the seal would make the seal a drawing of one.
        assertNull(frame.mediaUrl)
        assertNull(frame.thumbnailUrl)
    }

    @Test
    fun `should deliver an ordinary photo unchanged`() {
        val message = photo(viewOnce = false)

        val frame = broadcastAndDecode(message)

        assertTrue(!frame.viewOnce)
        org.junit.jupiter.api.Assertions.assertEquals(message.mediaUrl, frame.mediaUrl)
    }
}
