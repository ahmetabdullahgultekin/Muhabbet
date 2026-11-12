package com.muhabbet.messaging.adapter.out.external

import com.muhabbet.auth.domain.model.SessionRevocationReason
import com.muhabbet.auth.domain.port.out.SessionRevocationPort
import com.muhabbet.messaging.adapter.`in`.websocket.WebSocketSessionManager
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.socket.CloseStatus
import java.util.UUID

/**
 * Closes the sockets the auth module has just revoked (#747). Lives here because messaging owns the
 * sockets; auth only knows the port.
 *
 * **After commit, not now.** Every caller runs inside a `@Transactional` service method, and the
 * client's answer to a close is to reconnect. Closed before the commit, that reconnect can be
 * authorised against rows that still say the device is active — the revocation would close the
 * socket and then let it straight back in. When no transaction is active (a caller outside one, or a
 * unit test) the close happens immediately, since there is nothing to wait for.
 *
 * Closing is in-memory and quick, which is why it is acceptable on the committing thread: no I/O
 * beyond a close frame, bounded by the decorator's send limit.
 */
@Component
class WebSocketSessionRevocationAdapter(
    private val sessionManager: WebSocketSessionManager
) : SessionRevocationPort {

    override fun revokeDeviceSessions(userId: UUID, deviceId: UUID, reason: SessionRevocationReason) {
        afterCommit { sessionManager.closeForDevice(deviceId, closeStatus(reason)) }
    }

    override fun revokeUserSessions(userId: UUID, reason: SessionRevocationReason) {
        afterCommit { sessionManager.closeForUser(userId, closeStatus(reason)) }
    }

    private fun closeStatus(reason: SessionRevocationReason): CloseStatus =
        CloseStatus(WebSocketSessionManager.CLOSE_CODE_SESSION_REVOKED, reason.name)

    private fun afterCommit(action: () -> Unit) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = action()
        })
    }
}
