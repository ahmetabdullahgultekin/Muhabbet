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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.muhabbet.app.data.repository.MessageRepository
import com.muhabbet.app.ui.chat.formatMessageTime
import com.muhabbet.shared.model.ContentType
import com.muhabbet.shared.model.Message
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

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

    LaunchedEffect(Unit) {
        try {
            val result = messageRepository.getStarredMessages()
            messages = result.items
        } catch (_: Exception) { }
        isLoading = false
    }

    val youLabel = stringResource(Res.string.starred_you)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.starred_title)) },
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
        }
    ) { padding ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            messages.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = stringResource(Res.string.starred_title),
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(8.dp))
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Content type icon for media messages
        val icon = contentTypeIcon(message.contentType)
        if (icon != null) {
            Icon(
                icon,
                contentDescription = message.contentType.name,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
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

        Spacer(Modifier.width(8.dp))

        // Timestamp + star
        Column(horizontalAlignment = Alignment.End) {
            val timestamp = message.serverTimestamp ?: message.clientTimestamp
            Text(
                text = formatMessageTime(timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                Icons.Default.Star,
                contentDescription = stringResource(Res.string.starred_title),
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

private fun contentTypeIcon(contentType: ContentType): ImageVector? = when (contentType) {
    ContentType.IMAGE -> Icons.Default.Image
    ContentType.VIDEO -> Icons.Default.Videocam
    ContentType.DOCUMENT -> Icons.Default.Description
    ContentType.VOICE -> Icons.Default.Mic
    ContentType.LOCATION -> Icons.Default.LocationOn
    ContentType.POLL -> Icons.Default.Poll
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
