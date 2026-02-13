package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataMessageRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class DisappearingMessageCleanupJob(
    private val messageRepo: SpringDataMessageRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 60_000) // every minute
    @Transactional
    fun cleanExpiredMessages() {
        val now = Instant.now()
        val expired = messageRepo.findExpiredMessages(now)
        if (expired.isNotEmpty()) {
            expired.forEach { it.isDeleted = true; it.deletedAt = now }
            messageRepo.saveAll(expired)
            log.info("Cleaned {} expired disappearing messages", expired.size)
        }
    }
}
