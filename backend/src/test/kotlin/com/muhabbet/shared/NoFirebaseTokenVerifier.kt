package com.muhabbet.shared

import com.muhabbet.auth.domain.port.out.FirebaseTokenVerifier

/**
 * Stands in for Firebase in the tests that never touch it.
 *
 * Refuses every token, which is what the real SDK does in a deployment with no `FirebaseApp` — so a
 * test that reaches the Firebase path by accident fails with `AUTH_TOKEN_INVALID` rather than
 * quietly minting a session. Tests that mean to exercise that path supply their own verifier.
 */
object NoFirebaseTokenVerifier : FirebaseTokenVerifier {
    override fun phoneNumberOf(idToken: String): String =
        throw IllegalArgumentException("No Firebase in this test")
}
