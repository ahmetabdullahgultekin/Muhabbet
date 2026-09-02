package com.muhabbet.auth.adapter.out.external

import com.google.firebase.auth.FirebaseAuth
import com.muhabbet.auth.domain.port.out.FirebaseTokenVerifier
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * The real verifier: Firebase Admin checks the signature, issuer, audience and expiry.
 *
 * `getInstance()` is called per request rather than injected, exactly as the inline code did — when
 * `muhabbet.firebase.enabled` is false there is no `FirebaseApp` and the call throws, which the
 * caller reports as `AUTH_TOKEN_INVALID`. That keeps a deployment without Firebase behaving as it
 * always has instead of failing to start.
 */
@Component
class FirebaseAuthTokenVerifier : FirebaseTokenVerifier {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun phoneNumberOf(idToken: String): String? {
        val decoded = try {
            FirebaseAuth.getInstance().verifyIdToken(idToken)
        } catch (e: Exception) {
            log.warn("Firebase token verification failed: {}", e.message)
            throw IllegalArgumentException("Firebase token could not be verified", e)
        }
        return decoded.claims["phone_number"] as? String
    }
}
