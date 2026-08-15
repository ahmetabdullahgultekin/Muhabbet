package com.muhabbet.app.navigation

import androidx.compose.runtime.Composable
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
import com.muhabbet.app.ui.communities.CreateCommunityScreen
import com.muhabbet.app.ui.conversations.BroadcastDetailScreen
import com.muhabbet.app.ui.conversations.BroadcastListScreen
import com.muhabbet.app.ui.group.GroupEventScreen
import com.muhabbet.app.ui.privacy.PrivacyDashboardScreen
import com.muhabbet.app.ui.settings.AppLockScreen
import com.muhabbet.app.ui.settings.TwoStepSetupScreen
import com.muhabbet.app.ui.settings.WallpaperPickerScreen
import com.muhabbet.app.ui.status.StatusViewerScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Clock
import kotlinx.serialization.Serializable
import com.muhabbet.app.ui.conversations.ChatTarget
import com.muhabbet.app.ui.transition.AvatarHandoff
import com.muhabbet.app.ui.transition.LocalAvatarHandoff
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import com.arkivanov.decompose.extensions.compose.subscribeAsState

class MainComponent(
    componentContext: ComponentContext,
    val onLogout: () -> Unit
) : ComponentContext by componentContext {

    private val navigation = StackNavigation<Config>()

    private val _refreshTrigger = MutableStateFlow(0)
    val refreshTrigger: StateFlow<Int> = _refreshTrigger

    val childStack: Value<ChildStack<Config, Config>> = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialConfiguration = Config.HomeShell,
        handleBackButton = true,
        childFactory = { config, _ -> config }
    )

    @OptIn(DelicateDecomposeApi::class)
    fun openChat(conversationId: String, conversationName: String, otherUserId: String? = null, isGroup: Boolean = false, scrollToMessageId: String? = null, avatarUrl: String? = null) {
        navigation.push(Config.Chat(conversationId, conversationName, otherUserId, isGroup, scrollToMessageId, avatarUrl))
    }

    fun openChat(target: ChatTarget) {
        openChat(
            conversationId = target.conversationId,
            conversationName = target.name,
            otherUserId = target.otherUserId,
            isGroup = target.isGroup,
            avatarUrl = target.avatarUrl
        )
    }

    @OptIn(DelicateDecomposeApi::class)
    fun openNewConversation() {
        navigation.push(Config.NewConversation)
    }

    @OptIn(DelicateDecomposeApi::class)
    fun openSettings() {
        navigation.push(Config.Settings)
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

    @OptIn(DelicateDecomposeApi::class)
    fun openIncomingCall(callId: String, callerId: String, callerName: String?, callType: String) {
        navigation.push(Config.IncomingCall(callId, callerId, callerName, callType))
    }

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
    fun openCommunityDetail(communityId: String) {
        navigation.push(Config.CommunityDetail(communityId))
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
    fun replaceWithChat(conversationId: String, conversationName: String, isGroup: Boolean = false) {
        navigation.replaceCurrent(Config.Chat(conversationId, conversationName, isGroup = isGroup))
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
        @Serializable data object Settings : Config
        @Serializable data object CreateGroup : Config
        @Serializable data class GroupInfo(val conversationId: String, val name: String) : Config
        @Serializable data class UserProfile(val userId: String, val contactName: String? = null, val conversationId: String? = null) : Config
        @Serializable data class StatusViewer(val userId: String, val displayName: String) : Config
        @Serializable data object StarredMessages : Config
        @Serializable data class SharedMedia(val conversationId: String) : Config
        @Serializable data class MessageInfo(val messageId: String) : Config
        @Serializable data class IncomingCall(val callId: String, val callerId: String, val callerName: String? = null, val callType: String = "VOICE") : Config
        @Serializable data class ActiveCall(val callId: String, val otherUserId: String, val otherUserName: String? = null, val callType: String = "VOICE") : Config
        @Serializable data object CallHistory : Config
        @Serializable data object PrivacyDashboard : Config
        @Serializable data object TwoStepVerification : Config
        @Serializable data object AppLock : Config
        @Serializable data object Wallpaper : Config
        @Serializable data class CommunityDetail(val communityId: String) : Config
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
                onCallUser = { userId, name, callType ->
                    val callId = Clock.System.now().toEpochMilliseconds().toString()
                    component.openActiveCall(callId, userId, name, callType)
                },
                onNewCall = component::openPickContactForCall,
                onCommunityClick = { communityId -> component.openCommunityDetail(communityId) },
                onCreateCommunity = component::openCreateCommunity,
                refreshKey = component.refreshTrigger.collectAsState(0).value
            )
            is MainComponent.Config.ConversationList -> ConversationListScreen(
                onConversationClick = component::openChat,
                onNewConversation = component::openNewConversation,
                onSettings = component::openSettings,
                onStatusClick = { userId, displayName -> component.openStatusViewer(userId, displayName) },
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
                onNavigateToConversation = { convId, convName ->
                    component.replaceWithChat(convId, convName)
                },
                onMessageInfo = { messageId -> component.openMessageInfo(messageId) }
            )
            is MainComponent.Config.NewConversation -> NewConversationScreen(
                onConversationCreated = { id, name ->
                    component.replaceWithChat(id, name)
                },
                onCreateGroup = {
                    component.replaceWithCreateGroup()
                },
                onBack = component::goBack
            )
            is MainComponent.Config.CreateGroup -> CreateGroupScreen(
                onGroupCreated = { id, name ->
                    component.replaceWithChat(id, name, isGroup = true)
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
                onBack = component::goBack,
                onLogout = component.onLogout,
                onStarredMessages = component::openStarredMessages,
                onPrivacyDashboard = component::openPrivacyDashboard,
                onTwoStepVerification = component::openTwoStepVerification,
                onAppLock = component::openAppLock,
                onWallpaper = component::openWallpaper
            )
            is MainComponent.Config.StarredMessages -> StarredMessagesScreen(
                onBack = component::goBack,
                onNavigateToConversation = { convId, msgId ->
                    component.openChat(convId, "", scrollToMessageId = msgId)
                }
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
                onLogout = component.onLogout
            )
            is MainComponent.Config.CallHistory -> CallHistoryScreen(
                onBack = component::goBack,
                onCallUser = { userId, name, callType ->
                    val callId = Clock.System.now().toEpochMilliseconds().toString()
                    component.openActiveCall(callId, userId, name, callType)
                }
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
            is MainComponent.Config.CommunityDetail -> CommunityDetailScreen(
                communityId = config.communityId,
                onBack = component::goBack,
                onGroupClick = { conversationId ->
                    component.openChat(conversationId, "", isGroup = true)
                }
            )
            is MainComponent.Config.PickContactForCall -> NewConversationScreen(
                onConversationCreated = { id, name -> component.replaceWithChat(id, name) },
                onBack = component::goBack,
                onContactPicked = { userId, name ->
                    val callId = Clock.System.now().toEpochMilliseconds().toString()
                    component.replaceWithActiveCall(callId, userId, name, "VOICE")
                }
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
