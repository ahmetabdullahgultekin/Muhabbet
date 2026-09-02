package com.muhabbet.auth.domain.port.`in`

import com.muhabbet.auth.domain.model.TwoStepStatus
import java.util.UUID

interface TwoStepVerificationUseCase {
    /** Whether two-step verification is switched on, and whether a recovery address was stored. */
    fun status(userId: UUID): TwoStepStatus
    fun setupPin(userId: UUID, pin: String, email: String?)
    fun verifyPin(userId: UUID, pin: String): Boolean
    fun disablePin(userId: UUID, currentPin: String)

    // There is deliberately no reset. `resetPinViaEmail` compared an address the caller supplied
    // against the stored one and cleared the PIN — it verified nothing, and it was reachable only
    // from an authenticated session, which is exactly the session a locked-out user does not have.
    // It was therefore a way past the second factor for anyone who could guess a recovery address,
    // and no help at all to the person it was named for. Removed with the sign-in gate (#566); a
    // real reset needs a mail round-trip, which this deployment has no sender for.
}
