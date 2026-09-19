package com.muhabbet.media.adapter.out.external

import com.muhabbet.media.domain.port.out.MediaStoragePort
import com.muhabbet.shared.config.MediaProperties
import com.muhabbet.shared.config.ObjectStorageClientFactory
import io.minio.BucketExistsArgs
import io.minio.GetObjectArgs
import io.minio.GetPresignedObjectUrlArgs
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

    /**
     * Builds the client and checks, once, that the bucket is there. It never creates one: the
     * bucket and the key scoped to it are provisioned by infra, and the app key is deliberately not
     * allowed to create buckets. Idempotent and read-only, so it is safe on every start. A missing
     * or unreachable bucket is logged and left to [MinioHealthIndicator], which keeps reporting it.
     */
    @PostConstruct
    fun init() {
        client = ObjectStorageClientFactory.create(mediaProperties.minio)

        if (!mediaProperties.minio.publicEndpoint.isNullOrBlank()) {
            log.info("Object storage public endpoint configured: {}", mediaProperties.minio.publicEndpoint)
        }

        val bucket = mediaProperties.minio.bucket
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                log.error(
                    "Object storage bucket {} does not exist at {}: media uploads fail until it is provisioned",
                    bucket, mediaProperties.minio.endpoint
                )
            }
        } catch (e: Exception) {
            log.warn(
                "Object storage not reachable at startup ({}): media uploads unavailable until connected: {}",
                mediaProperties.minio.endpoint, e.message
            )
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
        return ObjectStorageClientFactory.toPublicUrl(mediaProperties.minio, url)
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
