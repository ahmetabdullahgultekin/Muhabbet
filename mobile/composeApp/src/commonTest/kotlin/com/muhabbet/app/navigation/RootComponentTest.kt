package com.muhabbet.app.navigation

import com.arkivanov.essenty.backhandler.BackCallback
import com.muhabbet.app.data.local.FakeTokenStorage
import com.muhabbet.app.presentation.TestComponentContext
import com.muhabbet.app.presentation.belowScreenModes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** App Lock's cover consumes back without touching the stack it hides (#803). */
@OptIn(ExperimentalCoroutinesApi::class)
class RootComponentTest {

    private val host = TestComponentContext()
    private var backgrounded = 0

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun signedInRoot(): Pair<RootComponent, MainComponent> {
        val storage = FakeTokenStorage().apply { saveTokens("a", "r", "user-1", "device-1") }
        val root = RootComponent(
            componentContext = host.context,
            tokenStorage = storage,
            mainDependencies = MainDependencies(
                pendingChatOpen = PendingChatOpen(),
                conversationLookup = ConversationLookup { null },
                blockedUsersStore = { error("not used") },
                appLockSettingsStore = { error("not used") },
                directChatStore = { _, _ -> error("not used") }
            ),
            onBackWhileLocked = { backgrounded++ }
        )
        val main = assertIs<RootComponent.Child.Main>(root.childStack.value.active.instance).component
        // Stands in for the predictive-back callback MainContent registers in composition.
        main.backHandler.belowScreenModes().register(BackCallback { main.goBack() })
        return root to main
    }

    @Test
    fun should_backgroundTheAppAndLeaveTheStack_when_backWhileLocked() {
        val (root, main) = signedInRoot()
        main.openSettings()
        main.openAbout()
        val before = main.childStack.value.items.map { it.configuration }

        root.onLockCoverShown(true)
        host.backDispatcher.back()

        assertEquals(1, backgrounded)
        assertEquals(before, main.childStack.value.items.map { it.configuration })
    }

    @Test
    fun should_navigateNormally_when_theCoverIsGone() {
        val (root, main) = signedInRoot()
        main.openSettings()

        root.onLockCoverShown(true)
        root.onLockCoverShown(false)
        host.backDispatcher.back()

        assertEquals(0, backgrounded)
        assertEquals(listOf<MainComponent.Config>(MainComponent.Config.HomeShell), main.childStack.value.items.map { it.configuration })
    }

    @Test
    fun should_consumeBackEvenAtTheRoot_when_locked() {
        val (root, _) = signedInRoot()

        root.onLockCoverShown(true)

        assertEquals(true, host.backDispatcher.back(), "otherwise back at the root closes the app from behind the lock")
        assertEquals(1, backgrounded)
    }
}
