package com.muhabbet.messaging.domain.model

import java.time.Instant
import java.util.UUID

/**
 * Tracks an active call between two users or a group call.
 * Stored in-memory only (ConcurrentHashMap) — not persisted until call ends.
 */
data class CallSession(
    val callId: String,
    val callerId: UUID,
    val calleeId: UUID,
    val callType: CallType,
    val status: CallStatus,
    val startedAt: Instant = Instant.now(),
    val answeredAt: Instant? = null,
    val endedAt: Instant? = null,
    // Group call support
    val conversationId: UUID? = null,
    val isGroupCall: Boolean = false,
    val participantIds: Set<UUID> = emptySet()
)

enum class CallType {
    VOICE,
    VIDEO
}

enum class CallStatus {
    INITIATED,
    ANSWERED,
    ENDED,
    DECLINED,
    MISSED
}
