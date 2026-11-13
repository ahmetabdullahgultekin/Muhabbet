package com.muhabbet.auth.adapter.`in`.event

import com.muhabbet.auth.domain.port.`in`.InvalidatePushTokenUseCase
import com.muhabbet.shared.event.PushTokenRejectedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Forgets a token the push provider has rejected for good (#908), so a phone whose app was
 * uninstalled stops costing a provider call per message forever.
 *
 * Synchronous on purpose: the publisher is already on the push executor, off the send path, and a
 * one-row update needs no further hand-off. It never throws back into the publisher — the push
 * that produced the rejection has already failed, and a cleanup failure must not turn into an
 * error on the message that triggered it. The next rejection of the same token retries.
 */
@Component
class PushTokenRejectedListener(
    private val invalidatePushTokenUseCase: InvalidatePushTokenUseCase
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun on(event: PushTokenRejectedEvent) {
        try {
            invalidatePushTokenUseCase.invalidatePushToken(event.pushToken)
        } catch (e: Exception) {
            log.warn("Could not forget rejected push token ({}): {}", event.reason, e.message)
        }
    }
}
