package com.muhabbet.app.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import com.muhabbet.app.ui.call.CallHistoryScreen
import com.muhabbet.app.ui.communities.CommunityListScreen
import com.muhabbet.app.ui.conversations.ConversationListScreen
import com.muhabbet.app.ui.status.UpdatesTabScreen
import com.muhabbet.app.ui.theme.LocalSemanticColors
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.app_name
import com.muhabbet.composeapp.generated.resources.home_tab_calls
import com.muhabbet.composeapp.generated.resources.home_tab_chats
import com.muhabbet.composeapp.generated.resources.home_tab_communities
import com.muhabbet.composeapp.generated.resources.home_tab_updates
import com.muhabbet.composeapp.generated.resources.search_messages_placeholder
import com.muhabbet.composeapp.generated.resources.settings_title
import org.jetbrains.compose.resources.stringResource

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
    refreshKey: Int = 0
) {
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.CHATS) }
    var showMoreMenu by remember { mutableStateOf(false) }

    val appName = stringResource(Res.string.app_name)
    val settingsTitle = stringResource(Res.string.settings_title)
    val searchDesc = stringResource(Res.string.search_messages_placeholder)
    val communitiesLabel = stringResource(Res.string.home_tab_communities)
    val chatsLabel = stringResource(Res.string.home_tab_chats)
    val updatesLabel = stringResource(Res.string.home_tab_updates)
    val callsLabel = stringResource(Res.string.home_tab_calls)

    val semanticColors = LocalSemanticColors.current
    val accentColor = MaterialTheme.colorScheme.primary
    val inactiveColor = semanticColors.secondaryText

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = appName,
                        fontWeight = FontWeight.Medium,
                        fontSize = 20.sp
                    )
                },
                actions = {
                    IconButton(onClick = { /* TODO: search */ }) {
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
        },
        bottomBar = {
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
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
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

