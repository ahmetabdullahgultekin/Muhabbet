package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.CallSession
import com.muhabbet.messaging.domain.model.CallType
import java.util.UUID

/** What placing a call produced. */
sealed class CallPlacement {
    /** The callee's phone is ringing. */
    data class Ringing(val session: CallSession) : CallPlacement()

    /** Caller or callee is already in a call. */
    data object Busy : CallPlacement()

    /**
     * Not rung, and deliberately indistinguishable from an unanswered call to the caller: either
     * side has blocked the other. A distinct answer would tell the caller they were blocked.
     */
    data object Unreachable : CallPlacement()
}

/** Starting a 1:1 call (#878). */
interface PlaceCallUseCase {
    /**
     * Rings [calleeId] for [callerId]. Refuses a call to oneself and an id already in use with
     * `CALL_INVALID_TARGET`; reports [CallPlacement.Busy] or [CallPlacement.Unreachable] otherwise.
     */
    fun placeCall(callId: String, callerId: UUID, calleeId: UUID, callType: CallType): CallPlacement
}

/**
 * Everything a participant does to a call already placed. Every method treats a non-participant
 * exactly like an unknown call id — `CALL_NOT_FOUND` — so guessing ids learns nothing and changes
 * nothing (#878).
 */
interface ConductCallUseCase {
    /** The callee answers ([accepted]) or declines. Returns the call as it now stands. */
    fun respond(callId: String, userId: UUID, accepted: Boolean): CallSession

    /** A participant hangs up. Returns the ended call; its history row has been written. */
    fun hangUp(callId: String, userId: UUID): CallSession

    /** The other participant from [userId]'s side, for relaying signalling; null for outsiders. */
    fun otherParty(callId: String, userId: UUID): UUID?
}

/** The ring timeout: calls nobody answered end as MISSED and free both users (#878). */
interface ExpireUnansweredCallsUseCase {
    /** Ends every call that has rung too long, returning them so their parties can be told. */
    fun expireUnansweredCalls(): List<CallSession>
}
