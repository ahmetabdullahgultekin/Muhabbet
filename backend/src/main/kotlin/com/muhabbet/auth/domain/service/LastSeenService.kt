package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.port.`in`.RecordLastSeenUseCase
import com.muhabbet.auth.domain.port.out.UserRepository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * The transaction boundary for the `last_seen_at` write (#402).
 *
 * A service of its own rather than another method on [AuthService]: the caller is the socket
 * lifecycle, not authentication, and AuthService already implements enough use cases.
 *
 * `@Transactional` rather than
 * [com.muhabbet.messaging.domain.port.out.TransactionRunner], which the send path uses, for two
 * reasons: that port belongs to the messaging module and importing it here would be a cross-module
 * domain dependency, and its reason for existing — keeping a fan-out out of the transaction — does
 * not apply to a single one-statement update. The trap that port guards against is still real,
 * though, and the shape here is what avoids it: the caller holds [RecordLastSeenUseCase] and is a
 * different bean, so the call crosses the proxy. A `this.recordLastSeen(...)` from inside this
 * class would not, and would silently run with no transaction — the same class of bug as #402.
 *
 * `open class` and `override fun` are load-bearing. Spring wraps this bean in a CGLIB subclass to
 * apply `@Transactional`, and a final class or method cannot be overridden, so the call would run
 * on the proxy instance whose fields are all null. `HexagonalArchitectureTest.SpringProxySafety`
 * holds that line for the method; the class is opened explicitly here, as the other domain services
 * are, rather than relying on the allopen plugin reading a member annotation.
 */
open class LastSeenService(
    private val userRepository: UserRepository
) : RecordLastSeenUseCase {

    @Transactional
    override fun recordLastSeen(userId: UUID, at: Instant) {
        userRepository.updateLastSeenAt(userId, at)
    }
}
