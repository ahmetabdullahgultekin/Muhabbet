package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.messaging.domain.port.out.ConversationSequencePort
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.hibernate.Session
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * `UPDATE … RETURNING` through the session's own JDBC connection, so it runs in — and its row lock
 * lives exactly as long as — the caller's transaction.
 *
 * Plain JDBC rather than a JPA native query: `conversations.last_seq` is deliberately not mapped on
 * `ConversationJpaEntity` (a mapped counter would be written back by any unrelated conversation save
 * from a stale entity), and a returning UPDATE is a statement JPA has no clean name for.
 *
 * `MANDATORY` for the reason given on `MessagePersistenceAdapter.save`: an allocation outside the
 * transaction that writes the row would commit the number on its own and release the lock that
 * makes the order safe.
 */
@Component
class ConversationSequenceAdapter(
    @PersistenceContext private val entityManager: EntityManager
) : ConversationSequencePort {

    @Transactional(propagation = Propagation.MANDATORY)
    override fun next(conversationId: UUID): Long =
        entityManager.unwrap(Session::class.java).doReturningWork { connection ->
            connection.prepareStatement(ALLOCATE_SQL).use { statement ->
                statement.setObject(1, conversationId)
                statement.executeQuery().use { rows ->
                    if (rows.next()) rows.getLong(1) else null
                }
            }
        } ?: throw BusinessException(ErrorCode.MSG_CONVERSATION_NOT_FOUND)

    private companion object {
        const val ALLOCATE_SQL =
            "UPDATE conversations SET last_seq = last_seq + 1 WHERE id = ? RETURNING last_seq"
    }
}
