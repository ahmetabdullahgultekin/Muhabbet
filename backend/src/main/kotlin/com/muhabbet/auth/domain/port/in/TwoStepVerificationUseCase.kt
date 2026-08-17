package com.muhabbet.auth.domain.port.`in`

import com.muhabbet.auth.domain.model.TwoStepStatus
import java.util.UUID

interface TwoStepVerificationUseCase {
    /** Whether two-step verification is switched on, and whether a recovery address was stored. */
    fun status(userId: UUID): TwoStepStatus
    fun setupPin(userId: UUID, pin: String, email: String?)
    fun verifyPin(userId: UUID, pin: String): Boolean
    fun disablePin(userId: UUID, currentPin: String)
    fun resetPinViaEmail(userId: UUID, email: String)
}
