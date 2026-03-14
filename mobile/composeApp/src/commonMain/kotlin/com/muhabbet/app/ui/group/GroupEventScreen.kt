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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.muhabbet.app.ui.theme.MuhabbetElevation
import com.muhabbet.app.ui.theme.MuhabbetSpacing
import com.muhabbet.app.util.DateTimeFormatter
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.shared.dto.CreateGroupEventRequest
import com.muhabbet.shared.dto.GroupEventResponse
import com.muhabbet.shared.dto.RsvpRequest
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

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
        } catch (_: Exception) { }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.group_event_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.group_event_create))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (events.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(MuhabbetSpacing.Medium))
                    Text(
                        text = stringResource(Res.string.group_event_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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

            if (event.description != null) {
                Spacer(Modifier.height(MuhabbetSpacing.Small))
                Text(
                    text = event.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(MuhabbetSpacing.Medium))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CalendarToday,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(MuhabbetSpacing.Small))
                Text(
                    text = DateTimeFormatter.formatDateTime(Instant.fromEpochMilliseconds(event.eventTime)),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (event.location != null) {
                Spacer(Modifier.height(MuhabbetSpacing.XSmall))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(MuhabbetSpacing.Small))
                    Text(
                        text = event.location,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(Modifier.height(MuhabbetSpacing.Small))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.People,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(MuhabbetSpacing.Small))
                Text(
                    text = "${event.goingCount} ${stringResource(Res.string.group_event_going).lowercase()}",
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
                OutlinedButton(
                    onClick = { onRsvp("MAYBE") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(Res.string.group_event_maybe))
                }
                OutlinedButton(
                    onClick = { onRsvp("NOT_GOING") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(Res.string.group_event_not_going))
                }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.group_event_create)) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(Res.string.group_event_name_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(MuhabbetSpacing.Medium))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(Res.string.community_description_hint)) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(MuhabbetSpacing.Medium))
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text(stringResource(Res.string.group_event_location)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCreate(
                        title,
                        description.ifBlank { null },
                        defaultTime,
                        location.ifBlank { null }
                    )
                },
                enabled = title.isNotBlank()
            ) {
                Text(stringResource(Res.string.group_event_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        }
    )
}
