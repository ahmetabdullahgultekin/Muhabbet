package com.muhabbet.messaging.domain.service

import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.model.UserStatus
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageBroadcaster
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

class GroupServiceTest {

    private lateinit var conversationRepository: ConversationRepository
    private lateinit var userRepository: UserRepository
    private lateinit var messageBroadcaster: MessageBroadcaster
    private lateinit var groupService: GroupService

    private val ownerId = UUID.randomUUID()
    private val adminId = UUID.randomUUID()
    private val memberId = UUID.randomUUID()
    private val newUserId = UUID.randomUUID()
    private val groupId = UUID.randomUUID()

    private fun groupConversation(id: UUID = groupId) = Conversation(
        id = id,
        type = ConversationType.GROUP,
        name = "Test Group",
        createdBy = ownerId
    )

    private fun directConversation(id: UUID = UUID.randomUUID()) = Conversation(
        id = id,
        type = ConversationType.DIRECT
    )

    private fun member(convId: UUID, userId: UUID, role: MemberRole, joinedAt: Instant = Instant.now()) =
        ConversationMember(
            conversationId = convId,
            userId = userId,
            role = role,
            joinedAt = joinedAt
        )

    private fun stubUser(id: UUID, name: String? = null) {
        every { userRepository.findById(id) } returns User(
            id = id,
            phoneNumber = "+905321234567",
            displayName = name,
            status = UserStatus.ACTIVE
        )
    }

    @BeforeEach
    fun setUp() {
        conversationRepository = mockk(relaxed = true)
        userRepository = mockk()
        messageBroadcaster = mockk(relaxed = true)

        groupService = GroupService(
            conversationRepository = conversationRepository,
            userRepository = userRepository,
            messageBroadcaster = messageBroadcaster
        )
    }

    // --- addMembers --------------------------------------------------

