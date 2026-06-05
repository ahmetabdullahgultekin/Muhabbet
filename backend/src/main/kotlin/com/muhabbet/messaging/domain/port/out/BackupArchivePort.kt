package com.muhabbet.messaging.domain.port.out

/**
 * Out-port for persisting a serialized message-backup archive to object storage.
 *
 * The domain ([com.muhabbet.messaging.domain.service.BackupService]) builds the archive bytes
 * and hands them to this port; the adapter (MinIO) is responsible for the actual upload and for
 * minting a time-limited download URL. Kept deliberately infrastructure-free (no streams, no S3
 * types) so the domain stays framework-agnostic per the hexagonal rules.
 */
interface BackupArchivePort {

    /** Result of uploading an archive: the storage key and a presigned download URL. */
    data class StoredArchive(
        val storageKey: String,
        val presignedUrl: String
    )

    /**
     * Upload [bytes] under a key derived from [userId]/[backupId] and return a presigned URL
     * valid for [expirySeconds].
     */
    fun upload(
        userId: java.util.UUID,
        backupId: java.util.UUID,
        bytes: ByteArray,
        contentType: String = "application/json",
        expirySeconds: Int = 7 * 24 * 3600
    ): StoredArchive
}
