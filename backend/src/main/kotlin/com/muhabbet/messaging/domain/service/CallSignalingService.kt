package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.CallSession
import com.muhabbet.messaging.domain.model.CallStatus
import com.muhabbet.messaging.domain.model.CallType
import com.muhabbet.messaging.domain.port.out.CallHistoryRepository
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages active call sessions in memory.
 * Enforces max 1 active call per user.
 * Persists call history when calls end.
 */
open class CallSignalingService(
    private val callHistoryRepository: CallHistoryRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // callId -> CallSession
    private val activeCalls = ConcurrentHashMap<String, CallSession>()

    // userId -> callId (reverse lookup for "is user in a call?" check)
    private val userActiveCalls = ConcurrentHashMap<UUID, String>()

    /**
     * Initiates a new call. Returns the CallSession if successful.
     * Throws if either user is already in an active call.
     */
    fun initiateCall(callId: String, callerId: UUID, calleeId: UUID, callType: CallType): CallSession {
        // Check if caller is already in a call
        if (userActiveCalls.containsKey(callerId)) {
            throw CallBusyException(callerId)
        }
        // Check if callee is already in a call
        if (userActiveCalls.containsKey(calleeId)) {
            throw CallBusyException(calleeId)
        }

        val session = CallSession(
            callId = callId,
            callerId = callerId,
            calleeId = calleeId,
            callType = callType,
            status = CallStatus.INITIATED
        )

        activeCalls[callId] = session
        userActiveCalls[callerId] = callId
        userActiveCalls[calleeId] = callId

        log.info("Call initiated: callId={}, caller={}, callee={}, type={}", callId, callerId, calleeId, callType)
        return session
    }

    /**
     * Marks a call as answered.
     */
    fun answerCall(callId: String): CallSession? {
        val session = activeCalls[callId] ?: return null
        val updated = session.copy(status = CallStatus.ANSWERED, answeredAt = Instant.now())
        activeCalls[callId] = updated
        log.info("Call answered: callId={}", callId)
        return updated
    }

    /**
     * Ends a call and persists history.
     * Returns the ended session for broadcasting.
     */
    fun endCall(callId: String, status: CallStatus = CallStatus.ENDED): CallSession? {
        val session = activeCalls.remove(callId) ?: return null
        val ended = session.copy(status = status, endedAt = Instant.now())

        // Remove user -> callId mappings
        userActiveCalls.remove(session.callerId)
        userActiveCalls.remove(session.calleeId)

        // Persist call history
        persistCallHistory(ended)

        log.info("Call ended: callId={}, status={}", callId, status)
        return ended
    }

    /**
     * Gets the active call session by callId.
     */
    fun getCall(callId: String): CallSession? = activeCalls[callId]

    /**
     * Gets the callId for a user's active call, if any.
     */
    fun getActiveCallForUser(userId: UUID): String? = userActiveCalls[userId]

    /**
     * Returns the other party's userId in a call.
     */
    fun getOtherParty(callId: String, userId: UUID): UUID? {
        val session = activeCalls[callId] ?: return null
        return when (userId) {
            session.callerId -> session.calleeId
            session.calleeId -> session.callerId
            else -> null
        }
    }

    private fun persistCallHistory(session: CallSession) {
        try {
            val durationSeconds = if (session.answeredAt != null && session.endedAt != null) {
                Duration.between(session.answeredAt, session.endedAt).seconds.toInt()
            } else {
                null
            }

            callHistoryRepository.save(
                CallHistoryRecord(
                    callId = session.callId,
                    callerId = session.callerId,
                    calleeId = session.calleeId,
                    callType = session.callType.name,
                    status = session.status.name,
                    startedAt = session.startedAt,
                    answeredAt = session.answeredAt,
                    endedAt = session.endedAt,
                    durationSeconds = durationSeconds
                )
            )
        } catch (e: Exception) {
            log.warn("Failed to persist call history for callId={}: {}", session.callId, e.message)
        }
    }
}

/**
 * Domain model for a persisted call history record.
 */
data class CallHistoryRecord(
    val id: UUID = UUID.randomUUID(),
    val callId: String,
    val callerId: UUID,
    val calleeId: UUID,
    val callType: String,
    val status: String,
    val startedAt: Instant,
    val answeredAt: Instant? = null,
    val endedAt: Instant? = null,
    val durationSeconds: Int? = null
)

/**
 * Thrown when a user is already in an active call.
 */
class CallBusyException(val userId: UUID) : RuntimeException("User $userId is already in an active call")
