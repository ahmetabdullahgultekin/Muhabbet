package com.muhabbet.app.ui.profile

import com.arkivanov.decompose.ComponentContext
import com.muhabbet.app.presentation.componentScope
import com.muhabbet.app.presentation.retainedStore
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * The user-profile destination. Today it owns only the "Message" action ([directChat]); the rest of
 * the profile screen still keeps its own state and is migrated separately (ADR 0010, #801).
 *
 * The Store's navigation effect is acted on here, in a scope tied to this component instance, and
 * not inside the retained Store: after a rotation the Store is the same object but [openChat]
 * belongs to a new navigation host.
 */
class UserProfileComponent(
    componentContext: ComponentContext,
    val userId: String,
    val contactName: String?,
    val conversationId: String?,
    storeFactory: () -> DirectChatStore,
    private val openChat: (conversationId: String) -> Unit
) : ComponentContext by componentContext {

    val directChat: DirectChatStore = retainedStore(storeFactory)

    private val failures = Channel<Unit>(Channel.BUFFERED)

    /** One emission per failed "Message" attempt, for the screen to report. */
    val messageFailures: Flow<Unit> = failures.receiveAsFlow()

    init {
        componentScope().launch {
            directChat.effects.collect { effect ->
                when (effect) {
                    is DirectChatEffect.OpenChat -> openChat(effect.conversationId)
                    DirectChatEffect.Failed -> failures.trySend(Unit)
                }
            }
        }
    }

    fun onMessageClick() = directChat.onEvent(DirectChatEvent.MessageClicked)
}
