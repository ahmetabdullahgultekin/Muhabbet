package com.muhabbet.app.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.navigate
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.value.Value
import com.muhabbet.app.presentation.componentScope
import com.muhabbet.app.ui.conversations.ChatTarget
import com.muhabbet.app.ui.privacy.BlockedUsersComponent
import com.muhabbet.app.ui.profile.UserProfileComponent
import com.muhabbet.app.ui.settings.AboutComponent
import com.muhabbet.app.ui.settings.AppLockSettingsComponent
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * The signed-in app's navigation: one stack, and a component per entry (ADR 0010).
 *
 * Every destination is created by [createChild] with its own `ComponentContext` — its own lifecycle,
 * `InstanceKeeper`, `StateKeeper` and back handler. Destinations already migrated to a Store get a
 * dedicated child type; the rest are [Child.Screen] until their migration lands, and already have
 * the context that migration needs.
 *
 * Every navigation goes through [navigatedTo] (or [replacedTopWith]), so a destination is on the
 * stack at most once and a double tap is a no-op (#825). There is no raw `push` left in this file,
 * and `verifyUi`'s `delicatePush` rule keeps it that way.
 */
class MainComponent(
    componentContext: ComponentContext,
    private val dependencies: MainDependencies,
    val onLogout: () -> Unit,
    openAtLanguageSettings: Boolean = false
) : ComponentContext by componentContext {

    private val navigation = StackNavigation<Config>()

    val childStack: Value<ChildStack<Config, Child>> = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialStack = { initialStack(openAtLanguageSettings, dependencies.pendingChatOpen.take()) },
        // The pop is owned by the predictive-back animation (see MainContent), registered below the
        // destinations' own in-screen modes. A second, stack-level callback here would sit *above*
        // the animation and pop without it.
        handleBackButton = false,
        childFactory = ::createChild
    )

    init {
        // A notification tapped, or a link opened, while the app is already running (#594). The
        // cold-start case never gets here: initialStack took that request before this line ran.
        componentScope().launch {
            dependencies.pendingChatOpen.pending.filterNotNull().collect {
                dependencies.pendingChatOpen.take()?.let(::openRequestedChat)
            }
        }
    }

    private fun createChild(config: Config, context: ComponentContext): Child = when (config) {
        is Config.Chat -> Child.Chat(ChatDestinationComponent(context, config, dependencies.conversationLookup))
        is Config.UserProfile -> Child.UserProfile(
            UserProfileComponent(
                componentContext = context,
                userId = config.userId,
                contactName = config.contactName,
                conversationId = config.conversationId,
                storeFactory = { dependencies.directChatStore(config.userId, config.conversationId) },
                openChat = { conversationId -> navigateTo(Config.Chat(conversationId)) }
            )
        )
        Config.BlockedUsers -> Child.BlockedUsers(BlockedUsersComponent(context, dependencies.blockedUsersStore, ::goBack))
        Config.About -> Child.About(AboutComponent(context, onBack = ::goBack, onReleaseNotes = ::openReleaseNotes))
        Config.AppLock -> Child.AppLock(AppLockSettingsComponent(context, dependencies.appLockSettingsStore, ::goBack))
        else -> Child.Screen(config, context)
    }

    private fun navigateTo(target: Config) {
        navigation.navigate { stack -> stack.navigatedTo(target, ::mergeArguments) }
    }

    private fun replaceTopWith(target: Config) {
        navigation.navigate { stack -> stack.replacedTopWith(target, ::mergeArguments) }
    }

    private fun openRequestedChat(request: ChatOpenRequest) {
        navigateTo(Config.Chat(conversationId = request.conversationId, isGroup = request.isGroup))
    }

    /**
     * The one way into a conversation. [ChatTarget] carries the whole identity, so a caller cannot
     * supply the id and quietly default the rest (#543, #555).
     *
     * [scrollToMessageId] is separate because it is not part of who the conversation is — it is
     * where in it to land. Re-opening a chat that is already on the stack returns to it and, if a new
     * landing point was given, moves there (#642, #825).
     */
    fun openChat(target: ChatTarget, scrollToMessageId: String? = null) {
        navigateTo(target.toConfig(scrollToMessageId))
    }

    fun openNewConversation() = navigateTo(Config.NewConversation)
    fun openSettings() = navigateTo(Config.Settings())
    fun openCreateGroup() = navigateTo(Config.CreateGroup)
    fun openGroupInfo(conversationId: String, conversationName: String) =
        navigateTo(Config.GroupInfo(conversationId, conversationName))
    fun openUserProfile(userId: String, contactName: String? = null, conversationId: String? = null) =
        navigateTo(Config.UserProfile(userId, contactName, conversationId))
    fun openStarredMessages() = navigateTo(Config.StarredMessages)

    /** The pinned "Arşivlenmiş · N" row's destination (#612). */
    fun openArchivedChats() = navigateTo(Config.ArchivedChats)
    fun openMessageInfo(messageId: String) = navigateTo(Config.MessageInfo(messageId))
    fun openSharedMedia(conversationId: String) = navigateTo(Config.SharedMedia(conversationId))
    fun openStatusViewer(userId: String, displayName: String) = navigateTo(Config.StatusViewer(userId, displayName))

    /**
     * Unreachable today: nothing turns an inbound ringing signal into a navigation, because the
     * client that would send `call.initiate` does not exist (#367–#373). Kept as the destination for
     * that wiring once it lands.
     */
    fun openIncomingCall(callId: String, callerId: String, callerName: String?, callType: String) =
        navigateTo(Config.IncomingCall(callId, callerId, callerName, callType))

    /** Reachable only through [replaceWithActiveCall] from a real incoming-call accept (#367–#373). */
    fun openActiveCall(callId: String, otherUserId: String, otherUserName: String?, callType: String) =
        navigateTo(Config.ActiveCall(callId, otherUserId, otherUserName, callType))

    fun openCallHistory() = navigateTo(Config.CallHistory)
    fun openPrivacyDashboard() = navigateTo(Config.PrivacyDashboard)
    fun openBlockedUsers() = navigateTo(Config.BlockedUsers)
    fun openTwoStepVerification() = navigateTo(Config.TwoStepVerification)
    fun openAppLock() = navigateTo(Config.AppLock)
    fun openWallpaper() = navigateTo(Config.Wallpaper)
    fun openStorage() = navigateTo(Config.Storage)
    fun openAbout() = navigateTo(Config.About)
    fun openReleaseNotes() = navigateTo(Config.ReleaseNotes)
    fun openCommunityDetail(communityId: String) = navigateTo(Config.CommunityDetail(communityId))
    fun openCommunityMembers(communityId: String) = navigateTo(Config.CommunityMembers(communityId))

    /** Unreachable today: the Calls-tab FAB that called this is gone (#684); kept for calling's return. */
    fun openPickContactForCall() = navigateTo(Config.PickContactForCall)
    fun openCreateCommunity() = navigateTo(Config.CreateCommunity)
    fun openGroupEvents(conversationId: String) = navigateTo(Config.GroupEvents(conversationId))
    fun openBroadcastLists() = navigateTo(Config.BroadcastLists)
    fun openBroadcastDetail(broadcastListId: String, broadcastListName: String) =
        navigateTo(Config.BroadcastDetail(broadcastListId, broadcastListName))

    /*
     * Swap the current screen for another in one operation.
     *
     * `goBack()` immediately followed by `openX()` is two navigations in one frame, and the second
     * can be lost — creating a community looked completely broken for exactly this reason. The
     * replace is one transformation of the stack.
     */

    /** Takes a whole [ChatTarget] for the same reason [openChat] does (#555). */
    fun replaceWithChat(target: ChatTarget) = replaceTopWith(target.toConfig(scrollToMessageId = null))
    fun replaceWithCreateGroup() = replaceTopWith(Config.CreateGroup)
    fun replaceWithCommunityDetail(communityId: String) = replaceTopWith(Config.CommunityDetail(communityId))
    fun replaceWithActiveCall(callId: String, callerId: String, callerName: String?, callType: String) =
        replaceTopWith(Config.ActiveCall(callId, callerId, callerName, callType))

    /**
     * Pops one entry. Nothing else: whether the screen underneath shows fresh data is that screen's
     * business, not navigation's (#828).
     */
    fun goBack() {
        navigation.pop()
    }

    /**
     * A stack entry. [identity] says which entries are the same destination (see [NavDestination]);
     * every other field is a launch argument.
     */
    @Serializable
    sealed interface Config : NavDestination {
        @Serializable data object HomeShell : Config
        @Serializable data object ConversationList : Config

        /**
         * A conversation. [name], [otherUserId], [avatarUrl] and [isGroup] are what the opener
         * already knew, used until [ChatDestinationComponent] resolves the conversation. A null
         * [name] means the opener knew only the id — a notification, a link — and the destination
         * resolves it.
         */
        @Serializable data class Chat(
            val conversationId: String,
            val name: String? = null,
            val otherUserId: String? = null,
            val isGroup: Boolean = false,
            val scrollToMessageId: String? = null,
            val avatarUrl: String? = null
        ) : Config {
            override val identity: String get() = conversationId
        }
        @Serializable data object NewConversation : Config
        // [focusLanguage] is set only by the launch that follows a language change, and scrolls the
        // screen to the Language section rather than leaving the user at the top of a long page
        // wondering whether anything happened.
        @Serializable data class Settings(val focusLanguage: Boolean = false) : Config
        @Serializable data object CreateGroup : Config
        @Serializable data class GroupInfo(val conversationId: String, val name: String) : Config {
            override val identity: String get() = conversationId
        }
        @Serializable data class UserProfile(val userId: String, val contactName: String? = null, val conversationId: String? = null) : Config {
            override val identity: String get() = userId
        }
        @Serializable data class StatusViewer(val userId: String, val displayName: String) : Config {
            override val identity: String get() = userId
        }
        @Serializable data object StarredMessages : Config
        @Serializable data object ArchivedChats : Config
        @Serializable data class SharedMedia(val conversationId: String) : Config {
            override val identity: String get() = conversationId
        }
        @Serializable data class MessageInfo(val messageId: String) : Config {
            override val identity: String get() = messageId
        }
        @Serializable data class IncomingCall(val callId: String, val callerId: String, val callerName: String? = null, val callType: String = "VOICE") : Config {
            override val identity: String get() = callId
        }
        @Serializable data class ActiveCall(val callId: String, val otherUserId: String, val otherUserName: String? = null, val callType: String = "VOICE") : Config {
            override val identity: String get() = callId
        }
        @Serializable data object CallHistory : Config
        @Serializable data object PrivacyDashboard : Config
        @Serializable data object BlockedUsers : Config
        @Serializable data object TwoStepVerification : Config
        @Serializable data object AppLock : Config
        @Serializable data object Wallpaper : Config
        @Serializable data object Storage : Config
        @Serializable data object About : Config
        @Serializable data object ReleaseNotes : Config
        @Serializable data class CommunityDetail(val communityId: String) : Config {
            override val identity: String get() = communityId
        }
        @Serializable data class CommunityMembers(val communityId: String) : Config {
            override val identity: String get() = communityId
        }
        @Serializable data object CreateCommunity : Config
        @Serializable data object PickContactForCall : Config
        @Serializable data class GroupEvents(val conversationId: String) : Config {
            override val identity: String get() = conversationId
        }
        @Serializable data object BroadcastLists : Config
        @Serializable data class BroadcastDetail(val broadcastListId: String, val broadcastListName: String) : Config {
            override val identity: String get() = broadcastListId
        }
    }

    /** A created stack entry. Each carries its own `ComponentContext`, for its lifecycle and back handler. */
    sealed interface Child {
        val context: ComponentContext

        /** A destination whose screen still holds its own state; migrates to a dedicated type. */
        class Screen(val config: Config, override val context: ComponentContext) : Child
        class Chat(val component: ChatDestinationComponent) : Child {
            override val context: ComponentContext get() = component
        }
        class UserProfile(val component: UserProfileComponent) : Child {
            override val context: ComponentContext get() = component
        }
        class BlockedUsers(val component: BlockedUsersComponent) : Child {
            override val context: ComponentContext get() = component
        }
        class About(val component: AboutComponent) : Child {
            override val context: ComponentContext get() = component
        }
        class AppLock(val component: AppLockSettingsComponent) : Child {
            override val context: ComponentContext get() = component
        }
    }

    internal companion object {

        /**
         * A whole stack rather than one entry, so a launch can land somewhere with the list
         * already underneath it — no visible navigation on the first frame:
         *  - a pending chat (a notification that started the process) opens on the chat (#823);
         *  - the language restart opens on Settings (#505).
         */
        fun initialStack(openAtLanguageSettings: Boolean, pendingChat: ChatOpenRequest?): List<Config> =
            buildList {
                add(Config.HomeShell)
                if (openAtLanguageSettings) add(Config.Settings(focusLanguage = true))
                if (pendingChat != null) {
                    add(Config.Chat(conversationId = pendingChat.conversationId, isGroup = pendingChat.isGroup))
                }
            }

        /**
         * What an entry already on the stack becomes when it is opened again. It is kept — re-opening
         * must not recreate a screen and throw its state away — except that a chat re-opened with a
         * new landing point moves there (Starred → a message in the chat already open).
         */
        fun mergeArguments(existing: Config, newer: Config): Config =
            if (existing is Config.Chat && newer is Config.Chat && newer.scrollToMessageId != null &&
                newer.scrollToMessageId != existing.scrollToMessageId
            ) {
                existing.copy(scrollToMessageId = newer.scrollToMessageId)
            } else {
                existing
            }
    }
}

private fun ChatTarget.toConfig(scrollToMessageId: String?): MainComponent.Config.Chat =
    MainComponent.Config.Chat(
        conversationId = conversationId,
        name = name,
        otherUserId = otherUserId,
        isGroup = isGroup,
        scrollToMessageId = scrollToMessageId,
        avatarUrl = avatarUrl
    )
