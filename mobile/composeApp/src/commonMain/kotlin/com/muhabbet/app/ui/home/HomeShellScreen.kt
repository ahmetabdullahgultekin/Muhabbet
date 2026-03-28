package com.muhabbet.app.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.UpdatesOutlined
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.muhabbet.app.ui.call.CallHistoryScreen
import com.muhabbet.app.ui.conversations.ConversationListScreen
import com.muhabbet.app.ui.status.UpdatesTabScreen
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.home_tab_calls
import com.muhabbet.composeapp.generated.resources.home_tab_chats
import com.muhabbet.composeapp.generated.resources.home_tab_communities
import com.muhabbet.composeapp.generated.resources.home_tab_updates
import org.jetbrains.compose.resources.stringResource

private enum class HomeTab {
    COMMUNITIES,
    CHATS,
    UPDATES,
    CALLS
}

private data class BottomNavItem(
    val tab: HomeTab,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun HomeShellScreen(
    onConversationClick: (id: String, name: String, otherUserId: String?, isGroup: Boolean) -> Unit,
    onNewConversation: () -> Unit,
    onSettings: () -> Unit,
    onStatusClick: (userId: String, displayName: String) -> Unit,
    onCallUser: (userId: String, name: String?, callType: String) -> Unit,
    refreshKey: Int = 0
) {
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.CHATS) }

    val communitiesLabel = stringResource(Res.string.home_tab_communities)
    val chatsLabel = stringResource(Res.string.home_tab_chats)
    val updatesLabel = stringResource(Res.string.home_tab_updates)
    val callsLabel = stringResource(Res.string.home_tab_calls)

    val navItems = listOf(
        BottomNavItem(
            tab = HomeTab.COMMUNITIES,
            label = communitiesLabel,
            selectedIcon = Icons.Filled.Groups,
            unselectedIcon = Icons.Outlined.Groups
        ),
        BottomNavItem(
            tab = HomeTab.CHATS,
            label = chatsLabel,
            selectedIcon = Icons.Outlined.ChatBubbleOutline,
            unselectedIcon = Icons.Outlined.ChatBubbleOutline
        ),
        BottomNavItem(
            tab = HomeTab.UPDATES,
            label = updatesLabel,
            selectedIcon = Icons.Outlined.UpdatesOutlined,
            unselectedIcon = Icons.Outlined.UpdatesOutlined
        ),
        BottomNavItem(
            tab = HomeTab.CALLS,
            label = callsLabel,
            selectedIcon = Icons.Filled.Call,
            unselectedIcon = Icons.Outlined.Call
        )
    )

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                navItems.forEach { item ->
                    val selected = selectedTab == item.tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { selectedTab = item.tab },
                        icon = {
                            Icon(
                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.surfaceVariant
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
            Crossfade(targetState = selectedTab, label = "homeTabTransition") { tab ->
                when (tab) {
                    HomeTab.COMMUNITIES -> ConversationListScreen(
                        onConversationClick = onConversationClick,
                        onNewConversation = onNewConversation,
                        onSettings = onSettings,
                        onStatusClick = onStatusClick,
                        refreshKey = refreshKey,
                        showTopBar = true,
                        showStatusRow = false
                    )
                    HomeTab.CHATS -> ConversationListScreen(
                        onConversationClick = onConversationClick,
                        onNewConversation = onNewConversation,
                        onSettings = onSettings,
                        onStatusClick = onStatusClick,
                        refreshKey = refreshKey,
                        showTopBar = true,
                        showStatusRow = false
                    )
                    HomeTab.UPDATES -> UpdatesTabScreen(
                        onStatusClick = onStatusClick,
                        onSettings = onSettings,
                        refreshKey = refreshKey,
                        showTopBar = true
                    )
                    HomeTab.CALLS -> CallHistoryScreen(
                        onBack = {},
                        onCallUser = onCallUser,
                        showBackButton = false,
                        showTopBar = true
                    )
                }
            }
        }
    }
}
