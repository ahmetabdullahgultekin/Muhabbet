package com.muhabbet.messaging.adapter.out.external

import com.muhabbet.messaging.domain.port.out.BackupArchivePort
import com.muhabbet.shared.config.MediaProperties
import com.muhabbet.shared.config.ObjectStorageClientFactory
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
        // Same bucket as media, provisioned by infra. Building the client does not connect, so an
        // unreachable store cannot fail the Spring context; missing configuration does, on purpose.
        client = ObjectStorageClientFactory.create(mediaProperties.minio)
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
        val presigned = ObjectStorageClientFactory.toPublicUrl(mediaProperties.minio, url)
        log.info("Backup archive uploaded: key={}, bytes={}", key, bytes.size)
        return BackupArchivePort.StoredArchive(storageKey = key, presignedUrl = presigned)
    }
}
