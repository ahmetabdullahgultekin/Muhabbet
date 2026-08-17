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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.muhabbet.app.data.repository.ConversationDirectory
import com.muhabbet.app.data.repository.MessageRepository
import com.muhabbet.app.ui.chat.formatMessageTime
import com.muhabbet.app.ui.conversations.ChatTarget
import com.muhabbet.app.ui.conversations.senderLabel
import com.muhabbet.app.ui.conversations.toChatTarget
import com.muhabbet.shared.dto.ConversationResponse
import com.muhabbet.shared.model.ContentType
import com.muhabbet.shared.model.Message
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetScaffold
import com.muhabbet.designsystem.components.MuhabbetLoadingState

/**
 * Every message you starred, across every conversation.
 *
 * A starred `Message` carries a `conversationId` and a `senderId` and nothing else about either, so
 * this screen resolves both against the conversation list before it can name anything (#543). Until
 * it did, it printed "Unknown contact" for every message that was not your own — including people
 * you actively chat with — and navigated with an empty name, landing the user in a chat with no
 * title, a "?" avatar and a dead tap where the person should be.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarredMessagesScreen(
    onBack: () -> Unit,
    onNavigateToConversation: ((target: ChatTarget, messageId: String) -> Unit)? = null,
    messageRepository: MessageRepository = koinInject(),
    conversationDirectory: ConversationDirectory = koinInject(),
    tokenStorage: TokenStorage = koinInject()
) {
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var conversations by remember { mutableStateOf<Map<String, ConversationResponse>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    val currentUserId = remember { tokenStorage.getUserId() ?: "" }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
            return@LaunchedEffect
        }
        // A second, independent request, and deliberately not folded into the one above: if the
        // identities cannot be fetched the messages are still worth showing, unnamed. Folding them
        // together would trade a list with weak labels for no list at all.
        runCatchingCancellable {
            conversations = conversationDirectory.lookUp(messages.map { it.conversationId }.toSet())
        }.exceptionOrNull()?.let { e ->
            Log.w("StarredMessagesScreen", "Could not resolve the conversations these came from: $e")
        }
    }

    val youLabel = stringResource(Res.string.starred_you)
    // A user id is not a name — its first eight characters read as a hex hash (#507).
    val unknownPersonLabel = stringResource(Res.string.unknown_person)
    val defaultChatName = stringResource(Res.string.chat_default_name)
    val unavailableMsg = stringResource(Res.string.starred_conversation_unavailable)

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
                        val conversation = conversations[message.conversationId]
                        val isOwn = message.senderId == currentUserId
                        StarredMessageItem(
                            message = message,
                            senderLabel = when {
                                isOwn -> youLabel
                                // Falls back only when the sender really cannot be placed — they
                                // left the conversation, or the conversation itself is gone.
                                else -> conversation?.senderLabel(message.senderId) ?: unknownPersonLabel
                            },
                            onClick = {
                                val target = conversation?.toChatTarget(currentUserId, defaultChatName)
                                if (target != null) {
                                    onNavigateToConversation?.invoke(target, message.id)
                                } else {
                                    // Navigating anyway is what #543 was: the right conversation,
                                    // opened with nothing to draw it with. Saying so is better.
                                    scope.launch { snackbarHostState.showSnackbar(unavailableMsg) }
                                }
                            }
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

/**
 * What a starred message reads as in the list.
 *
 * Composable, and every fallback comes from `composeResources`. These six were English literals —
 * "Photo", "Video", "Voice message" and so on — rendered untranslated inside a Turkish-default app.
 */
@Composable
private fun contentPreview(message: Message): String {
    if (message.isDeleted) return ""
    val fallback = when (message.contentType) {
        ContentType.IMAGE -> stringResource(Res.string.chat_photo)
        ContentType.VIDEO -> stringResource(Res.string.chat_video)
        ContentType.VOICE -> stringResource(Res.string.chat_voice_message)
        ContentType.DOCUMENT -> stringResource(Res.string.attach_document)
        ContentType.LOCATION -> stringResource(Res.string.attach_location)
        ContentType.POLL -> stringResource(Res.string.attach_poll)
        else -> ""
    }
    return message.content.ifBlank { fallback }.take(100)
}
