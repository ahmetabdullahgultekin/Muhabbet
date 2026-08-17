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

    /**
     * Delivery is transactional in [MessageService] (#556). This job's only remaining job is to say
     * out loud what happened, which is what it was not doing: a failure went to `log.warn` carrying
     * `e.message` and no stack trace, so the reason a scheduled message never arrived was a single
     * line with no cause in it — and a message that never arrives looks exactly like a message
     * nobody sent.
     *
     * The catch stays. Spring keeps scheduling after an unhandled exception, so it is not there to
     * protect the cadence; it is there so the failure is logged with this job's own context rather
     * than by the framework's error handler.
     */
    @Scheduled(fixedDelay = 60_000) // every minute
    fun deliverScheduledMessages() {
        try {
            val delivered = messageService.deliverScheduledMessages()
            // Only when something happened. At one run a minute, logging every empty sweep would
            // bury the runs that matter under 1,440 lines a day saying nothing.
            if (delivered > 0) {
                log.info("Scheduled delivery run complete: {} message(s) delivered", delivered)
            }
        } catch (e: Exception) {
            // ERROR with the exception, not WARN with its message: this is a user-visible failure
            // — somebody's message did not arrive — and without the stack trace there is nothing
            // to act on.
            log.error("Scheduled message delivery failed; messages due now were not sent", e)
        }
    }
}
