package com.muhabbet.app.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A request to open a conversation that arrived from **outside** the app's navigation — a tapped
 * notification, or a `muhabbet://chat/{id}` link.
 *
 * It exists because the two halves cannot see each other. The platform hands the request to an
 * Activity (or, on iOS, to the app delegate), and the thing that can act on it is [MainComponent],
 * which may not exist yet: the notification may have started the process, and the user may not even
 * be past the login screen. So the request is parked here and taken when there is somewhere to put
 * it:
 *
 * - **cold start** — the Activity parks the request in `onCreate`, before the component tree is
 *   built; [MainComponent] takes it while building its *initial* stack, so the first frame is the
 *   chat and the conversation list never flashes underneath (#823).
 * - **already running** — the Activity parks it in `onNewIntent`; [MainComponent] is observing
 *   [pending] and navigates at once (#594).
 * - **not logged in** — there is no [MainComponent] until sign-in completes, so the request waits,
 *   and the user lands in the right chat after signing in instead of on the list.
 *
 * A [StateFlow] rather than a `Channel` because the request must survive until something is able to
 * consume it, however long that takes, and because a second notification tapped before the first
 * was handled should replace it — the user wants the chat they just tapped, not both in sequence.
 */
data class ChatOpenRequest(
    val conversationId: String,
    /**
     * Only chooses the placeholder until the conversation resolves. There is deliberately no display
     * name here: a name carried by an intent is whatever the sender of that intent chose (#815), and
     * the chat resolves its own title from data this device already trusts.
     */
    val isGroup: Boolean = false
)

class PendingChatOpen {

    private val _pending = MutableStateFlow<ChatOpenRequest?>(null)
    val pending: StateFlow<ChatOpenRequest?> = _pending.asStateFlow()

    fun request(request: ChatOpenRequest) {
        _pending.value = request
    }

    /**
     * Takes whatever is pending, atomically: the caller is the one consumer of that request. Nothing
     * suspends between taking and navigating any more, so there is no window in which a newer
     * request could be thrown away with an older one.
     */
    fun take(): ChatOpenRequest? {
        while (true) {
            val current = _pending.value ?: return null
            if (_pending.compareAndSet(current, null)) return current
        }
    }
}
