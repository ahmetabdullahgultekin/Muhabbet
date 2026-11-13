package com.muhabbet.app.ui.profile

import com.muhabbet.app.presentation.Store
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Finds — or creates — the direct conversation with a user and answers with its id.
 *
 * The server's create-direct endpoint returns the existing conversation when there is one, so this is
 * one call either way; it is the same endpoint `NewConversationScreen` uses for a contact tap.
 */
fun interface DirectChatOpener {
    suspend fun conversationWith(userId: String): String
}

data class DirectChatUiState(val opening: Boolean = false)

sealed interface DirectChatEvent {
    data object MessageClicked : DirectChatEvent
}

sealed interface DirectChatEffect {
    data class OpenChat(val conversationId: String) : DirectChatEffect
    data object Failed : DirectChatEffect
}

/**
 * The profile's "Message" action (#806).
 *
 * It used to be `goBack()`, on the assumption that a profile is only ever opened from the 1:1 chat
 * underneath it. Two of its three entry points — a group member, a community member — have the group
 * underneath instead, so "Message" took the user back to the group. Now it resolves the actual
 * conversation, and the navigation's identity rule (`navigatedTo`) returns to that chat if it is
 * already on the stack rather than stacking a second copy.
 *
 * @param knownConversationId the direct conversation, when the profile was opened from it; no
 *   request is needed then.
 */
class DirectChatStore(
    private val userId: String,
    private val knownConversationId: String?,
    private val opener: DirectChatOpener,
    context: CoroutineContext = Dispatchers.Main.immediate
) : Store<DirectChatUiState, DirectChatEvent, DirectChatEffect>(DirectChatUiState(), context) {

    override fun onEvent(event: DirectChatEvent) {
        when (event) {
            DirectChatEvent.MessageClicked -> openChat()
        }
    }

    private fun openChat() {
        knownConversationId?.let {
            emit(DirectChatEffect.OpenChat(it))
            return
        }
        // A second tap while the first request is in flight would create nothing new — the server
        // returns the same conversation — but would navigate twice.
        if (currentState.opening) return
        updateState { it.copy(opening = true) }
        scope.launch {
            runCatchingCancellable { opener.conversationWith(userId) }
                .onSuccess { emit(DirectChatEffect.OpenChat(it)) }
                .onFailure { e ->
                    Log.e(TAG, "Could not open the direct conversation with $userId", e)
                    emit(DirectChatEffect.Failed)
                }
            updateState { it.copy(opening = false) }
        }
    }

    private companion object {
        const val TAG = "DirectChatStore"
    }
}
