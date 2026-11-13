package com.muhabbet.shared.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.mock.env.MockEnvironment

class StorageCredentialsGuardTest {

    private fun guard(access: String, secret: String, vararg profiles: String) = StorageCredentialsGuard(
        MediaProperties(minio = MediaProperties.MinioProperties(accessKey = access, secretKey = secret)),
        MockEnvironment().apply { setActiveProfiles(*profiles) }
    )

    @Test
    fun `should refuse to start when the access key is an unresolved placeholder outside dev and test`() {
        assertThrows<IllegalStateException> { guard("\${MINIO_ACCESS_KEY}", "s3cr3t-value", "prod").validate() }
    }

    @Test
    fun `should refuse to start when the secret key is blank outside dev and test`() {
        assertThrows<IllegalStateException> { guard("muhabbet-app", "", "prod").validate() }
    }

    @Test
    fun `should refuse to start when the root user default is configured with no profile`() {
        assertThrows<IllegalStateException> { guard("minioadmin", "minioadmin").validate() }
    }

    @Test
    fun `should start when explicit scoped credentials are configured in prod`() {
        assertDoesNotThrow { guard("muhabbet-app", "a-long-random-value", "prod").validate() }
    }

    @Test
    fun `should not check credentials when the dev or test profile is active`() {
        assertDoesNotThrow { guard("minioadmin", "minioadmin", "dev").validate() }
        assertDoesNotThrow { guard("", "", "test").validate() }
    }
}