    @Nested
    inner class AddMembers {

        @Test
        fun `should add new members successfully when requester is owner`() {
            val group = groupConversation()
            val existingMembers = listOf(
                member(groupId, ownerId, MemberRole.OWNER),
                member(groupId, memberId, MemberRole.MEMBER)
            )

            every { conversationRepository.findById(groupId) } returns group
            every { conversationRepository.findMember(groupId, ownerId) } returns existingMembers[0]
            every { conversationRepository.findMembersByConversationId(groupId) } returns existingMembers
            every { conversationRepository.saveMember(any()) } answers { firstArg() }
            stubUser(newUserId, "New User")

            val result = groupService.addMembers(groupId, ownerId, listOf(newUserId))

            assertEquals(1, result.size)
            assertEquals(newUserId, result[0].userId)
            assertEquals(MemberRole.MEMBER, result[0].role)
            verify { conversationRepository.saveMember(any()) }
        }

        @Test
        fun `should add new members successfully when requester is admin`() {
            val group = groupConversation()
            val adminMember = member(groupId, adminId, MemberRole.ADMIN)

            every { conversationRepository.findById(groupId) } returns group
            every { conversationRepository.findMember(groupId, adminId) } returns adminMember
            every { conversationRepository.findMembersByConversationId(groupId) } returns listOf(
                member(groupId, ownerId, MemberRole.OWNER),
                adminMember
            )
            every { conversationRepository.saveMember(any()) } answers { firstArg() }
            stubUser(newUserId)

            val result = groupService.addMembers(groupId, adminId, listOf(newUserId))

            assertEquals(1, result.size)
        }

        @Test
        fun `should throw GROUP_ALREADY_MEMBER when all users are already members`() {
            val group = groupConversation()
            val existingMembers = listOf(
                member(groupId, ownerId, MemberRole.OWNER),
                member(groupId, memberId, MemberRole.MEMBER)
            )

            every { conversationRepository.findById(groupId) } returns group
            every { conversationRepository.findMember(groupId, ownerId) } returns existingMembers[0]
            every { conversationRepository.findMembersByConversationId(groupId) } returns existingMembers

            val ex = assertThrows<BusinessException> {
                groupService.addMembers(groupId, ownerId, listOf(memberId))
            }
            assertEquals(ErrorCode.GROUP_ALREADY_MEMBER, ex.errorCode)
        }

        @Test
        fun `should throw CONV_MAX_MEMBERS when adding would exceed limit`() {
            val group = groupConversation()
            // Create 256 existing members (the max)
            val existingMembers = (0 until 256).map {
                member(groupId, UUID.randomUUID(), MemberRole.MEMBER)
            }

            every { conversationRepository.findById(groupId) } returns group
            every { conversationRepository.findMember(groupId, ownerId) } returns
                    member(groupId, ownerId, MemberRole.OWNER)
            every { conversationRepository.findMembersByConversationId(groupId) } returns existingMembers

            val ex = assertThrows<BusinessException> {
                groupService.addMembers(groupId, ownerId, listOf(newUserId))
            }
            assertEquals(ErrorCode.CONV_MAX_MEMBERS, ex.errorCode)
        }

        @Test
        fun `should throw GROUP_NOT_FOUND when conversation does not exist`() {
            every { conversationRepository.findById(groupId) } returns null

            val ex = assertThrows<BusinessException> {
                groupService.addMembers(groupId, ownerId, listOf(newUserId))
            }
            assertEquals(ErrorCode.GROUP_NOT_FOUND, ex.errorCode)
        }

        @Test
        fun `should throw GROUP_CANNOT_MODIFY_DIRECT when conversation is direct`() {
            every { conversationRepository.findById(groupId) } returns directConversation(groupId)

            val ex = assertThrows<BusinessException> {
                groupService.addMembers(groupId, ownerId, listOf(newUserId))
            }
            assertEquals(ErrorCode.GROUP_CANNOT_MODIFY_DIRECT, ex.errorCode)
        }

        @Test
        fun `should throw GROUP_PERMISSION_DENIED when requester is regular member`() {
            val group = groupConversation()

            every { conversationRepository.findById(groupId) } returns group
            every { conversationRepository.findMember(groupId, memberId) } returns
                    member(groupId, memberId, MemberRole.MEMBER)

            val ex = assertThrows<BusinessException> {
                groupService.addMembers(groupId, memberId, listOf(newUserId))
            }
            assertEquals(ErrorCode.GROUP_PERMISSION_DENIED, ex.errorCode)
        }

        @Test
        fun `should throw CONV_INVALID_PARTICIPANTS when new user does not exist`() {
            val group = groupConversation()
            val existingMembers = listOf(member(groupId, ownerId, MemberRole.OWNER))

            every { conversationRepository.findById(groupId) } returns group
            every { conversationRepository.findMember(groupId, ownerId) } returns existingMembers[0]
            every { conversationRepository.findMembersByConversationId(groupId) } returns existingMembers
            every { userRepository.findById(newUserId) } returns null

            val ex = assertThrows<BusinessException> {
                groupService.addMembers(groupId, ownerId, listOf(newUserId))
            }
            assertEquals(ErrorCode.CONV_INVALID_PARTICIPANTS, ex.errorCode)
        }

        @Test
        fun `should broadcast GroupMemberAdded to all group members`() {
            val group = groupConversation()
            val existingMembers = listOf(member(groupId, ownerId, MemberRole.OWNER))

            every { conversationRepository.findById(groupId) } returns group
            every { conversationRepository.findMember(groupId, ownerId) } returns existingMembers[0]
            every { conversationRepository.findMembersByConversationId(groupId) } returns existingMembers
            every { conversationRepository.saveMember(any()) } answers { firstArg() }
            stubUser(newUserId, "New User")

            groupService.addMembers(groupId, ownerId, listOf(newUserId))

            verify { messageBroadcaster.broadcastToUsers(any(), any()) }
        }
    }

    // --- removeMember ------------------------------------------------

