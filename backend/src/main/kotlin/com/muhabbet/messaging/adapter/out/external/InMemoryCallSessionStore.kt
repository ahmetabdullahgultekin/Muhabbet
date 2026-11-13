package com.muhabbet.messaging.adapter.out.external

import com.muhabbet.messaging.domain.model.CallSession
import com.muhabbet.messaging.domain.model.CallState
import com.muhabbet.messaging.domain.port.out.CallSessionStore
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/**
 * The single-instance [CallSessionStore]. Production runs one backend instance, so the store that
 * holds a call is the store every event for it reaches; a second instance needs a shared store
 * (Redis `SET NX` + TTL) behind the same port.
 *
 * One lock around both maps. Correctness here is the whole point — the old code checked two maps
 * and then wrote three entries with nothing holding them together — and call setup is rare enough
 * that a monitor costs nothing measurable.
 */
@Component
class InMemoryCallSessionStore : CallSessionStore {

    private val lock = Any()
    private val calls = HashMap<String, CallSession>()
    private val busy = HashMap<UUID, String>()

    override fun reserve(session: CallSession): Boolean = synchronized(lock) {
        if (session.callId in calls || session.participants.any { it in busy }) return false
        calls[session.callId] = session
        session.participants.forEach { busy[it] = session.callId }
        true
    }

    override fun find(callId: String): CallSession? = synchronized(lock) { calls[callId] }

    override fun replace(expected: CallSession, updated: CallSession): Boolean = synchronized(lock) {
        if (calls[expected.callId] != expected) return false
        calls[expected.callId] = updated
        true
    }

    override fun release(session: CallSession): Boolean = synchronized(lock) {
        if (calls[session.callId] != session) return false
        calls.remove(session.callId)
        session.participants.forEach { busy.remove(it, session.callId) }
        true
    }

    override fun findRingingStartedBefore(cutoff: Instant): List<CallSession> = synchronized(lock) {
        calls.values.filter { it.state == CallState.Ringing && !it.startedAt.isAfter(cutoff) }
    }
}
