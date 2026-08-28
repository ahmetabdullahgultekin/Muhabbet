package com.muhabbet.auth.domain.port.`in`

import java.time.Instant
import java.util.UUID

/**
 * Records the moment a user's last connection ended.
 *
 * It is an in-port for one concrete reason. The write behind it is a `@Modifying` query, so it
 * needs a transaction, and its only caller is the WebSocket adapter, which runs outside one:
 * calling the repository straight from the adapter threw `No active transaction for update or
 * delete query` on every disconnect, the adapter caught it and warned, and `last_seen_at` never
 * moved for as long as the socket lifecycle has existed (#402).
 *
 * The transaction boundary therefore sits on the service that implements this, and the adapter
 * reaches it through this interface. That is not decoration: `@Transactional` is applied by a
 * Spring proxy, so a call that does not cross one — a private helper on the adapter, or a `this.`
 * call inside the implementing class — reproduces exactly the same silent no-op.
 */
interface RecordLastSeenUseCase {
    fun recordLastSeen(userId: UUID, at: Instant)
}
