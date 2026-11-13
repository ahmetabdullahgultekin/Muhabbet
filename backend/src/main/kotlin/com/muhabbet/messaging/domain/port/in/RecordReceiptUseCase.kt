package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.Receipt

/**
 * The one entry point for delivery and read receipts, whichever transport carried them (#870).
 *
 * The WebSocket handler used to assemble the read pipeline itself — a conversation-wide bulk
 * update, then a single-message update and broadcast, in two transactions — so the rules about what
 * a receipt does lived in an adapter. An adapter now only translates its wire format into a
 * [Receipt] and calls this.
 *
 * Contract: idempotent and monotone. A receipt for a state the row has already reached changes
 * nothing and publishes nothing; every row it does move forward is published to that message's
 * sender, after commit, as the sender's aggregate tick.
 */
interface RecordReceiptUseCase {
    fun record(receipt: Receipt)
}
