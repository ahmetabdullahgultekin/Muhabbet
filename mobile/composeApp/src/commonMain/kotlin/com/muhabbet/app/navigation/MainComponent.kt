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
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.slide
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.value.Value
import com.muhabbet.app.ui.call.ActiveCallScreen
import com.muhabbet.app.ui.call.CallHistoryScreen
import com.muhabbet.app.ui.call.IncomingCallScreen
import com.muhabbet.app.ui.chat.ChatScreen
import com.muhabbet.app.ui.chat.MessageInfoScreen
import com.muhabbet.app.ui.conversations.ConversationListScreen
import com.muhabbet.app.ui.conversations.NewConversationScreen
import com.muhabbet.app.ui.group.CreateGroupScreen
import com.muhabbet.app.ui.group.GroupInfoScreen
import com.muhabbet.app.ui.profile.UserProfileScreen
import com.muhabbet.app.ui.settings.SettingsScreen
import com.muhabbet.app.ui.media.SharedMediaScreen
import com.muhabbet.app.ui.starred.StarredMessagesScreen
import com.muhabbet.app.ui.status.StatusViewerScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

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
        initialConfiguration = Config.ConversationList,
        handleBackButton = true,
        childFactory = { config, _ -> config }
    )

    @OptIn(DelicateDecomposeApi::class)
    fun openChat(conversationId: String, conversationName: String, otherUserId: String? = null, isGroup: Boolean = false, scrollToMessageId: String? = null) {
        navigation.push(Config.Chat(conversationId, conversationName, otherUserId, isGroup, scrollToMessageId))
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

    fun goBack() {
        navigation.pop()
        _refreshTrigger.value++
    }

    @Serializable
    sealed interface Config {
        @Serializable data object ConversationList : Config
        @Serializable data class Chat(val conversationId: String, val name: String, val otherUserId: String? = null, val isGroup: Boolean = false, val scrollToMessageId: String? = null) : Config
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
    }
}

@Composable
fun MainContent(component: MainComponent) {
    Children(
        stack = component.childStack,
        animation = stackAnimation(slide())
    ) { child ->
        when (val config = child.instance) {
            is MainComponent.Config.ConversationList -> ConversationListScreen(
                onConversationClick = { id, name, otherUserId, isGroup -> component.openChat(id, name, otherUserId, isGroup) },
                onNewConversation = component::openNewConversation,
                onSettings = component::openSettings,
                onStatusClick = { userId, displayName -> component.openStatusViewer(userId, displayName) },
                refreshKey = component.refreshTrigger.collectAsState(0).value
            )
            is MainComponent.Config.Chat -> ChatScreen(
                conversationId = config.conversationId,
                conversationName = config.name,
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
                    component.goBack()
                    component.openChat(convId, convName)
                },
                onMessageInfo = { messageId -> component.openMessageInfo(messageId) }
            )
            is MainComponent.Config.NewConversation -> NewConversationScreen(
                onConversationCreated = { id, name ->
                    component.goBack()
                    component.openChat(id, name)
                },
                onCreateGroup = {
                    component.goBack()
                    component.openCreateGroup()
                },
                onBack = component::goBack
            )
            is MainComponent.Config.CreateGroup -> CreateGroupScreen(
                onGroupCreated = { id, name ->
                    component.goBack()
                    component.openChat(id, name)
                },
                onBack = component::goBack
            )
            is MainComponent.Config.GroupInfo -> GroupInfoScreen(
                conversationId = config.conversationId,
                conversationName = config.name,
                onBack = component::goBack,
                onMemberClick = { userId -> component.openUserProfile(userId) },
                onSharedMediaClick = { component.openSharedMedia(config.conversationId) }
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
                onStarredMessages = component::openStarredMessages
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
                        component.goBack()
                        component.openActiveCall(config.callId, config.callerId, config.callerName, config.callType)
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
            is MainComponent.Config.CallHistory -> CallHistoryScreen(
                onBack = component::goBack,
                onCallUser = { userId, name, callType ->
                    val callId = kotlinx.datetime.Clock.System.now().toEpochMilliseconds().toString()
                    component.openActiveCall(callId, userId, name, callType)
                }
            )
        }
    }
}
