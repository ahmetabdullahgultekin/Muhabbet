package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.port.`in`.RecordLastSeenUseCase
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.shared.transaction.SpringTransactionRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Instant
import java.util.UUID

/**
 * #402 — the `last_seen_at` write must happen inside a transaction.
 *
 * The production symptom was one line, repeated on every disconnect: `Failed to persist
 * last_seen_at for <uuid>: No active transaction for update or delete query`. A `@Modifying` query
 * was being run straight from the WebSocket adapter, which has no transaction of its own, so the
 * write threw every single time and the column never moved. The adapter caught and warned, which is
 * why nobody noticed for months.
 *
 * A mock cannot reproduce that: a mocked repository happily "succeeds" with no transaction in
 * sight, so a test built on one would pass against the broken code. What this test asserts instead
 * is the thing that was actually missing — that by the time the repository call runs, a transaction
 * has been started — and it does so with the production [SpringTransactionRunner] over a recording
 * transaction manager, without a database, so it runs on a host with no Docker. The row-level proof
 * lives in `LastSeenPersistenceIntegrationTest`.
 *
 * Since ADR 0014 the boundary is a [com.muhabbet.shared.transaction.TransactionRunner] call rather
 * than a `@Transactional` proxy, so the old second test — "is the bean proxied?" — is gone with the
 * proxy. What replaced it is stronger: the service is called directly, with no Spring context at
 * all, and the write is still transactional. A proxy-based boundary fails exactly that.
 */
class LastSeenTransactionBoundaryTest {

    private val repository = RecordingUserRepository()
    private val useCase: RecordLastSeenUseCase =
        LastSeenService(repository, SpringTransactionRunner(RecordingTransactionManager()))

    @Test
    fun `should run the last seen write inside an active transaction`() {
        useCase.recordLastSeen(UUID.randomUUID(), Instant.now())

        assertTrue(
            repository.sawActiveTransaction == true,
            "last_seen_at was written with no transaction — this is exactly #402"
        )
    }

    /**
     * Records whether a transaction was actually active when the write was attempted — the one fact
     * the production failure turned on.
     */
    class RecordingUserRepository : UserRepository {
        var sawActiveTransaction: Boolean? = null

        override fun updateLastSeenAt(userId: UUID, lastSeenAt: Instant) {
            sawActiveTransaction = TransactionSynchronizationManager.isActualTransactionActive()
        }

        override fun findByPhoneNumber(phoneNumber: String): User? = null
        override fun findById(id: UUID): User? = null
        override fun findAllByIds(ids: List<UUID>): List<User> = emptyList()
        override fun save(user: User): User = user
        override fun existsByPhoneNumber(phoneNumber: String): Boolean = false
    }

    /**
     * The smallest transaction manager that still tells the truth. `AbstractPlatformTransactionManager`
     * is what marks a transaction active in [TransactionSynchronizationManager], so a stub built on
     * it makes "was a transaction open here?" answerable without a datasource.
     */
    class RecordingTransactionManager : AbstractPlatformTransactionManager() {
        override fun doGetTransaction(): Any = Any()
        override fun doBegin(transaction: Any, definition: TransactionDefinition) = Unit
        override fun doCommit(status: DefaultTransactionStatus) = Unit
        override fun doRollback(status: DefaultTransactionStatus) = Unit
    }
}
