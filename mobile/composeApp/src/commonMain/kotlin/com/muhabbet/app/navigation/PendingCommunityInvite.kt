package com.muhabbet.app.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A `muhabbet://community-invite/{token}` link that arrived from **outside** the composition.
 *
 * The same parking problem [PendingChatOpen] solves, for the same reason: the platform hands the URL
 * to an Activity, and the thing that can act on it — [MainComponent.openJoinCommunity] — lives in a
 * composition that may not exist yet. An invite is if anything the more likely of the two to arrive
 * cold, because it comes from outside the app entirely: someone taps a link in another messenger on
 * a phone where Muhabbet has never been opened, or has been signed out.
 *
 * The **not logged in** case is the one that matters most here and is why this is a flow rather than
 * a one-shot. `MainContent` is only composed once the user is past the login screen, so a token that
 * arrives at the phone-number field waits, and the person lands on the join screen after signing in
 * rather than on an empty conversation list wondering what the link did.
 *
 * A newer token replaces an older unconsumed one: someone who taps two invites wants the second, not
 * a queue.
 */
class PendingCommunityInvite {

    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun request(token: String) {
        _pending.value = token
    }

    /**
     * Clears [token] only if it is still the pending one, matching [PendingChatOpen.consume] — the
     * consumer navigates, and a second link tapped in between must not be discarded with the first.
     */
    fun consume(token: String) {
        _pending.compareAndSet(token, null)
    }
}
