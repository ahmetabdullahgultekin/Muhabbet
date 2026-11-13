package com.muhabbet.shared.config

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * Fail-closed startup guard for the object-storage credentials, in the spirit of [OtpModeGuard].
 *
 * `application.yml` carries no fallback for `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY`, but Spring's
 * configuration-properties binder does not fail on an unresolvable placeholder: it binds the literal
 * text `${MINIO_ACCESS_KEY}`, and the storage adapter only logs a warning when the bucket check then
 * fails. So "no default" alone would start a backend with broken media. Outside the dev and test
 * profiles this refuses to start when a key is missing, unresolved, or the MinIO root user's
 * well-known default.
 */
@Component
class StorageCredentialsGuard(
    private val mediaProperties: MediaProperties,
    private val environment: Environment
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun validate() {
        val profiles = environment.activeProfiles.toSet()
        if (profiles.any { it in RELAXED_PROFILES }) return

        val label = profiles.ifEmpty { setOf("default") }.joinToString(",")
        val minio = mediaProperties.minio
        check(isUsable(minio.accessKey)) {
            "muhabbet.media.minio.access-key is missing or a well-known default — refusing to start (profile=$label). " +
                "Set MINIO_ACCESS_KEY to the bucket-scoped user."
        }
        check(isUsable(minio.secretKey)) {
            "muhabbet.media.minio.secret-key is missing or a well-known default — refusing to start (profile=$label). " +
                "Set MINIO_SECRET_KEY."
        }
        log.info("Object-storage credentials validated (profile={}).", label)
    }

    private fun isUsable(value: String): Boolean =
        value.isNotBlank() && !value.startsWith(PLACEHOLDER_PREFIX) && value != WELL_KNOWN_DEFAULT

    private companion object {
        val RELAXED_PROFILES = setOf("dev", "test")
        const val PLACEHOLDER_PREFIX = "\${"
        const val WELL_KNOWN_DEFAULT = "minioadmin"
    }
}
