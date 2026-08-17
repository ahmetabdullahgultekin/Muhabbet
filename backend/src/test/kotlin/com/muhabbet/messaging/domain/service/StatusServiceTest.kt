package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.Status
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.StatusRepository
import com.muhabbet.messaging.domain.port.out.UserDirectoryPort
import com.muhabbet.messaging.domain.port.out.UserDisplayInfo
import com.muhabbet.shared.TestData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Regression cover for #507: the Updates tab showed a status from a stranger, labelled with a
 * truncated user id because there was no name to show. Both halves are tested here — the audience
 * is the viewer's contacts and nobody else, and every group the viewer does get carries a name.
 */
class StatusServiceTest {

    private lateinit var statusRepository: StatusRepository
    private lateinit var conversationRepository: ConversationRepository
    private lateinit var userDirectory: UserDirectoryPort
    private lateinit var service: StatusService

    private val viewer = TestData.USER_ID_1
    private val contact = TestData.USER_ID_2
    private val stranger = TestData.USER_ID_3

    @BeforeEach
    fun setUp() {
        statusRepository = mockk()
        conversationRepository = mockk()
        userDirectory = mockk()
        service = StatusService(statusRepository, conversationRepository, userDirectory)
    }

    private fun status(
        author: UUID,
        content: String = "Hello",
        visibility: String = "everyone",
        excluded: List<UUID> = emptyList(),
        included: List<UUID> = emptyList(),
        createdAt: Instant = Instant.now()
    ) = Status(
        userId = author,
        content = content,
        visibility = visibility,
        excludedUserIds = excluded,
        includedUserIds = included,
        createdAt = createdAt
    )

    @Nested
    inner class ContactScope {

        @Test
        fun `should return nothing when the viewer has no contacts`() {
            every { conversationRepository.findAllContactUserIds(viewer) } returns emptySet()

            val groups = service.getContactStatusesForUser(viewer)

            assertTrue(groups.isEmpty(), "a viewer with no contacts must see no statuses")
        }

        @Test
        fun `should not query statuses at all when the viewer has no contacts`() {
            every { conversationRepository.findAllContactUserIds(viewer) } returns emptySet()

            service.getContactStatusesForUser(viewer)

            // The old code read every active status first and filtered afterwards. Scope is now
            // decided before the query, so there is nothing to leak even by accident.
            verify(exactly = 0) { statusRepository.findActiveByUserIds(any()) }
        }

        @Test
        fun `should ask only for statuses authored by the viewer's contacts`() {
            every { conversationRepository.findAllContactUserIds(viewer) } returns setOf(contact)
            every { statusRepository.findActiveByUserIds(setOf(contact)) } returns emptyList()

            service.getContactStatusesForUser(viewer)

            verify(exactly = 1) { statusRepository.findActiveByUserIds(setOf(contact)) }
        }

        @Test
        fun `should not return a status from someone the viewer shares no conversation with`() {
            // The reported case: a default "everyone" status from an unrelated account.
            every { conversationRepository.findAllContactUserIds(viewer) } returns setOf(contact)
            every { statusRepository.findActiveByUserIds(setOf(contact)) } returns
                listOf(status(contact))
            every { userDirectory.findDisplayInfo(any()) } returns
                mapOf(contact to UserDisplayInfo(contact, "Zümra", null))

            val groups = service.getContactStatusesForUser(viewer)

            assertAll(
                { assertEquals(1, groups.size) },
                { assertEquals(contact, groups.first().userId) },
                { assertTrue(groups.none { it.userId == stranger }, "stranger must not appear") }
            )
        }
    }

