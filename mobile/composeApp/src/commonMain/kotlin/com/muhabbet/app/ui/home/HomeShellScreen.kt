package com.muhabbet.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.data.repository.ConversationRepository
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import com.muhabbet.app.ui.call.CallHistoryScreen
import com.muhabbet.app.ui.communities.CommunityListScreen
import com.muhabbet.designsystem.components.MuhabbetMenu
import com.muhabbet.designsystem.components.MuhabbetMenuItem
import com.muhabbet.designsystem.components.MuhabbetNavBar
import com.muhabbet.designsystem.components.MuhabbetNavItem
import com.muhabbet.designsystem.components.MuhabbetTopBar
import com.muhabbet.designsystem.components.UserAvatar
import com.muhabbet.app.ui.conversations.ConversationListScreen
import com.muhabbet.app.ui.status.UpdatesTabScreen
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.shared.dto.ConversationResponse
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.action_back
import com.muhabbet.composeapp.generated.resources.action_more_options
import com.muhabbet.composeapp.generated.resources.app_name
import com.muhabbet.composeapp.generated.resources.chat_default_name
import com.muhabbet.composeapp.generated.resources.error_load_conversations
import com.muhabbet.composeapp.generated.resources.home_search_no_results
import com.muhabbet.composeapp.generated.resources.home_search_placeholder
import com.muhabbet.composeapp.generated.resources.home_tab_calls
import com.muhabbet.composeapp.generated.resources.home_tab_chats
import com.muhabbet.composeapp.generated.resources.home_tab_communities
import com.muhabbet.composeapp.generated.resources.home_tab_updates
import com.muhabbet.composeapp.generated.resources.search_messages_placeholder
import com.muhabbet.composeapp.generated.resources.settings_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetScaffold
import com.muhabbet.designsystem.components.MuhabbetTopBarDefaults
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.util.foldForSearch
import com.muhabbet.designsystem.theme.MuhabbetHapticIntent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent
import com.muhabbet.designsystem.components.MuhabbetIconButton
import com.muhabbet.app.ui.conversations.ChatTarget
import com.muhabbet.app.ui.conversations.toChatTarget
import com.muhabbet.composeapp.generated.resources.home_search_failed
import com.muhabbet.composeapp.generated.resources.home_search_section_messages
import com.muhabbet.composeapp.generated.resources.home_search_section_chats
import com.muhabbet.app.ui.conversations.MessageSearchResultRow
import com.muhabbet.app.data.repository.MessageRepository
import com.muhabbet.shared.model.Message
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.muhabbet.designsystem.components.SectionHeader

private enum class HomeTab {
    COMMUNITIES,
    CHATS,
    UPDATES,
    CALLS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeShellScreen(
    onConversationClick: (ChatTarget) -> Unit,
    onNewConversation: () -> Unit,
    onSettings: () -> Unit,
    onStatusClick: (userId: String, displayName: String) -> Unit,
    onCallUser: (userId: String, name: String?, callType: String) -> Unit,
    onNewCall: () -> Unit,
    onCommunityClick: (String) -> Unit = {},
    onCreateCommunity: () -> Unit = {},
    refreshKey: Int = 0,
    conversationRepository: ConversationRepository = koinInject(),
    messageRepository: MessageRepository = koinInject(),
    tokenStorage: TokenStorage = koinInject()
) {
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.CHATS) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    // Message hits, separate from the conversation filter below. Until #638 this screen had no such
    // state at all: it filtered the loaded conversation list by name and never asked the server, so
    // searching for a word you had typed in a chat returned "No results found" while the message sat
    // two screens away. `MessageRepository.searchMessages` existed, worked, and had exactly one
    // caller — a screen the bottom nav does not reach.
    var messageResults by remember { mutableStateOf<List<Message>>(emptyList()) }
    var allConversations by remember { mutableStateOf<List<ConversationResponse>>(emptyList()) }
    val currentUserId = remember { tokenStorage.getUserId() ?: "" }
    // The field takes focus when search opens, and brings the keyboard with it. Without this the
    // user taps the magnifier, types, and nothing happens — no keyboard, no text, no request — which
    // is indistinguishable from search being broken and is how #638 was reported.
    val searchFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorLoadConversationsMsg = stringResource(Res.string.error_load_conversations)

