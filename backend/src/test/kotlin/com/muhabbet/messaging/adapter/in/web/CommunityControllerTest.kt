package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.model.Community
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.port.`in`.CommunityDetails
import com.muhabbet.messaging.domain.port.`in`.CommunityGroupSummary
import com.muhabbet.messaging.domain.port.`in`.CommunitySummary
import com.muhabbet.messaging.domain.port.`in`.ManageCommunityUseCase
import com.muhabbet.shared.TestData
import com.muhabbet.shared.security.JwtClaims
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import java.util.UUID
import com.muhabbet.shared.dto.CommunityDetailResponse as ClientCommunityDetailResponse
import com.muhabbet.shared.dto.CommunityResponse as ClientCommunityResponse

/**
 * The Communities screen broke because the JSON these endpoints emit could not be decoded into
 * the DTOs in `shared/.../dto/Dtos.kt`, which both sides are supposed to share. So these tests do
 * not check field names by eye: they serialise the controller's own response with Jackson — the
 * library Spring Boot 4 uses on the wire — and then decode it with the client's own DTO and
 * `Json` settings. A renamed or dropped required field fails the decode, exactly as it did on the
 * phone.
 */
class CommunityControllerTest {

    private lateinit var manageCommunityUseCase: ManageCommunityUseCase
    private lateinit var controller: CommunityController

    private val jackson = jacksonObjectMapper()
    private val clientJson = Json { ignoreUnknownKeys = true; isLenient = true }

    private val userId = TestData.USER_ID_1
    private val communityId = UUID.randomUUID()
    private val conversationId = UUID.randomUUID()
    private val createdAt = Instant.parse("2026-01-01T00:00:00Z")

    @BeforeEach
    fun setUp() {
        manageCommunityUseCase = mockk()
        controller = CommunityController(manageCommunityUseCase)
        setAuthenticatedUser(userId, TestData.DEVICE_ID_1)
    }

    @Nested
    inner class ListMyCommunities {

        @Test
        fun `should serve group and member counts the client can decode`() {
            every { manageCommunityUseCase.listForUser(userId) } returns listOf(
                CommunitySummary(community = community(), groupCount = 4, memberCount = 128)
            )

            val decoded = decodeList(controller.listMyCommunities().body?.data)

            assertEquals(1, decoded.size)
            assertEquals(communityId.toString(), decoded[0].id)
            assertEquals("Mahalle", decoded[0].name)
            assertEquals("Komsular", decoded[0].description)
            assertEquals(4, decoded[0].groupCount)
            assertEquals(128, decoded[0].memberCount)
            assertEquals(createdAt.toString(), decoded[0].createdAt)
        }

        @Test
        fun `should serve an empty list when the user has no communities`() {
            every { manageCommunityUseCase.listForUser(userId) } returns emptyList()

            assertEquals(0, decodeList(controller.listMyCommunities().body?.data).size)
        }
    }

    @Nested
    inner class Create {

        @Test
        fun `should serve the created community with its starting counts`() {
            every { manageCommunityUseCase.create("Mahalle", "Komsular", userId) } returns
                CommunitySummary(community = community(), groupCount = 0, memberCount = 1)

            val response = controller.create(CreateCommunityRequest("Mahalle", "Komsular"))
            val decoded = clientJson.decodeFromString<ClientCommunityResponse>(
                jackson.writeValueAsString(response.body?.data)
            )

            assertEquals(201, response.statusCode.value())
            assertEquals(0, decoded.groupCount)
            assertEquals(1, decoded.memberCount)
        }
    }

    @Nested
    inner class Update {

        @Test
        fun `should serve the renamed community with its counts intact`() {
            every {
                manageCommunityUseCase.update(communityId, userId, "Yeni Mahalle", "Yeni açıklama")
            } returns CommunitySummary(
                community = community().copy(name = "Yeni Mahalle", description = "Yeni açıklama"),
                groupCount = 4,
                memberCount = 128
            )

            val decoded = clientJson.decodeFromString<ClientCommunityResponse>(
                jackson.writeValueAsString(
                    controller.update(communityId, UpdateCommunityRequest("Yeni Mahalle", "Yeni açıklama")).body?.data
                )
            )

            assertEquals("Yeni Mahalle", decoded.name)
            assertEquals("Yeni açıklama", decoded.description)
            assertEquals(4, decoded.groupCount)
            assertEquals(128, decoded.memberCount)
        }

        @Test
        fun `should pass the authenticated caller to the use case`() {
            // Authorization is the use case's job, but only if it is told who is asking: the
            // controller must never take a user id from the request body.
            every { manageCommunityUseCase.update(communityId, userId, "Mahalle", null) } returns
                CommunitySummary(community = community(), groupCount = 0, memberCount = 1)

            controller.update(communityId, UpdateCommunityRequest("Mahalle", null))

            verify { manageCommunityUseCase.update(communityId, userId, "Mahalle", null) }
        }
    }

