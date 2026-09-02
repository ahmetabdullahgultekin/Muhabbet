package com.muhabbet.auth.domain.port.out

/**
 * Turns a Firebase ID token into the phone number it was issued for.
 *
 * Extracted from `AuthService` for #566. `FirebaseAuth.getInstance().verifyIdToken(...)` was called
 * inline from the domain service, which put a framework SDK in the domain layer and — the reason it
 * mattered here — made the Firebase sign-in **impossible to reach in a test**: every call died at
 * the SDK before it got anywhere near the two-step gate. The gate has to hold on both paths or it
 * holds on neither, so the path that could not be tested was the one worth testing.
 */
interface FirebaseTokenVerifier {
    /**
     * The verified `phone_number` claim, or null when the token is valid but carries no phone.
     *
     * @throws IllegalArgumentException when the token itself does not verify. The caller turns that
     *   into `AUTH_TOKEN_INVALID`; a checked-exception-free signature keeps the domain free of the
     *   SDK's own exception types.
     */
    fun phoneNumberOf(idToken: String): String?
}
