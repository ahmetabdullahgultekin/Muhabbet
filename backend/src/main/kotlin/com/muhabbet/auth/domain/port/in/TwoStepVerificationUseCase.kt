package com.muhabbet.auth.domain.port.`in`

import java.util.UUID

interface TwoStepVerificationUseCase {
    fun setupPin(userId: UUID, pin: String, email: String?)
    fun verifyPin(userId: UUID, pin: String): Boolean
    fun disablePin(userId: UUID, currentPin: String)
    fun resetPinViaEmail(userId: UUID, email: String)
}
