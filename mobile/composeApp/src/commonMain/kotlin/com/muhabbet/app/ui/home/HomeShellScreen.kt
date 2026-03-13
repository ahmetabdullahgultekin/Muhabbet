package com.muhabbet.app.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.muhabbet.app.ui.call.CallHistoryScreen
import com.muhabbet.app.ui.conversations.ConversationListScreen
import com.muhabbet.app.ui.status.UpdatesTabScreen
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.app_name
import com.muhabbet.composeapp.generated.resources.home_tab_calls
import com.muhabbet.composeapp.generated.resources.home_tab_chats
import com.muhabbet.composeapp.generated.resources.home_tab_updates
import com.muhabbet.composeapp.generated.resources.settings_title
import org.jetbrains.compose.resources.stringResource

private enum class HomeTab {
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
    refreshKey: Int = 0
) {
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.CHATS) }

    val appName = stringResource(Res.string.app_name)
    val chatsLabel = stringResource(Res.string.home_tab_chats)
    val updatesLabel = stringResource(Res.string.home_tab_updates)
    val callsLabel = stringResource(Res.string.home_tab_calls)
    val settingsTitle = stringResource(Res.string.settings_title)
    val tabs = listOf(
        HomeTab.CHATS to chatsLabel,
        HomeTab.UPDATES to updatesLabel,
        HomeTab.CALLS to callsLabel
    )

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(appName) },
                    actions = {
                        IconButton(onClick = onSettings) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = settingsTitle
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                TabRow(
                    selectedTabIndex = tabs.indexOfFirst { it.first == selectedTab },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    tabs.forEach { (tab, label) ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = { Text(label) },
                            icon = {
                                Icon(
                                    imageVector = when (tab) {
                                        HomeTab.CHATS -> Icons.Outlined.ChatBubbleOutline
                                        HomeTab.UPDATES -> Icons.Default.CameraAlt
                                        HomeTab.CALLS -> Icons.Default.Call
                                    },
                                    contentDescription = label
                                )
                            }
                        )
                    }
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
