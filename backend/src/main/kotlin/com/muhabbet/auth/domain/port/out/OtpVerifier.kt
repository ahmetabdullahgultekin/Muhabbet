package com.muhabbet.auth.domain.port.out

/**
 * Delegates code generation *and* checking to an external verification service.
 *
 * This is deliberately not an [OtpSender]. A sender is handed a code we generated and only has to
 * deliver it; a verifier owns the code end to end and will not accept one from us, so the two cannot
 * share a port. When a verifier is configured the service skips its own code generation and hash
 * comparison — cooldown, expiry and attempt limits stay on our side.
 */
interface OtpVerifier {

    /** Asks the provider to generate and deliver a code to [phoneNumber]. */
    suspend fun start(phoneNumber: String)

    /** Returns true when [code] is the one the provider issued for [phoneNumber]. */
    suspend fun check(phoneNumber: String, code: String): Boolean
}
