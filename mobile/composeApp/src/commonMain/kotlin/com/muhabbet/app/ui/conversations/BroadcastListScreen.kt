package com.muhabbet.app.ui.conversations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import com.muhabbet.designsystem.components.MuhabbetTopBar
import com.muhabbet.designsystem.theme.MuhabbetElevation
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.shared.dto.BroadcastListResponse
import com.muhabbet.shared.dto.CreateBroadcastListRequest
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetScaffold
import com.muhabbet.designsystem.components.MuhabbetLoadingState
import com.muhabbet.designsystem.components.MuhabbetEmptyState
import com.muhabbet.designsystem.components.MuhabbetDialog
import com.muhabbet.designsystem.components.MuhabbetTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BroadcastListScreen(
    onBack: () -> Unit,
    onBroadcastListClick: (id: String, name: String) -> Unit = { _, _ -> },
    apiClient: ApiClient = koinInject()
) {
    var lists by remember { mutableStateOf<List<BroadcastListResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showCreateDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val genericErrorMsg = stringResource(Res.string.error_generic)

    LaunchedEffect(Unit) {
        val failure = runCatchingCancellable {
            val response = apiClient.get<List<BroadcastListResponse>>("/api/v1/broadcasts")
            lists = response.data ?: emptyList()
        }.exceptionOrNull()
        // Clear the spinner BEFORE reporting — showSnackbar suspends until dismissed (~4s).
        isLoading = false
        if (failure != null) {
            // Without this the screen shows the "no broadcast lists" empty state, which is a lie.
            Log.e("BroadcastListScreen", "Failed to load broadcast lists", failure)
            snackbarHostState.showSnackbar(genericErrorMsg)
        }
    }

    if (showCreateDialog) {
        var listName by remember { mutableStateOf("") }
        MuhabbetDialog(
            onDismiss = { showCreateDialog = false },
            title = stringResource(Res.string.broadcast_list_create),
            dismissLabel = stringResource(Res.string.cancel),
            confirmLabel = stringResource(Res.string.broadcast_list_create),
            onConfirm = {
                        scope.launch {
                            try {
                                val response = apiClient.post<BroadcastListResponse>(
                                    "/api/v1/broadcasts",
                                    CreateBroadcastListRequest(name = listName, memberIds = emptyList())
                                )
                                val created = response.data
                                if (created != null) {
                                    lists = lists + created
                                }
                            } catch (_: Exception) {
                                snackbarHostState.showSnackbar(genericErrorMsg)
                            }
                        }
                        showCreateDialog = false
                    },
            confirmEnabled = listName.isNotBlank(),
            content ={
                MuhabbetTextField(
                    value = listName,
                    onValueChange = { listName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(Res.string.broadcast_list_name_hint),
                    singleLine = true,
                    imeAction = ImeAction.Done
                )
            }
        )
    }

    MuhabbetScaffold(
        topBar = {
            MuhabbetTopBar(
                title = stringResource(Res.string.broadcast_list_title),
                onBack = onBack,
                backContentDescription = stringResource(Res.string.action_back)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Muhabbet.icons.Add, contentDescription = stringResource(Res.string.broadcast_list_create))
            }
        },
        snackbarHostState = snackbarHostState
    ) { padding ->
        if (isLoading) {
            MuhabbetLoadingState(Modifier.fillMaxSize().padding(padding))
        } else if (lists.isEmpty()) {
            MuhabbetEmptyState(
                modifier = Modifier.fillMaxSize().padding(padding),
                icon = Muhabbet.icons.Channel,
                title = stringResource(Res.string.broadcast_list_empty)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                items(lists, key = { it.id }) { broadcastList ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onBroadcastListClick(broadcastList.id, broadcastList.name) },
                        tonalElevation = MuhabbetElevation.Level1
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = MuhabbetSpacing.Medium,
                                vertical = MuhabbetSpacing.Medium
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Muhabbet.icons.Channel,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(Modifier.width(MuhabbetSpacing.Medium))
                            Column {
                                Text(
                                    text = broadcastList.name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "${broadcastList.memberCount} ${stringResource(Res.string.community_members).lowercase()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
