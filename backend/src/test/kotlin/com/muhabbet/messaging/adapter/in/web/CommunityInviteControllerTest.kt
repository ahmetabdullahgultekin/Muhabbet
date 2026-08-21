package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.model.Community
import com.muhabbet.messaging.domain.model.CommunityInviteLink
import com.muhabbet.messaging.domain.port.`in`.CommunityInvitePreview
import com.muhabbet.messaging.domain.port.`in`.CommunitySummary
import com.muhabbet.messaging.domain.port.`in`.ManageCommunityInviteUseCase
import com.muhabbet.shared.TestData
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.security.JwtClaims
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import java.util.UUID
import com.muhabbet.shared.dto.CommunityInviteLinkResponse as ClientInviteLinkResponse
import com.muhabbet.shared.dto.CommunityInvitePreviewResponse as ClientInvitePreviewResponse

/**
 * The wire contract of the community invite endpoints (#387, #416).
 *
 * The controller is instantiated directly rather than driven through MockMvc: what is worth pinning
 * here is the mapping and the identity the controller passes down, not Spring's routing. Every
 * authorisation decision lives in [ManageCommunityInviteUseCase]'s implementation and is tested in
 * `CommunityInviteServiceTest`; what this class checks is that the controller hands it the
 * **authenticated** caller rather than anything from the request body, and that the JSON it produces
 * actually decodes into the client's own DTO.
 *
 * That last check is not ceremony. `InviteLinkController` — the group equivalent — returns a private
 * response class that omits two fields the shared `InviteLinkResponse` declares as required, so its
 * payload cannot decode on the device at all; `ignoreUnknownKeys` forgives extra fields, never
 * missing ones. These endpoints use the shared DTOs directly, and the round-trips below are what
 * would catch it if someone reintroduced a private copy.
 */
class CommunityInviteControllerTest {

    private val inviteUseCase = mockk<ManageCommunityInviteUseCase>()
    private lateinit var controller: CommunityInviteController

    private val userId = TestData.USER_ID_1
    private val communityId = UUID.randomUUID()
    private val createdAt = Instant.parse("2026-01-01T00:00:00Z")

    private val jackson = jacksonObjectMapper()
    private val clientJson = Json { ignoreUnknownKeys = true; isLenient = true }

    @BeforeEach
    fun setUp() {
        controller = CommunityInviteController(inviteUseCase)
        val claims = JwtClaims(userId = userId, deviceId = TestData.DEVICE_ID_1)
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(claims, null, emptyList())
    }

    @AfterEach
    fun tearDown() {
        // Otherwise the identity leaks into whatever test runs next on this thread.
        SecurityContextHolder.clearContext()
    }

    @Nested
    inner class CreateLink {

        @Test
        fun `should mint a link as the authenticated caller and return a usable url`() {
            every { inviteUseCase.createLink(communityId, userId, null, null) } returns link()

            val body = controller.createLink(
                communityId,
                com.muhabbet.shared.dto.CreateCommunityInviteRequest()
            ).body?.data
            val decoded = decodeLink(body)

            assertEquals("the-token", decoded.inviteToken)
            // Built by the server so the scheme can change without shipping an app, and so the
            // token never has to be pasted together on the device.
            assertEquals("muhabbet://community-invite/the-token", decoded.inviteUrl)
            assertTrue(decoded.isActive)
            assertEquals(0, decoded.useCount)
            assertNull(decoded.maxUses)
        }

        @Test
        fun `should pass the requester from the security context, not from the request`() {
            val requester = slot<UUID>()
            every { inviteUseCase.createLink(any(), capture(requester), any(), any()) } returns link()

            controller.createLink(communityId, com.muhabbet.shared.dto.CreateCommunityInviteRequest())

            assertEquals(userId, requester.captured)
        }

        @Test
        fun `should forward max uses and a parsed expiry`() {
            val expiry = Instant.parse("2027-01-01T00:00:00Z")
            every { inviteUseCase.createLink(communityId, userId, 5, expiry) } returns
                link(maxUses = 5, expiresAt = expiry)

            val decoded = decodeLink(
                controller.createLink(
                    communityId,
                    com.muhabbet.shared.dto.CreateCommunityInviteRequest(
                        maxUses = 5,
                        expiresAt = "2027-01-01T00:00:00Z"
                    )
                ).body?.data
            )

            assertEquals(5, decoded.maxUses)
            verify { inviteUseCase.createLink(communityId, userId, 5, expiry) }
        }

        @Test
        fun `should turn an unparseable expiry into a business error rather than a 500`() {
            // An unguarded Instant.parse throws DateTimeParseException, which the global handler
            // has no case for — the caller would get a 500 for a typo.
            val ex = assertThrows<BusinessException> {
                controller.createLink(
                    communityId,
                    com.muhabbet.shared.dto.CreateCommunityInviteRequest(expiresAt = "next tuesday")
                )
            }
            assertEquals(ErrorCode.COMMUNITY_INVITE_INVALID_EXPIRY, ex.errorCode)
        }

        @Test
        fun `should surface a permission failure from the use case`() {
            every { inviteUseCase.createLink(any(), any(), any(), any()) } throws
                BusinessException(ErrorCode.COMMUNITY_PERMISSION_DENIED)

            val ex = assertThrows<BusinessException> {
                controller.createLink(communityId, com.muhabbet.shared.dto.CreateCommunityInviteRequest())
            }
            assertEquals(ErrorCode.COMMUNITY_PERMISSION_DENIED, ex.errorCode)
        }
    }

