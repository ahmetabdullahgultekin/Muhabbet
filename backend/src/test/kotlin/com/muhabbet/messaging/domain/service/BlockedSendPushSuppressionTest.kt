package com.muhabbet.messaging.domain.service

import com.muhabbet.auth.domain.model.Device
import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.messaging.adapter.`in`.websocket.WebSocketSessionManager
import com.muhabbet.messaging.adapter.out.WebSocketMessageBroadcaster
import com.muhabbet.messaging.adapter.out.external.OfflinePushSender
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.PushNotification
import com.muhabbet.messaging.domain.port.`in`.SendMessageCommand
import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.PushNotificationPort
import com.muhabbet.messaging.domain.port.out.ReadReceiptPolicyPort
import com.muhabbet.messaging.domain.port.out.UserDirectoryPort
import com.muhabbet.shared.InlineTransactionRunner
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * #294, vector 3 — a blocked person must not be able to make the blocker's phone light up.
 *
 * Every other block test in this repository stops one layer short of the thing a victim actually
 * experiences. `MessagingServiceTest` proves the broadcaster is never called; it does not prove
 * that nothing reaches FCM, because the push fan-out does not live in `MessageService` — it lives
 * two hops away in [OfflinePushSender], behind [WebSocketMessageBroadcaster], and neither of them
 * has ever heard of a block. They are correct today only as a *consequence* of the message being
 * dropped before it is saved.
 *
 * A consequence is worth a test precisely because it is not a guard. Nobody editing
 * `WebSocketMessageBroadcaster` would think they were touching moderation code. So this wires the
 * real chain — service, real broadcaster, real push sender — and mocks only the two edges that
 * cannot exist in a unit test (the socket registry and FCM itself). What it asserts is the only
 * claim that matters to the person who pressed Block: **`sendPush` is never called.**
 *
 * The mirrored positive case is not decoration. Without it this file would pass just as well
 * against a build where push was broken outright, and would then be evidence of nothing.
 */
class BlockedSendPushSuppressionTest {

    private lateinit var conversationRepository: ConversationRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var userDirectory: UserDirectoryPort
    private lateinit var readReceiptPolicy: ReadReceiptPolicyPort
    private lateinit var blockPolicy: BlockPolicyPort
    private lateinit var sessionManager: WebSocketSessionManager
    private lateinit var deviceRepository: DeviceRepository
    private lateinit var pushNotificationPort: PushNotificationPort
    private lateinit var messageService: MessageService

    private val harasser = UUID.randomUUID()
    private val victim = UUID.randomUUID()
    private val conversationId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        conversationRepository = mockk(relaxed = true)
        messageRepository = mockk(relaxed = true)
        userDirectory = mockk(relaxed = true)
        readReceiptPolicy = mockk(relaxed = true)
        blockPolicy = mockk()
        sessionManager = mockk(relaxed = true)
        deviceRepository = mockk()
        pushNotificationPort = mockk(relaxed = true)

        val pushComposer = mockk<PushNotificationComposer>()
        every { pushComposer.compose(any(), any(), any(), any()) } returns PushNotification(
            title = "Harasser",
            body = "let me back in",
            collapseKey = conversationId.toString(),
            data = emptyMap()
        )

        // The victim is offline with a live push token, which is the case where a push is owed —
        // so an assertion that none is sent means the block stopped it, not the circumstances.
        every { sessionManager.isOnline(victim) } returns false
        every { sessionManager.isViewingConversation(any(), any()) } returns false
        every { deviceRepository.findByUserIdIn(any()) } returns listOf(
            Device(userId = victim, platform = "android", pushToken = "fcm-token-victim", locale = "tr")
        )

        val broadcaster = WebSocketMessageBroadcaster(
            sessionManager = sessionManager,
            userDirectory = userDirectory,
            conversationRepository = conversationRepository,
            offlinePushSender = OfflinePushSender(
                deviceRepository = deviceRepository,
                pushNotificationPort = pushNotificationPort,
                pushComposer = pushComposer
            )
        )

        messageService = MessageService(
            conversationRepository = conversationRepository,
            messageRepository = messageRepository,
            messageBroadcaster = broadcaster,
            userDirectory = userDirectory,
            readReceiptPolicy = readReceiptPolicy,
            blockPolicy = blockPolicy,
            transactions = InlineTransactionRunner(),
            mediaAttachmentPolicy = mockk(relaxed = true)
        )

        val members = listOf(
            ConversationMember(conversationId = conversationId, userId = harasser),
            ConversationMember(conversationId = conversationId, userId = victim)
        )
        every { conversationRepository.findMember(conversationId, harasser) } returns members[0]
        every { conversationRepository.findById(conversationId) } returns
            Conversation(id = conversationId, type = ConversationType.DIRECT, createdBy = victim)
        every { conversationRepository.findMembersByConversationId(conversationId) } returns members
        every { messageRepository.existsById(any()) } returns false
        every { messageRepository.save(any()) } answers { firstArg() }
    }

    private fun send() = messageService.sendMessage(
        SendMessageCommand(
            messageId = UUID.randomUUID(),
            conversationId = conversationId,
            senderId = harasser,
            content = "let me back in",
            clientTimestamp = Instant.now()
        )
    )

    @Test
    fun `should not push anything to a user who has blocked the sender`() {
        every { blockPolicy.hasBlocked(victim, harasser) } returns true

        send()

        verify(exactly = 0) { pushNotificationPort.sendPush(any(), any()) }
    }

    @Test
    fun `should not even look up the blocker's devices`() {
        // The device query is where the push token is read. Never reaching it means there is no
        // window in which a later change to the fan-out could start using one.
        every { blockPolicy.hasBlocked(victim, harasser) } returns true

        send()

        verify(exactly = 0) { deviceRepository.findByUserIdIn(any()) }
    }

    @Test
    fun `should push normally when nobody has blocked anybody`() {
        // The control. Without this the two assertions above would pass against a build where push
        // was simply broken, and would prove nothing about blocking at all.
        every { blockPolicy.hasBlocked(any(), any()) } returns false

        send()

        verify(exactly = 1) { pushNotificationPort.sendPush("fcm-token-victim", any()) }
    }

    @Test
    fun `should push normally when the sender is the one who blocked`() {
        // Directional here too: blocking someone does not silence your own outgoing notification
        // to them.
        every { blockPolicy.hasBlocked(victim, harasser) } returns false
        every { blockPolicy.hasBlocked(harasser, victim) } returns true

        send()

        verify(exactly = 1) { pushNotificationPort.sendPush("fcm-token-victim", any()) }
    }
}
