package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.CallEvent
import com.muhabbet.messaging.domain.model.CallHistoryRecord
import com.muhabbet.messaging.domain.model.CallRefusal
import com.muhabbet.messaging.domain.model.CallSession
import com.muhabbet.messaging.domain.model.CallState
import com.muhabbet.messaging.domain.model.CallTransition
import com.muhabbet.messaging.domain.model.CallType
import com.muhabbet.messaging.domain.port.`in`.CallPlacement
import com.muhabbet.messaging.domain.port.`in`.ConductCallUseCase
import com.muhabbet.messaging.domain.port.`in`.ExpireUnansweredCallsUseCase
import com.muhabbet.messaging.domain.port.`in`.PlaceCallUseCase
import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.messaging.domain.port.out.CallHistoryRepository
import com.muhabbet.messaging.domain.port.out.CallSessionStore
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

/**
 * 1:1 call signalling as a state machine (#878).
 *
 * Replaces a pair of in-memory maps with check-then-put busy tests, no transition guards, no ring
 * timeout and no authorization: two callers could both ring one callee, a client-supplied call id
 * silently overwrote a live call, an ended call could be answered again, the caller could answer
 * their own call, anyone holding a call id could end someone else's call, and a crashed client left
 * both users "busy" until the next deploy. Now: [CallSession.on] decides every change and who may
 * make it; [CallSessionStore] reserves atomically and swaps by compare-and-set; a blocked pair cannot
 * ring; and [expireUnansweredCalls] ends a call nobody picked up.
 *
 * Latent until the client places calls at all (#367), which is the point of fixing it first.
 */
open class CallSignalingService(
    private val store: CallSessionStore,
    private val callHistoryRepository: CallHistoryRepository,
    private val blockPolicy: BlockPolicyPort,
    private val clock: Clock = Clock.systemUTC()
) : PlaceCallUseCase, ConductCallUseCase, ExpireUnansweredCallsUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        /** How long a call may ring before it ends as MISSED — WhatsApp rings for about this long. */
        const val RING_TIMEOUT_SECONDS = 45L
    }

    override fun placeCall(callId: String, callerId: UUID, calleeId: UUID, callType: CallType): CallPlacement {
        if (callerId == calleeId || callId.isBlank()) throw BusinessException(ErrorCode.CALL_INVALID_TARGET)

        // Either direction refuses the call, and the caller hears the same thing as an unanswered
        // call — a distinct answer would be a reliable "you are blocked" oracle.
        if (blockPolicy.hasBlocked(calleeId, callerId) || blockPolicy.hasBlocked(callerId, calleeId)) {
            log.info("Call refused, a block stands between the parties: caller={}", callerId)
            return CallPlacement.Unreachable
        }

        // A reused id is refused, never overwritten: the old store replaced a live call with it.
        if (store.find(callId) != null) throw BusinessException(ErrorCode.CALL_INVALID_TARGET)

        val session = CallSession(callId, callerId, calleeId, callType, startedAt = clock.instant())
        if (!store.reserve(session)) return CallPlacement.Busy

        log.info("Call ringing: callId={}, caller={}, callee={}, type={}", callId, callerId, calleeId, callType)
        return CallPlacement.Ringing(session)
    }

    override fun respond(callId: String, userId: UUID, accepted: Boolean): CallSession =
        apply(callId, userId, if (accepted) CallEvent.Answer else CallEvent.Decline)

    override fun hangUp(callId: String, userId: UUID): CallSession = apply(callId, userId, CallEvent.Hangup)

    override fun otherParty(callId: String, userId: UUID): UUID? = store.find(callId)?.otherParty(userId)

    override fun expireUnansweredCalls(): List<CallSession> {
        val now = clock.instant()
        return store.findRingingStartedBefore(now.minusSeconds(RING_TIMEOUT_SECONDS)).mapNotNull { ringing ->
            when (val transition = ringing.on(CallEvent.RingTimeout, actor = null, now = now)) {
                // An answer or a hang-up that won the race is fine: there is nothing to expire.
                is CallTransition.Refused -> null
                is CallTransition.Moved -> commit(ringing, transition.session)
            }
        }
    }

    /**
     * One event by one participant: decide, then compare-and-set. Losing the CAS means another event
     * changed the call first; the event is re-judged against what is there now rather than
     * applied to a stale copy.
     */
    private fun apply(callId: String, actor: UUID, event: CallEvent): CallSession {
        val current = store.find(callId) ?: throw BusinessException(ErrorCode.CALL_NOT_FOUND)
        val next = when (val transition = current.on(event, actor, clock.instant())) {
            is CallTransition.Moved -> transition.session
            is CallTransition.Refused -> throw BusinessException(transition.refusal.toErrorCode())
        }
        return commit(current, next) ?: throw BusinessException(ErrorCode.CALL_INVALID_STATE)
    }

    /** Stores [next] in place of [current]; an ended call is released and written to history. */
    private fun commit(current: CallSession, next: CallSession): CallSession? {
        val stored = if (next.state is CallState.Ended) store.release(current) else store.replace(current, next)
        if (!stored) return null
        next.toHistory()?.let(::persistHistory)
        log.info("Call {}: callId={}", next.state, next.callId)
        return next
    }

    private fun persistHistory(record: CallHistoryRecord) {
        try {
            callHistoryRepository.save(record)
        } catch (e: Exception) {
            // The call has ended for both parties either way; a lost history row must not turn a
            // hang-up into an error the caller sees.
            log.warn("Failed to persist call history for callId={}: {}", record.callId, e.message)
        }
    }

    private fun CallRefusal.toErrorCode(): ErrorCode = when (this) {
        // An outsider learns nothing, not even that the call exists.
        CallRefusal.NOT_PARTICIPANT -> ErrorCode.CALL_NOT_FOUND
        CallRefusal.WRONG_ACTOR, CallRefusal.ILLEGAL_TRANSITION -> ErrorCode.CALL_INVALID_STATE
    }
}
