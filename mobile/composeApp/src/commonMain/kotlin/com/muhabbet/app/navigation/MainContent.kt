package com.muhabbet.app.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.presentation.LocalScreenBackHandler
import com.muhabbet.app.presentation.belowScreenModes
import com.muhabbet.app.ui.call.ActiveCallScreen
import com.muhabbet.app.ui.call.CallHistoryScreen
import com.muhabbet.app.ui.call.IncomingCallScreen
import com.muhabbet.app.ui.chat.ChatScreen
import com.muhabbet.app.ui.chat.MessageInfoScreen
import com.muhabbet.app.ui.communities.CommunityDetailScreen
import com.muhabbet.app.ui.communities.CommunityMembersScreen
import com.muhabbet.app.ui.communities.CreateCommunityScreen
import com.muhabbet.app.ui.contacts.rememberContactNames
import com.muhabbet.app.ui.conversations.ArchivedChatsScreen
import com.muhabbet.app.ui.conversations.BroadcastDetailScreen
import com.muhabbet.app.ui.conversations.BroadcastListScreen
import com.muhabbet.app.ui.conversations.ChatTarget
import com.muhabbet.app.ui.conversations.ConversationListScreen
import com.muhabbet.app.ui.conversations.NewConversationScreen
import com.muhabbet.app.ui.conversations.toChatTarget
import com.muhabbet.app.ui.group.CreateGroupScreen
import com.muhabbet.app.ui.group.GroupEventScreen
import com.muhabbet.app.ui.group.GroupInfoScreen
import com.muhabbet.app.ui.home.HomeShellScreen
import com.muhabbet.app.ui.media.SharedMediaScreen
import com.muhabbet.app.ui.privacy.BlockedUsersScreen
import com.muhabbet.app.ui.privacy.PrivacyDashboardScreen
import com.muhabbet.app.ui.profile.UserProfileScreen
import com.muhabbet.app.ui.settings.AboutScreen
import com.muhabbet.app.ui.settings.AppLockScreen
import com.muhabbet.app.ui.settings.SettingsScreen
import com.muhabbet.app.ui.settings.StorageUsageScreen
import com.muhabbet.app.ui.settings.TwoStepSetupScreen
import com.muhabbet.app.ui.settings.WallpaperPickerScreen
import com.muhabbet.app.ui.starred.StarredMessagesScreen
import com.muhabbet.app.ui.status.StatusViewerScreen
import com.muhabbet.app.ui.transition.AvatarHandoff
import com.muhabbet.app.ui.transition.LocalAvatarHandoff
import com.muhabbet.app.ui.whatsnew.ReleaseNotesScreen
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.chat_default_name
import com.muhabbet.shared.model.CallType
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * The main stack, plus the one thing that has to sit above it.
 *
 * `SharedTransitionLayout` wraps `Children` rather than living inside a screen because the two
 * avatars that hand off — the conversation row's and the chat title bar's — are in two *different*
 * children of this stack. Their nearest common ancestor is here and nowhere lower.
 *
 * Nothing in this function reads the stack directly. The handoff is created once and reads the
 * active conversation through a derived state, so a navigation recomposes the two avatars that care
 * and not every screen under this host (#831).
 */
@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
fun MainContent(component: MainComponent) {
    SharedTransitionLayout {
        val stack = component.childStack.subscribeAsState()
        val activeConversationId = remember(stack) {
            derivedStateOf { (stack.value.active.configuration as? MainComponent.Config.Chat)?.conversationId }
        }
        val handoff = remember(this, activeConversationId) { AvatarHandoff(this, activeConversationId) }
        CompositionLocalProvider(LocalAvatarHandoff provides handoff) {
            MainStack(component)
        }
    }
}

@Composable
private fun MainStack(component: MainComponent) {
    // Remembered: built inline it was a new StackAnimation — and a new back-gesture animatable — on
    // every recomposition of this host, mid-gesture included (#831).
    val animation = remember(component) {
        predictiveBack<MainComponent.Config, MainComponent.Child>(component.backHandler.belowScreenModes(), component::goBack)
    }
    Children(stack = component.childStack, animation = animation) { created ->
        val child = created.instance
        CompositionLocalProvider(LocalScreenBackHandler provides child.context.backHandler) {
            when (child) {
                is MainComponent.Child.Chat -> ChatDestination(component, child.component)
                is MainComponent.Child.UserProfile -> UserProfileScreen(
                    userId = child.component.userId,
                    contactName = child.component.contactName,
                    conversationId = child.component.conversationId,
                    onBack = component::goBack,
                    onMessageClick = child.component::onMessageClick,
                    messageFailures = child.component.messageFailures,
                    onGroupClick = { id, name -> component.openGroupInfo(id, name) },
                    onSharedMediaClick = { convId -> component.openSharedMedia(convId) }
                )
                is MainComponent.Child.BlockedUsers -> BlockedUsersScreen(child.component)
                is MainComponent.Child.About -> AboutScreen(child.component)
                is MainComponent.Child.AppLock -> AppLockScreen(child.component)
                is MainComponent.Child.Screen -> UnmigratedScreen(component, child.config)
            }
        }
    }
}

/**
 * A chat, titled from what the opener knew until [ChatDestinationComponent] has resolved the
 * conversation — then by the one naming rule every list uses (`toChatTarget`, address book first,
 * #549). A chat opened from a notification therefore appears at once under the placeholder name,
 * rather than after a lookup (#823).
 */
@Composable
private fun ChatDestination(component: MainComponent, destination: ChatDestinationComponent) {
    val config = destination.config
    val identity by destination.identity.state.collectAsStateWithLifecycle()
    val tokenStorage: TokenStorage = koinInject()
    val contactNames = rememberContactNames()
    val defaultChatName = stringResource(Res.string.chat_default_name)
    val currentUserId = remember(tokenStorage) { tokenStorage.getUserId() }
    val target = identity.conversation?.toChatTarget(currentUserId, defaultChatName, contactNames)
        ?: ChatTarget(
            conversationId = config.conversationId,
            name = config.name ?: defaultChatName,
            otherUserId = config.otherUserId,
            isGroup = config.isGroup,
            avatarUrl = config.avatarUrl
        )
    ChatScreen(
        conversationId = config.conversationId,
        conversationName = target.name,
        conversationAvatarUrl = target.avatarUrl,
        isGroup = target.isGroup,
        scrollToMessageId = config.scrollToMessageId,
        onBack = component::goBack,
        onTitleClick = {
            if (target.isGroup) {
                component.openGroupInfo(config.conversationId, target.name)
            } else {
                target.otherUserId?.let { component.openUserProfile(it, target.name, config.conversationId) }
            }
        },
        onNavigateToConversation = component::replaceWithChat,
        onMessageInfo = { messageId -> component.openMessageInfo(messageId) }
    )
}

/** Destinations whose screens have not yet moved their state into a component (ADR 0010). */
@Composable
private fun UnmigratedScreen(component: MainComponent, config: MainComponent.Config) {
    when (config) {
        is MainComponent.Config.HomeShell -> HomeShellScreen(
            onConversationClick = component::openChat,
            onNewConversation = component::openNewConversation,
            onSettings = component::openSettings,
            onStatusClick = { userId, displayName -> component.openStatusViewer(userId, displayName) },
            onCommunityClick = { communityId -> component.openCommunityDetail(communityId) },
            onCreateCommunity = component::openCreateCommunity,
            onOpenArchived = component::openArchivedChats
        )
        is MainComponent.Config.ConversationList -> ConversationListScreen(
            onConversationClick = component::openChat,
            onNewConversation = component::openNewConversation,
            onSettings = component::openSettings,
            onStatusClick = { userId, displayName -> component.openStatusViewer(userId, displayName) },
            onOpenArchived = component::openArchivedChats
        )
        is MainComponent.Config.NewConversation -> NewConversationScreen(
            onConversationCreated = component::replaceWithChat,
            onCreateGroup = component::replaceWithCreateGroup,
            onBack = component::goBack
        )
        is MainComponent.Config.CreateGroup -> CreateGroupScreen(
            // otherUserId and avatarUrl are genuinely absent here: a group has no "other user", and a
            // group created seconds ago has no photo yet. Routed through ChatTarget anyway so there is
            // one rule for what a chat's identity is.
            onGroupCreated = { id, name ->
                component.replaceWithChat(ChatTarget(conversationId = id, name = name, isGroup = true))
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
            onStorage = component::openStorage,
            onAbout = component::openAbout
        )
        is MainComponent.Config.StarredMessages -> StarredMessagesScreen(
            onBack = component::goBack,
            // The screen resolves the conversation before handing it over (#543).
            onNavigateToConversation = { target, msgId -> component.openChat(target, scrollToMessageId = msgId) }
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
        is MainComponent.Config.IncomingCall -> IncomingCallScreen(
            callId = config.callId,
            callerId = config.callerId,
            callerName = config.callerName,
            callType = config.callType.toCallType(),
            onAccept = {
                component.replaceWithActiveCall(config.callId, config.callerId, config.callerName, config.callType)
            },
            onDecline = component::goBack
        )
        is MainComponent.Config.ActiveCall -> ActiveCallScreen(
            callId = config.callId,
            otherUserId = config.otherUserId,
            otherUserName = config.otherUserName,
            callType = config.callType.toCallType(),
            onCallEnded = component::goBack
        )
        is MainComponent.Config.PrivacyDashboard -> PrivacyDashboardScreen(
            onBack = component::goBack,
            onLogout = component.onLogout,
            onBlockedUsers = component::openBlockedUsers
        )
        is MainComponent.Config.CallHistory -> CallHistoryScreen(onBack = component::goBack)
        is MainComponent.Config.TwoStepVerification -> TwoStepSetupScreen(onBack = component::goBack)
        is MainComponent.Config.Wallpaper -> WallpaperPickerScreen(onBack = component::goBack)
        is MainComponent.Config.Storage -> StorageUsageScreen(onBack = component::goBack)
        is MainComponent.Config.ReleaseNotes -> ReleaseNotesScreen(onBack = component::goBack)
        is MainComponent.Config.CommunityDetail -> CommunityDetailScreen(
            communityId = config.communityId,
            onBack = component::goBack,
            // The detail screen resolves the group into a ChatTarget, picture included (#543).
            onGroupClick = component::openChat,
            onMembersClick = component::openCommunityMembers
        )
        is MainComponent.Config.CommunityMembers -> CommunityMembersScreen(
            communityId = config.communityId,
            onBack = component::goBack,
            onMemberClick = { userId -> component.openUserProfile(userId) }
        )
        // Calling has never worked end to end (#367–#373). isCallPickerMode only hides the rows that
        // make no sense on a "who do you want to call" surface and turns a contact tap into the same
        // "coming soon" message the profile's call button shows.
        is MainComponent.Config.PickContactForCall -> NewConversationScreen(
            onConversationCreated = component::replaceWithChat,
            onBack = component::goBack,
            isCallPickerMode = true
        )
        is MainComponent.Config.CreateCommunity -> CreateCommunityScreen(
            onBack = component::goBack,
            onCommunityCreated = { communityId -> component.replaceWithCommunityDetail(communityId) }
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
        // Created as dedicated children by MainComponent.createChild, never as Child.Screen.
        is MainComponent.Config.Chat,
        is MainComponent.Config.UserProfile,
        MainComponent.Config.BlockedUsers,
        MainComponent.Config.About,
        MainComponent.Config.AppLock -> Unit
    }
}

private fun String.toCallType(): CallType = if (this == "VIDEO") CallType.VIDEO else CallType.VOICE