    @Nested
    inner class GetDetails {

        @Test
        fun `should serve a flat detail shape the client can decode`() {
            val announcementGroupId = UUID.randomUUID()
            every { manageCommunityUseCase.getDetails(communityId, userId) } returns CommunityDetails(
                community = community(),
                groups = listOf(
                    CommunityGroupSummary(
                        conversationId = conversationId,
                        name = "Bahçe Katı",
                        avatarUrl = "https://cdn.example/g.jpg",
                        memberCount = 12
                    )
                ),
                memberCount = 128,
                myRole = MemberRole.OWNER,
                announcementGroupId = announcementGroupId
            )

            val decoded = decodeDetail(controller.getDetails(communityId).body?.data)

            assertEquals(communityId.toString(), decoded.id)
            assertEquals("Mahalle", decoded.name)
            assertEquals(128, decoded.memberCount)
            assertEquals("OWNER", decoded.myRole)
            assertEquals(createdAt.toString(), decoded.createdAt)
            assertEquals(1, decoded.groups.size)
            assertEquals(conversationId.toString(), decoded.groups[0].conversationId)
            assertEquals("Bahçe Katı", decoded.groups[0].name)
            assertEquals("https://cdn.example/g.jpg", decoded.groups[0].avatarUrl)
            assertEquals(12, decoded.groups[0].memberCount)
            // #584: the announcement channel a member opens to actually talk in the community.
            assertEquals(announcementGroupId.toString(), decoded.announcementGroupId)
        }

        @Test
        fun `should serve the caller's role on a community that has no groups yet`() {
            every { manageCommunityUseCase.getDetails(communityId, userId) } returns CommunityDetails(
                community = community(),
                groups = emptyList(),
                memberCount = 3,
                myRole = MemberRole.MEMBER
            )

            val decoded = decodeDetail(controller.getDetails(communityId).body?.data)

            // The client's field is nullable, but the server never sends null: non-members are
            // refused by the use case rather than served a roleless community (#375).
            assertEquals("MEMBER", decoded.myRole)
            assertEquals(0, decoded.groups.size)
        }

        @Test
        fun `should pass the authenticated caller to the use case`() {
            every { manageCommunityUseCase.getDetails(communityId, userId) } returns CommunityDetails(
                community = community(),
                groups = emptyList(),
                memberCount = 1,
                myRole = MemberRole.MEMBER
            )

            controller.getDetails(communityId)

            verify { manageCommunityUseCase.getDetails(communityId, userId) }
        }
    }

    @Nested
    inner class Delete {

        @Test
        fun `should delete the community and answer with 200`() {
            every { manageCommunityUseCase.delete(communityId, userId) } returns Unit

            val response = controller.delete(communityId)

            assertEquals(200, response.statusCode.value())
        }

        @Test
        fun `should pass the authenticated caller to the use case, not a caller-supplied id`() {
            // Same rule Update.`should pass the authenticated caller to the use case` enforces:
            // ownership for a destructive action must come from the token, never the request.
            every { manageCommunityUseCase.delete(communityId, userId) } returns Unit

            controller.delete(communityId)

            verify { manageCommunityUseCase.delete(communityId, userId) }
        }
    }

    private fun community() = Community(
        id = communityId,
        name = "Mahalle",
        description = "Komsular",
        avatarUrl = null,
        createdBy = userId,
        createdAt = createdAt
    )

    private fun decodeList(body: List<CommunityResponse>?): List<ClientCommunityResponse> =
        clientJson.decodeFromString(jackson.writeValueAsString(body))

    private fun decodeDetail(body: CommunityDetailResponse?): ClientCommunityDetailResponse =
        clientJson.decodeFromString(jackson.writeValueAsString(body))

    private fun setAuthenticatedUser(userId: UUID, deviceId: UUID) {
        val claims = JwtClaims(userId = userId, deviceId = deviceId)
        val auth = UsernamePasswordAuthenticationToken(claims, null, emptyList())
        SecurityContextHolder.getContext().authentication = auth
    }
}
