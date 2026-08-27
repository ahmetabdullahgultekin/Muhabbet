package com.muhabbet.app.ui.chat

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muhabbet.app.data.repository.ConversationRepository
import com.muhabbet.designsystem.components.MuhabbetTopBar
import com.muhabbet.designsystem.components.UserAvatar
import com.muhabbet.designsystem.theme.MuhabbetElevation
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.shared.dto.ConversationResponse
import com.muhabbet.shared.model.ConversationType
import com.muhabbet.app.util.Log
import com.muhabbet.app.ui.contacts.rememberContactNames
import com.muhabbet.app.ui.conversations.resolveName
import com.muhabbet.app.util.runCatchingCancellable
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetScaffold
import com.muhabbet.designsystem.components.MuhabbetLoadingState
import com.muhabbet.designsystem.theme.MuhabbetSizes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactSharePicker(
    onBack: () -> Unit,
    onContactSelected: (userId: String, displayName: String, phoneNumber: String?) -> Unit,
    conversationRepository: ConversationRepository = koinInject()
) {
    var conversations by remember { mutableStateOf<List<ConversationResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }

    val errorLoadMsg = stringResource(Res.string.error_load_conversations)
    val unknownLabel = stringResource(Res.string.unknown)
    val contactNames = rememberContactNames()

    LaunchedEffect(Unit) {
        val failure = runCatchingCancellable {
            val result = conversationRepository.getConversations(limit = 100)
            conversations = result.items.filter { it.type == ConversationType.DIRECT }
        }.exceptionOrNull()
        // Clear the spinner BEFORE reporting — showSnackbar suspends until dismissed (~4s).
        isLoading = false
        if (failure != null) {
            // Without this the picker just renders empty, reading as "you have no contacts".
            Log.e("ContactSharePicker", "Failed to load contacts", failure)
            snackbarHostState.showSnackbar(errorLoadMsg)
        }
    }

    MuhabbetScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            MuhabbetTopBar(
                title = stringResource(Res.string.share_contact),
                onBack = onBack,
                backContentDescription = stringResource(Res.string.action_back)
            )
        }
    ) { padding ->
        if (isLoading) {
            MuhabbetLoadingState(Modifier.fillMaxSize().padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                items(conversations, key = { it.id }) { conv ->
                    val participant = conv.participants.firstOrNull()
                    // The chain used to be written out here and stopped at the display name, so a
                    // person you have saved as "Anne" but who has set no name of their own was
                    // offered as "Bilinmeyen" — the one option that identifies nobody (#549).
                    val displayName = conv.name
                        ?: participant?.resolveName(contactNames)
                        ?: unknownLabel
                    val phoneNumber = participant?.phoneNumber

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val userId = participant?.userId ?: return@clickable
                                onContactSelected(userId, displayName, phoneNumber)
                            },
                        tonalElevation = MuhabbetElevation.Level1
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = MuhabbetSpacing.Medium,
                                vertical = MuhabbetSpacing.Medium
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(
                                avatarUrl = participant?.avatarUrl,
                                displayName = displayName,
                                size = MuhabbetSizes.AvatarMedium
                            )
                            Spacer(Modifier.width(MuhabbetSpacing.Medium))
                            Column {
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                if (phoneNumber != null) {
                                    Text(
                                        text = phoneNumber,
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
}
