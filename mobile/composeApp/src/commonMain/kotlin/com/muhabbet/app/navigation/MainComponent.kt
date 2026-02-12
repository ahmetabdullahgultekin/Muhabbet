package com.muhabbet.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.DelicateDecomposeApi
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.slide
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.value.Value
import com.muhabbet.app.ui.chat.ChatScreen
import com.muhabbet.app.ui.conversations.ConversationListScreen
import com.muhabbet.app.ui.conversations.NewConversationScreen
import com.muhabbet.app.ui.group.CreateGroupScreen
import com.muhabbet.app.ui.group.GroupInfoScreen
import com.muhabbet.app.ui.profile.UserProfileScreen
import com.muhabbet.app.ui.settings.SettingsScreen
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
    fun openChat(conversationId: String, conversationName: String, otherUserId: String? = null, isGroup: Boolean = false) {
        navigation.push(Config.Chat(conversationId, conversationName, otherUserId, isGroup))
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

    @OptIn(DelicateDecomposeApi::class)
    fun openGroupInfo(conversationId: String, conversationName: String) {
        navigation.push(Config.GroupInfo(conversationId, conversationName))
    }

    @OptIn(DelicateDecomposeApi::class)
    fun openUserProfile(userId: String) {
        navigation.push(Config.UserProfile(userId))
    }

    fun goBack() {
        navigation.pop()
        _refreshTrigger.value++
    }

    @Serializable
    sealed interface Config {
        @Serializable data object ConversationList : Config
        @Serializable data class Chat(val conversationId: String, val name: String, val otherUserId: String? = null, val isGroup: Boolean = false) : Config
        @Serializable data object NewConversation : Config
        @Serializable data object Settings : Config
        @Serializable data object CreateGroup : Config
        @Serializable data class GroupInfo(val conversationId: String, val name: String) : Config
        @Serializable data class UserProfile(val userId: String) : Config
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
                refreshKey = component.refreshTrigger.collectAsState(0).value
            )
            is MainComponent.Config.Chat -> ChatScreen(
                conversationId = config.conversationId,
                conversationName = config.name,
                onBack = component::goBack,
                onTitleClick = {
                    if (config.isGroup) {
                        component.openGroupInfo(config.conversationId, config.name)
                    } else if (config.otherUserId != null) {
                        component.openUserProfile(config.otherUserId)
                    }
                }
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
                onBack = component::goBack
            )
            is MainComponent.Config.UserProfile -> UserProfileScreen(
                userId = config.userId,
                onBack = component::goBack
            )
            is MainComponent.Config.Settings -> SettingsScreen(
                onBack = component::goBack,
                onLogout = component.onLogout
            )
        }
    }
}
