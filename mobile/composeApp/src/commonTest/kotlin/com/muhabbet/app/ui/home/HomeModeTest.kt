package com.muhabbet.app.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Back on the home shell resolves innermost first (#803): search, then tab, then the platform. */
class HomeModeTest {

    @Test
    fun should_closeSearchAndKeepTheTab_when_backWhileSearching() {
        assertEquals(
            HomeMode.Browsing(HomeTab.UPDATES),
            HomeMode.Searching(HomeTab.UPDATES, query = "ayşe").afterBack()
        )
    }

    @Test
    fun should_returnToChats_when_backOnAnotherTab() {
        HomeTab.entries.filter { it != HomeTab.CHATS }.forEach { tab ->
            assertEquals(HomeMode.Browsing(HomeTab.CHATS), HomeMode.Browsing(tab).afterBack(), "from $tab")
        }
    }

    @Test
    fun should_leaveBackToThePlatform_when_browsingChats() {
        assertNull(HomeMode.Browsing(HomeTab.CHATS).afterBack())
    }

    @Test
    fun should_takeThreeBackPresses_when_searchingFromCalls() {
        var mode: HomeMode? = HomeMode.Searching(HomeTab.CALLS, "x")
        val visited = mutableListOf<HomeMode>()
        while (mode != null) {
            visited += mode
            mode = mode.afterBack()
        }
        assertEquals(
            listOf(HomeMode.Searching(HomeTab.CALLS, "x"), HomeMode.Browsing(HomeTab.CALLS), HomeMode.Browsing(HomeTab.CHATS)),
            visited
        )
    }
}
