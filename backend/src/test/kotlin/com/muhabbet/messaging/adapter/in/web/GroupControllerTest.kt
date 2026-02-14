package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.port.`in`.ManageGroupUseCase
import com.muhabbet.messaging.domain.port.out.PresencePort
import com.muhabbet.shared.TestData
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.security.JwtClaims
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

class GroupControllerTest {

    private lateinit var manageGroupUseCase: ManageGroupUseCase
    private lateinit var userRepository: UserRepository
    private lateinit var presencePort: PresencePort
    private lateinit var controller: GroupController

    private val userId = TestData.USER_ID_1
    private val groupId = TestData.GROUP_ID

    @BeforeEach
    fun setUp() {
        manageGroupUseCase = mockk()
        userRepository = mockk()
        presencePort = mockk()
        controller = GroupController(manageGroupUseCase, userRepository, presencePort)
        setAuthenticatedUser(userId, TestData.DEVICE_ID_1)
    }

    @Nested
    inner class AddMembers {

        @Test
        fun `should add members and return participant responses`() {
            val newMembers = listOf(
                TestData.member(conversationId = groupId, userId = TestData.USER_ID_2),
                TestData.member(conversationId = groupId, userId = TestData.USER_ID_3)
            )

            every {
                manageGroupUseCase.addMembers(groupId, userId, listOf(TestData.USER_ID_2, TestData.USER_ID_3))
            } returns newMembers
            every { presencePort.getOnlineUserIds(any()) } returns setOf(TestData.USER_ID_2)
            every { userRepository.findById(TestData.USER_ID_2) } returns TestData.user(id = TestData.USER_ID_2, displayName = "User 2")
            every { userRepository.findById(TestData.USER_ID_3) } returns TestData.user(id = TestData.USER_ID_3, displayName = "User 3")

            val response = controller.addMembers(
                groupId,
                com.muhabbet.shared.dto.AddMembersRequest(listOf(TestData.USER_ID_2.toString(), TestData.USER_ID_3.toString()))
            )

            assert(response.statusCode.value() == 200)
            assert(response.body?.data?.size == 2)
        }

        @Test
        fun `should throw GROUP_PERMISSION_DENIED for non-admin`() {
            every {
                manageGroupUseCase.addMembers(groupId, userId, any())
            } throws BusinessException(ErrorCode.GROUP_PERMISSION_DENIED)

            try {
                controller.addMembers(groupId, com.muhabbet.shared.dto.AddMembersRequest(listOf(TestData.USER_ID_2.toString())))
                assert(false) { "Expected BusinessException" }
            } catch (ex: BusinessException) {
                assert(ex.errorCode == ErrorCode.GROUP_PERMISSION_DENIED)
            }
        }
    }

    @Nested
    inner class RemoveMember {

        @Test
        fun `should remove member successfully`() {
            every { manageGroupUseCase.removeMember(groupId, userId, TestData.USER_ID_2) } returns Unit

            val response = controller.removeMember(groupId, TestData.USER_ID_2)

            assert(response.statusCode.value() == 200)
            verify { manageGroupUseCase.removeMember(groupId, userId, TestData.USER_ID_2) }
        }

        @Test
        fun `should throw GROUP_CANNOT_REMOVE_OWNER`() {
            every {
                manageGroupUseCase.removeMember(groupId, userId, TestData.USER_ID_2)
            } throws BusinessException(ErrorCode.GROUP_CANNOT_REMOVE_OWNER)

            try {
                controller.removeMember(groupId, TestData.USER_ID_2)
                assert(false) { "Expected BusinessException" }
            } catch (ex: BusinessException) {
                assert(ex.errorCode == ErrorCode.GROUP_CANNOT_REMOVE_OWNER)
            }
        }
    }

    @Nested
    inner class UpdateGroupInfo {

        @Test
        fun `should update group name and description`() {
            val updated = TestData.groupConversation(id = groupId, name = "New Name")

            every {
                manageGroupUseCase.updateGroupInfo(groupId, userId, "New Name", "New desc")
            } returns updated

            val response = controller.updateGroupInfo(
                groupId,
                com.muhabbet.shared.dto.UpdateGroupRequest(name = "New Name", description = "New desc")
            )

            assert(response.statusCode.value() == 200)
            assert(response.body?.data?.name == "New Name")
        }
    }

    @Nested
    inner class UpdateMemberRole {

        @Test
        fun `should update member role to admin`() {
            every {
                manageGroupUseCase.updateMemberRole(groupId, userId, TestData.USER_ID_2, MemberRole.ADMIN)
            } returns Unit

            val response = controller.updateMemberRole(
                groupId, TestData.USER_ID_2,
                com.muhabbet.shared.dto.UpdateRoleRequest(role = com.muhabbet.shared.model.MemberRole.ADMIN)
            )

            assert(response.statusCode.value() == 200)
        }
    }

    @Nested
    inner class LeaveGroup {

        @Test
        fun `should leave group successfully`() {
            every { manageGroupUseCase.leaveGroup(groupId, userId) } returns Unit

            val response = controller.leaveGroup(groupId)

            assert(response.statusCode.value() == 200)
            verify { manageGroupUseCase.leaveGroup(groupId, userId) }
        }

        @Test
        fun `should throw GROUP_OWNER_CANNOT_LEAVE`() {
            every {
                manageGroupUseCase.leaveGroup(groupId, userId)
            } throws BusinessException(ErrorCode.GROUP_OWNER_CANNOT_LEAVE)

            try {
                controller.leaveGroup(groupId)
                assert(false) { "Expected BusinessException" }
            } catch (ex: BusinessException) {
                assert(ex.errorCode == ErrorCode.GROUP_OWNER_CANNOT_LEAVE)
            }
        }
    }

    private fun setAuthenticatedUser(userId: UUID, deviceId: UUID) {
        val claims = JwtClaims(userId = userId, deviceId = deviceId)
        val auth = UsernamePasswordAuthenticationToken(claims, null, emptyList())
        SecurityContextHolder.getContext().authentication = auth
    }
}
