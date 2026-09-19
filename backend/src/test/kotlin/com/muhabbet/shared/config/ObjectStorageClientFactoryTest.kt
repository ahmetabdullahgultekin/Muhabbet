package com.muhabbet.shared.config

import io.minio.GetPresignedObjectUrlArgs
import io.minio.Http
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.MutablePropertySources
import org.springframework.core.env.PropertySourcesPropertyResolver
import org.springframework.core.io.ClassPathResource
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Configuration of the S3 client and the presigned-URL host rewrite. Nothing here opens a
 * connection: with the region stated, presigning is a local computation.
 */
class ObjectStorageClientFactoryTest {

    private val garage = MediaProperties.MinioProperties(
        endpoint = "http://garage:3900",
        publicEndpoint = "https://cdn.example.test",
        accessKey = "GK0123456789abcdef01234567",
        secretKey = "test-secret",
        bucket = "muhabbet-media",
        region = "garage"
    )

    private fun presign(storage: MediaProperties.MinioProperties, key: String = "images/a b.jpg"): String =
        ObjectStorageClientFactory.create(storage).getPresignedObjectUrl(
            GetPresignedObjectUrlArgs.builder()
                .method(Http.Method.GET)
                .bucket(storage.bucket)
                .`object`(key)
                .expiry(600, TimeUnit.SECONDS)
                .build()
        )

    @Test
    fun bind_WhenStorageVariablesSet_ShouldReadRegionAndPathStyle() {
        val source = MapConfigurationPropertySource(
            mapOf(
                "muhabbet.media.minio.endpoint" to "http://garage:3900",
                "muhabbet.media.minio.public-endpoint" to "https://cdn.example.test",
                "muhabbet.media.minio.access-key" to "key",
                "muhabbet.media.minio.secret-key" to "secret",
                "muhabbet.media.minio.bucket" to "media",
                "muhabbet.media.minio.region" to "garage",
                "muhabbet.media.minio.path-style" to "false"
            )
        )

        val storage = Binder(source).bind("muhabbet.media", MediaProperties::class.java).get().minio

        assertAll(
            { assertEquals("http://garage:3900", storage.endpoint) },
            { assertEquals("https://cdn.example.test", storage.publicEndpoint) },
            { assertEquals("media", storage.bucket) },
            { assertEquals("garage", storage.region) },
            { assertEquals(false, storage.pathStyle) }
        )
    }

    @Test
    fun bind_WhenOptionalVariablesAbsent_ShouldDefaultToDiscoveredRegionAndPathStyle() {
        val storage = Binder(MapConfigurationPropertySource(mapOf("muhabbet.media.minio.bucket" to "media")))
            .bind("muhabbet.media", MediaProperties::class.java).get().minio

        assertAll(
            { assertEquals(null, storage.region) },
            { assertTrue(storage.pathStyle) }
        )
    }

    @Test
    fun applicationYml_WhenNoEnvironment_ShouldCarryNoEndpointOrCredentialDefaults() {
        // A credential default in the base config reaches every environment that forgets to set
        // one. Only the dev profile may name a local credential.
        val sources = MutablePropertySources().apply {
            YamlPropertySourceLoader().load("application", ClassPathResource("application.yml")).forEach(::addLast)
        }
        val resolver = PropertySourcesPropertyResolver(sources)

        assertAll(
            { assertEquals("", resolver.getProperty("muhabbet.media.minio.endpoint")) },
            { assertEquals("", resolver.getProperty("muhabbet.media.minio.access-key")) },
            { assertEquals("", resolver.getProperty("muhabbet.media.minio.secret-key")) },
            { assertEquals("", resolver.getProperty("muhabbet.media.minio.region")) },
            { assertEquals("true", resolver.getProperty("muhabbet.media.minio.path-style")) }
        )
    }

    @Test
    fun create_WhenAccessKeyEmpty_ShouldFailNamingTheVariable() {
        val error = assertThrows<IllegalStateException> {
            ObjectStorageClientFactory.create(garage.copy(accessKey = " "))
        }

        assertTrue(error.message.orEmpty().contains("MINIO_ACCESS_KEY"))
    }

    @Test
    fun create_WhenSecretKeyOrEndpointEmpty_ShouldFail() {
        assertAll(
            { assertThrows<IllegalStateException> { ObjectStorageClientFactory.create(garage.copy(secretKey = "")) } },
            { assertThrows<IllegalStateException> { ObjectStorageClientFactory.create(garage.copy(endpoint = "")) } }
        )
    }

    @Test
    fun presign_WhenRegionConfigured_ShouldSignForThatRegionAgainstTheInternalHost() {
        val url = URI(presign(garage))

        assertAll(
            { assertEquals("garage:3900", url.rawAuthority) },
            { assertTrue(url.rawPath.startsWith("/muhabbet-media/")) },
            { assertTrue(url.rawQuery.contains("%2Fgarage%2Fs3%2Faws4_request"), url.rawQuery) },
            { assertTrue(url.rawQuery.contains("X-Amz-SignedHeaders=host")) }
        )
    }

    @Test
    fun presign_WhenPathStyleDisabled_ShouldPutTheBucketInTheHost() {
        val url = URI(presign(garage.copy(pathStyle = false)))

        assertEquals("muhabbet-media.garage:3900", url.rawAuthority)
    }

    @Test
    fun toPublicUrl_WhenPublicEndpointSet_ShouldReplaceOnlySchemeAndHost() {
        val signed = presign(garage)

        val public = ObjectStorageClientFactory.toPublicUrl(garage, signed)

        // Path and query carry the signature; one changed byte and the store refuses the URL.
        val signedUri = URI(signed)
        val publicUri = URI(public)
        assertAll(
            { assertEquals("https", publicUri.scheme) },
            { assertEquals("cdn.example.test", publicUri.rawAuthority) },
            { assertEquals(signedUri.rawPath, publicUri.rawPath) },
            { assertEquals(signedUri.rawQuery, publicUri.rawQuery) }
        )
    }

    @Test
    fun toPublicUrl_WhenEndpointsHaveTrailingSlashes_ShouldStillRewrite() {
        // The old rewrite was a string replace of the configured endpoint, so `http://garage:3900/`
        // matched nothing and every client got an address that only resolves inside Docker.
        val storage = garage.copy(endpoint = "http://garage:3900/", publicEndpoint = "https://cdn.example.test/")

        val public = ObjectStorageClientFactory.toPublicUrl(storage, presign(storage))

        assertTrue(public.startsWith("https://cdn.example.test/muhabbet-media/images/"), public)
    }

    @Test
    fun toPublicUrl_WhenNoPublicEndpoint_ShouldReturnTheSignedUrl() {
        val storage = garage.copy(publicEndpoint = " ")
        val signed = presign(storage)

        assertEquals(signed, ObjectStorageClientFactory.toPublicUrl(storage, signed))
    }
}
