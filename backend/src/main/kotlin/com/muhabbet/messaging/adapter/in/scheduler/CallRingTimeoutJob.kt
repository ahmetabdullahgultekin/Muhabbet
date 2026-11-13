package com.muhabbet.messaging.adapter.`in`.scheduler

import com.muhabbet.messaging.adapter.`in`.websocket.CallFrameHandler
import com.muhabbet.messaging.domain.port.`in`.ExpireUnansweredCallsUseCase
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * The ring timeout's clock (#878). Without it a call whose callee never answered — or whose caller's
 * app crashed — stayed ringing, and both users stayed "busy", until the process restarted.
 *
 * Polls every few seconds; the timeout itself lives in the domain
 * ([com.muhabbet.messaging.domain.service.CallSignalingService.RING_TIMEOUT_SECONDS]), so a call
 * ends at most one poll late.
 */
@Component
class CallRingTimeoutJob(
    private val expireUnansweredCalls: ExpireUnansweredCallsUseCase,
    private val callFrameHandler: CallFrameHandler
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 5_000)
    fun expireUnansweredCalls() {
        val expired = expireUnansweredCalls.expireUnansweredCalls()
        expired.forEach(callFrameHandler::announceMissed)
        if (expired.isNotEmpty()) log.info("Ring timeout ended {} unanswered call(s)", expired.size)
    }
}