    @Nested
    inner class RevokeLink {

        @Test
        fun `should pass both the community and the link id so the use case can check they agree`() {
            val linkId = UUID.randomUUID()
            every { inviteUseCase.revokeLink(communityId, linkId, userId) } returns Unit

            controller.revokeLink(communityId, linkId)

            verify { inviteUseCase.revokeLink(communityId, linkId, userId) }
        }
    }

    @Nested
    inner class ListLinks {

        @Test
        fun `should decode every listed link into the client shape`() {
            every { inviteUseCase.listLinks(communityId, userId) } returns
                listOf(link(), link(maxUses = 3, useCount = 1))

            val body = controller.listLinks(communityId).body?.data
            val decoded: List<ClientInviteLinkResponse> =
                clientJson.decodeFromString(jackson.writeValueAsString(body))

            assertEquals(2, decoded.size)
            assertEquals(3, decoded[1].maxUses)
            assertEquals(1, decoded[1].useCount)
        }
    }

    @Nested
    inner class Preview {

        @Test
        fun `should return only what a non-member is allowed to learn`() {
            every { inviteUseCase.preview("the-token", userId) } returns CommunityInvitePreview(
                communityId = communityId,
                name = "Mahalle",
                avatarUrl = null,
                memberCount = 12,
                inviterDisplayName = "Ayşe",
                alreadyMember = false
            )

            val decoded: ClientInvitePreviewResponse =
                clientJson.decodeFromString(jackson.writeValueAsString(controller.preview("the-token").body?.data))

            assertEquals("Mahalle", decoded.name)
            assertEquals(12, decoded.memberCount)
            assertEquals("Ayşe", decoded.inviterDisplayName)
            assertFalse(decoded.alreadyMember)
            // The shape itself is the guarantee: there is no groups field and no members field to
            // populate, so the leak #375 closed cannot come back through this door by accident.
            val raw = jackson.writeValueAsString(controller.preview("the-token").body?.data)
            assertFalse(raw.contains("groups"))
            assertFalse(raw.contains("members\""))
        }

        @Test
        fun `should pass the authenticated viewer so already-member can be answered`() {
            val viewer = slot<UUID>()
            every { inviteUseCase.preview(any(), capture(viewer)) } returns CommunityInvitePreview(
                communityId, "Mahalle", null, 1, null, true
            )

            controller.preview("the-token")

            assertEquals(userId, viewer.captured)
        }
    }

    @Nested
    inner class Accept {

        @Test
        fun `should return the joined community so the app can open it`() {
            every { inviteUseCase.accept("the-token", userId) } returns CommunitySummary(
                community = Community(
                    id = communityId,
                    name = "Mahalle",
                    description = "Komşular",
                    createdBy = TestData.USER_ID_2,
                    createdAt = createdAt
                ),
                groupCount = 2,
                memberCount = 13
            )

            val decoded: com.muhabbet.shared.dto.CommunityResponse =
                clientJson.decodeFromString(jackson.writeValueAsString(controller.accept("the-token").body?.data))

            assertEquals(communityId.toString(), decoded.id)
            assertEquals("Mahalle", decoded.name)
            assertEquals(13, decoded.memberCount)
            assertEquals(2, decoded.groupCount)
        }

        @Test
        fun `should join as the authenticated caller, never a caller-supplied id`() {
            val joiner = slot<UUID>()
            every { inviteUseCase.accept(any(), capture(joiner)) } returns CommunitySummary(
                community = Community(id = communityId, name = "Mahalle", createdBy = userId, createdAt = createdAt),
                groupCount = 0,
                memberCount = 1
            )

            controller.accept("the-token")

            assertEquals(userId, joiner.captured)
        }

        @Test
        fun `should surface an exhausted link`() {
            every { inviteUseCase.accept(any(), any()) } throws BusinessException(ErrorCode.INVITE_LINK_MAX_USES)

            val ex = assertThrows<BusinessException> { controller.accept("the-token") }
            assertEquals(ErrorCode.INVITE_LINK_MAX_USES, ex.errorCode)
        }
    }

    // ─── helpers ────────────────────────────────────────

    private fun decodeLink(body: Any?): ClientInviteLinkResponse =
        clientJson.decodeFromString(jackson.writeValueAsString(body))

    private fun link(
        maxUses: Int? = null,
        useCount: Int = 0,
        expiresAt: Instant? = null
    ) = CommunityInviteLink(
        communityId = communityId,
        inviteToken = "the-token",
        createdBy = userId,
        maxUses = maxUses,
        useCount = useCount,
        expiresAt = expiresAt,
        createdAt = createdAt
    )
}
