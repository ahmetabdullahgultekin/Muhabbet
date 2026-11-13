package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.MessageBackup
import java.util.UUID

interface BackupRepository {
    fun save(backup: MessageBackup): MessageBackup
    fun findById(id: UUID): MessageBackup?
    fun findLatestByUserId(userId: UUID): MessageBackup?
    fun findByUserId(userId: UUID): List<MessageBackup>

    /**
     * Records how [finished] ended — status, URL, counts, times, error — **only if the stored row is
     * still PENDING**, returning whether it did. A backup ends once (#897).
     */
    fun finish(finished: MessageBackup): Boolean
}
