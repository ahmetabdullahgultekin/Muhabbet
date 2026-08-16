package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.model.BroadcastList
import com.muhabbet.messaging.domain.port.`in`.BroadcastListMemberSummary
import com.muhabbet.messaging.domain.port.`in`.BroadcastListSummary
import com.muhabbet.messaging.domain.port.`in`.ManageBroadcastListUseCase
import com.muhabbet.shared.TestData
import com.muhabbet.shared.security.JwtClaims
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import java.util.UUID
import com.muhabbet.shared.dto.BroadcastListResponse as ClientBroadcastListResponse
import com.muhabbet.shared.dto.BroadcastMemberResponse as ClientBroadcastMemberResponse

/**
 * Broadcast lists had never been exercised end to end: the client asked `/api/v1/broadcasts` while
 * this controller serves `/api/v1/broadcast-lists`, so every request 404'd and the response shape
 * was never once decoded (#392). The path is fixed on the client; these tests pin the shape.
 *
 * As in [CommunityControllerTest], the check is not by eye. Each response is serialised with
 * Jackson — the library Spring Boot 4 writes the wire bytes with — and decoded with the client's
 * own DTO and `Json` settings, so a renamed or dropped field fails here the way it would on the
 * phone. `memberCount` is the field that motivated this: the client has always declared it with a
 * default of 0, so the old controller omitting it rendered "0 üye" on every row instead of failing.
 */
class BroadcastListControllerTest {

    private lateinit var manageBroadcastListUseCase: ManageBroadcastListUseCase
    private lateinit var controller: BroadcastListController

    private val jackson = jacksonObjectMapper()
    private val clientJson = Json { ignoreUnknownKeys = true; isLenient = true }

    private val userId = TestData.USER_ID_1
    private val listId = UUID.randomUUID()
    private val memberId = UUID.randomUUID()
    private val createdAt = Instant.parse("2026-01-01T00:00:00Z")

    @BeforeEach
    fun setUp() {
        manageBroadcastListUseCase = mockk()
        controller = BroadcastListController(manageBroadcastListUseCase)
        setAuthenticatedUser(userId, TestData.DEVICE_ID_1)
    }

    @Nested
    inner class GetMyLists {

        @Test
        fun `should serve the recipient count the client renders on every row`() {
            every { manageBroadcastListUseCase.getByOwner(userId) } returns listOf(
                BroadcastListSummary(list = broadcastList(), memberCount = 7)
            )

            val decoded = decodeLists(controller.getMyLists().body?.data)

            assertEquals(1, decoded.size)
            assertEquals(listId.toString(), decoded[0].id)
            assertEquals("Aile", decoded[0].name)
            assertEquals(7, decoded[0].memberCount)
            assertEquals(createdAt.toString(), decoded[0].createdAt)
        }

        @Test
        fun `should serve zero rather than omit the count for an empty list`() {
            // An omitted field is not an error on the client, it is silently the default — which is
            // indistinguishable from a genuinely empty list. Sending it explicitly is the fix.
            every { manageBroadcastListUseCase.getByOwner(userId) } returns listOf(
                BroadcastListSummary(list = broadcastList(), memberCount = 0)
            )

            assertEquals(0, decodeLists(controller.getMyLists().body?.data)[0].memberCount)
        }

        @Test
        fun `should serve an empty list when the user has none`() {
            every { manageBroadcastListUseCase.getByOwner(userId) } returns emptyList()

            assertEquals(0, decodeLists(controller.getMyLists().body?.data).size)
        }
    }

    @Nested
    inner class Create {

        @Test
        fun `should serve the created list with the count it was created with`() {
            every { manageBroadcastListUseCase.create(userId, "Aile", listOf(memberId)) } returns
                BroadcastListSummary(list = broadcastList(), memberCount = 1)

            val response = controller.create(
                CreateBroadcastListRequest("Aile", listOf(memberId.toString()))
            )
            val decoded = clientJson.decodeFromString<ClientBroadcastListResponse>(
                jackson.writeValueAsString(response.body?.data)
            )

            assertEquals(201, response.statusCode.value())
            assertEquals("Aile", decoded.name)
            assertEquals(1, decoded.memberCount)
        }

        @Test
        fun `should pass the authenticated caller to the use case`() {
            // Ownership is the use case's job, but only if it is told who is asking: the controller
            // must never take an owner id from the request body.
            every { manageBroadcastListUseCase.create(userId, "Aile", emptyList()) } returns
                BroadcastListSummary(list = broadcastList(), memberCount = 0)

            controller.create(CreateBroadcastListRequest("Aile"))

            verify { manageBroadcastListUseCase.create(userId, "Aile", emptyList()) }
        }
    }

    @Nested
    inner class GetMembers {

        @Test
        fun `should serve a name and an avatar, not a bare id`() {
            every { manageBroadcastListUseCase.getMembers(listId, userId) } returns listOf(
                BroadcastListMemberSummary(
                    userId = memberId,
                    displayName = "Ayşe Gülşah",
                    avatarUrl = "https://cdn.example/a.jpg"
                )
            )

            val decoded = decodeMembers(controller.getMembers(listId).body?.data)

            assertEquals(1, decoded.size)
            assertEquals(memberId.toString(), decoded[0].userId)
            assertEquals("Ayşe Gülşah", decoded[0].displayName)
            assertEquals("https://cdn.example/a.jpg", decoded[0].avatarUrl)
        }

        @Test
        fun `should serve a recipient who never set a display name`() {
            // Normal state, not an error. The client's fields are nullable and the screen falls
            // back to a localized "unknown" rather than printing the UUID.
            every { manageBroadcastListUseCase.getMembers(listId, userId) } returns listOf(
                BroadcastListMemberSummary(userId = memberId, displayName = null, avatarUrl = null)
            )

            val decoded = decodeMembers(controller.getMembers(listId).body?.data)

            assertNull(decoded[0].displayName)
            assertNull(decoded[0].avatarUrl)
        }

        @Test
        fun `should pass the authenticated caller to the use case`() {
            every { manageBroadcastListUseCase.getMembers(listId, userId) } returns emptyList()

            controller.getMembers(listId)

            verify { manageBroadcastListUseCase.getMembers(listId, userId) }
        }
    }

    @Nested
    inner class AddMembers {

        @Test
        fun `should serve the added recipients in the same shape as the member list`() {
            every { manageBroadcastListUseCase.addMembers(listId, userId, listOf(memberId)) } returns
                listOf(BroadcastListMemberSummary(memberId, "Mehmet", null))

            val decoded = decodeMembers(
                controller.addMembers(listId, AddBroadcastMembersRequest(listOf(memberId.toString()))).body?.data
            )

            assertEquals("Mehmet", decoded.single().displayName)
        }
    }

    private fun broadcastList() = BroadcastList(
        id = listId,
        ownerId = userId,
        name = "Aile",
        createdAt = createdAt
    )

    private fun decodeLists(body: List<BroadcastListResponse>?): List<ClientBroadcastListResponse> =
        clientJson.decodeFromString(jackson.writeValueAsString(body))

    private fun decodeMembers(body: List<BroadcastMemberResponse>?): List<ClientBroadcastMemberResponse> =
        clientJson.decodeFromString(jackson.writeValueAsString(body))

    private fun setAuthenticatedUser(userId: UUID, deviceId: UUID) {
        val claims = JwtClaims(userId = userId, deviceId = deviceId)
        val auth = UsernamePasswordAuthenticationToken(claims, null, emptyList())
        SecurityContextHolder.getContext().authentication = auth
    }
}
