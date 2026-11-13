package com.muhabbet.messaging.domain.port.`in`

/**
 * Delivers the scheduled messages that have come due. Called by the scheduler adapter once a
 * minute; an in-port so that adapter depends on a use case rather than on a concrete service.
 */
interface DeliverScheduledMessagesUseCase {
    /**
     * Returns the number of messages actually delivered, so the caller can distinguish a run that
     * had nothing to do from a run that did nothing. Messages dropped for a block are not counted:
     * from the sender's side they were not delivered.
     */
    fun deliverScheduledMessages(): Int
}