    @Nested
    inner class RemoveMember {

        @Test
        fun `should remove member successfully when owner removes member`() {
            val group = groupConversation()

            every { conversationRepository.findById(groupId) } returns group
            every { conversationRepository.findMember(groupId, memberId) } returns
                    member(groupId, memberId, MemberRole.MEMBER)
            every { conversationRepository.findMember(groupId, ownerId) } returns
                    member(groupId, ownerId, MemberRole.OWNER)
            every { conversationRepository.findMembersByConversationId(groupId) } returns listOf(
                member(groupId, ownerId, MemberRole.OWNER)
            )

            groupService.removeMember(groupId, ownerId, memberId)

            verify { conversationRepository.removeMember(groupId, memberId) }
        }

        @Test
        fun `should remove member successfully when admin removes member`() {
            val group = groupConversation()

            every { conversationRepository.findById(groupId) } returns group
            every { conversationRepository.findMember(groupId, memberId) } returns
                    member(groupId, memberId, MemberRole.MEMBER)
            every { conversationRepository.findMember(groupId, adminId) } returns
                    member(groupId, adminId, MemberRole.ADMIN)
            every { conversationRepository.findMembersByConversationId(groupId) } returns listOf(
                member(groupId, ownerId, MemberRole.OWNER),
                member(groupId, adminId, MemberRole.ADMIN)
            )

            groupService.removeMember(groupId, adminId, memberId)

            verify { conversationRepository.removeMember(groupId, memberId) }
        }

        @Test
        fun `should throw GROUP_CANNOT_REMOVE_OWNER when trying to remove owner`() {
            val group = groupConversation()

            every { conversationRepository.findById(groupId) } returns group
            every { conversationRepository.findMember(groupId, ownerId) } returns
                    member(groupId, ownerId, MemberRole.OWNER)

            val ex = assertThrows<BusinessException> {
                groupService.removeMember(groupId, adminId, ownerId)
            }
            assertEquals(ErrorCode.GROUP_CANNOT_REMOVE_OWNER, ex.errorCode)
        }

        @Test
        fun `should throw GROUP_NOT_MEMBER when target is not a member`() {
            val group = groupConversation()

            every { conversationRepository.findById(groupId) } returns group
            every { conversationRepository.findMember(groupId, newUserId) } returns null

            val ex = assertThrows<BusinessException> {
                groupService.removeMember(groupId, ownerId, newUserId)
            }
            assertEquals(ErrorCode.GROUP_NOT_MEMBER, ex.errorCode)
        }

        @Test
        fun `should throw GROUP_PERMISSION_DENIED when regular member tries to remove`() {
            val group = groupConversation()
            val targetId = UUID.randomUUID()

            every { conversationRepository.findById(groupId) } returns group
            every { conversationRepository.findMember(groupId, targetId) } returns
                    member(groupId, targetId, MemberRole.MEMBER)
            every { conversationRepository.findMember(groupId, memberId) } returns
                    member(groupId, memberId, MemberRole.MEMBER)

            val ex = assertThrows<BusinessException> {
                groupService.removeMember(groupId, memberId, targetId)
            }
            assertEquals(ErrorCode.GROUP_PERMISSION_DENIED, ex.errorCode)
        }

        @Test
        fun `should throw GROUP_PERMISSION_DENIED when admin tries to remove admin`() {
            val group = groupConversation()
            val otherAdminId = UUID.randomUUID()

            every { conversationRepository.findById(groupId) } returns group
            every { conversationRepository.findMember(groupId, otherAdminId) } returns
                    member(groupId, otherAdminId, MemberRole.ADMIN)
            every { conversationRepository.findMember(groupId, adminId) } returns
                    member(groupId, adminId, MemberRole.ADMIN)

            val ex = assertThrows<BusinessException> {
                groupService.removeMember(groupId, adminId, otherAdminId)
            }
            assertEquals(ErrorCode.GROUP_PERMISSION_DENIED, ex.errorCode)
        }

        @Test
        fun `should throw GROUP_NOT_FOUND when conversation does not exist`() {
            every { conversationRepository.findById(groupId) } returns null

            val ex = assertThrows<BusinessException> {
                groupService.removeMember(groupId, ownerId, memberId)
            }
            assertEquals(ErrorCode.GROUP_NOT_FOUND, ex.errorCode)
        }

        @Test
        fun `should throw GROUP_CANNOT_MODIFY_DIRECT when conversation is direct`() {
            every { conversationRepository.findById(groupId) } returns directConversation(groupId)

            val ex = assertThrows<BusinessException> {
                groupService.removeMember(groupId, ownerId, memberId)
            }
            assertEquals(ErrorCode.GROUP_CANNOT_MODIFY_DIRECT, ex.errorCode)
        }

        @Test
        fun `should broadcast GroupMemberRemoved after successful removal`() {
            val group = groupConversation()

            every { conversationRepository.findById(groupId) } returns group
            every { conversationRepository.findMember(groupId, memberId) } returns
                    member(groupId, memberId, MemberRole.MEMBER)
            every { conversationRepository.findMember(groupId, ownerId) } returns
                    member(groupId, ownerId, MemberRole.OWNER)
            every { conversationRepository.findMembersByConversationId(groupId) } returns listOf(
                member(groupId, ownerId, MemberRole.OWNER)
            )

            groupService.removeMember(groupId, ownerId, memberId)

            verify { messageBroadcaster.broadcastToUsers(any(), any()) }
        }
    }

