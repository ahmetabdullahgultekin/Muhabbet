package com.muhabbet.messaging.adapter.out.external

import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import com.muhabbet.messaging.domain.port.out.PushTokenInvalidationPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

/**
 * Unit tests for [FcmPushNotificationAdapter]'s error handling — specifically the dead-token
 * cleanup (presence/notification V&V Finding B). The real Firebase call cannot run here (no
 * credentials), so the live `send` is replaced by a test seam ([TestableFcmAdapter.failWith])
 * and the [FirebaseMessagingException] (package-private ctor) is mocked to report a given
 * [MessagingErrorCode] — only the adapter's classification + invalidation logic is exercised.
 */
class FcmPushNotificationAdapterTest {

    private val invalidationPort = mockk<PushTokenInvalidationPort>(relaxed = true)

    /** Adapter whose Firebase call is overridable so the catch-branch is testable offline. */
    private class TestableFcmAdapter(
        invalidationPort: PushTokenInvalidationPort
    ) : FcmPushNotificationAdapter(credentialsPath = "unused", pushTokenInvalidationPort = invalidationPort) {
        /** When set, every send throws it; when null, send succeeds. */
        var failWith: Exception? = null

        override fun sendToFirebase(message: Message): String {
            failWith?.let { throw it }
            return "msg-id-ok"
        }
    }

    private fun fcmException(code: MessagingErrorCode): FirebaseMessagingException {
        val ex = mockk<FirebaseMessagingException>(relaxed = true)
        every { ex.messagingErrorCode } returns code
        every { ex.message } returns "firebase error: $code"
        return ex
    }

    private fun adapter() = TestableFcmAdapter(invalidationPort)

    @Test
    fun `should invalidate token when Firebase reports UNREGISTERED`() {
        val adapter = adapter().apply { failWith = fcmException(MessagingErrorCode.UNREGISTERED) }

        adapter.sendPush("dead-token-1", "Title", "Body", emptyMap())

        verify(exactly = 1) { invalidationPort.invalidate("dead-token-1") }
    }

    @Test
    fun `should invalidate token when Firebase reports INVALID_ARGUMENT`() {
        val adapter = adapter().apply { failWith = fcmException(MessagingErrorCode.INVALID_ARGUMENT) }

        adapter.sendPush("dead-token-2", "Title", "Body", emptyMap())

        verify(exactly = 1) { invalidationPort.invalidate("dead-token-2") }
    }

    @Test
    fun `should invalidate token when Firebase reports SENDER_ID_MISMATCH`() {
        val adapter = adapter().apply { failWith = fcmException(MessagingErrorCode.SENDER_ID_MISMATCH) }

        adapter.sendPush("dead-token-3", "Title", "Body", emptyMap())

        verify(exactly = 1) { invalidationPort.invalidate("dead-token-3") }
    }

    @Test
    fun `should NOT invalidate token on a successful send`() {
        val adapter = adapter() // failWith stays null → send succeeds

        adapter.sendPush("live-token", "Title", "Body", emptyMap())

        verify(exactly = 0) { invalidationPort.invalidate(any()) }
    }

    @Test
    fun `should NOT invalidate token on a transient error (UNAVAILABLE)`() {
        val adapter = adapter().apply { failWith = fcmException(MessagingErrorCode.UNAVAILABLE) }

        adapter.sendPush("live-token", "Title", "Body", emptyMap())

        verify(exactly = 0) { invalidationPort.invalidate(any()) }
    }

    @Test
    fun `should NOT invalidate token on INTERNAL error`() {
        val adapter = adapter().apply { failWith = fcmException(MessagingErrorCode.INTERNAL) }

        adapter.sendPush("live-token", "Title", "Body", emptyMap())

        verify(exactly = 0) { invalidationPort.invalidate(any()) }
    }

    @Test
    fun `should NOT invalidate token on a generic non-Firebase exception`() {
        val adapter = adapter().apply { failWith = RuntimeException("network down") }

        adapter.sendPush("live-token", "Title", "Body", emptyMap())

        verify(exactly = 0) { invalidationPort.invalidate(any()) }
    }

    @Test
    fun `should swallow invalidation failure and not propagate`() {
        every { invalidationPort.invalidate(any()) } throws RuntimeException("db down")
        val adapter = adapter().apply { failWith = fcmException(MessagingErrorCode.UNREGISTERED) }

        // Must not throw — push failures never break the (already-persisted) message path.
        adapter.sendPush("dead-token", "Title", "Body", emptyMap())

        verify(exactly = 1) { invalidationPort.invalidate("dead-token") }
    }
}
