package com.muhabbet.messaging.domain.port.`in`

/**
 * Sweeps disappearing messages whose time is up and tells everyone watching.
 *
 * Separate from [ManageDisappearingMessageUseCase] rather than a second method on it: the only
 * caller of that one is a controller acting for a user who is changing a timer, and the only caller
 * of this one is a scheduler acting for nobody. Merging them would hand each caller a method it
 * must never invoke, which is the interface-segregation problem in its plainest form — a controller
 * one typo away from expiring the whole database on someone's HTTP request.
 */
interface ExpireDisappearingMessagesUseCase {

    /**
     * Deletes every message due at the moment of the call and broadcasts the fact.
     *
     * @return how many messages this run expired, so the caller can log a run that did something
     *   and stay silent on the ones that did not.
     */
    fun expireDueMessages(): Int
}