    // --- updateGroupInfo ---------------------------------------------

    @Nested
    inner class UpdateGroupInfo {

        @Test
        fun `should update group name successfully when requester is owner`() {
            val group = groupConversation()

            every { conversationRepository.findById(groupId) } returns group
            every { conversationRepository.findMember(groupId, ownerId) } returns
                    member(groupId, ownerId, MemberRole.OWNER)
            every { conversationRepository.updateConversation(any()) } answers { firstArg() }
            every { conversationRepository.findMembersByConversationId(groupId) } returns listOf(
                member(groupId, ownerId, MemberRole.OWNER)
            )

            val result = groupService.updateGroupInfo(groupId, ownerId, "New Name", null)

            assertEquals("New Name", result.name)
        }

        @Test
        fun `should update group description when requester is admin`() {
            val group = groupConversation()

            every { conversationRepository.findById(groupId) } returns group
            every { conversationRepository.findMember(groupId, adminId) } returns
                    member(groupId, adminId, MemberRole.ADMIN)
            every { conversationRepository.updateConversation(any()) } answers { firstArg() }
            every { conversationRepository.findMembersByConversationId(groupId) } returns listOf(
                member(groupId, ownerId, MemberRole.OWNER),
                member(groupId, adminId, MemberRole.ADMIN)
            )

            val result = groupService.updateGroupInfo(groupId, adminId, null, "New Description")

            assertEquals("New Description", result.description)
            assertEquals("Test Group", result.name) // Name should remain unchanged
        }

        @Test
        fun `should throw GROUP_PERMISSION_DENIED when requester is regular member`() {
            val group = groupConversation()

            every { conversationRepository.findById(groupId) } returns group
            every { conversationRepository.findMember(groupId, memberId) } returns
                    member(groupId, memberId, MemberRole.MEMBER)

            val ex = assertThrows<BusinessException> {
                groupService.updateGroupInfo(groupId, memberId, "Hacked Name", null)
            }
            assertEquals(ErrorCode.GROUP_PERMISSION_DENIED, ex.errorCode)
        }

        @Test
        fun `should throw VALIDATION_ERROR when group name is invalid`() {
            val group = groupConversation()

            every { conversationRepository.findById(groupId) } returns group
            every { conversationRepository.findMember(groupId, ownerId) } returns
                    member(groupId, ownerId, MemberRole.OWNER)

            val ex = assertThrows<BusinessException> {
                groupService.updateGroupInfo(groupId, ownerId, "   ", null)
            }
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode)
        }

