package com.muhabbet.auth.adapter.out.persistence

import com.muhabbet.auth.adapter.out.persistence.entity.OtpRequestJpaEntity
import com.muhabbet.auth.adapter.out.persistence.repository.SpringDataOtpRepository
import com.muhabbet.auth.domain.model.OtpRequest
import com.muhabbet.auth.domain.port.out.OtpRepository
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class OtpPersistenceAdapter(
    private val springDataOtpRepository: SpringDataOtpRepository
) : OtpRepository {

    override fun save(otpRequest: OtpRequest): OtpRequest =
        springDataOtpRepository.save(OtpRequestJpaEntity.fromDomain(otpRequest)).toDomain()

    override fun findActiveByPhoneNumber(phoneNumber: String): OtpRequest? =
        springDataOtpRepository.findActiveByPhoneNumber(phoneNumber, Instant.now())?.toDomain()

    override fun incrementAttempts(otpRequest: OtpRequest) {
        val entity = springDataOtpRepository.findById(otpRequest.id).orElse(null) ?: return
        entity.attempts = otpRequest.attempts + 1
        springDataOtpRepository.save(entity)
    }

    override fun markVerified(otpRequest: OtpRequest) {
        val entity = springDataOtpRepository.findById(otpRequest.id).orElse(null) ?: return
        entity.verified = true
        springDataOtpRepository.save(entity)
    }
}
