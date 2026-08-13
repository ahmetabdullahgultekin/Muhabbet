package com.muhabbet.auth.domain.port.out

import com.muhabbet.auth.domain.model.OtpRequest

interface OtpRepository {
    fun save(otpRequest: OtpRequest): OtpRequest
    fun findActiveByPhoneNumber(phoneNumber: String): OtpRequest?
    /**
     * Claims one verification attempt against [maxAttempts]. Returns false when the budget is spent.
     *
     * Deliberately not a read followed by an increment: the two together are not atomic, and
     * concurrent verifies interleave between them.
     */
    fun claimAttempt(otpRequest: OtpRequest, maxAttempts: Int): Boolean
    fun markVerified(otpRequest: OtpRequest)
}
