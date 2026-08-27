package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.port.`in`.RecordLastSeenUseCase
import com.muhabbet.auth.domain.port.out.UserRepository
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.aop.support.AopUtils
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.EnableTransactionManagement
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
 * is the thing that was actually missing — that by the time the repository call runs, Spring has
 * started a transaction — and it does so without a database, so it runs on a host with no Docker.
 * The row-level proof lives in `LastSeenPersistenceIntegrationTest`.
 *
 * It fails if `@Transactional` is dropped, if the method or class stops being open (a CGLIB proxy
 * cannot override a final method, so the annotation is silently ignored), or if a future refactor
 * moves the write back to a caller that does not cross the proxy.
 */
class LastSeenTransactionBoundaryTest {

    @Test
    fun `should run the last seen write inside an active transaction`() {
        AnnotationConfigApplicationContext(TxTestConfig::class.java).use { ctx ->
            val useCase = ctx.getBean(RecordLastSeenUseCase::class.java)
            val repository = ctx.getBean(RecordingUserRepository::class.java)

            useCase.recordLastSeen(UUID.randomUUID(), Instant.now())

            assertTrue(
                repository.sawActiveTransaction == true,
                "last_seen_at was written with no transaction — this is exactly #402"
            )
        }
    }

    @Test
    fun `should reach the write through a proxy rather than the raw bean`() {
        // The other half of the same guarantee. @Transactional does nothing at all unless the
        // caller's reference is the proxy, so a wiring change that hands out the target instance
        // would leave the annotation in place and the transaction gone.
        AnnotationConfigApplicationContext(TxTestConfig::class.java).use { ctx ->
            val useCase = ctx.getBean(RecordLastSeenUseCase::class.java)

            assertTrue(AopUtils.isAopProxy(useCase), "LastSeenService is not proxied: ${useCase.javaClass}")
        }
    }

    /**
     * `proxyTargetClass = true` mirrors Spring Boot, which sets it globally. It matters: with a JDK
     * interface proxy a final class or method would still be transactional, so this test would pass
     * against a `LastSeenService` that silently is not one in production.
     */
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    open class TxTestConfig {

        @Bean
        open fun transactionManager(): PlatformTransactionManager = RecordingTransactionManager()

        @Bean
        open fun userRepository(): RecordingUserRepository = RecordingUserRepository()

        @Bean
        open fun lastSeenService(userRepository: RecordingUserRepository): LastSeenService =
            LastSeenService(userRepository)
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
