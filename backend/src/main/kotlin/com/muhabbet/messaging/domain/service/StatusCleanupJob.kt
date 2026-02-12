package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataStatusRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class StatusCleanupJob(
    private val statusRepo: SpringDataStatusRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 3600_000) // every hour
    @Transactional
    fun cleanExpiredStatuses() {
        val now = Instant.now()
        statusRepo.deleteExpired(now)
        log.debug("Cleaned expired statuses")
    }
}
