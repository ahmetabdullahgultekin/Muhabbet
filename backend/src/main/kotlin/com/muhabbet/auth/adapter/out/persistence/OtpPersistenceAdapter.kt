package com.muhabbet.auth.adapter.out.persistence

import com.muhabbet.auth.adapter.out.persistence.entity.OtpRequestJpaEntity
import com.muhabbet.auth.adapter.out.persistence.repository.SpringDataOtpRepository
import com.muhabbet.auth.domain.model.OtpRequest
import com.muhabbet.auth.domain.port.out.OtpRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class OtpPersistenceAdapter(
    private val springDataOtpRepository: SpringDataOtpRepository
) : OtpRepository {

    override fun save(otpRequest: OtpRequest): OtpRequest =
        springDataOtpRepository.save(OtpRequestJpaEntity.fromDomain(otpRequest)).toDomain()

    override fun findActiveByPhoneNumber(phoneNumber: String): OtpRequest? =
        springDataOtpRepository.findActiveByPhoneNumber(phoneNumber, Instant.now())?.toDomain()

    /**
     * Runs in its own transaction so the attempt survives the caller's.
     *
     * `AuthService.verifyOtp` is @Transactional and throws BusinessException — an unchecked exception —
     * on a wrong code. That marks the caller's transaction rollback-only, which used to discard this
     * write along with it: the counter reset to zero on every wrong guess and the max-attempts guard
     * never fired, leaving the OTP brute-forceable for its full validity window. Committing separately
     * is the point of REQUIRES_NEW here, not an optimisation (#266).
     *
     * The cost is that the caller's connection sits idle while this one runs, so a verify holds two
     * connections briefly. That is why the whole decision is one statement — there is nothing else to
     * do inside this transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun claimAttempt(otpRequest: OtpRequest, maxAttempts: Int): Boolean =
        springDataOtpRepository.claimAttempt(otpRequest.id, maxAttempts) == 1

    /**
     * REQUIRES_NEW for the mirror image of [claimAttempt]'s reason: the caller refunds the attempt
     * and then throws `AUTH_2FA_PIN_REQUIRED`, so a refund that joined the caller's transaction
     * would be rolled back with it and the round trip would be charged after all.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun refundAttempt(otpRequest: OtpRequest) {
        springDataOtpRepository.refundAttempt(otpRequest.id)
    }

    override fun markVerified(otpRequest: OtpRequest) {
        springDataOtpRepository.markVerified(otpRequest.id)
    }
}
