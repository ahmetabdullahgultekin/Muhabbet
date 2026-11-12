package com.muhabbet.auth.domain.port.`in`

import java.time.Instant
import java.util.UUID

/**
 * Whether a token's `(userId, deviceId)` may still be honoured — asked by the REST filter on every
 * request and by the WebSocket handshake (#747). The signature check happens before this; this is
 * the part a signature cannot answer, because it changes after the token was issued.
 *
 * [issuedAt] is the token's `iat`. A device that has logged out refuses every token issued at or
 * before its logout; a null is read as "older than any logout".
 */
fun interface CheckSessionValidityUseCase {
    fun isSessionValid(userId: UUID, deviceId: UUID, issuedAt: Instant?): Boolean
}
