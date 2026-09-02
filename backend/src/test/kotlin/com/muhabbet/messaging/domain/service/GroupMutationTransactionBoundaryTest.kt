package com.muhabbet.messaging.domain.service

import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.model.UserStatus
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageBroadcaster
import com.muhabbet.messaging.domain.port.out.TransactionRunner
import com.muhabbet.shared.FailingTransactionRunner
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * #669 — the five group mutations, each of which used to broadcast from inside its own
 * `@Transactional` method.
 *
 * They are rare compared with a send, which is why they were split out of #491 rather than fixed
 * with it, but the shape is identical: `GroupMemberAdded` and its four siblings go to every member
 * of the group over blocking WebSocket writes, and the pool connection stayed checked out for all
 * of them. The ordering half is the more visible one here — an add or a role change announced from
 * inside a transaction that then rolls back leaves every client showing a membership the database
 * does not have, and unlike a message there is no later delivery to correct it.
 *
 * Each site gets the same pair of assertions `SendMessageTransactionBoundaryTest` makes: nothing
 * goes out before the commit, and nothing goes out at all if the commit fails. A method that opens
 * no transaction fails the first — [RecordingRunner] starts at -1 rather than 0, so "never asked"
 * is not mistaken for "asked and clean".
 */
class GroupMutationTransactionBoundaryTest {

    private val conversationRepository: ConversationRepository = mockk(relaxed = true)
    private val userRepository: UserRepository = mockk(relaxed = true)
    private val messageBroadcaster: MessageBroadcaster = mockk(relaxed = true)
    private val blockPolicy: BlockPolicyPort = mockk(relaxed = true)

    private val ownerId = UUID.randomUUID()
    private val memberId = UUID.randomUUID()
    private val newUserId = UUID.randomUUID()
    private val groupId = UUID.randomUUID()

    private fun member(userId: UUID, role: MemberRole) =
        ConversationMember(conversationId = groupId, userId = userId, role = role)

    @BeforeEach
    fun setUp() {
        every { blockPolicy.hasBlocked(any(), any()) } returns false
        every { conversationRepository.findById(groupId) } returns
            Conversation(id = groupId, type = ConversationType.GROUP, name = "Test Group", createdBy = ownerId)
        every { conversationRepository.findMember(groupId, ownerId) } returns member(ownerId, MemberRole.OWNER)
        every { conversationRepository.findMember(groupId, memberId) } returns member(memberId, MemberRole.MEMBER)
        every { conversationRepository.findMembersByConversationId(groupId) } returns listOf(
            member(ownerId, MemberRole.OWNER),
            member(memberId, MemberRole.MEMBER)
        )
        every { conversationRepository.saveMember(any()) } answers { firstArg() }
        every { conversationRepository.updateConversation(any()) } answers { firstArg() }
        every { userRepository.findAllByIds(any()) } returns listOf(
            User(id = newUserId, phoneNumber = "+905000000009", displayName = "Yeni", status = UserStatus.ACTIVE)
        )
    }

    private fun serviceWith(transactions: TransactionRunner) = GroupService(
        conversationRepository = conversationRepository,
        userRepository = userRepository,
        messageBroadcaster = messageBroadcaster,
        blockPolicy = blockPolicy,
        transactions = transactions
    )

    /** See [MessageMutationTransactionBoundaryTest] — same recorder, same reason for starting at -1. */
    private class RecordingRunner(private val broadcasterCalls: () -> Int) : TransactionRunner {
        var callsAtCommit: Int = -1
        override fun <T : Any> inTransaction(block: () -> T): T {
            val result = block()
            callsAtCommit = broadcasterCalls()
            return result
        }
    }

    /**
     * Runs [mutate] twice: once against a runner that watches the commit boundary, once against one
     * that rolls back. Every one of the five sites is checked the same way, so the shared helper is
     * the honest expression of it — five copies of these nine lines would say no more.
     */
    private fun assertFansOutOnlyAfterCommit(what: String, mutate: GroupService.() -> Unit) {
        var broadcasts = 0
        every { messageBroadcaster.broadcastToUsers(any(), any()) } answers { broadcasts++; Unit }

        val runner = RecordingRunner { broadcasts }
        serviceWith(runner).mutate()

        assertEquals(0, runner.callsAtCommit, "$what fanned out inside the transaction")
        assertEquals(1, broadcasts, "$what did not fan out after the commit either")

        broadcasts = 0
        assertThrows(IllegalStateException::class.java) {
            serviceWith(FailingTransactionRunner(IllegalStateException("commit failed"))).mutate()
        }
        assertEquals(0, broadcasts, "$what fanned out for a transaction that did not commit")
    }

    @Test
    fun `should not broadcast until the transaction has committed when members are added`() {
        assertFansOutOnlyAfterCommit("addMembers") { addMembers(groupId, ownerId, listOf(newUserId)) }
    }

    @Test
    fun `should not broadcast until the transaction has committed when a member is removed`() {
        assertFansOutOnlyAfterCommit("removeMember") { removeMember(groupId, ownerId, memberId) }
    }

    @Test
    fun `should not broadcast until the transaction has committed when group info is updated`() {
        assertFansOutOnlyAfterCommit("updateGroupInfo") {
            updateGroupInfo(groupId, ownerId, "Yeni ad", null, null)
        }
    }

    @Test
    fun `should not broadcast until the transaction has committed when a member role is updated`() {
        assertFansOutOnlyAfterCommit("updateMemberRole") {
            updateMemberRole(groupId, ownerId, memberId, MemberRole.ADMIN)
        }
    }

    @Test
    fun `should not broadcast until the transaction has committed when a member leaves`() {
        assertFansOutOnlyAfterCommit("leaveGroup") { leaveGroup(groupId, memberId) }
    }

    @Test
    fun `should not broadcast when the last member leaves an empty group`() {
        every { conversationRepository.findMember(groupId, ownerId) } returns member(ownerId, MemberRole.OWNER)
        every { conversationRepository.findMembersByConversationId(groupId) } returns
            listOf(member(ownerId, MemberRole.OWNER))

        serviceWith(RecordingRunner { 0 }).leaveGroup(groupId, ownerId)

        // There is nobody left to tell. The null recipient list carries that decision out of the
        // transaction; an empty list would have said the same thing by accident.
        verify(exactly = 0) { messageBroadcaster.broadcastToUsers(any(), any()) }
        verify { conversationRepository.removeMember(groupId, ownerId) }
    }
}
