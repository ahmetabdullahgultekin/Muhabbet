package com.muhabbet.media.adapter.out.external

import com.muhabbet.media.domain.port.out.MediaStoragePort
import com.muhabbet.shared.config.MediaProperties
import io.minio.BucketExistsArgs
import io.minio.GetObjectArgs
import io.minio.GetPresignedObjectUrlArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import io.minio.Http
import io.minio.errors.ErrorResponseException
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.InputStream
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

    override fun deleteObject(key: String) {
        client.removeObject(
            RemoveObjectArgs.builder()
                .bucket(mediaProperties.minio.bucket)
                .`object`(key)
                .build()
        )
    }

    /**
     * Reads the whole object into memory, because the one caller (#541) hands the bytes back inside
     * a single JSON response and cannot stream. Bounded by the upload size limit
     * `ValidationRules.MAX_IMAGE_SIZE_BYTES`, which is what put the object here in the first place.
     *
     * A missing object is null rather than an exception: the burn path may be retried after a
     * partial failure, and "already gone" is the outcome it wanted.
     */
    override fun getObject(key: String): ByteArray? = try {
        client.getObject(
            GetObjectArgs.builder()
                .bucket(mediaProperties.minio.bucket)
                .`object`(key)
                .build()
        ).use { it.readBytes() }
    } catch (e: ErrorResponseException) {
        log.warn("Media object not readable: key={}, code={}", key, e.errorResponse()?.code())
        null
    }
}
