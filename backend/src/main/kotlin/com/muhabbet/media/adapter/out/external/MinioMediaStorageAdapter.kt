package com.muhabbet.media.adapter.out.external

import com.muhabbet.media.domain.port.out.MediaStoragePort
import com.muhabbet.shared.config.MediaProperties
import io.minio.BucketExistsArgs
import io.minio.GetPresignedObjectUrlArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import io.minio.Http
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.InputStream
import java.net.URI
import java.util.concurrent.TimeUnit

@Component
class MinioMediaStorageAdapter(
    private val mediaProperties: MediaProperties
) : MediaStoragePort {

    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var client: MinioClient

    @PostConstruct
    fun init() {
        client = MinioClient.builder()
            .endpoint(mediaProperties.minio.endpoint)
            .credentials(mediaProperties.minio.accessKey, mediaProperties.minio.secretKey)
            .build()

        if (!mediaProperties.minio.publicEndpoint.isNullOrBlank()) {
            log.info("MinIO public endpoint configured: {}", mediaProperties.minio.publicEndpoint)
        }

        try {
            val bucket = mediaProperties.minio.bucket
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build())
                log.info("Created MinIO bucket: {}", bucket)
            }
        } catch (e: Exception) {
            log.warn("MinIO not reachable at startup ({}): media uploads unavailable until connected", mediaProperties.minio.endpoint)
        }
    }

    override fun putObject(key: String, inputStream: InputStream, contentType: String, sizeBytes: Long) {
        client.putObject(
            PutObjectArgs.builder()
                .bucket(mediaProperties.minio.bucket)
                .`object`(key)
                .stream(inputStream, sizeBytes, -1)
                .contentType(contentType)
                .build()
        )
    }

    override fun getPresignedUrl(key: String, expirySeconds: Int): String {
        val url = client.getPresignedObjectUrl(
            GetPresignedObjectUrlArgs.builder()
                .method(Http.Method.GET)
                .bucket(mediaProperties.minio.bucket)
                .`object`(key)
                .expiry(expirySeconds, TimeUnit.SECONDS)
                .build()
        )
        // Rewrite internal MinIO endpoint to public URL for external access
        val publicEndpoint = mediaProperties.minio.publicEndpoint
        return if (!publicEndpoint.isNullOrBlank()) {
            url.replace(mediaProperties.minio.endpoint, publicEndpoint)
        } else {
            url
        }
    }

    /**
     * Reverses [getPresignedUrl]: the origin must be one we publish under, the path must start with
     * our bucket, and what is left is the object key.
     *
     * Both endpoints are accepted. `publicEndpoint` is what clients see in production, but it is
     * optional — where it is unset (local dev, tests) [getPresignedUrl] returns the internal
     * endpoint unrewritten, and a URL minted by this very method would otherwise fail to resolve.
     *
     * The comparison is on scheme, host and effective port, not `startsWith(endpoint)`. A prefix
     * test would accept `https://cdn-muhabbet.example.com.attacker.test/…`, which is exactly the
     * class of bypass a host check exists to prevent. The query string is ignored: the signature on
     * a presigned URL expires, so it cannot be part of the identity of the object.
     */
    override fun resolveObjectKey(url: String): String? {
        val uri = try {
            URI(url.trim())
        } catch (e: Exception) {
            return null
        }

        val endpoints = listOfNotNull(
            mediaProperties.minio.publicEndpoint?.takeIf { it.isNotBlank() },
            mediaProperties.minio.endpoint.takeIf { it.isNotBlank() }
        )
        if (endpoints.none { sameOrigin(uri, it) }) return null

        val path = uri.path?.removePrefix("/") ?: return null
        val bucketPrefix = mediaProperties.minio.bucket + "/"
        if (!path.startsWith(bucketPrefix)) return null

        return path.removePrefix(bucketPrefix).takeIf { it.isNotBlank() }
    }

    private fun sameOrigin(uri: URI, endpoint: String): Boolean {
        val other = try {
            URI(endpoint)
        } catch (e: Exception) {
            return false
        }
        val host = uri.host ?: return false
        val otherHost = other.host ?: return false
        return host.equals(otherHost, ignoreCase = true) &&
            uri.scheme.orEmpty().equals(other.scheme.orEmpty(), ignoreCase = true) &&
            effectivePort(uri) == effectivePort(other)
    }

    /** An absent port means the scheme's default, so `https://h` and `https://h:443` are one origin. */
    private fun effectivePort(uri: URI): Int = when {
        uri.port != -1 -> uri.port
        uri.scheme.equals("https", ignoreCase = true) -> 443
        else -> 80
    }

    override fun deleteObject(key: String) {
        client.removeObject(
            RemoveObjectArgs.builder()
                .bucket(mediaProperties.minio.bucket)
                .`object`(key)
                .build()
        )
    }
}
