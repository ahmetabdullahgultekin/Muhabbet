package com.muhabbet.messaging.adapter.`in`.scheduler

import com.muhabbet.messaging.domain.service.MessageService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ScheduledMessageJob(
    private val messageService: MessageService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 60_000) // every minute
    fun deliverScheduledMessages() {
        try {
            messageService.deliverScheduledMessages()
        } catch (e: Exception) {
            log.warn("Error delivering scheduled messages: {}", e.message)
        }
    }
}
