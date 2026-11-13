package com.muhabbet.shared.security

import io.jsonwebtoken.JwsHeader
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.LocatorAdapter
import io.jsonwebtoken.security.Keys
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.security.Key
import java.security.SecureRandom
import java.util.Base64
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

/**
 * What a signature-valid access token claims. **Not** proof that the session is still alive: the
 * REST filter checks [tokenVersion] against the device's current epoch and reads the admin flag from
 * the database ([LiveSessionLookup]) before it trusts any of this.
 *
 * @property isAdmin the admin flag as of the *request*, filled in by the filter from the database.
 *   Tokens no longer carry it (#923): a flag baked into a token outlives a demotion.
 */
data class JwtClaims(
    val userId: UUID,
    val deviceId: UUID,
    val isAdmin: Boolean = false,
    val tokenVersion: Int = 0
)

@Component
class JwtProvider(
    private val jwtProperties: JwtProperties,
    private val environment: Environment
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val secureRandom = SecureRandom()

    // Lazy so the startup guard (validateSecret) runs and reports a clear message BEFORE JJWT's own
    // WeakKeyException would fire for a short secret. Built on first sign/verify, after boot validation.
    private val signingKey: SecretKey by lazy { hmacKey(jwtProperties.secret) }

    /**
     * Every key that may verify, by `kid`: the current one plus the retired ones (#923). Signing
     * uses only [signingKey]; a retired key can verify a token for its remaining lifetime and can
     * never mint a new one.
     */
    private val verificationKeys: Map<String, SecretKey> by lazy {
        buildMap {
            jwtProperties.previousKeys.forEach { put(it.id, hmacKey(it.secret)) }
            put(jwtProperties.keyId, signingKey)
        }
    }

    /**
     * Picks the verification key by the token's `kid`. A token with no `kid`, or one this server
     * does not know, gets no key — JJWT then rejects it — rather than being tried against every key.
     */
    private val keyLocator = object : LocatorAdapter<Key>() {
        override fun locate(header: JwsHeader): Key? = header.keyId?.let { verificationKeys[it] }
    }

    companion object {
        // The literal default declared in application.yml. If the live secret equals this, JWT_SECRET
        // was never overridden — fail closed so a forgeable, world-known signing key never boots.
        private const val DEV_DEFAULT_SECRET =
            "dev-secret-change-in-production-min-256-bits-long-key-here"

        // HS256 needs ≥256 bits of key material. Anything shorter is brute-forceable.
        private const val MIN_SECRET_BYTES = 32

        /** Access-token epoch claim; compared with `devices.token_version` on every REST request. */
        const val TOKEN_VERSION_CLAIM = "tv"
        const val DEVICE_ID_CLAIM = "deviceId"

        private fun hmacKey(secret: String): SecretKey = Keys.hmacShaKeyFor(secret.toByteArray(Charsets.UTF_8))
    }

    /**
     * Fail-closed startup guard for the JWT key ring. Runs on every profile.
     * Boot is aborted (IllegalStateException) when any key — current or retired —
     *  - is still the world-known dev default (in application.yml), or
     *  - has fewer than 32 bytes of entropy (too weak for HS256);
     * or when two keys share a `kid`, which would make "which key verifies this token" ambiguous.
     * This is the single guard that prevents shipping a forgeable token signer to production.
     */
    @PostConstruct
    fun validateSecret() {
        val activeProfiles = environment.activeProfiles.toList()
        val profileLabel = if (activeProfiles.isEmpty()) "default" else activeProfiles.joinToString(",")

        requireStrong(jwtProperties.secret, "JWT_SECRET", profileLabel)
        jwtProperties.previousKeys.forEach { requireStrong(it.secret, "previous JWT key '${it.id}'", profileLabel) }

        val ids = jwtProperties.previousKeys.map { it.id } + jwtProperties.keyId
        check(ids.none { it.isBlank() }) { "A JWT key id is blank — refusing to start (profile=$profileLabel)." }
        check(ids.toSet().size == ids.size) {
            "JWT key ids must be unique; got $ids — refusing to start (profile=$profileLabel)."
        }
        log.info("JWT key ring validated: current kid={}, retired={} (profile={}).",
            jwtProperties.keyId, jwtProperties.previousKeys.size, profileLabel)
    }

    private fun requireStrong(secret: String, label: String, profileLabel: String) {
        check(secret != DEV_DEFAULT_SECRET) {
            "$label is still the dev default — refusing to start (profile=$profileLabel). " +
                "Set it to a unique value with at least $MIN_SECRET_BYTES bytes of entropy."
        }
        check(secret.toByteArray(Charsets.UTF_8).size >= MIN_SECRET_BYTES) {
            "$label is too short for HS256 — refusing to start (profile=$profileLabel). " +
                "It must be at least $MIN_SECRET_BYTES bytes."
        }
    }

    val accessTokenExpirySeconds: Long = jwtProperties.accessTokenExpiry

    /**
     * Mints an access token for one device session.
     *
     * Carries `kid` (which key signed it), `aud` (who it is for), `jti` (a unique id, so one token
     * can be named in a log or a future deny-list) and [tokenVersion] — the device's epoch at mint
     * time, which is how a logout or a revoke kills it before it expires (#923). It deliberately
     * carries **no** admin flag: privilege is read from the database per request.
     */
    fun generateAccessToken(userId: UUID, deviceId: UUID, tokenVersion: Int): String {
        val now = Date()
        val expiry = Date(now.time + jwtProperties.accessTokenExpiry * 1000)

        return Jwts.builder()
            .header().keyId(jwtProperties.keyId).and()
            .id(UUID.randomUUID().toString())
            .subject(userId.toString())
            .audience().add(jwtProperties.audience).and()
            .claim(DEVICE_ID_CLAIM, deviceId.toString())
            .claim(TOKEN_VERSION_CLAIM, tokenVersion)
            .issuedAt(now)
            .expiration(expiry)
            .issuer(jwtProperties.issuer)
            .signWith(signingKey)
            .compact()
    }

    fun generateRefreshToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * Verifies signature (by `kid`), issuer, audience and expiry, and returns the claims — or null
     * when any of it fails or a required claim is missing. Signature-valid is not the same as
     * session-alive; see [JwtClaims].
     */
    fun validateToken(token: String): JwtClaims? {
        return try {
            val claims = Jwts.parser()
                .keyLocator(keyLocator)
                .requireIssuer(jwtProperties.issuer)
                .requireAudience(jwtProperties.audience)
                .build()
                .parseSignedClaims(token)
                .payload

            val tokenVersion = (claims[TOKEN_VERSION_CLAIM] as? Number)?.toInt() ?: return null
            JwtClaims(
                userId = UUID.fromString(claims.subject),
                deviceId = UUID.fromString(claims[DEVICE_ID_CLAIM] as String),
                tokenVersion = tokenVersion
            )
        } catch (ex: Exception) {
            log.debug("JWT validation failed: {}", ex.message)
            null
        }
    }
}
