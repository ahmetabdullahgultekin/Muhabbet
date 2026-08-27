package com.muhabbet.messaging.adapter.`in`.scheduler

import com.muhabbet.messaging.domain.port.`in`.ExpireDisappearingMessagesUseCase
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Wakes the disappearing-message sweep once a minute. Nothing else.
 *
 * It used to hold the whole feature: it injected `SpringDataMessageRepository` — one adapter
 * reaching straight into another, past the domain — flipped `isDeleted` on every due row and
 * stopped there. That shape is why #513 existed. Deleting rows is all a JPA repository can do, so
 * "and tell the members" had nowhere to live, and the clients found out only by reloading.
 *
 * The work moved to `DisappearingMessageService`, where the out-ports for broadcasting are. What is
 * left here is the schedule, which is genuinely an inbound adapter's concern.
 */
@Component
class DisappearingMessageCleanupJob(
    private val expireDisappearingMessages: ExpireDisappearingMessagesUseCase
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 60_000) // every minute
    fun cleanExpiredMessages() {
        try {
            expireDisappearingMessages.expireDueMessages()
        } catch (e: Exception) {
            // ERROR with the exception rather than WARN with its message: a failed sweep means
            // messages the user was promised would vanish are still readable, which is the kind of
            // failure that needs a stack trace attached to it.
            //
            // The catch stays for the reason `ScheduledMessageJob` keeps its own — not to protect
            // the cadence, which Spring maintains anyway, but so the failure is logged with this
            // job's context instead of the framework's generic error handler.
            log.error("Disappearing message sweep failed; messages due now are still readable", e)
        }
    }
}