    val appName = stringResource(Res.string.app_name)
    val settingsTitle = stringResource(Res.string.settings_title)
    val searchDesc = stringResource(Res.string.search_messages_placeholder)
    val backDesc = stringResource(Res.string.action_back)
    val moreOptionsDesc = stringResource(Res.string.action_more_options)
    val searchPlaceholder = stringResource(Res.string.home_search_placeholder)
    val searchNoResults = stringResource(Res.string.home_search_no_results)
    val searchSectionChats = stringResource(Res.string.home_search_section_chats)
    val searchSectionMessages = stringResource(Res.string.home_search_section_messages)
    // Resolved here, not inside the coroutine: stringResource is @Composable and cannot be called
    // from scope.launch.
    val searchFailedMsg = stringResource(Res.string.home_search_failed)
    val defaultChatName = stringResource(Res.string.chat_default_name)
    val communitiesLabel = stringResource(Res.string.home_tab_communities)
    val chatsLabel = stringResource(Res.string.home_tab_chats)
    val updatesLabel = stringResource(Res.string.home_tab_updates)
    val callsLabel = stringResource(Res.string.home_tab_calls)

    // Built by mapping over HomeTab.entries rather than by hand, so the list order and the
    // selectedTab.ordinal that indexes into it cannot drift apart. A hand-written list is one
    // reordered line away from a tab that highlights its neighbour.
    val navItems = remember(communitiesLabel, chatsLabel, updatesLabel, callsLabel) {
        HomeTab.entries.map { tab ->
            when (tab) {
                HomeTab.COMMUNITIES -> MuhabbetNavItem(Muhabbet.icons.TabCommunities, communitiesLabel)
                HomeTab.CHATS -> MuhabbetNavItem(Muhabbet.icons.TabChats, chatsLabel)
                HomeTab.UPDATES -> MuhabbetNavItem(Muhabbet.icons.TabUpdates, updatesLabel)
                HomeTab.CALLS -> MuhabbetNavItem(Muhabbet.icons.TabCalls, callsLabel)
            }
        }
    }

    // Load conversations when search is activated, to have a list to filter
    LaunchedEffect(isSearchActive) {
        if (isSearchActive && allConversations.isEmpty()) {
            runCatchingCancellable {
                val result = conversationRepository.getConversations()
                allConversations = result.items
            }.onFailure { e ->
                // Without this, search over an empty list reports "no results" for every query.
                Log.e("HomeShellScreen", "Failed to load conversations for search", e)
                snackbarHostState.showSnackbar(errorLoadConversationsMsg)
            }
        }
    }

    // One place a tab change happens, so the haptic cannot be wired to three of the four items.
    // Re-selecting the current tab is silent: a haptic for "nothing happened" is noise.
    val haptics = Muhabbet.haptics
    val selectTab: (HomeTab) -> Unit = { tab ->
        if (tab != selectedTab) {
            haptics.perform(MuhabbetHapticIntent.TabSwitched)
            selectedTab = tab
        }
    }

    // foldForSearch, not lowercase(). Kotlin's no-arg lowercase() applies root-locale rules, so
    // "İsmail" became "i" + U+0307 — which nobody can type — and searching a Turkish contact list
    // for "ismail" matched nothing. See the function's docblock; it also folds ı/I/i/İ together, so
    // the user does not have to know which of the four the name was stored with.
    val filteredConversations = remember(searchQuery, allConversations) {
        if (searchQuery.isBlank()) allConversations
        else allConversations.filter { conv ->
            val query = foldForSearch(searchQuery.trim())
            val nameMatch = conv.name?.let { foldForSearch(it).contains(query) } == true
            val participantMatch = conv.participants.any { p ->
                p.displayName?.let { foldForSearch(it).contains(query) } == true ||
                    p.phoneNumber?.let { foldForSearch(it).contains(query) } == true
            }
            nameMatch || participantMatch
        }
    }

    MuhabbetScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            if (isSearchActive) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { newQuery ->
                                searchQuery = newQuery
                                // Two characters before asking the server. One character matches
                                // most of a person's history and the request is wasted; zero
                                // characters is the user clearing the field, which must clear the
                                // results rather than search for "".
                                if (newQuery.trim().length >= MIN_MESSAGE_SEARCH_LENGTH) {
                                    scope.launch {
                                        runCatchingCancellable {
                                            messageResults = messageRepository
                                                .searchMessages(newQuery.trim())
                                                .items
                                        }.onFailure { e ->
                                            // An empty list is how "no matches" renders, so a failed
                                            // search that only cleared the list would read as a
                                            // confident "nothing you have said contains that".
                                            Log.e("HomeShell", "Message search failed", e)
                                            messageResults = emptyList()
                                            snackbarHostState.showSnackbar(searchFailedMsg)
                                        }
                                    }
                                } else {
                                    messageResults = emptyList()
                                }
                            },
                            placeholder = { Text(searchPlaceholder, style = MaterialTheme.typography.bodyMedium) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                        // Keyed on isSearchActive so it fires when the bar opens, not on every
                        // recomposition the query causes.
                        LaunchedEffect(isSearchActive) {
                            if (isSearchActive) searchFocus.requestFocus()
                        }
                    },
                    navigationIcon = {
                        MuhabbetIconButton(
                            icon = Muhabbet.icons.Back,
                            contentDescription = backDesc,
                            onClick = {
                            isSearchActive = false
                            searchQuery = ""
                        }
                        )
                    },
                    // Bespoke bar (transforms into a search field), shared colours.
                    colors = MuhabbetTopBarDefaults.colors()
                )
            } else {
                MuhabbetTopBar(
                    title = appName,
                    actions = {
                        MuhabbetIconButton(
                            icon = Muhabbet.icons.Search,
                            contentDescription = searchDesc,
                            onClick = {
                            isSearchActive = true
                            searchQuery = ""
                        }
                        )
                        Box {
                            // Sole route to Settings. It shows no text, so without this description
                            // a screen reader announces only "button" and UI automation has nothing
                            // to match on but screen coordinates.
                            MuhabbetIconButton(
                                icon = Muhabbet.icons.More,
                                contentDescription = moreOptionsDesc,
                                onClick = { showMoreMenu = true },
                                modifier = Modifier.testTag("overflow_menu")
                            )
                            MuhabbetMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                MuhabbetMenuItem(
                                    text = settingsTitle,
                                    modifier = Modifier.testTag("menu_settings"),
                                    onClick = {
                                        showMoreMenu = false
                                        onSettings()
                                    }
                                )
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (!isSearchActive) {
                MuhabbetNavBar(
                    items = navItems,
                    selectedIndex = selectedTab.ordinal,
                    onSelect = { index -> selectTab(HomeTab.entries[index]) }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isSearchActive) {
                // Search results overlay.
                //
                // Two kinds of hit, and they are not interchangeable: conversations and contacts
                // match on a name and are filtered locally, messages match on their text and come
                // from the server. Showing only the first is what made search look broken (#638).
                // Messages sit below the conversations because a person searching a name usually
                // wants the chat, and a person searching a phrase usually knows there is no chat by
                // that name.
                if (filteredConversations.isEmpty() && messageResults.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = searchNoResults,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (filteredConversations.isNotEmpty() && messageResults.isNotEmpty()) {
                            item(key = "header_conversations") {
                                SectionHeader(title = searchSectionChats)
                            }
                        }
                        items(filteredConversations, key = { it.id }) { conv ->
                            ConversationSearchResultItem(
                                conversation = conv,
                                currentUserId = currentUserId,
                                onClick = {
                                    isSearchActive = false
                                    searchQuery = ""
                                    // Was `conv.name ?: other?.displayName ?: ""` — no phone-number
                                    // fallback and an empty string at the end, so searching for a
                                    // contact who has set no display name opened a nameless chat
                                    // (#543, reached from the home search instead).
                                    onConversationClick(conv.toChatTarget(currentUserId, defaultChatName))
                                }
                            )
                        }

                        if (messageResults.isNotEmpty()) {
                            item(key = "header_messages") {
                                SectionHeader(title = searchSectionMessages)
                            }
                            items(messageResults, key = { "msg_" + it.id }) { msg ->
                                MessageSearchResultRow(
                                    message = msg,
                                    conversations = allConversations,
                                    currentUserId = currentUserId,
                                    fallbackName = defaultChatName,
                                    onClick = { target ->
                                        isSearchActive = false
                                        searchQuery = ""
                                        messageResults = emptyList()
                                        onConversationClick(target)
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                // AnimatedContent with a direction, not Crossfade. Crossfading between bottom-nav
                // tabs is a well-known unfinished-app tell: the tabs sit in a row, so movement
                // between them should agree with that row. Going right slides left, and back.
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        val forward = targetState.ordinal > initialState.ordinal
                        val enter = slideInHorizontally(Muhabbet.motion.offsetSpatialDefault()) {
                            if (forward) it / TabSlideFraction else -it / TabSlideFraction
                        } + fadeIn(Muhabbet.motion.effectsFast())
                        val exit = slideOutHorizontally(Muhabbet.motion.offsetSpatialDefault()) {
                            if (forward) -it / TabSlideFraction else it / TabSlideFraction
                        } + fadeOut(Muhabbet.motion.effectsFast())
                        enter togetherWith exit
                    },
                    label = "homeTabTransition"
                ) { tab ->
                    when (tab) {
                        HomeTab.COMMUNITIES -> CommunityListScreen(
                            onCommunityClick = onCommunityClick,
                            onCreateCommunity = onCreateCommunity
                        )
                        HomeTab.CHATS -> ConversationListScreen(
                            onConversationClick = onConversationClick,
                            onNewConversation = onNewConversation,
                            onSettings = onSettings,
                            onStatusClick = onStatusClick,
                            refreshKey = refreshKey,
                            showTopBar = false,
                            showStatusRow = false
                        )
                        HomeTab.UPDATES -> UpdatesTabScreen(
                            onStatusClick = onStatusClick,
                            onSettings = onSettings,
                            refreshKey = refreshKey,
                            showTopBar = false
                        )
                        HomeTab.CALLS -> CallHistoryScreen(
                            onBack = {},
                            onCallUser = onCallUser,
                            onNewCall = onNewCall,
                            showBackButton = false,
                            showTopBar = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationSearchResultItem(
    conversation: ConversationResponse,
    currentUserId: String,
    onClick: () -> Unit
) {
    val displayName = conversation.name
        ?: conversation.participants.firstOrNull { it.userId != currentUserId }?.displayName
        ?: ""
    val avatarUrl = conversation.avatarUrl
        ?: conversation.participants.firstOrNull { it.userId != currentUserId }?.avatarUrl

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            avatarUrl = avatarUrl,
            displayName = displayName,
            size = MuhabbetSizes.AvatarMedium
        )
        Spacer(Modifier.width(MuhabbetSpacing.Medium))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1
            )
            conversation.lastMessagePreview?.let { preview ->
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * How far a tab slides in as a fraction of the screen: a hint of travel, not a page turn. A full
 * width slide between sibling tabs reads as navigating away rather than switching.
 */
private const val TabSlideFraction = 6

/**
 * Characters typed before the home search asks the server for message hits.
 *
 * One character matches most of a person's history, so the request is wasted and the result is
 * noise; zero characters is the user clearing the field, which must clear the results rather than
 * search for the empty string.
 */
private const val MIN_MESSAGE_SEARCH_LENGTH = 2
