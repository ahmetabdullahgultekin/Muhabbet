package com.muhabbet.app.ui.home

import androidx.compose.runtime.saveable.Saver

internal enum class HomeTab {
    COMMUNITIES,
    CHATS,
    UPDATES,
    CALLS
}

/**
 * What the home shell is doing: browsing a tab, or searching on top of one (#803).
 *
 * One value instead of three variables (`selectedTab`, `isSearchActive`, `searchQuery`) that could
 * disagree — a query with search closed, say. More importantly it is something back can be resolved
 * against: before this, those three lived in composable locals that no back callback could see, so
 * back from search, or from the Calls tab, closed the app.
 */
internal sealed interface HomeMode {
    /** The tab underneath; while searching, the one search returns to. */
    val tab: HomeTab

    data class Browsing(override val tab: HomeTab) : HomeMode
    data class Searching(override val tab: HomeTab, val query: String = "") : HomeMode
}

/**
 * Where back goes from here, innermost first — the Android and WhatsApp convention: close search,
 * then return to the start tab, then (null) leave it to the platform.
 */
internal fun HomeMode.afterBack(): HomeMode? = when (this) {
    is HomeMode.Searching -> HomeMode.Browsing(tab)
    is HomeMode.Browsing -> if (tab == START_TAB) null else HomeMode.Browsing(START_TAB)
}

internal val START_TAB = HomeTab.CHATS

/** Survives rotation and process death, as `selectedTab` did before it — query text included. */
internal val HomeModeSaver: Saver<HomeMode, List<String>> = Saver(
    save = { mode ->
        when (mode) {
            is HomeMode.Browsing -> listOf(mode.tab.name)
            is HomeMode.Searching -> listOf(mode.tab.name, mode.query)
        }
    },
    restore = { saved ->
        val tab = HomeTab.entries.firstOrNull { it.name == saved.firstOrNull() } ?: START_TAB
        if (saved.size > 1) HomeMode.Searching(tab, saved[1]) else HomeMode.Browsing(tab)
    }
)
