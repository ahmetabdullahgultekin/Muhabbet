package com.muhabbet.app.navigation

import com.arkivanov.essenty.backhandler.BackCallback
import com.muhabbet.app.navigation.MainComponent.Config
import com.muhabbet.app.presentation.TestComponentContext
import com.muhabbet.app.presentation.belowScreenModes
import com.muhabbet.app.ui.conversations.ChatTarget
import com.muhabbet.app.ui.profile.DirectChatOpener
import com.muhabbet.app.ui.profile.DirectChatStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The signed-in navigation, driven through a real Decompose context with no Compose and no DI
 * container: destination identity (#825), cold-start and running chat opens (#823, #594), the
 * profile's "Message" (#806), and back resolution (#803).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainComponentTest {

    private val pending = PendingChatOpen()
    private val directChats = mutableMapOf("u-member" to "dm-member")
    private var lookups = 0

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun dependencies(lookup: ConversationLookup = ConversationLookup { lookups++; null }) = MainDependencies(
        pendingChatOpen = pending,
        conversationLookup = lookup,
        blockedUsersStore = { error("not used here") },
        appLockSettingsStore = { error("not used here") },
        directChatStore = { userId, known ->
            DirectChatStore(userId, known, DirectChatOpener { directChats.getValue(it) })
        }
    )

    private fun main(
        host: TestComponentContext = TestComponentContext(),
        lookup: ConversationLookup = ConversationLookup { lookups++; null }
    ) = MainComponent(host.context, dependencies(lookup), onLogout = {})

    private val MainComponent.configs: List<Config> get() = childStack.value.items.map { it.configuration }

    private fun chat(id: String, name: String = "Ayşe") = ChatTarget(conversationId = id, name = name)

    @Test
    fun should_keepOneSettings_when_openedTwice() {
        val main = main()

        main.openSettings()
        main.openSettings()

        assertEquals(listOf(Config.HomeShell, Config.Settings()), main.configs)
    }

    @Test
    fun should_keepOneChatAndMoveToTheMessage_when_reopenedFromStarred() {
        val main = main()

        main.openChat(chat("x"))
        main.openStarredMessages()
        main.openChat(chat("x"), scrollToMessageId = "m")

        assertEquals(
            listOf(Config.HomeShell, Config.Chat("x", name = "Ayşe", scrollToMessageId = "m")),
            main.configs
        )
    }

    @Test
    fun should_openOnTheChatWithNoListFirst_when_aNotificationStartedTheApp() {
        pending.request(ChatOpenRequest("x"))

        // A directory that never answers must not hold navigation back (#823).
        val main = main(lookup = ConversationLookup { awaitCancellation() })

        assertEquals(listOf(Config.HomeShell, Config.Chat("x")), main.configs)
        assertIs<MainComponent.Child.Chat>(main.childStack.value.active.instance)
        assertNull(pending.pending.value, "the request is consumed by the initial stack, not replayed")
    }

    @Test
    fun should_navigateAtOnce_when_aNotificationArrivesWhileRunning() {
        val main = main(lookup = ConversationLookup { awaitCancellation() })

        pending.request(ChatOpenRequest("x"))

        assertEquals(listOf(Config.HomeShell, Config.Chat("x")), main.configs)
        assertNull(pending.pending.value)
    }

    @Test
    fun should_returnToTheOpenChat_when_itsNotificationIsTapped() {
        val main = main()
        main.openChat(chat("x", name = "Elif Zümra"))

        pending.request(ChatOpenRequest("x"))

        assertEquals(listOf(Config.HomeShell, Config.Chat("x", name = "Elif Zümra")), main.configs)
    }

    @Test
    fun should_resolveTheTitleInTheDestination_when_openedByIdOnly() {
        pending.request(ChatOpenRequest("x"))
        main()

        assertEquals(1, lookups, "the chat looks itself up after navigating, not before")
    }

    @Test
    fun should_notLookUp_when_theOpenerKnewTheName() {
        val main = main()
        main.openChat(chat("x"))

        assertEquals(0, lookups)
    }

    @Test
    fun should_openTheDirectChat_when_messageTappedOnAGroupMembersProfile() {
        val main = main()
        main.openGroupInfo("g", "Aile")
        main.openUserProfile("u-member")

        val profile = assertIs<MainComponent.Child.UserProfile>(main.childStack.value.active.instance)
        profile.component.onMessageClick()

        // The chat on top; back returns to the profile the user came from, not past it.
        assertEquals(
            listOf(Config.HomeShell, Config.GroupInfo("g", "Aile"), Config.UserProfile("u-member"), Config.Chat("dm-member")),
            main.configs
        )
    }

    @Test
    fun should_popBackToTheChat_when_messageTappedOnTheProfileOpenedFromIt() {
        val main = main()
        main.openChat(chat("dm-member"))
        main.openUserProfile("u-member", "Ayşe", conversationId = "dm-member")

        val profile = assertIs<MainComponent.Child.UserProfile>(main.childStack.value.active.instance)
        profile.component.onMessageClick()

        assertEquals(listOf(Config.HomeShell, Config.Chat("dm-member", name = "Ayşe")), main.configs)
    }

    @Test
    fun should_giveEveryEntryItsOwnContext() {
        val main = main()
        main.openSettings()

        val contexts = main.childStack.value.items.map { it.instance.context }
        assertEquals(contexts.size, contexts.toSet().size)
    }

    @Test
    fun should_letAnInScreenModeWinOverThePop_when_bothAreRegistered() {
        val host = TestComponentContext()
        val main = main(host)
        main.openChat(chat("x"))
        var pops = 0
        // What MainContent registers for the predictive-back animation, in composition — i.e. after
        // the destinations' handlers, which is the order that used to let it win.
        main.backHandler.belowScreenModes().register(BackCallback { pops++; main.goBack() })
        var cancelledEdit = false
        val editMode = BackCallback { cancelledEdit = true }
        main.childStack.value.active.instance.context.backHandler.register(editMode)

        host.backDispatcher.back()

        assertTrue(cancelledEdit, "back cancels the edit first")
        assertEquals(0, pops)
        assertEquals(listOf(Config.HomeShell, Config.Chat("x", name = "Ayşe")), main.configs)

        editMode.isEnabled = false
        host.backDispatcher.back()

        assertEquals(1, pops, "with no mode open, back pops the chat")
        assertEquals(listOf(Config.HomeShell), main.configs)
    }

    @Test
    fun should_leaveBackToThePlatform_when_atTheStartWithNoMode() {
        val host = TestComponentContext()
        main(host)

        assertEquals(false, host.backDispatcher.back(), "unhandled, so Android leaves the app")
    }
}
