package com.muhabbet.app.navigation

import com.arkivanov.decompose.ComponentContext
import com.muhabbet.app.presentation.Store
import com.muhabbet.app.presentation.retainedStore
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import com.muhabbet.shared.dto.ConversationResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * The chat destination. Owns one thing the screen cannot: who the conversation *is*, when the stack
 * entry was created knowing only its id.
 *
 * That is the case for a tapped notification, a `muhabbet://chat/{id}` link and the profile's
 * "Message" action. All three used to look the conversation up *before* navigating — on a cold start
 * the conversation list rendered first and the chat slid in over it after the request returned
 * (#823). Navigation now happens at once, and the title resolves here, asynchronously, while the
 * screen shows the localized placeholder name.
 *
 * Never from the intent: a name supplied by whoever launched the app is not shown (#815).
 */
class ChatDestinationComponent(
    componentContext: ComponentContext,
    val config: MainComponent.Config.Chat,
    lookup: ConversationLookup,
    context: CoroutineContext = Dispatchers.Main.immediate
) : ComponentContext by componentContext {

    val identity: ChatIdentityStore = retainedStore {
        ChatIdentityStore(config.conversationId, needsLookup = config.name == null, lookup, context)
    }
}

data class ChatIdentityState(
    /** The conversation as this device knows it, once looked up; null until then or if not found. */
    val conversation: ConversationResponse? = null
)

/** Resolves a conversation by id, once, for an entry that arrived without a name. Takes no events. */
class ChatIdentityStore(
    private val conversationId: String,
    needsLookup: Boolean,
    private val lookup: ConversationLookup,
    context: CoroutineContext
) : Store<ChatIdentityState, Nothing, Nothing>(ChatIdentityState(), context) {

    init {
        if (needsLookup) {
            scope.launch {
                runCatchingCancellable { lookup.find(conversationId) }
                    .onSuccess { found -> updateState { it.copy(conversation = found) } }
                    .onFailure { e -> Log.w(TAG, "Could not resolve conversation $conversationId: ${e.message}") }
            }
        }
    }

    override fun onEvent(event: Nothing) = Unit

    private companion object {
        const val TAG = "ChatIdentityStore"
    }
}
