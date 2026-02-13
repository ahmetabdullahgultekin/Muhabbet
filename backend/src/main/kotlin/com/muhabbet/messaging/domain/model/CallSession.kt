package com.muhabbet.messaging.domain.model

import java.time.Instant
import java.util.UUID

/**
 * Tracks an active call between two users.
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
    val endedAt: Instant? = null
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
