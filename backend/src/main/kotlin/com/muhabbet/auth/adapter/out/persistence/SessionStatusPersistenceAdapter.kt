package com.muhabbet.auth.adapter.out.persistence

import com.muhabbet.auth.domain.model.UserStatus
import com.muhabbet.auth.domain.port.out.DeviceLogoutPort
import com.muhabbet.auth.domain.port.out.SessionStatusPort
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/**
 * One statement, two primary-key probes, on every authenticated request — which is why it is one
 * native `SELECT` rather than two repository `findById`s that would each hydrate a whole entity.
 *
 * `UPPER(status)` because the column's SQL default (V1) is lower-case `'active'` while JPA writes the
 * enum name; a row written either way must compare correctly.
 *
 * `logged_out_at` (V32) is compared truncated to the second because a JWT's `iat` carries whole
 * seconds: a token issued in the same second as the logout cannot be shown to postdate it, so it is
 * refused. The cost is that a sign-in completed within that same second has its first access token
 * refused once and refreshes — the safe side of the trade.
 *
 * `logged_out_at` is written here, by a native `UPDATE`, rather than mapped on `DeviceJpaEntity`,
 * because that entity is rebuilt from the domain model on every save: mapped, the column would be
 * reset by any unrelated device write. Unmapped, no save touches it.
 */
@Component
class SessionStatusPersistenceAdapter(
    private val entityManager: EntityManager
) : SessionStatusPort, DeviceLogoutPort {

    override fun isRevoked(userId: UUID, deviceId: UUID, issuedAt: Instant): Boolean {
        val result = entityManager.createNativeQuery(
            """
            SELECT (EXISTS (SELECT 1 FROM users WHERE id = :userId AND UPPER(status) = :deleted)
                 OR EXISTS (SELECT 1 FROM devices WHERE id = :deviceId
                            AND (revoked_at IS NOT NULL
                                 OR date_trunc('second', logged_out_at) >= :issuedAt)))
            """
        )
            .setParameter("userId", userId)
            .setParameter("deviceId", deviceId)
            .setParameter("deleted", UserStatus.DELETED.name)
            .setParameter("issuedAt", issuedAt)
            .singleResult
        // Fail closed and loudly. A security check must never read a type it did not expect as
        // "allow": `as? Boolean ?: false` would have turned a driver or dialect change into every
        // revoked device and erased account being honoured again.
        return when (result) {
            is Boolean -> result
            else -> error("Session status query returned ${result?.javaClass?.name}, expected Boolean")
        }
    }

    override fun recordLogout(userId: UUID, deviceId: UUID, at: Instant) {
        entityManager.createNativeQuery(
            "UPDATE devices SET logged_out_at = :at WHERE id = :deviceId AND user_id = :userId"
        )
            .setParameter("at", at)
            .setParameter("deviceId", deviceId)
            .setParameter("userId", userId)
            .executeUpdate()
    }
}