    @Nested
    inner class AuthorName {

        @Test
        fun `should carry the author's name and avatar so the client never labels a user id`() {
            every { conversationRepository.findAllContactUserIds(viewer) } returns setOf(contact)
            every { statusRepository.findActiveByUserIds(setOf(contact)) } returns
                listOf(status(contact))
            every { userDirectory.findDisplayInfo(setOf(contact)) } returns
                mapOf(contact to UserDisplayInfo(contact, "Zümra", "https://cdn/a.jpg"))

            val group = service.getContactStatusesForUser(viewer).single()

            assertAll(
                { assertEquals("Zümra", group.displayName) },
                { assertEquals("https://cdn/a.jpg", group.avatarUrl) }
            )
        }

        @Test
        fun `should leave the name null rather than invent one when the directory has no record`() {
            every { conversationRepository.findAllContactUserIds(viewer) } returns setOf(contact)
            every { statusRepository.findActiveByUserIds(setOf(contact)) } returns
                listOf(status(contact))
            every { userDirectory.findDisplayInfo(setOf(contact)) } returns emptyMap()

            val group = service.getContactStatusesForUser(viewer).single()

            assertNull(group.displayName, "an absent name is null, never a truncated id")
        }

        @Test
        fun `should resolve names for all authors in one directory call`() {
            val secondContact = UUID.fromString("00000000-0000-0000-0000-0000000000a1")
            every { conversationRepository.findAllContactUserIds(viewer) } returns
                setOf(contact, secondContact)
            every { statusRepository.findActiveByUserIds(any()) } returns
                listOf(status(contact), status(secondContact))
            every { userDirectory.findDisplayInfo(any()) } returns emptyMap()

            service.getContactStatusesForUser(viewer)

            verify(exactly = 1) { userDirectory.findDisplayInfo(any()) }
        }

        @Test
        fun `should not touch the directory when no status survives the audience filter`() {
            every { conversationRepository.findAllContactUserIds(viewer) } returns setOf(contact)
            every { statusRepository.findActiveByUserIds(setOf(contact)) } returns
                listOf(status(contact, excluded = listOf(viewer)))

            val groups = service.getContactStatusesForUser(viewer)

            assertTrue(groups.isEmpty())
            verify(exactly = 0) { userDirectory.findDisplayInfo(any()) }
        }
    }

    @Nested
    inner class AudienceFilter {

        @BeforeEach
        fun contactExists() {
            every { conversationRepository.findAllContactUserIds(viewer) } returns setOf(contact)
            every { userDirectory.findDisplayInfo(any()) } returns
                mapOf(contact to UserDisplayInfo(contact, "Zümra", null))
        }

        @Test
        fun `should drop a status that excludes the viewer`() {
            every { statusRepository.findActiveByUserIds(setOf(contact)) } returns
                listOf(status(contact, visibility = "contacts_except", excluded = listOf(viewer)))

            assertTrue(service.getContactStatusesForUser(viewer).isEmpty())
        }

        @Test
        fun `should keep a status whose exclusion list names somebody else`() {
            every { statusRepository.findActiveByUserIds(setOf(contact)) } returns
                listOf(status(contact, visibility = "contacts_except", excluded = listOf(stranger)))

            assertEquals(1, service.getContactStatusesForUser(viewer).size)
        }

        @Test
        fun `should keep an only_share_with status that names the viewer`() {
            every { statusRepository.findActiveByUserIds(setOf(contact)) } returns
                listOf(status(contact, visibility = "only_share_with", included = listOf(viewer)))

            assertEquals(1, service.getContactStatusesForUser(viewer).size)
        }

        @Test
        fun `should drop an only_share_with status that does not name the viewer`() {
            every { statusRepository.findActiveByUserIds(setOf(contact)) } returns
                listOf(status(contact, visibility = "only_share_with", included = listOf(stranger)))

            assertTrue(service.getContactStatusesForUser(viewer).isEmpty())
        }

        @Test
        fun `should still honour the exclusion list for a visibility value it does not recognise`() {
            // Unknown values used to fall through to `else -> true`, ignoring exclusions outright.
            every { statusRepository.findActiveByUserIds(setOf(contact)) } returns
                listOf(status(contact, visibility = "some_future_mode", excluded = listOf(viewer)))

            assertTrue(service.getContactStatusesForUser(viewer).isEmpty())
        }
    }

    @Nested
    inner class Grouping {

        @Test
        fun `should group an author's statuses newest first`() {
            val older = Instant.now().minusSeconds(600)
            val newer = Instant.now()
            every { conversationRepository.findAllContactUserIds(viewer) } returns setOf(contact)
            every { statusRepository.findActiveByUserIds(setOf(contact)) } returns listOf(
                status(contact, content = "older", createdAt = older),
                status(contact, content = "newer", createdAt = newer)
            )
            every { userDirectory.findDisplayInfo(any()) } returns emptyMap()

            val group = service.getContactStatusesForUser(viewer).single()

            assertAll(
                { assertEquals(2, group.statuses.size) },
                { assertEquals("newer", group.statuses.first().content) },
                { assertEquals("older", group.statuses.last().content) }
            )
        }
    }
}
