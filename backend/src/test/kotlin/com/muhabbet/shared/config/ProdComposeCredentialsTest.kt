package com.muhabbet.shared.config

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Pins the credential contract of the deploying compose file (`docker-compose.prod.yml` at the
 * repository root — not the legacy one under `infra/`).
 *
 * Read as text on purpose: Compose's own `${VAR:?message}` syntax is what makes a missing variable
 * stop the deploy, and that is a property of the file, not of any parsed model of it.
 */
class ProdComposeCredentialsTest {

    private val compose: String = run {
        // Gradle runs backend tests with backend/ as the working directory.
        val file = listOf(File("../docker-compose.prod.yml"), File("docker-compose.prod.yml")).first { it.isFile }
        file.readText()
    }

    /** The file with comment lines removed, so prose explaining history cannot satisfy or fail a rule. */
    private val config: String = compose.lines().filterNot { it.trimStart().startsWith("#") }.joinToString("\n")

    @Test
    fun `should require the MinIO access and secret key when the backend is deployed`() {
        assertTrue(Regex("""MINIO_ACCESS_KEY: \$\{MINIO_ACCESS_KEY:\?""").containsMatchIn(config))
        assertTrue(Regex("""MINIO_SECRET_KEY: \$\{MINIO_SECRET_KEY:\?""").containsMatchIn(config))
    }

    @Test
    fun `should require the public media endpoint when the backend is deployed`() {
        assertTrue(Regex("""MINIO_PUBLIC_ENDPOINT: \$\{MINIO_PUBLIC_ENDPOINT:\?""").containsMatchIn(config))
        assertFalse(config.contains("nip.io"), "no host-specific default for the media endpoint")
    }

    @Test
    fun `should not fall back to the MinIO root identity when a variable is missing`() {
        assertFalse(config.contains("minioadmin"))
        assertFalse(config.contains("MINIO_ROOT_PASSWORD"), "the app must not be handed the root password")
        assertFalse(config.contains("MINIO_ROOT_USER"))
    }

    @Test
    fun `should not grant anonymous read on any bucket prefix when initialising storage`() {
        assertFalse(config.contains("mc anonymous set download"))
        assertFalse(config.contains("mc anonymous set public"))
    }

    @Test
    fun `should not interpolate a secret into an entrypoint when the init container is configured`() {
        val entrypoint = config.substringAfter("entrypoint:", "").substringBefore("networks:")
        assertFalse(Regex("""(?<!\$)\$\{""").containsMatchIn(entrypoint), "entrypoint must use \$\$VAR, not \${VAR}")
    }
}
