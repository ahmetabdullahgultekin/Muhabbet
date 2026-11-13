package com.muhabbet.shared.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.ClassPathResource

/**
 * Pins that the shared `application.yml` carries no usable credential of its own.
 *
 * A well-known development value (`minioadmin`, `muhabbet_dev`) used as a fallback in the base file
 * means a production host that forgets a variable starts anyway and talks to its storage with a
 * default anyone can look up. With no fallback, Spring cannot resolve the placeholder and the
 * context refuses to start — the failure is loud and happens at deploy time, not later.
 *
 * Development and test keep their convenience values, but only in their own profile files.
 */
class CredentialDefaultsTest {

    /** An environment holding only the given YAML files, with no OS variables or system properties. */
    private fun environmentOf(vararg files: String): StandardEnvironment {
        val env = StandardEnvironment()
        env.propertySources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)
        env.propertySources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME)
        val loader = YamlPropertySourceLoader()
        // Later files win, as a profile file wins over application.yml.
        files.forEach { file ->
            loader.load(file, ClassPathResource(file)).forEach { env.propertySources.addFirst(it) }
        }
        return env
    }

    @Test
    fun `should refuse to resolve MinIO credentials when only the prod profile is active and none are set`() {
        val env = environmentOf("application.yml", "application-prod.yml")

        CREDENTIAL_KEYS.forEach { key ->
            assertThrows<IllegalArgumentException>("$key must have no fallback outside dev/test") {
                env.getProperty(key)
            }
        }
    }

    @Test
    fun `should resolve local credentials when the dev profile is active`() {
        val env = environmentOf("application.yml", "application-dev.yml")

        CREDENTIAL_KEYS.forEach { key ->
            assertFalse(env.getProperty(key).isNullOrBlank(), "$key should have a dev value")
        }
    }

    @Test
    fun `should not name the MinIO root user anywhere in the base or prod configuration when loaded`() {
        listOf("application.yml", "application-prod.yml").forEach { file ->
            val text = ClassPathResource(file).inputStream.bufferedReader().use { it.readText() }
            assertFalse(text.contains("minioadmin"), "$file should not carry a MinIO default")
        }
    }

    @Test
    fun `should default MinIO credentials to empty when constructed without configuration`() {
        val minio = MediaProperties.MinioProperties()

        assertEquals("", minio.accessKey)
        assertEquals("", minio.secretKey)
        assertTrue(minio.publicEndpoint == null)
    }

    private companion object {
        val CREDENTIAL_KEYS = listOf(
            "muhabbet.media.minio.access-key",
            "muhabbet.media.minio.secret-key",
            "spring.datasource.password",
        )
    }
}
