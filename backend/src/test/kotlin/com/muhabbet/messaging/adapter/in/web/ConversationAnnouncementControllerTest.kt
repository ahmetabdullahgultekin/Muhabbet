package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.port.`in`.CreateConversationUseCase
import com.muhabbet.messaging.domain.port.`in`.GetConversationsUseCase
import com.muhabbet.messaging.domain.port.`in`.ManageGroupUseCase
import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.service.PresenceVisibility
import com.muhabbet.messaging.domain.port.out.PresencePort
import com.muhabbet.shared.TestData
import com.muhabbet.shared.dto.SetAnnouncementModeRequest
import com.muhabbet.shared.security.JwtClaims
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

/**
 * The route half of #509.
 *
 * The group screen used to PATCH `{"announcementOnly": …}` at `/conversations/{id}`, whose body is
 * `UpdateGroupRequest` and carries no such field; the value was dropped, the server answered 200,
 * and the switch reported success. What is pinned here is the endpoint that actually exists: that
 * it binds the flag off the shared [SetAnnouncementModeRequest], hands it to the use case with the
 * authenticated caller, and returns **what the server stored** rather than what was asked for.
 *
 * The last of those is not pedantry. If the reply merely echoed the request, a client that renders
 * the response is back to rendering its own guess, which is the bug.
 */
class ConversationAnnouncementControllerTest {

    private lateinit var manageGroupUseCase: ManageGroupUseCase
    private lateinit var controller: ConversationController

    private val me = TestData.USER_ID_1
    private val conversationId = UUID.randomUUID()

    private fun group(announcementOnly: Boolean) = Conversation(
        id = conversationId,
        type = ConversationType.GROUP,
        name = "Test Group",
        announcementOnly = announcementOnly
    )

    @BeforeEach
    fun setUp() {
        manageGroupUseCase = mockk()

        controller = ConversationController(
            createConversationUseCase = mockk<CreateConversationUseCase>(),
            getConversationsUseCase = mockk<GetConversationsUseCase>(),
            manageGroupUseCase = manageGroupUseCase,
            conversationRepository = mockk<ConversationRepository>(relaxed = true),
            userRepository = mockk<UserRepository>(),
            presencePort = mockk<PresencePort>(),
            presenceVisibility = PresenceVisibility(
                mockk<BlockPolicyPort>(relaxed = true),
                mockk<ConversationRepository>(relaxed = true)
            )
        )

        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            JwtClaims(userId = me, deviceId = TestData.DEVICE_ID_1),
            null,
            emptyList()
        )
    }

    @AfterEach
    fun clearContext() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `should pass the enabled flag and the authenticated caller to the use case`() {
        every { manageGroupUseCase.setAnnouncementMode(conversationId, me, true) } returns group(true)

        val response = controller.setAnnouncementMode(conversationId, SetAnnouncementModeRequest(enabled = true))

        verify { manageGroupUseCase.setAnnouncementMode(conversationId, me, true) }
        assertTrue(response.body?.data?.announcementOnly == true)
    }

    @Test
    fun `should carry a false through rather than treating any request as turning it on`() {
        every { manageGroupUseCase.setAnnouncementMode(conversationId, me, false) } returns group(false)

        val response = controller.setAnnouncementMode(conversationId, SetAnnouncementModeRequest(enabled = false))

        verify { manageGroupUseCase.setAnnouncementMode(conversationId, me, false) }
        assertFalse(response.body?.data?.announcementOnly == true)
    }

    /**
     * A contrived divergence — the use case returns a conversation whose flag disagrees with the
     * request — because it is the only way to tell an echo apart from a read. The response must
     * follow the stored conversation.
     */
    @Test
    fun `should report what was stored, not what was asked for`() {
        every { manageGroupUseCase.setAnnouncementMode(conversationId, me, true) } returns group(false)

        val response = controller.setAnnouncementMode(conversationId, SetAnnouncementModeRequest(enabled = true))

        assertEquals(false, response.body?.data?.announcementOnly)
    }
}
