package com.muhabbet.app.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A request to open a conversation that arrived from **outside** the composition — a tapped
 * notification, or a `muhabbet://chat/{id}` link.
 *
 * It exists because the two halves cannot see each other. The platform hands the request to an
 * Activity (or, on iOS, to the app delegate), and the thing that can act on it is
 * [MainComponent.openChat], which lives in a composition that may not exist yet: the notification
 * may have started the process, and the user may not even be past the login screen. So the request
 * is parked here and collected when there is somewhere to put it.
 *
 * That parking is the whole design, and it is what makes the behaviour correct in the awkward
 * cases rather than only the easy one:
 *
 * - **cold start** — the Activity writes the request in `onCreate`, before `setContent`; the
 *   consumer picks it up on its first composition.
 * - **already running** — the Activity writes it in `onNewIntent`; the consumer is already
 *   collecting, so it fires immediately. This is the common case and the one the old code missed
 *   entirely (#594): the conversation id was written onto the intent and read by nobody.
 * - **not logged in** — `MainContent` is only composed once `RootComponent` has switched to
 *   `Child.Main`, so a request that arrives at the login screen waits, and the user lands in the
 *   right chat after signing in instead of on the list.
 *
 * A [StateFlow] rather than a `Channel` because the request must survive until something is able to
 * consume it, however long that takes, and because a second notification tapped before the first
 * was handled should replace it — the user wants the chat they just tapped, not both in sequence.
 */
data class ChatOpenRequest(
    val conversationId: String,
    /**
     * The name to show in the title bar, when the source knew it. A notification does — it was
     * built from the sender's name. A bare deep link does not, and the consumer resolves it.
     */
    val displayName: String? = null,
    val isGroup: Boolean = false
)

class PendingChatOpen {

    private val _pending = MutableStateFlow<ChatOpenRequest?>(null)
    val pending: StateFlow<ChatOpenRequest?> = _pending.asStateFlow()

    fun request(request: ChatOpenRequest) {
        _pending.value = request
    }

    /**
     * Clears [request] only if it is still the pending one.
     *
     * Resolving a display name can suspend, and a second notification can be tapped while that is
     * in flight. An unconditional `value = null` would then throw away the newer request and leave
     * the user in the older chat.
     */
    fun consume(request: ChatOpenRequest) {
        _pending.compareAndSet(request, null)
    }
}
