package com.muhabbet.moderation.adapter.out.persistence

import com.muhabbet.shared.TestData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * The two batched block lookups are mirror images, and the only thing separating them is which
 * column each reads back. Get that one line wrong and a block is enforced in the wrong direction
 * while every higher-level test still passes, because a mocked port answers whatever it is told to
 * (#687 - the direction the feed was missing was invisible for exactly this reason).
 *
 * So this asserts the mapping itself: which query is issued, and which side of the row is returned.
 * A row here always has a distinct blocker and blocked, so an inverted `mapTo` cannot produce the
 * expected set by coincidence.
 */
class BlockPersistenceAdapterTest {

    private lateinit var springData: SpringDataBlockRepository
    private lateinit var adapter: BlockPersistenceAdapter

    private val me = TestData.USER_ID_1
    private val other = TestData.USER_ID_2

    @BeforeEach
    fun setUp() {
        springData = mockk()
        adapter = BlockPersistenceAdapter(springData)
    }

    private fun block(blocker: UUID, blocked: UUID) =
        BlockJpaEntity(blockerId = blocker, blockedId = blocked)

    @Test
    fun `should return the blocker side when asked who among these has blocked me`() {
        every { springData.findByBlockedIdAndBlockerIdIn(me, listOf(other)) } returns
            listOf(block(blocker = other, blocked = me))

        assertEquals(setOf(other), adapter.findBlockersAmong(me, listOf(other)))
    }

    @Test
    fun `should return the blocked side when asked which of these I have blocked`() {
        every { springData.findByBlockerIdAndBlockedIdIn(me, listOf(other)) } returns
            listOf(block(blocker = me, blocked = other))

        assertEquals(setOf(other), adapter.findBlockedAmong(me, listOf(other)))
    }

    @Test
    fun `should ask the mirror query for the mirror question`() {
        // Named because the two Spring Data methods differ by argument order alone: calling the
        // wrong one compiles, runs, and answers the opposite question.
        every { springData.findByBlockerIdAndBlockedIdIn(any(), any()) } returns emptyList()

        adapter.findBlockedAmong(me, listOf(other))

        verify(exactly = 1) { springData.findByBlockerIdAndBlockedIdIn(me, listOf(other)) }
        verify(exactly = 0) { springData.findByBlockedIdAndBlockerIdIn(any(), any()) }
    }

    @Test
    fun `should not issue a query at all for an empty candidate set`() {
        // An empty IN () is pointless and invalid on some engines - the same short circuit its
        // mirror has carried since #294.
        assertEquals(emptySet<UUID>(), adapter.findBlockedAmong(me, emptyList()))

        verify(exactly = 0) { springData.findByBlockerIdAndBlockedIdIn(any(), any()) }
    }

    @Test
    fun `should collapse duplicate candidate ids before querying`() {
        every { springData.findByBlockerIdAndBlockedIdIn(me, listOf(other)) } returns emptyList()

        adapter.findBlockedAmong(me, listOf(other, other, other))

        verify(exactly = 1) { springData.findByBlockerIdAndBlockedIdIn(me, listOf(other)) }
    }
}
