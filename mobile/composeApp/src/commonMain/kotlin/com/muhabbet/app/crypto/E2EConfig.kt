package com.muhabbet.app.crypto

/**
 * Feature flag for end-to-end encryption on the message send/receive path.
 *
 * DEFAULT = false → preserves the exact current production behavior (messages travel as
 * plaintext under TLS, identical to HEAD). E2E is CORE-PATH and security-critical, so it is
 * shipped OFF and must be flipped on deliberately (canary → broad) after human review.
 *
 * When false:
 *  - [MessageEncryptor.encryptOutgoing] returns the content unchanged.
 *  - [MessageEncryptor.decryptIncoming] returns the content unchanged.
 *  The send/receive path is byte-for-byte identical to the pre-wiring behavior.
 *
 * When true:
 *  - Eligible outgoing message bodies are encrypted via [com.muhabbet.shared.port.EncryptionPort]
 *    and wrapped in an [com.muhabbet.shared.port.E2EEnvelope].
 *  - Incoming envelopes are detected and decrypted before reaching the UI.
 *  - Anything not eligible / not an envelope passes through untouched (graceful fallback).
 *
 * This is intentionally a compile-time constant (KISS): there is no runtime UI to toggle it
 * and no requirement for per-build override yet. Flip the constant (or wire it to a build
 * config field) when promoting E2E past canary.
 */
object E2EConfig {
    /** Master switch for encrypt-on-send / decrypt-on-receive. Keep false until canary sign-off. */
    const val ENABLED: Boolean = false
}
