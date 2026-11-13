package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.port.`in`.RecordLastSeenUseCase
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.shared.transaction.TransactionRunner
import java.time.Instant
import java.util.UUID

/**
 * The transaction boundary for the `last_seen_at` write (#402).
 *
 * A service of its own rather than another method on the OTP service: the caller is the socket
 * lifecycle, not authentication.
 *
 * The write is a `@Modifying` update, which throws "No active transaction for update or delete
 * query" when run outside one — that was #402, and it failed silently on every disconnect for
 * months. The boundary is stated through [TransactionRunner] (ADR 0014) rather than a
 * `@Transactional` annotation, which only took effect when the call crossed a CGLIB proxy and so
 * depended on the class staying `open` and the caller holding the proxy. A call cannot be bypassed.
 */
class LastSeenService(
    private val userRepository: UserRepository,
    private val transactions: TransactionRunner
) : RecordLastSeenUseCase {

    override fun recordLastSeen(userId: UUID, at: Instant) {
        transactions.inTransaction<Unit> {
            userRepository.updateLastSeenAt(userId, at)
        }
    }
}
