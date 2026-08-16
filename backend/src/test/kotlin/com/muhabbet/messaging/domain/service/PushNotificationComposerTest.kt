package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.port.out.NotificationTextPort
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Instant
import java.util.Locale
import java.util.UUID

/**
 * The two lines a phone actually shows. #469 arrived from a real device reading
 *
 *     Yeni mesaj
 *     Dorduncu test - cihaz 3 dakikadir cevrimdisi
 *
 * — a constant where the sender's name belongs, and the raw content as the body whatever the
 * message was. These tests pin the title (1:1 vs group), the per-type body, and the collapse key.
 *
 * The text port is mocked rather than pointed at the real bundles on purpose: what is under test
 * is *which* string is chosen, not how it is spelled. `NotificationTextCatalogTest` covers the
 * bundles.
 */
class PushNotificationComposerTest {

    private lateinit var texts: NotificationTextPort
    private lateinit var composer: PushNotificationComposer

    private val conversationId = UUID.randomUUID()
    private val senderId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        texts = mockk()
        every { texts.contentSummary(any(), any()) } answers { "summary:${firstArg<ContentType>().name}" }
        every { texts.groupTitle(any(), any(), any()) } answers { "${firstArg<String>()} · ${secondArg<String>()}" }
        every { texts.unknownSender(any()) } returns "Bilinmeyen"
        composer = PushNotificationComposer(texts)
    }

    private fun message(
        contentType: ContentType = ContentType.TEXT,
        content: String = "merhaba"
    ) = Message(
        id = UUID.randomUUID(),
        conversationId = conversationId,
        senderId = senderId,
        contentType = contentType,
        content = content,
        clientTimestamp = Instant.now()
    )

    private fun conversation(type: ConversationType, name: String? = null) =
        Conversation(id = conversationId, type = type, name = name)

    // ─── Title composition ───────────────────────────────

    @Test
    fun `should use the sender name as the title for a direct message`() {
        val push = composer.compose(message(), senderName = "Ayşe", conversation = conversation(ConversationType.DIRECT))

        assertEquals("Ayşe", push.title)
        assertNotEquals("Yeni mesaj", push.title, "The constant label #469 was filed about is back")
    }

    @Test
    fun `should use sender and group name as the title for a group message`() {
        val push = composer.compose(
            message(),
            senderName = "Ayşe",
            conversation = conversation(ConversationType.GROUP, name = "Aile")
        )

        assertEquals("Ayşe · Aile", push.title)
    }

    @Test
    fun `should use sender and channel name as the title for a channel message`() {
        val push = composer.compose(
            message(),
            senderName = "Ayşe",
            conversation = conversation(ConversationType.CHANNEL, name = "Duyurular")
        )

        assertEquals("Ayşe · Duyurular", push.title)
    }

    @Test
    fun `should fall back to the sender alone when a group has no name`() {
        val push = composer.compose(
            message(),
            senderName = "Ayşe",
            conversation = conversation(ConversationType.GROUP, name = "   ")
        )

        assertEquals("Ayşe", push.title, "A blank group name must not produce a trailing separator")
    }

    @Test
    fun `should fall back to the sender alone when the conversation could not be loaded`() {
        val push = composer.compose(message(), senderName = "Ayşe", conversation = null)

        assertEquals("Ayşe", push.title)
        assertEquals(ConversationType.DIRECT.name, push.data["conversationType"])
    }

    @Test
    fun `should use the unknown-sender text when the sender has no display name`() {
        val push = composer.compose(message(), senderName = null, conversation = conversation(ConversationType.DIRECT))

        assertEquals("Bilinmeyen", push.title)
        assertEquals("Bilinmeyen", push.data["senderName"])
    }

    @Test
    fun `should treat a blank display name as no display name`() {
        val push = composer.compose(message(), senderName = "  ", conversation = conversation(ConversationType.DIRECT))

        assertEquals("Bilinmeyen", push.title)
    }

    // ─── Body per content type ───────────────────────────

    @Test
    fun `should use the message content as the body for a text message`() {
        val push = composer.compose(message(content = "merhaba"), "Ayşe", conversation(ConversationType.DIRECT))

        assertEquals("merhaba", push.body)
    }

    @Test
    fun `should truncate a long text body`() {
        val push = composer.compose(message(content = "a".repeat(500)), "Ayşe", conversation(ConversationType.DIRECT))

        assertEquals(PushNotificationComposer.MAX_BODY_LENGTH, push.body.length)
    }

    @ParameterizedTest
    @EnumSource(value = ContentType::class, names = ["TEXT"], mode = EnumSource.Mode.EXCLUDE)
    fun `should summarise the body for every non-text content type`(contentType: ContentType) {
        // The content of a photo or a poll is a caption, a blob key or nothing — never a tray line.
        val push = composer.compose(
            message(contentType = contentType, content = "s3://bucket/blob"),
            "Ayşe",
            conversation(ConversationType.DIRECT)
        )

        assertEquals("summary:${contentType.name}", push.body)
    }

    @Test
    fun `should not leak media content into the body of a photo`() {
        val push = composer.compose(
            message(contentType = ContentType.IMAGE, content = ""),
            "Ayşe",
            conversation(ConversationType.DIRECT)
        )

        assertEquals("summary:IMAGE", push.body, "An empty body is what the photo push showed before #469")
    }

    // ─── Collapse key and payload ────────────────────────

    @Test
    fun `should key the collapse on the conversation so a chat occupies one tray entry`() {
        val first = composer.compose(message(content = "bir"), "Ayşe", conversation(ConversationType.DIRECT))
        val second = composer.compose(message(content = "iki"), "Ayşe", conversation(ConversationType.DIRECT))

        assertEquals(conversationId.toString(), first.collapseKey)
        assertEquals(first.collapseKey, second.collapseKey, "Two messages in one chat must collapse together")
    }

    @Test
    fun `should give different conversations different collapse keys`() {
        val other = Message(
            id = UUID.randomUUID(),
            conversationId = UUID.randomUUID(),
            senderId = senderId,
            content = "merhaba",
            clientTimestamp = Instant.now()
        )

        val a = composer.compose(message(), "Ayşe", conversation(ConversationType.DIRECT))
        val b = composer.compose(other, "Ayşe", null)

        assertNotEquals(a.collapseKey, b.collapseKey, "Collapsing two chats into one entry hides a message")
    }

    @Test
    fun `should carry the identifiers the client needs to open the conversation`() {
        val msg = message()

        val push = composer.compose(msg, "Ayşe", conversation(ConversationType.GROUP, name = "Aile"))

        // MuhabbetFirebaseMessagingService reads exactly these keys; the live broadcaster used to
        // send only the first two, so the client's own notification path had no name to show.
        assertEquals(conversationId.toString(), push.data["conversationId"])
        assertEquals(msg.id.toString(), push.data["messageId"])
        assertEquals(senderId.toString(), push.data["senderId"])
        assertEquals("Ayşe", push.data["senderName"])
        assertEquals("GROUP", push.data["conversationType"])
    }

    // ─── Locale seam ─────────────────────────────────────

    @Test
    fun `should fall back to Turkish when the recipient locale is unknown`() {
        composer.compose(message(contentType = ContentType.VOICE), "Ayşe", conversation(ConversationType.DIRECT))

        io.mockk.verify { texts.contentSummary(ContentType.VOICE, PushNotificationComposer.FALLBACK_LOCALE) }
    }

    @Test
    fun `should ask for text in the recipient locale when one is known`() {
        val english = Locale.ENGLISH

        composer.compose(
            message(contentType = ContentType.VOICE),
            "Ayşe",
            conversation(ConversationType.DIRECT),
            recipientLocale = english
        )

        io.mockk.verify { texts.contentSummary(ContentType.VOICE, english) }
    }
}
