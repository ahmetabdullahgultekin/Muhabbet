package com.muhabbet.messaging.domain.port.out

import java.util.UUID

/**
 * How many outbound fetches one user may cause.
 *
 * The link-preview endpoint makes the server fetch a URL of the caller's choosing. Without a
 * per-user budget, one account can turn the server into a fetch proxy, and the shared per-IP edge
 * limit does not distinguish one abusive account from a whole office behind one address.
 */
interface LinkPreviewQuotaPort {

    /** Records one fetch about to be made for [userId] and reports whether it is within budget. */
    fun tryConsume(userId: UUID): Boolean

    /**
     * Returns one unit charged by [tryConsume] when the fetch it paid for was never made (the
     * instance was busy). Best effort: it never takes the budget below zero, and a refund that lands
     * in the next window simply has nothing to return.
     */
    fun refund(userId: UUID)
}
