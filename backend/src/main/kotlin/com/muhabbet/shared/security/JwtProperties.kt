package com.muhabbet.shared.security

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * @property secret the **current** HS256 signing key. Every new token is signed with it.
 * @property keyId the `kid` header stamped on every token signed with [secret] (#923). Change it
 *   whenever [secret] changes, and move the old pair into [previousKeys].
 * @property previousKeys retired keys that still **verify** but never sign. A token minted with one
 *   keeps working until it expires (15 minutes for access tokens), so rotating [secret] no longer
 *   logs every user out at once. Remove an entry once the overlap window has passed.
 *   Bound from the environment as `MUHABBET_JWT_PREVIOUSKEYS_0_ID` / `..._0_SECRET`.
 * @property audience the `aud` claim of tokens issued to the app, required on verification.
 */
@ConfigurationProperties(prefix = "muhabbet.jwt")
data class JwtProperties(
    val secret: String,
    val accessTokenExpiry: Long = 900,
    val refreshTokenExpiry: Long = 2592000,
    val issuer: String = "muhabbet",
    val keyId: String = "primary",
    val previousKeys: List<JwtVerificationKey> = emptyList(),
    val audience: String = "muhabbet-app"
)

/** A retired signing key kept for verification only; see [JwtProperties.previousKeys]. */
data class JwtVerificationKey(
    val id: String,
    val secret: String
)
