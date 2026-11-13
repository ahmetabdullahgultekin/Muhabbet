package com.muhabbet.app.navigation

import com.muhabbet.app.navigation.MainComponent.Config
import kotlin.test.Test
import kotlin.test.assertEquals

/** The stack transformation every navigation goes through (#825). */
class NavDestinationTest {

    private fun List<Config>.open(target: Config) = navigatedTo(target, MainComponent.Companion::mergeArguments)

    @Test
    fun should_pushOnce_when_openedTwice() {
        val stack = listOf(Config.HomeShell).open(Config.Settings()).open(Config.Settings())
        assertEquals(listOf(Config.HomeShell, Config.Settings()), stack)
    }

    @Test
    fun should_returnToTheChatNotStackIt_when_namesDiffer() {
        val stack = listOf(Config.HomeShell, Config.Chat("x", name = "Ayşe"), Config.UserProfile("u"))
            .open(Config.Chat("x", name = "Zümra"))
        assertEquals(listOf(Config.HomeShell, Config.Chat("x", name = "Ayşe")), stack)
    }

    @Test
    fun should_moveToTheNewLandingPoint_when_reopenedWithAScrollTarget() {
        val stack = listOf(Config.HomeShell, Config.Chat("x", name = "Ayşe"), Config.StarredMessages)
            .open(Config.Chat("x", name = "Ayşe", scrollToMessageId = "m"))
        assertEquals(listOf(Config.HomeShell, Config.Chat("x", name = "Ayşe", scrollToMessageId = "m")), stack)
    }

    @Test
    fun should_treatDifferentIdsAsDifferentDestinations() {
        val stack = listOf(Config.HomeShell, Config.GroupInfo("g1", "A")).open(Config.GroupInfo("g2", "B"))
        assertEquals(3, stack.size)
    }

    @Test
    fun should_replaceTheTopAndReuseAnExistingEntry_when_replacingWithAChatAlreadyBelow() {
        val stack = listOf(Config.HomeShell, Config.Chat("x"), Config.NewConversation)
            .replacedTopWith(Config.Chat("x", name = "Ayşe"), MainComponent.Companion::mergeArguments)
        assertEquals(listOf(Config.HomeShell, Config.Chat("x")), stack)
    }

    @Test
    fun should_startOnTheChat_when_aRequestIsPendingAtLaunch() {
        assertEquals(
            listOf(Config.HomeShell, Config.Chat("x", isGroup = true)),
            MainComponent.initialStack(openAtLanguageSettings = false, pendingChat = ChatOpenRequest("x", isGroup = true))
        )
        assertEquals(listOf(Config.HomeShell), MainComponent.initialStack(false, null))
    }
}
