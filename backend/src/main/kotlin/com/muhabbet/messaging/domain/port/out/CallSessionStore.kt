package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.CallSession
import java.time.Instant

/**
 * Where live calls are held while they ring or run (#878).
 *
 * The contract is what the old pair of maps lacked: [reserve] is **atomic** across the call id and
 * both participants — "is either busy?" and "mark both busy" can no longer interleave, so two
 * callers cannot both ring one callee — and [replace] is compare-and-set, so two events racing on
 * one call cannot both apply. Behind a port so a multi-instance deployment can swap the in-memory
 * adapter for a shared store (Redis `SET NX` with a TTL) without the service changing.
 */
interface CallSessionStore {
    /**
     * Holds [session] and marks both participants busy, all or nothing. False when the call id is
     * already in use — an existing call is never overwritten — or either participant is in a call.
     */
    fun reserve(session: CallSession): Boolean

    fun find(callId: String): CallSession?

    /** Swaps [expected] for [updated] only if [expected] is still what is held. */
    fun replace(expected: CallSession, updated: CallSession): Boolean

    /** Drops [session] and frees both participants, if [session] is still what is held. */
    fun release(session: CallSession): Boolean

    /** Calls still ringing that started at or before [cutoff], for the ring timeout. */
    fun findRingingStartedBefore(cutoff: Instant): List<CallSession>
}
