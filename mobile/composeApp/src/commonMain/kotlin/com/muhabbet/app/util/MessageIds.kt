package com.muhabbet.app.util

/**
 * A random UUIDv4 in the canonical form, used as a message's id and as the request id that
 * correlates a WebSocket ack back to the send that produced it.
 *
 * The server treats the message id as an idempotency key, so it must be generated once per send and
 * reused across a retry of that same send — never regenerated to "make the retry go through".
 *
 * Lives in `util` rather than beside the chat bubbles it used to: the notification inline reply
 * (#510) sends without any UI at all, and a repository reaching into `ui.chat` for an id generator
 * is the wrong direction of dependency.
 */
internal fun generateMessageId(): String {
    val chars = "0123456789abcdef"
    return buildString {
        repeat(8) { append(chars.random()) }; append('-')
        repeat(4) { append(chars.random()) }; append('-'); append('4')
        repeat(3) { append(chars.random()) }; append('-')
        append(listOf('8', '9', 'a', 'b').random())
        repeat(3) { append(chars.random()) }; append('-')
        repeat(12) { append(chars.random()) }
    }
}
