package com.muhabbet.auth.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [ExportedPage.of] is what keeps the KVKK export (#341) from silently truncating a large account's
 * messages/media — it must report `hasMore` honestly and never drop the "there's more" signal.
 */
class UserDataExportTest {

    @Test
    fun `should report no more pages when fewer items than the limit come back`() {
        val page = ExportedPage.of(items = listOf("a", "b"), limit = 5, totalCount = 2) { it }

        assertEquals(listOf("a", "b"), page.items)
        assertFalse(page.hasMore)
        assertNull(page.nextCursor)
        assertEquals(2, page.totalCount)
    }

    @Test
    fun `should report no more pages when exactly the limit worth of items come back`() {
        val page = ExportedPage.of(items = listOf("a", "b"), limit = 2, totalCount = 2) { it }

        assertFalse(page.hasMore, "an exact-limit fetch must not be mistaken for a full page")
        assertNull(page.nextCursor)
    }

    @Test
    fun `should trim to the limit and report more when limit plus one items come back`() {
        val page = ExportedPage.of(items = listOf("a", "b", "c"), limit = 2, totalCount = 500) { it }

        assertEquals(listOf("a", "b"), page.items, "the limit+1'th row is only a probe, not real data")
        assertTrue(page.hasMore)
        assertEquals("b", page.nextCursor, "cursor must resolve from the last item actually returned, not the probe row")
        assertEquals(500, page.totalCount)
    }
}
