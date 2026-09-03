package com.muhabbet.app.ui.chat

/**
 * Why a `message.send` was refused, in the device's language.
 *
 * The backend has answered every refusal with its real [com.muhabbet.shared.protocol.WsMessage
 * .ServerAck.errorCode] since #572's server half landed — `MSG_CONTENT_TOO_LONG`, `MSG_NOT_MEMBER`,
 * `MSG_ANNOUNCEMENT_ONLY`, `MSG_DUPLICATE` and `VALIDATION_ERROR` are all distinguishable on the
 * wire. The app then threw the code away and showed one fixed sentence for all of them, so a user
 * whose message was too long and a user who had been removed from the group were told exactly the
 * same thing and neither could act on it. This is the reader half of that fix.
 *
 * Resolved at composition, like [com.muhabbet.app.ui.auth.OtpVerifyScreen]'s equivalent, because
 * `stringResource` is `@Composable` and the ack arrives inside a `scope.launch`.
 */
internal class SendFailureMessages(
    /**
     * Everything not named below — a 500 answered as `INTERNAL_ERROR`, an unrecognised code from a
     * newer backend, or an ack with no code at all. Naming a cause we cannot explain would be worse
     * than saying plainly that the message did not go.
     *
     * `VALIDATION_ERROR` deliberately lands here too. On the send path it means the app put a
     * malformed id in its own frame — a bug in this build, not something the sender did or can
     * undo — and there is no sentence about it that helps somebody holding a phone.
     */
    private val generic: String,
    private val tooLong: String,
    private val notMember: String,
    private val announcementOnly: String,
    /**
     * The refusal that used to arrive as nothing at all (#725).
     *
     * `WebSocketRateLimiter` answered with a bare `WsMessage.Error`, which the chat screen dropped,
     * so a rate-limited send produced no sentence, no failed bubble and no reason — just a clock
     * that never settled. The server now answers it on the ack like every other refusal, which is
     * what lets it be one more line here instead of a second failure channel.
     */
    private val rateLimited: String,
) {
    fun forCode(code: String?): String = when (code) {
        "MSG_CONTENT_TOO_LONG" -> tooLong
        "MSG_NOT_MEMBER" -> notMember
        "MSG_ANNOUNCEMENT_ONLY" -> announcementOnly
        "RATE_LIMITED" -> rateLimited
        else -> generic
    }
}

/**
 * Whether an error ack means the server already holds this message.
 *
 * `MSG_DUPLICATE` is the idempotency guard in `MessageService.persistSend` recognising a
 * `messageId` it has already stored, which is the ordinary outcome of the client resending after a
 * reconnect. The message was delivered; only this second copy of it was refused. Reporting that as
 * a send failure is a lie the user acts on by typing it again, so it is treated as the acceptance
 * it actually is and says nothing.
 */
internal fun serverAlreadyHasMessage(code: String?): Boolean = code == "MSG_DUPLICATE"
