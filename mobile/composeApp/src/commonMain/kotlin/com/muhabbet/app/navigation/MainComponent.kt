package com.muhabbet.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.DelicateDecomposeApi
import com.arkivanov.decompose.router.stack.navigate
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.router.stack.replaceCurrent
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.value.Value
import com.muhabbet.app.ui.call.ActiveCallScreen
import com.muhabbet.app.ui.call.CallHistoryScreen
import com.muhabbet.app.ui.call.IncomingCallScreen
import com.muhabbet.app.ui.chat.ChatScreen
import com.muhabbet.app.ui.chat.MessageInfoScreen
import com.muhabbet.app.ui.conversations.ArchivedChatsScreen
import com.muhabbet.app.ui.conversations.ConversationListScreen
import com.muhabbet.app.ui.home.HomeShellScreen
import com.muhabbet.app.ui.conversations.NewConversationScreen
import com.muhabbet.app.ui.group.CreateGroupScreen
import com.muhabbet.app.ui.group.GroupInfoScreen
import com.muhabbet.app.ui.profile.UserProfileScreen
import com.muhabbet.app.ui.settings.SettingsScreen
import com.muhabbet.app.ui.media.SharedMediaScreen
import com.muhabbet.app.ui.starred.StarredMessagesScreen
import com.muhabbet.app.ui.communities.CommunityDetailScreen
import com.muhabbet.app.ui.communities.CommunityListScreen
import com.muhabbet.app.ui.communities.CommunityMembersScreen
import com.muhabbet.app.ui.communities.CreateCommunityScreen
import com.muhabbet.app.ui.conversations.BroadcastDetailScreen
import com.muhabbet.app.ui.conversations.BroadcastListScreen
import com.muhabbet.app.ui.group.GroupEventScreen
import com.muhabbet.app.ui.privacy.PrivacyDashboardScreen
import com.muhabbet.app.ui.privacy.BlockedUsersScreen
import com.muhabbet.app.ui.settings.AboutScreen
import com.muhabbet.app.ui.settings.AppLockScreen
import com.muhabbet.app.ui.settings.TwoStepSetupScreen
import com.muhabbet.app.ui.settings.WallpaperPickerScreen
import com.muhabbet.app.ui.status.StatusViewerScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import com.muhabbet.app.ui.conversations.ChatTarget
import com.muhabbet.app.ui.conversations.toChatTarget
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.data.repository.ConversationDirectory
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.chat_default_name
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.muhabbet.app.ui.transition.AvatarHandoff
import com.muhabbet.app.ui.transition.LocalAvatarHandoff
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import com.arkivanov.decompose.extensions.compose.subscribeAsState

