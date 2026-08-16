package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.BroadcastList
import com.muhabbet.messaging.domain.model.BroadcastListMember
import com.muhabbet.messaging.domain.port.out.BroadcastListRepository
import com.muhabbet.messaging.domain.port.out.UserDirectoryPort
import com.muhabbet.messaging.domain.port.out.UserDisplayInfo
import com.muhabbet.shared.TestData
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

/**
 * The recipient count and the recipient names, which are the two things the broadcast screens
 * render and which the domain had no way to produce before #392.
 */
class BroadcastListServiceTest {

    private lateinit var repository: BroadcastListRepository
    private lateinit var userDirectory: UserDirectoryPort
    private lateinit var service: BroadcastListService

    private val ownerId = TestData.USER_ID_1
    private val strangerId = TestData.USER_ID_2
    private val listId = UUID.randomUUID()
    private val otherListId = UUID.randomUUID()
    private val memberId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        repository = mockk(relaxUnitFun = true)
        userDirectory = mockk()
        service = BroadcastListService(repository, userDirectory)
    }

    @Test
    fun `should resolve every count in one query when listing`() {
        every { repository.findByOwnerId(ownerId) } returns listOf(list(listId, "Aile"), list(otherListId, "İş"))
        every { repository.countMembersByListIds(listOf(listId, otherListId)) } returns mapOf(listId to 7)

        val summaries = service.getByOwner(ownerId)

        assertEquals(listOf(7, 0), summaries.map { it.memberCount })
        // A list with no recipients produces no GROUP BY row, so the absent key must default to 0
        // rather than drop the list from the screen.
        assertEquals(listOf("Aile", "İş"), summaries.map { it.list.name })
        verify(exactly = 1) { repository.countMembersByListIds(any()) }
    }

    @Test
    fun `should not ask for counts when the owner has no lists`() {
        // `IN ()` is not valid SQL; the guard is what keeps an empty account from erroring.
        every { repository.findByOwnerId(ownerId) } returns emptyList()

        assertTrue(service.getByOwner(ownerId).isEmpty())

        verify(exactly = 0) { repository.countMembersByListIds(any()) }
    }

    @Test
    fun `should report the count a new list was created with`() {
        every { repository.save(any()) } answers { firstArg() }
        every { repository.addMember(any()) } answers { firstArg() }

        val summary = service.create(ownerId, "Aile", listOf(memberId))

        assertEquals(1, summary.memberCount)
        assertEquals("Aile", summary.list.name)
    }

    @Test
    fun `should count a repeated recipient once when creating`() {
        // Two identical ids would otherwise be two membership rows and a count of 2 for one person.
        val saved = slot<BroadcastListMember>()
        every { repository.save(any()) } answers { firstArg() }
        every { repository.addMember(capture(saved)) } answers { firstArg() }

        assertEquals(1, service.create(ownerId, "Aile", listOf(memberId, memberId)).memberCount)

        verify(exactly = 1) { repository.addMember(any()) }
    }

    @Test
    fun `should put a name and an avatar on each recipient`() {
        every { repository.findById(listId) } returns list(listId, "Aile")
        every { repository.findMembers(listId) } returns listOf(BroadcastListMember(listId, memberId))
        every { userDirectory.findDisplayInfo(listOf(memberId)) } returns mapOf(
            memberId to UserDisplayInfo(memberId, "Ayşe", "https://cdn.example/a.jpg")
        )

        val member = service.getMembers(listId, ownerId).single()

        assertEquals(memberId, member.userId)
        assertEquals("Ayşe", member.displayName)
        assertEquals("https://cdn.example/a.jpg", member.avatarUrl)
    }

    @Test
    fun `should return a recipient the directory does not know with a null name`() {
        every { repository.findById(listId) } returns list(listId, "Aile")
        every { repository.findMembers(listId) } returns listOf(BroadcastListMember(listId, memberId))
        every { userDirectory.findDisplayInfo(listOf(memberId)) } returns emptyMap()

        val member = service.getMembers(listId, ownerId).single()

        // Dropping the row would silently shrink a recipient list the owner still broadcasts to.
        assertEquals(memberId, member.userId)
        assertNull(member.displayName)
    }

    @Test
    fun `should not ask the directory about an empty recipient list`() {
        every { repository.findById(listId) } returns list(listId, "Aile")
        every { repository.findMembers(listId) } returns emptyList()

        assertTrue(service.getMembers(listId, ownerId).isEmpty())

        verify(exactly = 0) { userDirectory.findDisplayInfo(any()) }
    }

    @Test
    fun `should refuse to show recipients of a list the caller does not own`() {
        every { repository.findById(listId) } returns list(listId, "Aile")

        val failure = assertThrows<BusinessException> { service.getMembers(listId, strangerId) }

        // NOT_FOUND rather than FORBIDDEN on purpose: a stranger must not learn that the id exists.
        assertEquals(ErrorCode.BROADCAST_LIST_NOT_FOUND, failure.errorCode)
        verify(exactly = 0) { repository.findMembers(any()) }
    }

    @Test
    fun `should skip recipients already on the list when adding`() {
        every { repository.findById(listId) } returns list(listId, "Aile")
        every { repository.findMembers(listId) } returns listOf(BroadcastListMember(listId, memberId))
        val newMemberId = UUID.randomUUID()
        every { repository.addMember(any()) } answers { firstArg() }
        every { userDirectory.findDisplayInfo(listOf(newMemberId)) } returns
            mapOf(newMemberId to UserDisplayInfo(newMemberId, "Mehmet", null))

        val added = service.addMembers(listId, ownerId, listOf(memberId, newMemberId))

        assertEquals(listOf(newMemberId), added.map { it.userId })
        verify(exactly = 1) { repository.addMember(any()) }
    }

    private fun list(id: UUID, name: String) = BroadcastList(
        id = id,
        ownerId = ownerId,
        name = name,
        createdAt = Instant.parse("2026-01-01T00:00:00Z")
    )
}
