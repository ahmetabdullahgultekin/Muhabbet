package com.muhabbet.auth.adapter.out.persistence

import com.muhabbet.auth.domain.model.UserStatus
import com.muhabbet.auth.domain.port.out.SessionStatusPort
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * One statement, two primary-key probes, on every authenticated request — which is why it is one
 * native `SELECT` rather than two repository `findById`s that would each hydrate a whole entity.
 *
 * `UPPER(status)` because the column's SQL default (V1) is lower-case `'active'` while JPA writes the
 * enum name; a row written either way must compare correctly.
 */
@Component
class SessionStatusPersistenceAdapter(
    private val entityManager: EntityManager
) : SessionStatusPort {

    override fun isRevoked(userId: UUID, deviceId: UUID): Boolean {
        val result = entityManager.createNativeQuery(
            """
            SELECT (EXISTS (SELECT 1 FROM users WHERE id = :userId AND UPPER(status) = :deleted)
                 OR EXISTS (SELECT 1 FROM devices WHERE id = :deviceId AND revoked_at IS NOT NULL))
            """
        )
            .setParameter("userId", userId)
            .setParameter("deviceId", deviceId)
            .setParameter("deleted", UserStatus.DELETED.name)
            .singleResult
        return result as? Boolean ?: false
    }
}
