package com.muhabbet.app.ui.group

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.designsystem.components.MuhabbetTopBar
import com.muhabbet.designsystem.theme.MuhabbetElevation
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.app.util.DateTimeFormatter
import com.muhabbet.app.util.Log
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.shared.dto.CreateGroupEventRequest
import com.muhabbet.shared.dto.GroupEventResponse
import com.muhabbet.shared.dto.RsvpRequest
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetScaffold
import com.muhabbet.designsystem.components.MuhabbetLoadingState
import com.muhabbet.designsystem.components.MuhabbetEmptyState
import com.muhabbet.designsystem.components.MuhabbetDialog
import com.muhabbet.designsystem.components.MuhabbetTextField
import com.muhabbet.designsystem.components.MuhabbetButtonRole
import com.muhabbet.designsystem.components.MuhabbetButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupEventScreen(
    conversationId: String,
    onBack: () -> Unit,
    apiClient: ApiClient = koinInject()
) {
    var events by remember { mutableStateOf<List<GroupEventResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showCreateDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val genericErrorMsg = stringResource(Res.string.error_generic)

    LaunchedEffect(conversationId) {
        try {
            val response = apiClient.get<List<GroupEventResponse>>("/api/v1/conversations/$conversationId/events")
            events = response.data ?: emptyList()
        } catch (e: Exception) {
            Log.e("GroupEventScreen", "Failed to load group events", e)
        }
        isLoading = false
    }

    if (showCreateDialog) {
        CreateEventDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { title, description, eventTime, location ->
                scope.launch {
                    try {
                        val response = apiClient.post<GroupEventResponse>(
                            "/api/v1/conversations/$conversationId/events",
                            CreateGroupEventRequest(
                                title = title,
                                description = description,
                                eventTime = eventTime,
                                location = location
                            )
                        )
                        val newEvent = response.data
                        if (newEvent != null) {
                            events = events + newEvent
                        }
                    } catch (_: Exception) {
                        snackbarHostState.showSnackbar(genericErrorMsg)
                    }
                }
                showCreateDialog = false
            }
        )
    }

    MuhabbetScaffold(
        topBar = {
            MuhabbetTopBar(
                title = stringResource(Res.string.group_event_title),
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
                Icon(Muhabbet.icons.Add, contentDescription = stringResource(Res.string.group_event_create))
            }
        },
        snackbarHostState = snackbarHostState
    ) { padding ->
        if (isLoading) {
            MuhabbetLoadingState(Modifier.fillMaxSize().padding(padding))
        } else if (events.isEmpty()) {
            MuhabbetEmptyState(
                modifier = Modifier.fillMaxSize().padding(padding),
                icon = Muhabbet.icons.Calendar,
                title = stringResource(Res.string.group_event_empty)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Medium),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(MuhabbetSpacing.Medium)
            ) {
                items(events, key = { it.id }) { event ->
                    EventCard(
                        event = event,
                        onRsvp = { status ->
                            scope.launch {
                                try {
                                    apiClient.post<Unit>(
                                        "/api/v1/conversations/$conversationId/events/${event.id}/rsvp",
                                        RsvpRequest(status = status)
                                    )
                                } catch (_: Exception) {
                                    snackbarHostState.showSnackbar(genericErrorMsg)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EventCard(
    event: GroupEventResponse,
    onRsvp: (String) -> Unit
) {
    Surface(
        tonalElevation = MuhabbetElevation.Level2,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(MuhabbetSpacing.Large)) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            event.description?.let { desc ->
                Spacer(Modifier.height(MuhabbetSpacing.Small))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(MuhabbetSpacing.Medium))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Muhabbet.icons.Calendar,
                    contentDescription = null,
                    modifier = Modifier.size(MuhabbetSizes.IconSmall),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(MuhabbetSpacing.Small))
                Text(
                    text = DateTimeFormatter.formatTime(event.eventTime),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            event.location?.let { loc ->
                Spacer(Modifier.height(MuhabbetSpacing.XSmall))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Muhabbet.icons.Location,
                        contentDescription = null,
                        modifier = Modifier.size(MuhabbetSizes.IconSmall),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(MuhabbetSpacing.Small))
                    Text(
                        text = loc,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(Modifier.height(MuhabbetSpacing.Small))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Muhabbet.icons.People,
                    contentDescription = null,
                    modifier = Modifier.size(MuhabbetSizes.IconSmall),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(MuhabbetSpacing.Small))
                Text(
                    text = stringResource(Res.string.group_event_going_count, event.goingCount),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(MuhabbetSpacing.Large))

            // RSVP buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Small)
            ) {
                Button(
                    onClick = { onRsvp("GOING") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(stringResource(Res.string.group_event_going))
                }
                MuhabbetButton(
                    text = stringResource(Res.string.group_event_maybe),
                    onClick = { onRsvp("MAYBE") },
                    modifier = Modifier.weight(1f),
                    role = MuhabbetButtonRole.Secondary
                )
                MuhabbetButton(
                    text = stringResource(Res.string.group_event_not_going),
                    onClick = { onRsvp("NOT_GOING") },
                    modifier = Modifier.weight(1f),
                    role = MuhabbetButtonRole.Secondary
                )
            }
        }
    }
}

@Composable
private fun CreateEventDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, description: String?, eventTime: Long, location: String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    // For simplicity, use current time + 1 day as default event time
    val defaultTime = remember { kotlin.time.Clock.System.now().toEpochMilliseconds() + 86400000L }

    MuhabbetDialog(
        onDismiss = onDismiss,
        title = stringResource(Res.string.group_event_create),
        dismissLabel = stringResource(Res.string.cancel),
        confirmLabel = stringResource(Res.string.group_event_create),
        onConfirm = {
                    onCreate(
                        title,
                        description.ifBlank { null },
                        defaultTime,
                        location.ifBlank { null }
                    )
                },
        confirmEnabled = title.isNotBlank(),
        content ={
            Column {
                MuhabbetTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(Res.string.group_event_name_hint),
                    singleLine = true,
                    imeAction = ImeAction.Next
                )
                Spacer(Modifier.height(MuhabbetSpacing.Medium))
                MuhabbetTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(Res.string.community_description_hint),
                    singleLine = false,
                    maxLines = 3
                )
                Spacer(Modifier.height(MuhabbetSpacing.Medium))
                MuhabbetTextField(
                    value = location,
                    onValueChange = { location = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(Res.string.group_event_location),
                    singleLine = true,
                    imeAction = ImeAction.Done
                )
            }
        }
    )
}
