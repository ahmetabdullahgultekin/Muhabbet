package com.muhabbet.messaging.domain.model

import java.time.Instant
import java.util.UUID

/**
 * Where a backup is in its life (#897). PENDING is the build in progress; COMPLETED and FAILED are
 * what it ends as. EXPIRED is never stored: a COMPLETED backup whose presigned URL has run out
 * *reads* as EXPIRED through [MessageBackup.effectiveStatus], so nothing has to sweep the table to
 * keep the answer true.
 */
enum class BackupStatus {
    PENDING, COMPLETED, FAILED, EXPIRED
}

/**
 * A message backup. Moved out of the port file it used to share with `BackupRepository`, and its
 * status is no longer a free string: the service set `"COMPLETED"`/`"FAILED"` by literal, so a
 * typo was a valid state and a COMPLETED backup with no URL was representable.
 */
data class MessageBackup(
    val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val status: BackupStatus = BackupStatus.PENDING,
    val backupUrl: String? = null,
    val fileSizeBytes: Long? = null,
    val messageCount: Int? = null,
    val conversationCount: Int? = null,
    val startedAt: Instant = Instant.now(),
    val completedAt: Instant? = null,
    val expiresAt: Instant? = null,
    val errorMessage: String? = null
) {
    /** The status as of [now]; a completed backup past its expiry reads EXPIRED. */
    fun effectiveStatus(now: Instant): BackupStatus =
        if (status == BackupStatus.COMPLETED && expiresAt != null && !expiresAt.isAfter(now)) BackupStatus.EXPIRED else status

    /** PENDING -> COMPLETED with everything a COMPLETED backup must have. */
    fun complete(
        backupUrl: String,
        fileSizeBytes: Long,
        messageCount: Int,
        conversationCount: Int,
        now: Instant,
        validFor: Long
    ): BackupTransition = fromPending {
        copy(
            status = BackupStatus.COMPLETED,
            backupUrl = backupUrl,
            fileSizeBytes = fileSizeBytes,
            messageCount = messageCount,
            conversationCount = conversationCount,
            completedAt = now,
            expiresAt = now.plusSeconds(validFor)
        )
    }

    /** PENDING -> FAILED, recording [reason] (an error kind, never user content). */
    fun fail(reason: String, now: Instant): BackupTransition = fromPending {
        copy(status = BackupStatus.FAILED, completedAt = now, errorMessage = reason)
    }

    private inline fun fromPending(next: () -> MessageBackup): BackupTransition =
        if (status == BackupStatus.PENDING) BackupTransition.Moved(next()) else BackupTransition.Rejected(status)

    companion object {
        fun request(userId: UUID, now: Instant): MessageBackup = MessageBackup(userId = userId, startedAt = now)
    }
}

sealed interface BackupTransition {
    data class Moved(val backup: MessageBackup) : BackupTransition
    data class Rejected(val from: BackupStatus) : BackupTransition
}
