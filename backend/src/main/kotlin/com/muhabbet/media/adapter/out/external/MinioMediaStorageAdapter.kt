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
        // Force browsers to download (not render) the object via the presigned URL. Documents are
        // uploaded WhatsApp-style with arbitrary content types (no allowlist), so an attacker can
        // upload a `text/html` / `image/svg+xml` file; without this header it would render inline
        // from the media origin (stored-XSS / phishing surface — Finding B of the 2026-06-08 media
        // V&V review). MinIO honours the S3 `response-content-disposition` override query param and
        // signs it into the presigned URL, so the disposition cannot be stripped or altered without
        // invalidating the signature.
        //
        // We apply `attachment` UNIFORMLY (not just to documents) because the storage port only knows
        // the object key, not the media category, and the in-app image/audio loader fetches the raw
        // bytes — it never relies on inline browser rendering, so forcing download does not change
        // in-app media behaviour. This keeps the port minimal (no per-call category flag to thread).
        val url = client.getPresignedObjectUrl(buildPresignedGetArgs(key, expirySeconds))
        return rewriteToPublicEndpoint(url)
    }

    /**
     * Builds the presigned GET args, including the signed `attachment` disposition override. Extracted
     * so the disposition wiring is unit-testable without a live MinIO (presigning itself does a network
     * round-trip in the SDK).
     */
    internal fun buildPresignedGetArgs(key: String, expirySeconds: Int): GetPresignedObjectUrlArgs =
        GetPresignedObjectUrlArgs.builder()
            .method(Http.Method.GET)
            .bucket(mediaProperties.minio.bucket)
            .`object`(key)
            .expiry(expirySeconds, TimeUnit.SECONDS)
            .extraQueryParams(mapOf(RESPONSE_CONTENT_DISPOSITION to ATTACHMENT))
            .build()

    /**
     * Rewrites the internal MinIO endpoint to the public URL for external access. Only the scheme+host
     * prefix is replaced, so the signed `response-content-disposition` query param survives intact.
     */
    internal fun rewriteToPublicEndpoint(url: String): String {
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

    companion object {
        // S3 / MinIO response-header override query param: signed into the presigned URL so the
        // disposition cannot be altered without breaking the signature.
        private const val RESPONSE_CONTENT_DISPOSITION = "response-content-disposition"
        private const val ATTACHMENT = "attachment"
    }
}
