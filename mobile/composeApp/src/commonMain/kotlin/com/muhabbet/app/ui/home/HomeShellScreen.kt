package com.muhabbet.app.ui.home

import androidx.compose.animation.Crossfade
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.data.repository.ConversationRepository
import com.muhabbet.app.ui.call.CallHistoryScreen
import com.muhabbet.app.ui.communities.CommunityListScreen
import com.muhabbet.app.ui.components.UserAvatar
import com.muhabbet.app.ui.conversations.ConversationListScreen
import com.muhabbet.app.ui.status.UpdatesTabScreen
import com.muhabbet.app.ui.theme.LocalSemanticColors
import com.muhabbet.app.ui.theme.MuhabbetSpacing
import com.muhabbet.shared.dto.ConversationResponse
import com.muhabbet.shared.model.ConversationType
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.app_name
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

private enum class HomeTab {
    COMMUNITIES,
    CHATS,
    UPDATES,
    CALLS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeShellScreen(
    onConversationClick: (id: String, name: String, otherUserId: String?, isGroup: Boolean) -> Unit,
    onNewConversation: () -> Unit,
    onSettings: () -> Unit,
    onStatusClick: (userId: String, displayName: String) -> Unit,
    onCallUser: (userId: String, name: String?, callType: String) -> Unit,
    onCommunityClick: (String) -> Unit = {},
    onCreateCommunity: () -> Unit = {},
    refreshKey: Int = 0,
    conversationRepository: ConversationRepository = koinInject(),
    tokenStorage: TokenStorage = koinInject()
) {
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.CHATS) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var allConversations by remember { mutableStateOf<List<ConversationResponse>>(emptyList()) }
    val currentUserId = remember { tokenStorage.getUserId() ?: "" }

    val appName = stringResource(Res.string.app_name)
    val settingsTitle = stringResource(Res.string.settings_title)
    val searchDesc = stringResource(Res.string.search_messages_placeholder)
    val searchPlaceholder = stringResource(Res.string.home_search_placeholder)
    val searchNoResults = stringResource(Res.string.home_search_no_results)
    val communitiesLabel = stringResource(Res.string.home_tab_communities)
    val chatsLabel = stringResource(Res.string.home_tab_chats)
    val updatesLabel = stringResource(Res.string.home_tab_updates)
    val callsLabel = stringResource(Res.string.home_tab_calls)

    val semanticColors = LocalSemanticColors.current
    val accentColor = MaterialTheme.colorScheme.primary
    val inactiveColor = semanticColors.secondaryText

    // Load conversations when search is activated, to have a list to filter
    LaunchedEffect(isSearchActive) {
        if (isSearchActive && allConversations.isEmpty()) {
            try {
                val result = conversationRepository.getConversations()
                allConversations = result.items
            } catch (_: Exception) { }
        }
    }

    val filteredConversations = remember(searchQuery, allConversations) {
        if (searchQuery.isBlank()) allConversations
        else allConversations.filter { conv ->
            val query = searchQuery.trim().lowercase()
            val nameMatch = conv.name?.lowercase()?.contains(query) == true
            val participantMatch = conv.participants.any { p ->
                p.displayName?.lowercase()?.contains(query) == true ||
                    p.phoneNumber?.lowercase()?.contains(query) == true
            }
            nameMatch || participantMatch
        }
    }

    Scaffold(
        topBar = {
            if (isSearchActive) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(searchPlaceholder, style = MaterialTheme.typography.bodyMedium) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearchActive = false
                            searchQuery = ""
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = appName,
                            fontWeight = FontWeight.Medium,
                            fontSize = 20.sp
                        )
                    },
                    actions = {
                        IconButton(onClick = {
                            isSearchActive = true
                            searchQuery = ""
                        }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = searchDesc
                            )
                        }
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = null
                                )
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(settingsTitle) },
                                    onClick = {
                                        showMoreMenu = false
                                        onSettings()
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        bottomBar = {
            if (!isSearchActive) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    NavigationBarItem(
                        selected = selectedTab == HomeTab.COMMUNITIES,
                        onClick = { selectedTab = HomeTab.COMMUNITIES },
                        icon = { Icon(Icons.Default.Groups, contentDescription = communitiesLabel) },
                        label = { Text(communitiesLabel) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = accentColor,
                            selectedTextColor = accentColor,
                            unselectedIconColor = inactiveColor,
                            unselectedTextColor = inactiveColor,
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == HomeTab.CHATS,
                        onClick = { selectedTab = HomeTab.CHATS },
                        icon = { Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = chatsLabel) },
                        label = { Text(chatsLabel) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = accentColor,
                            selectedTextColor = accentColor,
                            unselectedIconColor = inactiveColor,
                            unselectedTextColor = inactiveColor,
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == HomeTab.UPDATES,
                        onClick = { selectedTab = HomeTab.UPDATES },
                        icon = { Icon(Icons.Default.CameraAlt, contentDescription = updatesLabel) },
                        label = { Text(updatesLabel) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = accentColor,
                            selectedTextColor = accentColor,
                            unselectedIconColor = inactiveColor,
                            unselectedTextColor = inactiveColor,
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == HomeTab.CALLS,
                        onClick = { selectedTab = HomeTab.CALLS },
                        icon = { Icon(Icons.Default.Call, contentDescription = callsLabel) },
                        label = { Text(callsLabel) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = accentColor,
                            selectedTextColor = accentColor,
                            unselectedIconColor = inactiveColor,
                            unselectedTextColor = inactiveColor,
                            indicatorColor = Color.Transparent
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isSearchActive) {
                // Search results overlay
                if (filteredConversations.isEmpty()) {
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
                        items(filteredConversations, key = { it.id }) { conv ->
                            ConversationSearchResultItem(
                                conversation = conv,
                                currentUserId = currentUserId,
                                onClick = {
                                    val otherUserId = if (conv.type == ConversationType.DIRECT) {
                                        conv.participants.firstOrNull { it.userId != currentUserId }?.userId
                                    } else null
                                    isSearchActive = false
                                    searchQuery = ""
                                    onConversationClick(
                                        conv.id,
                                        conv.name ?: conv.participants.firstOrNull { it.userId != currentUserId }?.displayName ?: "",
                                        otherUserId,
                                        conv.type == ConversationType.GROUP
                                    )
                                }
                            )
                        }
                    }
                }
            } else {
                Crossfade(targetState = selectedTab, label = "homeTabTransition") { tab ->
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
            size = 44.dp
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