        @Test
        fun `should throw VALIDATION_ERROR when group name exceeds max length`() {
            val group = groupConversation()

            every { conversationRepository.findById(groupId) } returns group
            every { conversationRepository.findMember(groupId, ownerId) } returns
                    member(groupId, ownerId, MemberRole.OWNER)

            val longName = "A".repeat(129) // MAX is 128

            val ex = assertThrows<BusinessException> {
                groupService.updateGroupInfo(groupId, ownerId, longName, null)
            }
            assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode)
        }

        @Test
        fun `should throw GROUP_NOT_FOUND when conversation does not exist`() {
            every { conversationRepository.findById(groupId) } returns null

            val ex = assertThrows<BusinessException> {
                groupService.updateGroupInfo(groupId, ownerId, "New Name", null)
            }
            assertEquals(ErrorCode.GROUP_NOT_FOUND, ex.errorCode)
        }

        @Test
        fun `should throw GROUP_CANNOT_MODIFY_DIRECT when conversation is direct`() {
            every { conversationRepository.findById(groupId) } returns directConversation(groupId)

            val ex = assertThrows<BusinessException> {
                groupService.updateGroupInfo(groupId, ownerId, "New Name", null)
            }
            assertEquals(ErrorCode.GROUP_CANNOT_MODIFY_DIRECT, ex.errorCode)
        }

        @Test
        fun `should broadcast GroupInfoUpdated after successful update`() {
            val group = groupConversation()

            every { conversationRepository.findById(groupId) } returns group
            every { conversationRepository.findMember(groupId, ownerId) } returns
                    member(groupId, ownerId, MemberRole.OWNER)
            every { conversationRepository.updateConversation(any()) } answers { firstArg() }
            every { conversationRepository.findMembersByConversationId(groupId) } returns listOf(
                member(groupId, ownerId, MemberRole.OWNER),
                member(groupId, memberId, MemberRole.MEMBER)
            )

            groupService.updateGroupInfo(groupId, ownerId, "Updated Name", "Updated Desc")

            verify { messageBroadcaster.broadcastToUsers(match { it.size == 2 }, any()) }
        }
    }

    // --- updateMemberRole --------------------------------------------

    @Nested
    inner class UpdateMemberRole {

        @Test
        fun `should update member role to admin when requester is owner`() {
            val group = groupConversation()

            every { conversationRepository.findById(groupId) } returns group
            every { conversationRepository.findMember(groupId, ownerId) } returns
                    member(groupId, ownerId, MemberRole.OWNER)
            every { conversationRepository.findMember(groupId, memberId) } returns
                    member(groupId, memberId, MemberRole.MEMBER)
            every { conversationRepository.findMembersByConversationId(groupId) } returns listOf(
                member(groupId, ownerId, MemberRole.OWNER),
                member(groupId, memberId, MemberRole.MEMBER)
            )

            groupService.updateMemberRole(groupId, ownerId, memberId, MemberRole.ADMIN)

            verify { conversationRepository.updateMemberRole(groupId, memberId, MemberRole.ADMIN) }
        }

        @Test
        fun `should throw GROUP_PERMISSION_DENIED when requester is admin`() {
            val group = groupConversation()

            every { conversationRepository.findById(groupId) } returns group
            every { conversationRepository.findMember(groupId, adminId) } returns
                    member(groupId, adminId, MemberRole.ADMIN)

            val ex = assertThrows<BusinessException> {
                groupService.updateMemberRole(groupId, adminId, memberId, MemberRole.ADMIN)
            }
            assertEquals(ErrorCode.GROUP_PERMISSION_DENIED, ex.errorCode)
        }

        @Test
        fun `should throw GROUP_PERMISSION_DENIED when requester is regular member`() {
            val group = groupConversation()

            every { conversationRepository.findById(groupId) } returns group
            every { conversationRepository.findMember(groupId, memberId) } returns
                    member(groupId, memberId, MemberRole.MEMBER)

            val ex = assertThrows<BusinessException> {
                groupService.updateMemberRole(groupId, memberId, adminId, MemberRole.MEMBER)
            }
            assertEquals(ErrorCode.GROUP_PERMISSION_DENIED, ex.errorCode)
        }

        @Test
        fun `should throw GROUP_NOT_MEMBER when target user is not a member`() {
            val group = groupConversation()

            every { conversationRepository.findById(groupId) } returns group
            every { conversationRepository.findMember(groupId, ownerId) } returns
                    member(groupId, ownerId, MemberRole.OWNER)
            every { conversationRepository.findMember(groupId, newUserId) } returns null

            val ex = assertThrows<BusinessException> {
                groupService.updateMemberRole(groupId, ownerId, newUserId, MemberRole.ADMIN)
            }
            assertEquals(ErrorCode.GROUP_NOT_MEMBER, ex.errorCode)
        }

        @Test
        fun `should throw GROUP_NOT_FOUND when conversation does not exist`() {
            every { conversationRepository.findById(groupId) } returns null

            val ex = assertThrows<BusinessException> {
                groupService.updateMemberRole(groupId, ownerId, memberId, MemberRole.ADMIN)
            }
            assertEquals(ErrorCode.GROUP_NOT_FOUND, ex.errorCode)
        }

        @Test
        fun `should throw GROUP_CANNOT_MODIFY_DIRECT when conversation is direct`() {
            every { conversationRepository.findById(groupId) } returns directConversation(groupId)

            val ex = assertThrows<BusinessException> {
                groupService.updateMemberRole(groupId, ownerId, memberId, MemberRole.ADMIN)
            }
            assertEquals(ErrorCode.GROUP_CANNOT_MODIFY_DIRECT, ex.errorCode)
        }

        @Test
        fun `should broadcast GroupRoleUpdated after successful role change`() {
            val group = groupConversation()

            every { conversationRepository.findById(groupId) } returns group
            every { conversationRepository.findMember(groupId, ownerId) } returns
                    member(groupId, ownerId, MemberRole.OWNER)
            every { conversationRepository.findMember(groupId, memberId) } returns
                    member(groupId, memberId, MemberRole.MEMBER)
            every { conversationRepository.findMembersByConversationId(groupId) } returns listOf(
                member(groupId, ownerId, MemberRole.OWNER),
                member(groupId, memberId, MemberRole.MEMBER)
            )

            groupService.updateMemberRole(groupId, ownerId, memberId, MemberRole.ADMIN)

            verify { messageBroadcaster.broadcastToUsers(any(), any()) }
        }
    }

    // --- leaveGroup --------------------------------------------------

    @Nested
    inner class LeaveGroup {

        @Test
        fun `should leave group successfully when requester is regular member`() {
            val group = groupConversation()

            every { conversationRepository.findById(groupId) } returns group
            every { conversationRepository.findMember(groupId, memberId) } returns
                    member(groupId, memberId, MemberRole.MEMBER)
            every { conversationRepository.findMembersByConversationId(groupId) } returns listOf(
                member(groupId, ownerId, MemberRole.OWNER)
            )

            groupService.leaveGroup(groupId, memberId)

            verify { conversationRepository.removeMember(groupId, memberId) }
        }

        @Test
        fun `should transfer ownership to admin when owner leaves and admin exists`() {
            val group = groupConversation()
            val adminJoinedAt = Instant.now().minusSeconds(3600)
            val memberJoinedAt = Instant.now().minusSeconds(1800)

            every { conversationRepository.findById(groupId) } returns group
            every { conversationRepository.findMember(groupId, ownerId) } returns
                    member(groupId, ownerId, MemberRole.OWNER)
            every { conversationRepository.findMembersByConversationId(groupId) } returnsMany listOf(
                // First call: for finding new owner
                listOf(
                    member(groupId, ownerId, MemberRole.OWNER),
                    member(groupId, adminId, MemberRole.ADMIN, adminJoinedAt),
                    member(groupId, memberId, MemberRole.MEMBER, memberJoinedAt)
                ),
                // Second call: for broadcasting
                listOf(
                    member(groupId, adminId, MemberRole.OWNER),
                    member(groupId, memberId, MemberRole.MEMBER)
                )
            )

            groupService.leaveGroup(groupId, ownerId)

            verify { conversationRepository.updateMemberRole(groupId, adminId, MemberRole.OWNER) }
            verify { conversationRepository.removeMember(groupId, ownerId) }
        }

        @Test
        fun `should transfer ownership to oldest member when owner leaves and no admin exists`() {
            val group = groupConversation()
            val olderMemberJoinedAt = Instant.now().minusSeconds(3600)
            val newerMemberJoinedAt = Instant.now().minusSeconds(1800)
            val oldMemberId = UUID.randomUUID()
            val newMemberId = UUID.randomUUID()

            every { conversationRepository.findById(groupId) } returns group
            every { conversationRepository.findMember(groupId, ownerId) } returns
                    member(groupId, ownerId, MemberRole.OWNER)
            every { conversationRepository.findMembersByConversationId(groupId) } returnsMany listOf(
                listOf(
                    member(groupId, ownerId, MemberRole.OWNER),
                    member(groupId, newMemberId, MemberRole.MEMBER, newerMemberJoinedAt),
                    member(groupId, oldMemberId, MemberRole.MEMBER, olderMemberJoinedAt)
                ),
                listOf(
                    member(groupId, oldMemberId, MemberRole.OWNER),
                    member(groupId, newMemberId, MemberRole.MEMBER)
                )
            )

            groupService.leaveGroup(groupId, ownerId)

            verify { conversationRepository.updateMemberRole(groupId, oldMemberId, MemberRole.OWNER) }
            verify { conversationRepository.removeMember(groupId, ownerId) }
        }

        @Test
        fun `should just remove when owner is the last member`() {
            val group = groupConversation()

            every { conversationRepository.findById(groupId) } returns group
            every { conversationRepository.findMember(groupId, ownerId) } returns
                    member(groupId, ownerId, MemberRole.OWNER)
            every { conversationRepository.findMembersByConversationId(groupId) } returns listOf(
                member(groupId, ownerId, MemberRole.OWNER)
            )

            groupService.leaveGroup(groupId, ownerId)

            verify { conversationRepository.removeMember(groupId, ownerId) }
            verify(exactly = 0) { conversationRepository.updateMemberRole(any(), any(), any()) }
        }

        @Test
        fun `should throw GROUP_NOT_FOUND when conversation does not exist`() {
            every { conversationRepository.findById(groupId) } returns null

            val ex = assertThrows<BusinessException> {
                groupService.leaveGroup(groupId, memberId)
            }
            assertEquals(ErrorCode.GROUP_NOT_FOUND, ex.errorCode)
        }

        @Test
        fun `should throw GROUP_CANNOT_MODIFY_DIRECT when conversation is direct`() {
            every { conversationRepository.findById(groupId) } returns directConversation(groupId)

            val ex = assertThrows<BusinessException> {
                groupService.leaveGroup(groupId, memberId)
            }
            assertEquals(ErrorCode.GROUP_CANNOT_MODIFY_DIRECT, ex.errorCode)
        }

        @Test
        fun `should throw GROUP_NOT_MEMBER when user is not in the group`() {
            val group = groupConversation()

            every { conversationRepository.findById(groupId) } returns group
            every { conversationRepository.findMember(groupId, newUserId) } returns null

            val ex = assertThrows<BusinessException> {
                groupService.leaveGroup(groupId, newUserId)
            }
            assertEquals(ErrorCode.GROUP_NOT_MEMBER, ex.errorCode)
        }

        @Test
        fun `should broadcast GroupMemberLeft to remaining members`() {
            val group = groupConversation()

            every { conversationRepository.findById(groupId) } returns group
            every { conversationRepository.findMember(groupId, memberId) } returns
                    member(groupId, memberId, MemberRole.MEMBER)
            every { conversationRepository.findMembersByConversationId(groupId) } returns listOf(
                member(groupId, ownerId, MemberRole.OWNER),
                member(groupId, adminId, MemberRole.ADMIN)
            )

            groupService.leaveGroup(groupId, memberId)

            verify { messageBroadcaster.broadcastToUsers(match { it.size == 2 }, any()) }
        }
    }
}
