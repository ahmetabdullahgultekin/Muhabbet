package com.muhabbet.auth.adapter.`in`.web

import com.muhabbet.auth.adapter.out.external.ModerationBlockDirectoryAdapter
import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.model.UserStatus
import com.muhabbet.auth.domain.port.out.BlockDirectoryPort
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.PresencePort
import com.muhabbet.moderation.domain.port.`in`.BlockUserUseCase
import com.muhabbet.shared.security.JwtClaims
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Instant
import java.util.UUID

/**
 * A blocked person must not be able to watch you (#551).
 *
 * `resolveVisibility` already applied the target's own `onlineStatusVisibility` and
 * `aboutVisibility`, but knew nothing about blocks — so blocking a harasser left them a live green
 * dot and a last-seen timestamp telling them when you were awake. That is the concrete harm; these
 * pin the short-circuit.
 *
 * And it must hold in **both** directions (#711): the guard originally asked only whether the
 * target had blocked the requester, so someone who blocked a harasser went on being shown that
 * harasser's dot, last seen and about. Blocking is not "be less visible to them", it is "I am done
 * with this person".
 *
 * These drive the **real** [ModerationBlockDirectoryAdapter] over a mocked `BlockUserUseCase`
 * rather than stubbing [BlockDirectoryPort] itself. The port answers one symmetric question, so a
 * mock of it could not tell the two directions apart — the adapter is where they exist, and it is
 * therefore the thing that has to be in the test.
 */
class UserProfileBlockVisibilityTest {

    private lateinit var userRepository: UserRepository
    private lateinit var presencePort: PresencePort
    private lateinit var conversationRepository: ConversationRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var blockUserUseCase: BlockUserUseCase
    private lateinit var blockDirectory: BlockDirectoryPort
    private lateinit var controller: UserController

    private val targetId: UUID = UUID.randomUUID()
    private val requesterId: UUID = UUID.randomUUID()

    private val target = User(
        id = targetId,
        phoneNumber = "+905000000001",
        displayName = "Target",
        about = "Hakkımda",
        status = UserStatus.ACTIVE,
        lastSeenAt = Instant.parse("2026-08-17T10:00:00Z")
    )

    @BeforeEach
    fun setUp() {
        userRepository = mockk()
        presencePort = mockk()
        conversationRepository = mockk(relaxed = true)
        messageRepository = mockk(relaxed = true)
        blockUserUseCase = mockk()
        blockDirectory = ModerationBlockDirectoryAdapter(blockUserUseCase)
        every { blockUserUseCase.isBlocked(any(), any()) } returns false

        controller = UserController(
            userRepository = userRepository,
            presencePort = presencePort,
            conversationRepository = conversationRepository,
            messageRepository = messageRepository,
            blockDirectory = blockDirectory
        )

        every { userRepository.findById(targetId) } returns target
        every { presencePort.isOnline(targetId) } returns true

        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            JwtClaims(userId = requesterId, deviceId = UUID.randomUUID()),
            null,
            emptyList()
        )
    }

    @AfterEach
    fun clearContext() {
        SecurityContextHolder.clearContext()
    }

    private fun targetBlocked(requester: Boolean) {
        every { blockUserUseCase.isBlocked(targetId, requesterId) } returns requester
    }

    private fun requesterBlocked(target: Boolean) {
        every { blockUserUseCase.isBlocked(requesterId, targetId) } returns target
    }

    @Test
    fun `should hide presence last seen and about from someone the target has blocked`() {
        targetBlocked(true)

        val profile = controller.getUserById(targetId).body?.data
        assertNotNull(profile)

        assertFalse(profile?.isOnline ?: true)
        assertNull(profile?.lastSeenAt)
        assertNull(profile?.about)
    }

    @Test
    fun `should hide presence last seen and about from someone who has blocked the target`() {
        // The mirror. Nobody stubs the other direction here, so this fails outright on a guard
        // that only asks "has the target blocked me".
        requesterBlocked(true)

        val profile = controller.getUserById(targetId).body?.data
        assertNotNull(profile)

        assertFalse(profile?.isOnline ?: true)
        assertNull(profile?.lastSeenAt)
        assertNull(profile?.about)
    }

    @Test
    fun `should still show the name and avatar to someone the target has blocked`() {
        // A shared chat from before the block would otherwise turn into an anonymous row.
        targetBlocked(true)

        val profile = controller.getUserById(targetId).body?.data

        assertEquals("Target", profile?.displayName)
    }

    @Test
    fun `should show presence and about when neither has blocked the other`() {
        val profile = controller.getUserById(targetId).body?.data
        assertNotNull(profile)

        assertTrue(profile?.isOnline ?: false)
        assertNotNull(profile?.lastSeenAt)
        assertEquals("Hakkımda", profile?.about)
    }

    @Test
    fun `should hide presence on the detail endpoint too`() {
        // Two endpoints read the same profile; a guard on only one of them is not a guard.
        targetBlocked(true)
        every { conversationRepository.findConversationsByUserId(any()) } returns emptyList()

        val detail = controller.getUserDetail(targetId).body?.data
        assertNotNull(detail)

        assertFalse(detail?.isOnline ?: true)
        assertNull(detail?.lastSeenAt)
        assertNull(detail?.about)
    }

    @Test
    fun `should hide presence on the detail endpoint for the blocker too`() {
        requesterBlocked(true)
        every { conversationRepository.findConversationsByUserId(any()) } returns emptyList()

        val detail = controller.getUserDetail(targetId).body?.data
        assertNotNull(detail)

        assertFalse(detail?.isOnline ?: true)
        assertNull(detail?.lastSeenAt)
        assertNull(detail?.about)
    }
}
