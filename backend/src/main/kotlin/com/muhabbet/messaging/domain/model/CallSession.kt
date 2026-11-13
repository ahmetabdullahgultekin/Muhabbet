package com.muhabbet.messaging.domain.model

import java.time.Duration
import java.time.Instant
import java.util.UUID

enum class CallType {
    VOICE,
    VIDEO
}

/** How a call ended — the only part of a call's state that outlives it, in `call_history`. */
enum class CallOutcome {
    /** Answered, then hung up by either party. */
    ENDED,

    /** The callee refused it while it rang. */
    DECLINED,

    /** It rang and was never answered: the caller gave up, the callee was away, or it timed out. */
    MISSED
}

/** Where a call is. A call never leaves [Ended]. */
sealed interface CallState {
    data object Ringing : CallState
    data class Active(val answeredAt: Instant) : CallState
    /** [answeredAt] survives the end, so the history row can say how long the call lasted. */
    data class Ended(val outcome: CallOutcome, val endedAt: Instant, val answeredAt: Instant? = null) : CallState
}

/** What can happen to a call, and who may make it happen is decided by [CallSession.on]. */
sealed interface CallEvent {
    /** The callee picks up. */
    data object Answer : CallEvent

    /** The callee refuses while it rings. */
    data object Decline : CallEvent

    /** Either party hangs up. */
    data object Hangup : CallEvent

    /** Nobody answered in time; raised by the system, not a person. */
    data object RingTimeout : CallEvent
}

enum class CallRefusal {
    /** The actor is not the caller or the callee. Reported as CALL_NOT_FOUND, so it reveals nothing. */
    NOT_PARTICIPANT,

    /** A participant, but not the one who may do this (the caller answering their own call). */
    WRONG_ACTOR,

    /** The event makes no sense in the call's state (answering an ended call). */
    ILLEGAL_TRANSITION
}

sealed interface CallTransition {
    data class Moved(val session: CallSession) : CallTransition
    data class Refused(val refusal: CallRefusal) : CallTransition
}

/**
 * One 1:1 call, as a state machine (#878).
 *
 * The signalling service used to keep a status enum it changed with `copy()` from anywhere: an
 * ended call could be "answered" again, the caller could answer their own call, and anyone who knew
 * a call id could end it. Every change now goes through [on], which checks both the transition and
 * the actor, and is pure, so the whole table is tested without a socket or a clock.
 */
data class CallSession(
    val callId: String,
    val callerId: UUID,
    val calleeId: UUID,
    val callType: CallType,
    val startedAt: Instant,
    val state: CallState = CallState.Ringing
) {
    val participants: Set<UUID> get() = setOf(callerId, calleeId)

    /** The other participant from [userId]'s side, or null when [userId] is not in the call. */
    fun otherParty(userId: UUID): UUID? = when (userId) {
        callerId -> calleeId
        calleeId -> callerId
        else -> null
    }

    /**
     * Applies [event] by [actor] (null for the system) at [now]. Only the callee answers or
     * declines; either participant hangs up; only the system times a call out.
     */
    fun on(event: CallEvent, actor: UUID?, now: Instant): CallTransition {
        if (actor != null && actor !in participants) return CallTransition.Refused(CallRefusal.NOT_PARTICIPANT)
        if (event == CallEvent.RingTimeout && actor != null) return CallTransition.Refused(CallRefusal.WRONG_ACTOR)
        if (event != CallEvent.RingTimeout && actor == null) return CallTransition.Refused(CallRefusal.WRONG_ACTOR)

        val next: CallState = when (state) {
            is CallState.Ended -> return CallTransition.Refused(CallRefusal.ILLEGAL_TRANSITION)
            CallState.Ringing -> when (event) {
                CallEvent.Answer, CallEvent.Decline ->
                    if (actor != calleeId) return CallTransition.Refused(CallRefusal.WRONG_ACTOR)
                    else if (event == CallEvent.Answer) CallState.Active(now)
                    else CallState.Ended(CallOutcome.DECLINED, now)
                // The callee hanging up a ringing call is a decline; the caller doing it is a call
                // the callee missed.
                CallEvent.Hangup ->
                    CallState.Ended(if (actor == calleeId) CallOutcome.DECLINED else CallOutcome.MISSED, now)
                CallEvent.RingTimeout -> CallState.Ended(CallOutcome.MISSED, now)
            }
            is CallState.Active -> when (event) {
                CallEvent.Hangup -> CallState.Ended(CallOutcome.ENDED, now, answeredAt = state.answeredAt)
                CallEvent.Answer, CallEvent.Decline, CallEvent.RingTimeout ->
                    return CallTransition.Refused(CallRefusal.ILLEGAL_TRANSITION)
            }
        }
        return CallTransition.Moved(copy(state = next))
    }

    /** The history row for an ended call; null while it is still going. */
    fun toHistory(): CallHistoryRecord? {
        val ended = state as? CallState.Ended ?: return null
        return CallHistoryRecord(
            callId = callId,
            callerId = callerId,
            calleeId = calleeId,
            callType = callType,
            status = ended.outcome,
            startedAt = startedAt,
            answeredAt = ended.answeredAt,
            endedAt = ended.endedAt,
            durationSeconds = ended.answeredAt?.let { Duration.between(it, ended.endedAt).seconds.toInt() }
        )
    }
}

/**
 * A persisted call history record, written once when a call ends. Its type and outcome are enums
 * (#897): they were free strings, so the history table could hold values no client understood.
 */
data class CallHistoryRecord(
    val id: UUID = UUID.randomUUID(),
    val callId: String,
    val callerId: UUID,
    val calleeId: UUID,
    val callType: CallType,
    val status: CallOutcome,
    val startedAt: Instant,
    val answeredAt: Instant? = null,
    val endedAt: Instant? = null,
    val durationSeconds: Int? = null,
    val conversationId: UUID? = null,
    val isGroupCall: Boolean = false,
    val participantCount: Int = 2
)
