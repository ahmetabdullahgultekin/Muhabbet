package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.CallSession
import com.muhabbet.messaging.domain.model.CallStatus
import com.muhabbet.messaging.domain.model.CallType
import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.messaging.domain.port.out.CallHistoryRepository
import com.muhabbet.messaging.domain.port.out.UserDirectoryPort
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.slf4j.LoggerFactory
import java.time.Clock
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
    private val callHistoryRepository: CallHistoryRepository,
    private val blockPolicy: BlockPolicyPort,
    private val userDirectory: UserDirectoryPort,
    private val clock: Clock
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        /**
         * How long a call may ring unanswered before it no longer counts as keeping either party
         * busy. Checked lazily when someone next tries to ring them, so an abandoned ring cannot
         * leave a user unreachable until the server restarts.
         */
        const val RING_TIMEOUT_SECONDS = 60L
    }

    // callId -> CallSession
    private val activeCalls = ConcurrentHashMap<String, CallSession>()

    // userId -> callId (reverse lookup for "is user in a call?" check)
    private val userActiveCalls = ConcurrentHashMap<UUID, String>()

    /**
     * Initiates a new call. Returns the CallSession if successful.
     * Throws if either user is already in an active call.
     */
    fun initiateCall(callId: String, callerId: UUID, calleeId: UUID, callType: CallType): CallSession {
        // Calls follow the same rules as messages: a block in either direction stops them, and the
        // callee has to be a real account. One refusal for all of them, so the answer does not say
        // which. A call id already in use is refused rather than overwritten, or a second caller
        // could take over someone else's call.
        requireCallable(callId, callerId, calleeId)
        expireStaleRing(callerId)
        expireStaleRing(calleeId)

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
            status = CallStatus.INITIATED,
            startedAt = clock.instant()
        )

        claimCallId(session)
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

    /**
     * Stores [session] under its id only if no call holds that id yet. Two initiations with the
     * same id can both pass [requireCallable]; this is the atomic step that lets only one own it.
     */
    private fun claimCallId(session: CallSession) {
        if (activeCalls.putIfAbsent(session.callId, session) != null) {
            throw BusinessException(ErrorCode.CALL_INVALID_TARGET)
        }
    }

    private fun requireCallable(callId: String, callerId: UUID, calleeId: UUID) {
        val refused = callerId == calleeId ||
            activeCalls.containsKey(callId) ||
            blockPolicy.hasBlocked(calleeId, callerId) ||
            blockPolicy.hasBlocked(callerId, calleeId) ||
            !userDirectory.findDisplayInfo(listOf(calleeId)).containsKey(calleeId)
        if (refused) {
            throw BusinessException(ErrorCode.CALL_INVALID_TARGET)
        }
    }

    /** Whether [userId] is one of the people in call [callId]; nobody else may answer or end it. */
    fun isParticipant(callId: String, userId: UUID): Boolean {
        val session = activeCalls[callId] ?: return false
        return userId == session.callerId || userId == session.calleeId || userId in session.participantIds
    }

    /** Ends, as missed, a 1:1 call of [userId]'s that has rung unanswered past [RING_TIMEOUT_SECONDS]. */
    private fun expireStaleRing(userId: UUID) {
        val callId = userActiveCalls[userId] ?: return
        val session = activeCalls[callId] ?: return
        val ringingFor = Duration.between(session.startedAt, clock.instant()).seconds
        if (!session.isGroupCall && session.status == CallStatus.INITIATED && ringingFor > RING_TIMEOUT_SECONDS) {
            endCall(callId, CallStatus.MISSED)
        }
    }

    /**
     * Initiates a group call. All members of the conversation can join.
     */
    fun initiateGroupCall(callId: String, callerId: UUID, conversationId: UUID, callType: CallType, participantIds: Set<UUID>): CallSession {
        if (userActiveCalls.containsKey(callerId)) {
            throw CallBusyException(callerId)
        }

        // For group calls, calleeId is set to callerId (self-referential) since there's no single callee
        val session = CallSession(
            callId = callId,
            callerId = callerId,
            calleeId = callerId,
            callType = callType,
            status = CallStatus.INITIATED,
            conversationId = conversationId,
            isGroupCall = true,
            participantIds = participantIds
        )

        claimCallId(session)
        userActiveCalls[callerId] = callId

        log.info("Group call initiated: callId={}, caller={}, conv={}, participants={}", callId, callerId, conversationId, participantIds.size)
        return session
    }

    /**
     * Adds a participant to a group call.
     */
    fun joinGroupCall(callId: String, userId: UUID): CallSession? {
        val session = activeCalls[callId] ?: return null
        if (!session.isGroupCall) return null

        val updated = session.copy(
            participantIds = session.participantIds + userId
        )
        activeCalls[callId] = updated
        userActiveCalls[userId] = callId

        log.info("User {} joined group call {}", userId, callId)
        return updated
    }

    /**
     * Removes a participant from a group call.
     */
    fun leaveGroupCall(callId: String, userId: UUID): CallSession? {
        val session = activeCalls[callId] ?: return null
        userActiveCalls.remove(userId)

        val remaining = session.participantIds - userId
        if (remaining.isEmpty()) {
            // Last participant left — end the call
            return endCall(callId, CallStatus.ENDED)
        }

        val updated = session.copy(participantIds = remaining)
        activeCalls[callId] = updated

        log.info("User {} left group call {}, {} remaining", userId, callId, remaining.size)
        return updated
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
                    durationSeconds = durationSeconds,
                    conversationId = session.conversationId,
                    isGroupCall = session.isGroupCall,
                    participantCount = if (session.isGroupCall) session.participantIds.size else 2
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
    val durationSeconds: Int? = null,
    val conversationId: UUID? = null,
    val isGroupCall: Boolean = false,
    val participantCount: Int = 2
)

/**
 * Thrown when a user is already in an active call.
 */
class CallBusyException(val userId: UUID) : RuntimeException("User $userId is already in an active call")