class MainComponent(
    componentContext: ComponentContext,
    val onLogout: () -> Unit,
    openAtLanguageSettings: Boolean = false
) : ComponentContext by componentContext {

    private val navigation = StackNavigation<Config>()

    private val _refreshTrigger = MutableStateFlow(0)
    val refreshTrigger: StateFlow<Int> = _refreshTrigger

    val childStack: Value<ChildStack<Config, Config>> = childStack(
        source = navigation,
        serializer = Config.serializer(),
        // A whole stack rather than one configuration, so the language restart lands on Settings
        // with the conversation list still underneath it. Pushing Settings after the fact would be
        // a visible navigation on launch, and starting with Settings alone would leave back with
        // nowhere to go. Everything else starts, as always, on the home shell.
        initialStack = {
            if (openAtLanguageSettings) {
                listOf(Config.HomeShell, Config.Settings(focusLanguage = true))
            } else {
                listOf(Config.HomeShell)
            }
        },
        handleBackButton = true,
        childFactory = { config, _ -> config }
    )

    /**
     * Opens a conversation, returning to it if it is already on the stack rather than stacking a
     * second copy of it (#642).
     *
     * This used to be an unconditional `push`. A notification tapped while that same chat was
     * already open therefore put a second chat screen on top of the first: the top one did not
     * respond, and back dropped it to reveal the real one underneath — which is what the owner
     * reported on 0.3.8. The same happened arriving from Starred Messages.
     *
     * The idiom is the one [openGroupInfo], [openUserProfile], [openSharedMedia] and
     * [openStatusViewer] already use, and `CLAUDE.md` records under "Navigation stack pop-then-push
     * anti-pattern". `openChat` was the one entry point that did not.
     *
     * Equality is `Config.Chat`'s, which is a data class over every field — so re-opening the same
     * conversation with a *different* [scrollToMessageId] (jumping to a starred message, then to
     * another in the same chat) is correctly a new destination rather than a no-op.
     */
    fun openChat(conversationId: String, conversationName: String, otherUserId: String? = null, isGroup: Boolean = false, scrollToMessageId: String? = null, avatarUrl: String? = null) {
        val target = Config.Chat(conversationId, conversationName, otherUserId, isGroup, scrollToMessageId, avatarUrl)
        navigation.navigate { stack ->
            if (target in stack) stack.dropLastWhile { it != target } else stack + target
        }
    }

    /**
     * The preferred entry point: [ChatTarget] carries every part of the chat's identity together, so
     * a caller cannot supply the conversation id and quietly default the rest.
     *
     * [scrollToMessageId] stays a separate argument rather than joining [ChatTarget] because it is
     * not part of who the conversation is — it is where in it to land, and only two screens
     * (Starred Messages, and #463's forwarded-message jump) ever have one.
     */
    fun openChat(target: ChatTarget, scrollToMessageId: String? = null) {
        openChat(
            conversationId = target.conversationId,
            conversationName = target.name,
            otherUserId = target.otherUserId,
            isGroup = target.isGroup,
            scrollToMessageId = scrollToMessageId,
            avatarUrl = target.avatarUrl
        )
    }

    @OptIn(DelicateDecomposeApi::class)
    fun openNewConversation() {
        navigation.push(Config.NewConversation)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun openSettings() {
        navigation.push(Config.Settings())
    }

    @OptIn(DelicateDecomposeApi::class)
    fun openCreateGroup() {
        navigation.push(Config.CreateGroup)
    }

    fun openGroupInfo(conversationId: String, conversationName: String) {
        val target = Config.GroupInfo(conversationId, conversationName)
        navigation.navigate { stack ->
            if (target in stack) stack.dropLastWhile { it != target } else stack + target
        }
    }

    fun openUserProfile(userId: String, contactName: String? = null, conversationId: String? = null) {
        val target = Config.UserProfile(userId, contactName, conversationId)
        navigation.navigate { stack ->
            if (target in stack) stack.dropLastWhile { it != target } else stack + target
        }
    }

    @OptIn(DelicateDecomposeApi::class)
    fun openStarredMessages() {
        navigation.push(Config.StarredMessages)
    }

    /** The pinned "Arşivlenmiş · N" row's destination (#612) — every conversation with `archived`
     *  set, and the one place unarchiving is reachable from besides the long-press menu that put it
     *  there in the first place. */
    @OptIn(DelicateDecomposeApi::class)
    fun openArchivedChats() {
        navigation.push(Config.ArchivedChats)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun openMessageInfo(messageId: String) {
        navigation.push(Config.MessageInfo(messageId))
    }

    fun openSharedMedia(conversationId: String) {
        val target = Config.SharedMedia(conversationId)
        navigation.navigate { stack ->
            if (target in stack) stack.dropLastWhile { it != target } else stack + target
        }
    }

    fun openStatusViewer(userId: String, displayName: String) {
        val target = Config.StatusViewer(userId, displayName)
        navigation.navigate { stack ->
            if (target in stack) stack.dropLastWhile { it != target } else stack + target
        }
    }

    /**
     * Unreachable today: nothing calls this. There is no WS listener that turns an inbound
     * `call.room`/ringing signal into a navigation, because the client that would send
     * `call.initiate` in the first place does not exist (#367–#373). Left in place — along with
     * [IncomingCallScreen][com.muhabbet.app.ui.call.IncomingCallScreen] — as the real destination
     * for that wiring once it lands, rather than deleted and rebuilt from scratch.
     */
    @OptIn(DelicateDecomposeApi::class)
    fun openIncomingCall(callId: String, callerId: String, callerName: String?, callType: String) {
        navigation.push(Config.IncomingCall(callId, callerId, callerName, callType))
    }

    /**
     * No outgoing entry point calls this any more (see [PickContactForCall][Config.PickContactForCall]
     * and the Calls tab in [HomeShellScreen][com.muhabbet.app.ui.home.HomeShellScreen], both of which
     * now show a "coming soon" message instead of minting a fake call id and pushing this screen —
     * #367–#373). Still reachable from [replaceWithActiveCall] via a real incoming-call accept, and
     * kept as the target [ActiveCallScreen][com.muhabbet.app.ui.call.ActiveCallScreen] for whichever
     * flow calling actually ships with.
     */
    @OptIn(DelicateDecomposeApi::class)
    fun openActiveCall(callId: String, otherUserId: String, otherUserName: String?, callType: String) {
        navigation.push(Config.ActiveCall(callId, otherUserId, otherUserName, callType))
    }

    @OptIn(DelicateDecomposeApi::class)
    fun openCallHistory() {
        navigation.push(Config.CallHistory)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun openPrivacyDashboard() {
        navigation.push(Config.PrivacyDashboard)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun openBlockedUsers() {
        navigation.push(Config.BlockedUsers)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun openTwoStepVerification() {
        navigation.push(Config.TwoStepVerification)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun openAppLock() {
        navigation.push(Config.AppLock)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun openWallpaper() {
        navigation.push(Config.Wallpaper)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun openAbout() {
        navigation.push(Config.About)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun openCommunityDetail(communityId: String) {
        navigation.push(Config.CommunityDetail(communityId))
    }

    fun openCommunityMembers(communityId: String) {
        val target = Config.CommunityMembers(communityId)
        navigation.navigate { stack ->
            if (target in stack) stack.dropLastWhile { it != target } else stack + target
        }
    }

    @OptIn(DelicateDecomposeApi::class)
    fun openPickContactForCall() {
        navigation.push(Config.PickContactForCall)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun openCreateCommunity() {
        navigation.push(Config.CreateCommunity)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun openGroupEvents(conversationId: String) {
        navigation.push(Config.GroupEvents(conversationId))
    }

    @OptIn(DelicateDecomposeApi::class)
    fun openBroadcastLists() {
        navigation.push(Config.BroadcastLists)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun openBroadcastDetail(broadcastListId: String, broadcastListName: String) {
        navigation.push(Config.BroadcastDetail(broadcastListId, broadcastListName))
    }

    /**
     * Swap the current screen for another in one operation.
     *
     * These exist because `goBack()` immediately followed by `openX()` does not reliably work:
     * they are two separate navigation operations dispatched in the same frame, and the second can
     * be lost, leaving the user staring at the screen they just acted on. Creating a community
     * looked completely broken for exactly this reason — the POST returned 201, the community was
     * in the database, and the create form just sat there.
     *
     * CLAUDE.md already warns against pop-then-push; `replaceCurrent` is the fix.
     */
    /**
     * Takes a whole [ChatTarget] for the same reason [openChat] does. It used to take
     * `(id, name, isGroup)`, and every one of its callers therefore had no way to supply
     * `otherUserId` or `avatarUrl` — so a chat entered straight after creating it, or right after
     * forwarding into it, showed the right title over no avatar and a dead tap on the person, while
     * the same chat opened later from the list was fine (#555). It was #543's symptom arriving from
     * a different direction, and the cure is the same: one type carrying the whole identity, so a
     * caller cannot supply half of it.
     */
    fun replaceWithChat(target: ChatTarget) {
        navigation.replaceCurrent(
            Config.Chat(
                conversationId = target.conversationId,
                name = target.name,
                otherUserId = target.otherUserId,
                isGroup = target.isGroup,
                avatarUrl = target.avatarUrl
            )
        )
        _refreshTrigger.value++
    }

    fun replaceWithCreateGroup() {
        navigation.replaceCurrent(Config.CreateGroup)
    }

    fun replaceWithCommunityDetail(communityId: String) {
        navigation.replaceCurrent(Config.CommunityDetail(communityId))
    }

    fun replaceWithActiveCall(callId: String, callerId: String, callerName: String?, callType: String) {
        navigation.replaceCurrent(Config.ActiveCall(callId, callerId, callerName, callType))
    }

    fun goBack() {
        navigation.pop()
        _refreshTrigger.value++
    }

    @Serializable
    sealed interface Config {
        @Serializable data object HomeShell : Config
        @Serializable data object ConversationList : Config
        @Serializable data class Chat(val conversationId: String, val name: String, val otherUserId: String? = null, val isGroup: Boolean = false, val scrollToMessageId: String? = null, val avatarUrl: String? = null) : Config
        @Serializable data object NewConversation : Config
        // [focusLanguage] is set only by the launch that follows a language change, and scrolls the
        // screen to the Language section rather than leaving the user at the top of a long page
        // wondering whether anything happened.
        @Serializable data class Settings(val focusLanguage: Boolean = false) : Config
        @Serializable data object CreateGroup : Config
        @Serializable data class GroupInfo(val conversationId: String, val name: String) : Config
        @Serializable data class UserProfile(val userId: String, val contactName: String? = null, val conversationId: String? = null) : Config
        @Serializable data class StatusViewer(val userId: String, val displayName: String) : Config
        @Serializable data object StarredMessages : Config
        @Serializable data object ArchivedChats : Config
        @Serializable data class SharedMedia(val conversationId: String) : Config
        @Serializable data class MessageInfo(val messageId: String) : Config
        @Serializable data class IncomingCall(val callId: String, val callerId: String, val callerName: String? = null, val callType: String = "VOICE") : Config
        @Serializable data class ActiveCall(val callId: String, val otherUserId: String, val otherUserName: String? = null, val callType: String = "VOICE") : Config
        @Serializable data object CallHistory : Config
        @Serializable data object PrivacyDashboard : Config
        @Serializable data object BlockedUsers : Config
        @Serializable data object TwoStepVerification : Config
        @Serializable data object AppLock : Config
        @Serializable data object Wallpaper : Config
        @Serializable data object About : Config
        @Serializable data class CommunityDetail(val communityId: String) : Config
        @Serializable data class CommunityMembers(val communityId: String) : Config
        @Serializable data object CreateCommunity : Config
        @Serializable data object PickContactForCall : Config
        @Serializable data class GroupEvents(val conversationId: String) : Config
        @Serializable data object BroadcastLists : Config
        @Serializable data class BroadcastDetail(val broadcastListId: String, val broadcastListName: String) : Config
    }
}

/**
 * The main stack, plus the one thing that has to sit above it.
 *
 * `SharedTransitionLayout` wraps `Children` rather than living inside a screen because the two
 * avatars that hand off — the conversation row's and the chat title bar's — are in two *different*
 * children of this stack. Their nearest common ancestor is here and nowhere lower.
 *
 * The stack itself is [MainStack] so that adding this wrapper did not re-indent 250 lines of `when`
 * branches, which would have buried the change in whitespace.
 */
@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
fun MainContent(component: MainComponent) {
    OpenChatFromOutside(component)
    SharedTransitionLayout {
        val stack by component.childStack.subscribeAsState()
        val handoff = AvatarHandoff(
            scope = this,
            activeConversationId = (stack.active.configuration as? MainComponent.Config.Chat)?.conversationId
        )
        CompositionLocalProvider(LocalAvatarHandoff provides handoff) {
            MainStack(component)
        }
    }
}

/**
 * Acts on a [ChatOpenRequest] parked by the platform — a tapped notification, or a
 * `muhabbet://chat/{id}` link.
 *
 * Mounted inside `MainContent`, so it only ever runs for a signed-in user with a navigation stack
 * to push onto. A request that arrives at the login screen simply waits here until it does.
 *
 * The name is the fiddly part. A notification carries the sender's name and needs no lookup; a bare
 * deep link carries only an id, and [ChatScreen] renders the title verbatim with no fallback of its
 * own — which is how #543 produced a chat with no title. So an unnamed request is resolved through
 * [ConversationDirectory] and [toChatTarget], the one place that decides what a conversation is
 * called, and falls back to the same localized default every list uses rather than to `""`.
 *
 * Navigating with an unresolved name is deliberate in the failure case: the user asked for *this*
 * conversation, and opening it titled "Sohbet" serves them better than refusing to move.
 */
@Composable
private fun OpenChatFromOutside(component: MainComponent) {
    val pendingChatOpen: PendingChatOpen = koinInject()
    val conversationDirectory: ConversationDirectory = koinInject()
    val tokenStorage: TokenStorage = koinInject()
    val defaultChatName = stringResource(Res.string.chat_default_name)

    val pending by pendingChatOpen.pending.collectAsState()

    LaunchedEffect(pending) {
        val request = pending ?: return@LaunchedEffect

        // Resolved locally FIRST, and the notification's name used only if that fails (#642).
        //
        // It used to be the other way round. The server knows the account's display name — "Zümra"
        // — and the phone knows what this user called them in their address book — "Elif Zümra".
        // Every other screen in the app shows the second, so preferring the payload made the
        // notification path the only one that disagreed with itself: the pushed chat said one name
        // and the chat underneath said another.
        //
        // Cache-first, so the common case costs no network. A `null` means the conversation is not
        // on this device — deleted, left, or past the pages the directory will walk — and only then
        // is the notification's name better than nothing.
        val conversation = runCatching {
            conversationDirectory.lookUp(setOf(request.conversationId))[request.conversationId]
        }.getOrNull()

        val target = conversation?.toChatTarget(
            currentUserId = runCatching { tokenStorage.getUserId() }.getOrNull(),
            fallbackName = request.displayName ?: defaultChatName
        ) ?: ChatTarget(
            conversationId = request.conversationId,
            name = request.displayName ?: defaultChatName,
            isGroup = request.isGroup
        )

        // Cleared before navigating, and conditionally: a second notification tapped while the
        // lookup above was suspended must not be thrown away with the one it replaced.
        pendingChatOpen.consume(request)
        component.openChat(target)
    }
}

@Composable
private fun MainStack(component: MainComponent) {
    Children(
        stack = component.childStack,
        animation = predictiveBack(component.backHandler, component::goBack)
    ) { child ->
        when (val config = child.instance) {
            is MainComponent.Config.HomeShell -> HomeShellScreen(
                onConversationClick = component::openChat,
                onNewConversation = component::openNewConversation,
                onSettings = component::openSettings,
                onStatusClick = { userId, displayName -> component.openStatusViewer(userId, displayName) },
                onNewCall = component::openPickContactForCall,
                onCommunityClick = { communityId -> component.openCommunityDetail(communityId) },
                onCreateCommunity = component::openCreateCommunity,
                onOpenArchived = component::openArchivedChats,
                refreshKey = component.refreshTrigger.collectAsState(0).value
            )
            is MainComponent.Config.ConversationList -> ConversationListScreen(
                onConversationClick = component::openChat,
                onNewConversation = component::openNewConversation,
                onSettings = component::openSettings,
                onStatusClick = { userId, displayName -> component.openStatusViewer(userId, displayName) },
                onOpenArchived = component::openArchivedChats,
                refreshKey = component.refreshTrigger.collectAsState(0).value
            )
            is MainComponent.Config.Chat -> ChatScreen(
                conversationId = config.conversationId,
                conversationName = config.name,
                conversationAvatarUrl = config.avatarUrl,
                isGroup = config.isGroup,
                scrollToMessageId = config.scrollToMessageId,
                onBack = component::goBack,
                onTitleClick = {
                    if (config.isGroup) {
                        component.openGroupInfo(config.conversationId, config.name)
                    } else if (config.otherUserId != null) {
                        component.openUserProfile(config.otherUserId, config.name, config.conversationId)
                    }
                },
                onNavigateToConversation = component::replaceWithChat,
                onMessageInfo = { messageId -> component.openMessageInfo(messageId) }
            )
            is MainComponent.Config.NewConversation -> NewConversationScreen(
                onConversationCreated = component::replaceWithChat,
                onCreateGroup = {
                    component.replaceWithCreateGroup()
                },
                onBack = component::goBack
            )
            is MainComponent.Config.CreateGroup -> CreateGroupScreen(
                // otherUserId and avatarUrl are genuinely absent here, not dropped: a group has no
                // "other user", and a group created seconds ago has no photo yet. Routed through
                // ChatTarget anyway so there is one rule for what a chat's identity is, rather than
                // a fifth path that happens to be right today.
                onGroupCreated = { id, name ->
                    component.replaceWithChat(
                        ChatTarget(conversationId = id, name = name, isGroup = true)
                    )
                },
                onBack = component::goBack
            )
            is MainComponent.Config.GroupInfo -> GroupInfoScreen(
                conversationId = config.conversationId,
                conversationName = config.name,
                onBack = component::goBack,
                onMemberClick = { userId -> component.openUserProfile(userId) },
                onSharedMediaClick = { component.openSharedMedia(config.conversationId) },
                onEventsClick = { component.openGroupEvents(config.conversationId) }
            )
            is MainComponent.Config.UserProfile -> UserProfileScreen(
                userId = config.userId,
                contactName = config.contactName,
                conversationId = config.conversationId,
                onBack = component::goBack,
                onMessageClick = { component.goBack() },
                onGroupClick = { id, name -> component.openGroupInfo(id, name) },
                onSharedMediaClick = { convId -> component.openSharedMedia(convId) }
            )
            is MainComponent.Config.StatusViewer -> StatusViewerScreen(
                userId = config.userId,
                displayName = config.displayName,
                onBack = component::goBack
            )
            is MainComponent.Config.Settings -> SettingsScreen(
                focusLanguageSection = config.focusLanguage,
                onBack = component::goBack,
                onLogout = component.onLogout,
                onStarredMessages = component::openStarredMessages,
                onPrivacyDashboard = component::openPrivacyDashboard,
                onTwoStepVerification = component::openTwoStepVerification,
                onAppLock = component::openAppLock,
                onWallpaper = component::openWallpaper,
                onAbout = component::openAbout
            )
            is MainComponent.Config.StarredMessages -> StarredMessagesScreen(
                onBack = component::goBack,
                // The screen resolves the conversation before handing it over (#543). It used to
                // pass the id and `""`, which opened the right conversation with no title, no
                // avatar and no user id to tap through to.
                onNavigateToConversation = { target, msgId ->
                    component.openChat(target, scrollToMessageId = msgId)
                }
            )
            is MainComponent.Config.ArchivedChats -> ArchivedChatsScreen(
                onBack = component::goBack,
                onConversationClick = component::openChat
            )
            is MainComponent.Config.SharedMedia -> SharedMediaScreen(
                conversationId = config.conversationId,
                onBack = component::goBack
            )
            is MainComponent.Config.MessageInfo -> MessageInfoScreen(
                messageId = config.messageId,
                onBack = component::goBack
            )
            is MainComponent.Config.IncomingCall -> {
                val callType = if (config.callType == "VIDEO") com.muhabbet.shared.model.CallType.VIDEO else com.muhabbet.shared.model.CallType.VOICE
                IncomingCallScreen(
                    callId = config.callId,
                    callerId = config.callerId,
                    callerName = config.callerName,
                    callType = callType,
                    onAccept = {
                        component.replaceWithActiveCall(config.callId, config.callerId, config.callerName, config.callType)
                    },
                    onDecline = component::goBack
                )
            }
            is MainComponent.Config.ActiveCall -> {
                val callType = if (config.callType == "VIDEO") com.muhabbet.shared.model.CallType.VIDEO else com.muhabbet.shared.model.CallType.VOICE
                ActiveCallScreen(
                    callId = config.callId,
                    otherUserId = config.otherUserId,
                    otherUserName = config.otherUserName,
                    callType = callType,
                    onCallEnded = component::goBack
                )
            }
            is MainComponent.Config.PrivacyDashboard -> PrivacyDashboardScreen(
                onBack = component::goBack,
                onLogout = component.onLogout,
                onBlockedUsers = component::openBlockedUsers
            )
            is MainComponent.Config.BlockedUsers -> BlockedUsersScreen(
                onBack = component::goBack
            )
            is MainComponent.Config.CallHistory -> CallHistoryScreen(
                onBack = component::goBack
            )
            is MainComponent.Config.TwoStepVerification -> TwoStepSetupScreen(
                onBack = component::goBack
            )
            is MainComponent.Config.AppLock -> AppLockScreen(
                onBack = component::goBack
            )
            is MainComponent.Config.Wallpaper -> WallpaperPickerScreen(
                onBack = component::goBack
            )
            is MainComponent.Config.About -> AboutScreen(
                onBack = component::goBack
            )
            is MainComponent.Config.CommunityDetail -> CommunityDetailScreen(
                communityId = config.communityId,
                onBack = component::goBack,
                // The detail screen resolves the group into a ChatTarget. It used to hand over
                // `group.name.orEmpty()` — the same blank title bar as #543, one screen along —
                // and dropped the group's picture, which CommunityGroupInfo carries all along.
                onGroupClick = component::openChat,
                onMembersClick = component::openCommunityMembers
            )
            is MainComponent.Config.CommunityMembers -> CommunityMembersScreen(
                communityId = config.communityId,
                onBack = component::goBack,
                onMemberClick = { userId -> component.openUserProfile(userId) }
            )
            // Calling has never worked end to end (#367–#373: the client never sends
            // call.initiate, no mic track is ever published, and LiveKit is unconfigured in
            // prod). isCallPickerMode only hides the group/by-number rows that make no sense on
            // a "who do you want to call" surface and turns a contact tap into the same
            // "coming soon" message ProfileScreen's call button already shows — it does not open
            // a call screen for a call that is not happening.
            is MainComponent.Config.PickContactForCall -> NewConversationScreen(
                onConversationCreated = component::replaceWithChat,
                onBack = component::goBack,
                isCallPickerMode = true
            )
            is MainComponent.Config.CreateCommunity -> CreateCommunityScreen(
                onBack = component::goBack,
                onCommunityCreated = { communityId ->
                    component.replaceWithCommunityDetail(communityId)
                }
            )
            is MainComponent.Config.GroupEvents -> GroupEventScreen(
                conversationId = config.conversationId,
                onBack = component::goBack
            )
            is MainComponent.Config.BroadcastLists -> BroadcastListScreen(
                onBack = component::goBack,
                onBroadcastListClick = { id, name -> component.openBroadcastDetail(id, name) }
            )
            is MainComponent.Config.BroadcastDetail -> BroadcastDetailScreen(
                broadcastListId = config.broadcastListId,
                broadcastListName = config.broadcastListName,
                onBack = component::goBack
            )
        }
    }
}
