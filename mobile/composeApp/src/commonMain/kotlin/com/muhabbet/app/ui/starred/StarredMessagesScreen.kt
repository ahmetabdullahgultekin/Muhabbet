package com.muhabbet.app.ui.starred

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import com.muhabbet.designsystem.components.MuhabbetTopBar
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.app.data.repository.MessageRepository
import com.muhabbet.app.ui.chat.formatMessageTime
import com.muhabbet.shared.model.ContentType
import com.muhabbet.shared.model.Message
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetScaffold
import com.muhabbet.designsystem.components.MuhabbetLoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarredMessagesScreen(
    onBack: () -> Unit,
    onNavigateToConversation: ((conversationId: String, messageId: String) -> Unit)? = null,
    messageRepository: MessageRepository = koinInject(),
    tokenStorage: TokenStorage = koinInject()
) {
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val currentUserId = remember { tokenStorage.getUserId() ?: "" }
    val snackbarHostState = remember { SnackbarHostState() }

    val errorLoadMsg = stringResource(Res.string.error_load_failed)

    LaunchedEffect(Unit) {
        val failure = runCatchingCancellable {
            val result = messageRepository.getStarredMessages()
            messages = result.items
        }.exceptionOrNull()
        // Clear the spinner BEFORE reporting — showSnackbar suspends until dismissed (~4s).
        isLoading = false
        if (failure != null) {
            // Without this the screen shows the "no starred messages" empty state, which is a lie.
            Log.e("StarredMessagesScreen", "Failed to load starred messages", failure)
            snackbarHostState.showSnackbar(errorLoadMsg)
        }
    }

    val youLabel = stringResource(Res.string.starred_you)

    MuhabbetScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            MuhabbetTopBar(
                title = stringResource(Res.string.starred_title),
                onBack = onBack,
                backContentDescription = stringResource(Res.string.action_back)
            )
        }
    ) { padding ->
        when {
            isLoading -> {
                MuhabbetLoadingState(Modifier.fillMaxSize().padding(padding))
            }
            messages.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Muhabbet.icons.Star,
                            contentDescription = stringResource(Res.string.starred_title),
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(MuhabbetSpacing.Small))
                        Text(
                            text = stringResource(Res.string.starred_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding)
                ) {
                    items(messages, key = { it.id }) { message ->
                        val isOwn = message.senderId == currentUserId
                        StarredMessageItem(
                            message = message,
                            senderLabel = if (isOwn) youLabel else message.senderId.take(8),
                            onClick = { onNavigateToConversation?.invoke(message.conversationId, message.id) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun StarredMessageItem(
    message: Message,
    senderLabel: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Content type icon for media messages
        val icon = contentTypeIcon(message.contentType)
        if (icon != null) {
            Icon(
                icon,
                contentDescription = message.contentType.name,
                modifier = Modifier.size(MuhabbetSizes.IconMedium),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(MuhabbetSpacing.Medium))
        }

        Column(modifier = Modifier.weight(1f)) {
            // Sender
            Text(
                text = senderLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            // Content preview
            Text(
                text = contentPreview(message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(MuhabbetSpacing.Small))

        // Timestamp + star
        Column(horizontalAlignment = Alignment.End) {
            val timestamp = message.serverTimestamp ?: message.clientTimestamp
            Text(
                text = formatMessageTime(timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                Muhabbet.icons.Star,
                contentDescription = stringResource(Res.string.starred_title),
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

private fun contentTypeIcon(contentType: ContentType): ImageVector? = when (contentType) {
    ContentType.IMAGE -> Muhabbet.icons.Image
    ContentType.VIDEO -> Muhabbet.icons.Video
    ContentType.DOCUMENT -> Muhabbet.icons.Document
    ContentType.VOICE -> Muhabbet.icons.Mic
    ContentType.LOCATION -> Muhabbet.icons.Location
    ContentType.POLL -> Muhabbet.icons.Poll
    else -> null
}

private fun contentPreview(message: Message): String {
    if (message.isDeleted) return ""
    return when (message.contentType) {
        ContentType.IMAGE -> message.content.ifBlank { "Photo" }
        ContentType.VIDEO -> message.content.ifBlank { "Video" }
        ContentType.VOICE -> message.content.ifBlank { "Voice message" }
        ContentType.DOCUMENT -> message.content.ifBlank { "Document" }
        ContentType.LOCATION -> message.content.ifBlank { "Location" }
        ContentType.POLL -> message.content.ifBlank { "Poll" }
        else -> message.content
    }.take(100)
}
