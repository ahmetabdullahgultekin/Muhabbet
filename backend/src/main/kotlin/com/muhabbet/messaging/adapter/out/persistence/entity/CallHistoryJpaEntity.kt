package com.muhabbet.messaging.adapter.out.persistence.entity

import com.muhabbet.messaging.domain.service.CallHistoryRecord
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "call_history")
class CallHistoryJpaEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "call_id", nullable = false, unique = true)
    val callId: String,

    @Column(name = "caller_id", nullable = false)
    val callerId: UUID,

    @Column(name = "callee_id", nullable = false)
    val calleeId: UUID,

    @Column(name = "call_type", nullable = false)
    val callType: String,

    @Column(name = "status", nullable = false)
    val status: String,

    @Column(name = "started_at")
    val startedAt: Instant = Instant.now(),

    @Column(name = "answered_at")
    val answeredAt: Instant? = null,

    @Column(name = "ended_at")
    val endedAt: Instant? = null,

    @Column(name = "duration_seconds")
    val durationSeconds: Int? = null
) {
    fun toDomain(): CallHistoryRecord = CallHistoryRecord(
        id = id,
        callId = callId,
        callerId = callerId,
        calleeId = calleeId,
        callType = callType,
        status = status,
        startedAt = startedAt,
        answeredAt = answeredAt,
        endedAt = endedAt,
        durationSeconds = durationSeconds
    )

    companion object {
        fun fromDomain(record: CallHistoryRecord): CallHistoryJpaEntity = CallHistoryJpaEntity(
            id = record.id,
            callId = record.callId,
            callerId = record.callerId,
            calleeId = record.calleeId,
            callType = record.callType,
            status = record.status,
            startedAt = record.startedAt,
            answeredAt = record.answeredAt,
            endedAt = record.endedAt,
            durationSeconds = record.durationSeconds
        )
    }
}
