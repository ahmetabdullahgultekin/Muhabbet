package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.Status
import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
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
    private lateinit var blockPolicy: BlockPolicyPort
    private lateinit var service: StatusService

    private val viewer = TestData.USER_ID_1
    private val contact = TestData.USER_ID_2
    private val stranger = TestData.USER_ID_3

    @BeforeEach
    fun setUp() {
        statusRepository = mockk()
        conversationRepository = mockk()
        userDirectory = mockk()
        blockPolicy = mockk()
        // Default across the suite: nobody has blocked anybody, so every pre-existing expectation
        // in this file keeps meaning what it meant. Both directions, for the same reason.
        every { blockPolicy.findBlockedBy(any(), any()) } returns emptySet()
        every { blockPolicy.findBlockedAmong(any(), any()) } returns emptySet()
        // Nothing here posts media, so the attachment policy is never consulted.
        service = StatusService(
            statusRepository,
            conversationRepository,
            userDirectory,
            blockPolicy,
            mockk(relaxed = true)
        )
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

    /**
     * #294, vector 4 - "can a blocked person still watch your stories?"
     *
     * Contact scope alone does not answer this. A block does not delete the conversation the two
     * share, so the person you blocked stays a "contact" by the only definition this app has, and
     * [StatusService.getContactStatusesForUser] served them every status you posted afterwards. Of
     * the six surfaces a block has to close, this was the one still open: presence, about, the send
     * path and the group-add all grew a guard in #554 and this did not.
     *
     * The audience list is not a substitute. It is the author's own allow/deny list, maintained per
     * status in the composer - nobody edits it when they block someone, and expecting them to would
     * make blocking a two-step action that silently half-works.
     *
     * Asked in the batched direction on purpose: one query for the whole contact set, not one per
     * author. The Updates tab resolves everyone the viewer knows the moment it opens.
     */
    @Nested
    inner class BlockedViewer {

        @Test
        fun `should hide an author's statuses from someone that author has blocked`() {
            every { conversationRepository.findAllContactUserIds(viewer) } returns setOf(contact)
            every { blockPolicy.findBlockedBy(viewer, setOf(contact)) } returns setOf(contact)
            every { statusRepository.findActiveByUserIds(any()) } returns listOf(status(contact))
            every { userDirectory.findDisplayInfo(any()) } returns emptyMap()

            assertTrue(service.getContactStatusesForUser(viewer).isEmpty())
        }

        @Test
        fun `should keep the statuses of contacts who have not blocked the viewer`() {
            // The blocker is dropped and nobody else is: one hostile contact must not cost the
            // viewer the rest of the tab.
            every { conversationRepository.findAllContactUserIds(viewer) } returns setOf(contact, stranger)
            every { blockPolicy.findBlockedBy(viewer, any()) } returns setOf(contact)
            // Answers the ids it is actually asked for, the way the real query does. A stub that
            // returned both rows regardless would hide the whole point: the narrowing happens in
            // the argument, so a fixed return value would make the test pass even if the service
            // asked for everyone.
            val posted = mapOf(
                contact to status(contact, content = "from the blocker"),
                stranger to status(stranger, content = "from someone else")
            )
            every { statusRepository.findActiveByUserIds(any()) } answers {
                firstArg<Collection<UUID>>().mapNotNull { posted[it] }
            }
            every { userDirectory.findDisplayInfo(any()) } returns emptyMap()

            val groups = service.getContactStatusesForUser(viewer)

            assertAll(
                { assertEquals(1, groups.size) },
                { assertEquals(stranger, groups.single().userId) },
                { assertEquals("from someone else", groups.single().statuses.single().content) }
            )
        }

        @Test
        fun `should not read a status the blocker posted at all`() {
            // Narrowed before the repository call, not after it. A row that never leaves the
            // database cannot leak through a later change to the mapping, and the query is smaller.
            every { conversationRepository.findAllContactUserIds(viewer) } returns setOf(contact, stranger)
            every { blockPolicy.findBlockedBy(viewer, setOf(contact, stranger)) } returns setOf(contact)
            every { statusRepository.findActiveByUserIds(setOf(stranger)) } returns emptyList()

            service.getContactStatusesForUser(viewer)

            verify(exactly = 1) { statusRepository.findActiveByUserIds(setOf(stranger)) }
            verify(exactly = 0) { statusRepository.findActiveByUserIds(match { contact in it }) }
        }

        @Test
        fun `should return nothing without touching the repository when every contact has blocked the viewer`() {
            every { conversationRepository.findAllContactUserIds(viewer) } returns setOf(contact)
            every { blockPolicy.findBlockedBy(viewer, setOf(contact)) } returns setOf(contact)

            assertTrue(service.getContactStatusesForUser(viewer).isEmpty())

            verify(exactly = 0) { statusRepository.findActiveByUserIds(any()) }
        }

        @Test
        fun `should not ask about blocks when the viewer has no contacts`() {
            // The empty-contact short circuit predates this guard and stays in front of it: no
            // contacts means no query of any kind, the block lookup included.
            every { conversationRepository.findAllContactUserIds(viewer) } returns emptySet()

            assertTrue(service.getContactStatusesForUser(viewer).isEmpty())

            verify(exactly = 0) { blockPolicy.findBlockedBy(any(), any()) }
            verify(exactly = 0) { blockPolicy.findBlockedAmong(any(), any()) }
        }
    }

    /**
     * #687 - the other half of the same control.
     *
     * [BlockedViewer] above covers the direction #685 closed: A blocks B, and B stops seeing A's
     * stories. This class covers the direction it left open: A blocks B, and **A** keeps seeing
     * B's stories, every day, in the Updates tab. The filter asked only "who has blocked me", so a
     * block was half a control - and the half that was missing is the one the person who pressed
     * Block was actually asking for. Blocking a harasser and then being shown their stories is not
     * a lesser bug than the reverse; it is the one the user notices.
     *
     * Both directions are asked as one batched question each, never one per author. A feed that
     * resolves every contact the moment it opens cannot afford a query per row, which is the whole
     * reason the port carries a batched shape at all.
     */
    @Nested
    inner class ViewerWhoBlocked {

        @Test
        fun `should hide the statuses of someone the viewer has blocked`() {
            every { conversationRepository.findAllContactUserIds(viewer) } returns setOf(contact)
            every { blockPolicy.findBlockedAmong(viewer, setOf(contact)) } returns setOf(contact)
            every { statusRepository.findActiveByUserIds(any()) } returns listOf(status(contact))
            every { userDirectory.findDisplayInfo(any()) } returns emptyMap()

            assertTrue(
                service.getContactStatusesForUser(viewer).isEmpty(),
                "blocking someone must stop their stories reaching the blocker"
            )
        }

        @Test
        fun `should keep the statuses of contacts the viewer has not blocked`() {
            // Blocking one person costs the viewer that person's stories and nothing else.
            every { conversationRepository.findAllContactUserIds(viewer) } returns setOf(contact, stranger)
            every { blockPolicy.findBlockedAmong(viewer, any()) } returns setOf(contact)
            val posted = mapOf(
                contact to status(contact, content = "from the person the viewer blocked"),
                stranger to status(stranger, content = "from someone else")
            )
            every { statusRepository.findActiveByUserIds(any()) } answers {
                firstArg<Collection<UUID>>().mapNotNull { posted[it] }
            }
            every { userDirectory.findDisplayInfo(any()) } returns emptyMap()

            val groups = service.getContactStatusesForUser(viewer)

            assertAll(
                { assertEquals(1, groups.size) },
                { assertEquals(stranger, groups.single().userId) },
                { assertEquals("from someone else", groups.single().statuses.single().content) }
            )
        }

        @Test
        fun `should not read a status posted by someone the viewer blocked`() {
            // Narrowed before the repository call, exactly as the other direction is: a row that
            // is never loaded cannot leak through a later change to the mapping below it.
            every { conversationRepository.findAllContactUserIds(viewer) } returns setOf(contact, stranger)
            every { blockPolicy.findBlockedAmong(viewer, setOf(contact, stranger)) } returns setOf(contact)
            every { statusRepository.findActiveByUserIds(setOf(stranger)) } returns emptyList()

            service.getContactStatusesForUser(viewer)

            verify(exactly = 1) { statusRepository.findActiveByUserIds(setOf(stranger)) }
            verify(exactly = 0) { statusRepository.findActiveByUserIds(match { contact in it }) }
        }

        @Test
        fun `should hide an author blocked in either direction and keep the one blocked in neither`() {
            // The case a one-directional filter can never produce: one contact blocked the viewer,
            // the viewer blocked another, and a third is party to neither. Only the third survives.
            val third = UUID.fromString("00000000-0000-0000-0000-0000000000b1")
            every { conversationRepository.findAllContactUserIds(viewer) } returns
                setOf(contact, stranger, third)
            every { blockPolicy.findBlockedBy(viewer, any()) } returns setOf(contact)
            every { blockPolicy.findBlockedAmong(viewer, any()) } returns setOf(stranger)
            val posted = mapOf(
                contact to status(contact, content = "blocked the viewer"),
                stranger to status(stranger, content = "the viewer blocked them"),
                third to status(third, content = "no block either way")
            )
            every { statusRepository.findActiveByUserIds(any()) } answers {
                firstArg<Collection<UUID>>().mapNotNull { posted[it] }
            }
            every { userDirectory.findDisplayInfo(any()) } returns emptyMap()

            val groups = service.getContactStatusesForUser(viewer)

            assertAll(
                { assertEquals(1, groups.size) },
                { assertEquals(third, groups.single().userId) },
                { assertEquals("no block either way", groups.single().statuses.single().content) }
            )
        }

        @Test
        fun `should return nothing without touching the repository when every contact is blocked either way`() {
            every { conversationRepository.findAllContactUserIds(viewer) } returns setOf(contact, stranger)
            every { blockPolicy.findBlockedBy(viewer, any()) } returns setOf(contact)
            every { blockPolicy.findBlockedAmong(viewer, any()) } returns setOf(stranger)

            assertTrue(service.getContactStatusesForUser(viewer).isEmpty())

            verify(exactly = 0) { statusRepository.findActiveByUserIds(any()) }
        }

        @Test
        fun `should ask each block direction exactly once for the whole contact set`() {
            // The N+1 guard. `findBlockedAmong` exists so a feed can be filtered with one query
            // rather than one per author; asking it per contact would put a block lookup on every
            // row of the screen the app opens most.
            val third = UUID.fromString("00000000-0000-0000-0000-0000000000b2")
            val contacts = setOf(contact, stranger, third)
            every { conversationRepository.findAllContactUserIds(viewer) } returns contacts
            every { statusRepository.findActiveByUserIds(any()) } returns emptyList()

            service.getContactStatusesForUser(viewer)

            verify(exactly = 1) { blockPolicy.findBlockedBy(viewer, contacts) }
            verify(exactly = 1) { blockPolicy.findBlockedAmong(viewer, contacts) }
        }
    }
}
