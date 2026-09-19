package com.muhabbet.shared.config

import io.minio.BucketExistsArgs
import io.minio.GetPresignedObjectUrlArgs
import io.minio.Http
import io.minio.PutObjectArgs
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.Transferable
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.ByteArrayInputStream
import java.net.URI
import java.security.SecureRandom
import java.util.HexFormat
import java.util.concurrent.TimeUnit

/**
 * The storage client against a real Garage, the store production runs: a per-app key (not an
 * admin one), the `garage` signing region, path-style addressing, and presigned URLs that still
 * validate after the host rewrite, provided the proxy forwards the internal host.
 *
 * Skipped, not failed, where Docker is unavailable.
 */
@Testcontainers(disabledWithoutDocker = true)
class GarageObjectStorageIntegrationTest {

    companion object {
        private const val BUCKET = "muhabbet-media"
        private const val REGION = "garage"
        private const val S3_PORT = 3900
        private const val PUBLIC_ENDPOINT = "https://cdn.example.test"

        private val random = SecureRandom()
        private fun hex(bytes: Int): String = HexFormat.of().formatHex(ByteArray(bytes).also(random::nextBytes))

        // Generated per run: Garage wants a `GK` + 24 hex key id and a 64 hex secret.
        private val accessKey = "GK" + hex(12)
        private val secretKey = hex(32)

        private val config = """
            metadata_dir = "/var/lib/garage/meta"
            data_dir = "/var/lib/garage/data"
            db_engine = "sqlite"
            replication_factor = 1
            rpc_bind_addr = "[::]:3901"
            rpc_public_addr = "127.0.0.1:3901"
            rpc_secret = "${hex(32)}"

            [s3_api]
            s3_region = "$REGION"
            api_bind_addr = "[::]:$S3_PORT"
            root_domain = ".s3.garage.localhost"
        """.trimIndent()

        @Container
        @JvmStatic
        val garage: GenericContainer<*> = GenericContainer("dxflrs/garage:v2.4.1")
            .withCopyToContainer(Transferable.of(config), "/etc/garage.toml")
            .withExposedPorts(S3_PORT)
            // Any HTTP answer means the S3 listener is up; the image has no shell for a port probe.
            .waitingFor(Wait.forHttp("/").forPort(S3_PORT).forStatusCodeMatching { true })

        /** The single-node setup infra performs: layout, bucket, a key allowed on that bucket only. */
        @BeforeAll
        @JvmStatic
        fun provision() {
            val nodeId = garageCli("node", "id", "-q").substringBefore('@').trim()
            garageCli("layout", "assign", "-z", "dc1", "-c", "1G", nodeId)
            garageCli("layout", "apply", "--version", "1")
            garageCli("bucket", "create", BUCKET)
            garageCli("key", "import", "--yes", "-n", "muhabbet-app", accessKey, secretKey)
            garageCli("bucket", "allow", "--read", "--write", BUCKET, "--key", "muhabbet-app")
        }

        /** Retries while the fresh node settles (layout propagation takes a moment). */
        private fun garageCli(vararg args: String): String {
            var last = ""
            repeat(40) {
                val result = garage.execInContainer("/garage", *args)
                if (result.exitCode == 0) return result.stdout
                last = result.stderr + result.stdout
                Thread.sleep(250)
            }
            error("garage ${args.joinToString(" ")} failed: $last")
        }
    }

    private val internalEndpoint get() = "http://${garage.host}:${garage.getMappedPort(S3_PORT)}"

    private fun storage(region: String? = REGION) = MediaProperties.MinioProperties(
        endpoint = internalEndpoint,
        publicEndpoint = PUBLIC_ENDPOINT,
        accessKey = accessKey,
        secretKey = secretKey,
        bucket = BUCKET,
        region = region
    )

    private val http = OkHttpClient()

    private fun upload(key: String, bytes: ByteArray) {
        ObjectStorageClientFactory.create(storage()).putObject(
            PutObjectArgs.builder()
                .bucket(BUCKET)
                .`object`(key)
                .stream(ByteArrayInputStream(bytes), bytes.size.toLong(), -1)
                .contentType("image/jpeg")
                .build()
        )
    }

    private fun publicUrl(key: String): String {
        val storage = storage()
        val signed = ObjectStorageClientFactory.create(storage).getPresignedObjectUrl(
            GetPresignedObjectUrlArgs.builder()
                .method(Http.Method.GET)
                .bucket(BUCKET)
                .`object`(key)
                .expiry(300, TimeUnit.SECONDS)
                .build()
        )
        return ObjectStorageClientFactory.toPublicUrl(storage, signed)
    }

    /**
     * What the reverse proxy does with a request for the public URL: same path and query, sent to
     * the store, with [hostHeader] as the Host it forwards.
     */
    private fun fetchThroughProxy(publicUrl: String, hostHeader: String): Pair<Int, ByteArray> {
        val public = URI(publicUrl)
        val upstream = internalEndpoint + public.rawPath + "?" + public.rawQuery
        val request = Request.Builder().url(upstream).header("Host", hostHeader).build()
        return http.newCall(request).execute().use { it.code to it.body.bytes() }
    }

    @Test
    fun bucketExists_WhenAppKeyAndRegionConfigured_ShouldFindTheProvisionedBucket() {
        val client = ObjectStorageClientFactory.create(storage())

        assertTrue(client.bucketExists(BucketExistsArgs.builder().bucket(BUCKET).build()))
    }

    @Test
    fun bucketExists_WhenRegionLeftBlank_ShouldDiscoverGaragesRegion() {
        val client = ObjectStorageClientFactory.create(storage(region = null))

        assertTrue(client.bucketExists(BucketExistsArgs.builder().bucket(BUCKET).build()))
    }

    @Test
    fun putObject_WhenSignedForAnotherRegion_ShouldBeRejected() {
        // Why MINIO_REGION exists: SDKs default to us-east-1, and Garage refuses it.
        val client = ObjectStorageClientFactory.create(storage(region = "us-east-1"))

        assertThrows<Exception> {
            client.putObject(
                PutObjectArgs.builder()
                    .bucket(BUCKET)
                    .`object`("wrong-region.txt")
                    .stream(ByteArrayInputStream(byteArrayOf(1)), 1, -1)
                    .build()
            )
        }
    }

    @Test
    fun presignedUrl_WhenProxyForwardsTheInternalHost_ShouldServeTheObject() {
        val bytes = hex(64).toByteArray()
        upload("images/proxy-ok.jpg", bytes)
        val url = publicUrl("images/proxy-ok.jpg")

        val (status, body) = fetchThroughProxy(url, hostHeader = URI(internalEndpoint).rawAuthority)

        assertTrue(url.startsWith("$PUBLIC_ENDPOINT/$BUCKET/images/proxy-ok.jpg?"), url)
        assertEquals(200, status)
        assertArrayEquals(bytes, body)
    }

    @Test
    fun presignedUrl_WhenProxyForwardsThePublicHost_ShouldFailTheSignature() {
        // The reason the public media route must not pass the public Host header through.
        upload("images/proxy-wrong-host.jpg", byteArrayOf(1, 2, 3))
        val url = publicUrl("images/proxy-wrong-host.jpg")

        val (status, _) = fetchThroughProxy(url, hostHeader = URI(PUBLIC_ENDPOINT).rawAuthority)

        assertEquals(403, status)
    }
}
