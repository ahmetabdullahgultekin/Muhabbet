package com.muhabbet.messaging.adapter.out.external

import com.muhabbet.auth.domain.model.Device
import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.model.PushNotification
import com.muhabbet.messaging.domain.port.out.PushNotificationPort
import com.muhabbet.messaging.domain.service.PushNotificationComposer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * The last hop of a push: the recipient's device rows, and the language each one reads.
 *
 * #469 shipped "Yeni mesaj" to every phone on earth. #476 moved the wording into resource bundles
 * and gave the composer a `recipientLocale` — and then every caller passed null, so
 * `notification-messages_en.properties` sat in the tree unreachable and an English-locale user was
 * still notified in Turkish. `devices.locale` (V22) is where the answer now lives, and this is the
 * class that reads it.
 *
 * The real bundles and the real composer are used rather than mocks: what is being proved is that
 * an English device is handed English *words*, which a mocked text port cannot tell you.
 */
class OfflinePushSenderTest {

    private val deviceRepository: DeviceRepository = mockk()
    private val pushNotificationPort: PushNotificationPort = mockk(relaxed = true)
    private val composer = PushNotificationComposer(
        MessageSourceNotificationTextAdapter(NotificationTextConfig().notificationMessageSource())
    )

    private val sender = OfflinePushSender(deviceRepository, pushNotificationPort, composer)

    private val recipientId = UUID.randomUUID()
    private val conversationId = UUID.randomUUID()

    private fun voiceMessage() = Message(
        id = UUID.randomUUID(),
        conversationId = conversationId,
        senderId = UUID.randomUUID(),
        contentType = ContentType.VOICE,
        content = "s3://bucket/blob.ogg",
        clientTimestamp = Instant.now()
    )

    private fun device(token: String, locale: String?) = Device(
        userId = recipientId,
        platform = "android",
        pushToken = token,
        locale = locale
    )

    private fun directConversation() =
        Conversation(id = conversationId, type = ConversationType.DIRECT)

    private fun sendTo(vararg devices: Device): List<PushNotification> {
        every { deviceRepository.findByUserIdIn(listOf(recipientId)) } returns devices.toList()
        val sent = mutableListOf<PushNotification>()
        every { pushNotificationPort.sendPush(any(), capture(sent)) } returns Unit

        sender.sendTo(recipientId, voiceMessage(), senderName = "Ayşe", conversation = directConversation())

        return sent
    }

    @Test
    fun `should write the push in the language the device registered`() {
        val sent = sendTo(device("token-en", locale = "en"))

        assertEquals("🎙️ Voice message", sent.single().body)
    }

    @Test
    fun `should write the push in Turkish for a Turkish device`() {
        val sent = sendTo(device("token-tr", locale = "tr"))

        assertEquals("🎙️ Sesli mesaj", sent.single().body)
    }

    @Test
    fun `should fall back to Turkish for a device that never registered a language`() {
        // Every row in production before V22, and every device still on an older build. The
        // fallback has to be the behaviour that shipped, not an error and not English.
        val sent = sendTo(device("token-legacy", locale = null))

        assertEquals("🎙️ Sesli mesaj", sent.single().body)
    }

    @Test
    fun `should give each device of one user its own language`() {
        // A Turkish phone and an English tablet are two rows and two answers. Composing once for
        // the user would have to pick one of them and be wrong for the other.
        val sent = sendTo(device("token-tr", locale = "tr"), device("token-en", locale = "en"))

        assertEquals(listOf("🎙️ Sesli mesaj", "🎙️ Voice message"), sent.map { it.body })
    }

    @Test
    fun `should fall back to Turkish for a language with no bundle`() {
        val sent = sendTo(device("token-de", locale = "de"))

        assertEquals("🎙️ Sesli mesaj", sent.single().body)
    }

    @Test
    fun `should still name the sender in the title`() {
        // The headline defect of #469. Regression guard: the language work must not walk it back.
        val sent = sendTo(device("token-en", locale = "en"))

        assertEquals("Ayşe", sent.single().title)
    }

    @Test
    fun `should compose once per language rather than once per device`() {
        // sendMessage is the busiest path in the app (#491/#492). Two devices reading the same
        // language must not cost two bundle lookups.
        val countingComposer: PushNotificationComposer = mockk()
        every { countingComposer.compose(any(), any(), any(), any()) } returns
            PushNotification("t", "b", conversationId.toString(), emptyMap())
        val sender = OfflinePushSender(deviceRepository, pushNotificationPort, countingComposer)
        every { deviceRepository.findByUserIdIn(listOf(recipientId)) } returns
            listOf(device("a", "tr"), device("b", "tr"), device("c", "tr"))

        sender.sendTo(recipientId, voiceMessage(), "Ayşe", directConversation())

        verify(exactly = 1) { countingComposer.compose(any(), any(), any(), any()) }
        verify(exactly = 3) { pushNotificationPort.sendPush(any(), any()) }
    }

    @Test
    fun `should read the device rows exactly once`() {
        sendTo(device("token-tr", locale = "tr"), device("token-en", locale = "en"))

        verify(exactly = 1) { deviceRepository.findByUserIdIn(listOf(recipientId)) }
    }

    @Test
    fun `should skip a device with no push token`() {
        val sent = sendTo(device("token-en", locale = "en"), Device(userId = recipientId, platform = "ios"))

        assertEquals(1, sent.size)
    }

    @Test
    fun `should not throw when the device lookup fails`() {
        // The message itself is already stored and will arrive on reconnect. A failed courtesy push
        // must not abort the loop and cost the remaining recipients theirs.
        every { deviceRepository.findByUserIdIn(listOf(recipientId)) } throws IllegalStateException("db down")

        sender.sendTo(recipientId, voiceMessage(), "Ayşe", directConversation())
    }
}
