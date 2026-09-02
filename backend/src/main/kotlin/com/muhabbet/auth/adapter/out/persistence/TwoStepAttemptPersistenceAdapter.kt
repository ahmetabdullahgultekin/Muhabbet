package com.muhabbet.auth.adapter.out.persistence

import com.muhabbet.auth.adapter.out.persistence.repository.SpringDataTwoStepAttemptRepository
import com.muhabbet.auth.domain.port.out.TwoStepAttemptRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Component
class TwoStepAttemptPersistenceAdapter(
    private val springDataTwoStepAttemptRepository: SpringDataTwoStepAttemptRepository
) : TwoStepAttemptRepository {

    /**
     * Runs in its own transaction, for the same reason [OtpPersistenceAdapter.claimAttempt] does.
     *
     * Every caller rejects a wrong PIN by throwing a `BusinessException`, which marks the enclosing
     * transaction rollback-only and would take this increment down with it — the counter would
     * return to zero after every wrong guess and the limit would never fire. That was #266 on the
     * OTP, and repeating it here would leave a six-digit second factor with no ceiling at all.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun claimAttempt(userId: UUID, maxAttempts: Int, lockFor: Duration): Boolean {
        val now = Instant.now()
        springDataTwoStepAttemptRepository.ensureRow(userId, now)
        return springDataTwoStepAttemptRepository.claimAttempt(
            userId = userId,
            maxAttempts = maxAttempts,
            now = now,
            lockUntil = now.plus(lockFor)
        ) == 1
    }

    /**
     * Also REQUIRES_NEW. `setupPin` clears the window before it has finished storing the new hash,
     * and a later failure in that same method must not resurrect the previous lock.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun clear(userId: UUID) {
        springDataTwoStepAttemptRepository.clear(userId, Instant.now())
    }
}
