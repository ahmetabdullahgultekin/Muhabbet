package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.model.CommunityMember
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.port.`in`.CommunityMemberCandidate
import com.muhabbet.messaging.domain.port.`in`.CommunityMemberSummary
import com.muhabbet.messaging.domain.port.`in`.ManageCommunityMembershipUseCase
import com.muhabbet.shared.TestData
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.security.JwtClaims
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import java.util.UUID
import com.muhabbet.shared.dto.CommunityMemberCandidateResponse as ClientCandidateResponse
import com.muhabbet.shared.dto.CommunityMemberResponse as ClientMemberResponse

/**
 * Same discipline as [CommunityControllerTest]: the controller's own response is serialised with
 * Jackson and then decoded with the client's DTO and `Json` settings, so a field the app cannot read
 * fails here rather than on a phone.
 *
 * Authorization is asserted as pass-through. These endpoints have no rules of their own — the use
 * case owns them (`CommunityServiceTest`) — but the controller can still get the *caller* wrong, and
 * that is the failure a directly-instantiated controller test can actually catch.
 */
class CommunityMemberControllerTest {

    private lateinit var membershipUseCase: ManageCommunityMembershipUseCase
    private lateinit var controller: CommunityMemberController

    private val jackson = jacksonObjectMapper()
    private val clientJson = Json { ignoreUnknownKeys = true; isLenient = true }

    private val userId = TestData.USER_ID_1
    private val memberId = TestData.USER_ID_2
    private val communityId = UUID.randomUUID()
    private val joinedAt = Instant.parse("2026-01-01T00:00:00Z")

    @BeforeEach
    fun setUp() {
        membershipUseCase = mockk()
        controller = CommunityMemberController(membershipUseCase)
        setAuthenticatedUser(userId, TestData.DEVICE_ID_1)
    }

    @Nested
    inner class ListMembers {

        @Test
        fun `should serve a member list the client can decode`() {
            every { membershipUseCase.listMembers(communityId, userId) } returns listOf(
                CommunityMemberSummary(
                    userId = memberId,
                    displayName = "Ayşe Yılmaz",
                    avatarUrl = "https://cdn.example/a.jpg",
                    role = MemberRole.OWNER,
                    joinedAt = joinedAt
                )
            )

            val decoded = decodeMembers(controller.listMembers(communityId).body?.data)

            assertEquals(1, decoded.size)
            assertEquals(memberId.toString(), decoded[0].userId)
            assertEquals("Ayşe Yılmaz", decoded[0].displayName)
            assertEquals("https://cdn.example/a.jpg", decoded[0].avatarUrl)
            assertEquals("OWNER", decoded[0].role)
            assertEquals(joinedAt.toString(), decoded[0].joinedAt)
        }

        @Test
        fun `should serve a member with no display name as null rather than dropping the row`() {
            every { membershipUseCase.listMembers(communityId, userId) } returns listOf(
                CommunityMemberSummary(memberId, null, null, MemberRole.MEMBER, joinedAt)
            )

            val decoded = decodeMembers(controller.listMembers(communityId).body?.data)

            assertEquals(1, decoded.size)
            assertEquals(null, decoded[0].displayName)
        }

        @Test
        fun `should pass the authenticated caller to the use case`() {
            every { membershipUseCase.listMembers(communityId, userId) } returns emptyList()

            controller.listMembers(communityId)

            verify { membershipUseCase.listMembers(communityId, userId) }
        }

        @Test
        fun `should surface the refusal when the caller is not a member`() {
            every { membershipUseCase.listMembers(communityId, userId) } throws
                BusinessException(ErrorCode.COMMUNITY_PERMISSION_DENIED)

            val ex = assertThrows<BusinessException> { controller.listMembers(communityId) }

            assertEquals(ErrorCode.COMMUNITY_PERMISSION_DENIED, ex.errorCode)
        }
    }

    @Nested
    inner class ListMemberCandidates {

        @Test
        fun `should serve a candidate list the client can decode`() {
            every { membershipUseCase.listAddableUsers(communityId, userId) } returns listOf(
                CommunityMemberCandidate(memberId, "Mehmet Demir", "https://cdn.example/m.jpg")
            )

            val decoded = decodeCandidates(controller.listMemberCandidates(communityId).body?.data)

            assertEquals(1, decoded.size)
            assertEquals(memberId.toString(), decoded[0].userId)
            assertEquals("Mehmet Demir", decoded[0].displayName)
            assertEquals("https://cdn.example/m.jpg", decoded[0].avatarUrl)
        }

        @Test
        fun `should serve an empty list when nobody can be added yet`() {
            every { membershipUseCase.listAddableUsers(communityId, userId) } returns emptyList()

            assertEquals(0, decodeCandidates(controller.listMemberCandidates(communityId).body?.data).size)
        }

        @Test
        fun `should surface the refusal when the caller does not run the community`() {
            every { membershipUseCase.listAddableUsers(communityId, userId) } throws
                BusinessException(ErrorCode.COMMUNITY_PERMISSION_DENIED)

            val ex = assertThrows<BusinessException> { controller.listMemberCandidates(communityId) }

            assertEquals(ErrorCode.COMMUNITY_PERMISSION_DENIED, ex.errorCode)
        }
    }

    @Nested
    inner class AddMember {

        @Test
        fun `should enrol the user named in the body on behalf of the authenticated caller`() {
            every { membershipUseCase.addMember(communityId, memberId, userId) } returns
                CommunityMember(communityId = communityId, userId = memberId)

            controller.addMember(communityId, AddMemberRequest(memberId.toString()))

            verify { membershipUseCase.addMember(communityId, memberId, userId) }
        }

        @Test
        fun `should surface the refusal when the target belongs to none of the groups`() {
            every { membershipUseCase.addMember(communityId, memberId, userId) } throws
                BusinessException(ErrorCode.COMMUNITY_MEMBER_NOT_IN_ANY_GROUP)

            val ex = assertThrows<BusinessException> {
                controller.addMember(communityId, AddMemberRequest(memberId.toString()))
            }

            assertEquals(ErrorCode.COMMUNITY_MEMBER_NOT_IN_ANY_GROUP, ex.errorCode)
        }
    }

    @Nested
    inner class Leave {

        @Test
        fun `should remove the authenticated caller and nobody else`() {
            every { membershipUseCase.leave(communityId, userId) } returns Unit

            controller.leave(communityId)

            verify { membershipUseCase.leave(communityId, userId) }
        }

        @Test
        fun `should surface the refusal when the caller is the only member`() {
            every { membershipUseCase.leave(communityId, userId) } throws
                BusinessException(ErrorCode.COMMUNITY_LAST_MEMBER_CANNOT_LEAVE)

            val ex = assertThrows<BusinessException> { controller.leave(communityId) }

            assertEquals(ErrorCode.COMMUNITY_LAST_MEMBER_CANNOT_LEAVE, ex.errorCode)
        }
    }

    private fun decodeMembers(body: List<CommunityMemberResponse>?): List<ClientMemberResponse> =
        clientJson.decodeFromString(jackson.writeValueAsString(body))

    private fun decodeCandidates(body: List<CommunityMemberCandidateResponse>?): List<ClientCandidateResponse> =
        clientJson.decodeFromString(jackson.writeValueAsString(body))

    private fun setAuthenticatedUser(userId: UUID, deviceId: UUID) {
        val claims = JwtClaims(userId = userId, deviceId = deviceId)
        val auth = UsernamePasswordAuthenticationToken(claims, null, emptyList())
        SecurityContextHolder.getContext().authentication = auth
    }
}
