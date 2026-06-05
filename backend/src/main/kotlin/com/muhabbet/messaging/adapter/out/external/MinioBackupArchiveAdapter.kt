package com.muhabbet.messaging.adapter.out.external

import com.muhabbet.messaging.domain.port.out.BackupArchivePort
import com.muhabbet.shared.config.MediaProperties
import io.minio.GetPresignedObjectUrlArgs
import io.minio.Http
import io.minio.MinioClient
import io.minio.PutObjectArgs
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * MinIO-backed implementation of [BackupArchivePort].
 *
 * Reuses the same MinIO instance/bucket as media (object key prefix `backups/`) so no new
 * infrastructure is required. Mirrors [com.muhabbet.media.adapter.out.external.MinioMediaStorageAdapter]:
 * internal endpoint for SDK calls, public-endpoint rewrite on presigned URLs.
 */
@Component
class MinioBackupArchiveAdapter(
    private val mediaProperties: MediaProperties
) : BackupArchivePort {

    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var client: MinioClient

    @PostConstruct
    fun init() {
        // Bucket creation is handled by MinioMediaStorageAdapter (same bucket); here we only
        // build the client. Wrapped defensively so a missing MinIO at boot doesn't fail the
        // Spring context (see CLAUDE.md gotcha).
        try {
            client = MinioClient.builder()
                .endpoint(mediaProperties.minio.endpoint)
                .credentials(mediaProperties.minio.accessKey, mediaProperties.minio.secretKey)
                .build()
        } catch (e: Exception) {
            log.warn("MinIO client init failed for backups: {}", e.message)
        }
    }

    override fun upload(
        userId: UUID,
        backupId: UUID,
        bytes: ByteArray,
        contentType: String,
        expirySeconds: Int
    ): BackupArchivePort.StoredArchive {
        val key = "backups/$userId/$backupId.json"
        client.putObject(
            PutObjectArgs.builder()
                .bucket(mediaProperties.minio.bucket)
                .`object`(key)
                .stream(ByteArrayInputStream(bytes), bytes.size.toLong(), -1)
                .contentType(contentType)
                .build()
        )
        val url = client.getPresignedObjectUrl(
            GetPresignedObjectUrlArgs.builder()
                .method(Http.Method.GET)
                .bucket(mediaProperties.minio.bucket)
                .`object`(key)
                .expiry(expirySeconds, TimeUnit.SECONDS)
                .build()
        )
        val publicEndpoint = mediaProperties.minio.publicEndpoint
        val presigned = if (!publicEndpoint.isNullOrBlank()) {
            url.replace(mediaProperties.minio.endpoint, publicEndpoint)
        } else {
            url
        }
        log.info("Backup archive uploaded: key={}, bytes={}", key, bytes.size)
        return BackupArchivePort.StoredArchive(storageKey = key, presignedUrl = presigned)
    }
}
