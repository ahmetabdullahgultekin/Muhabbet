package com.muhabbet.messaging.adapter.out.external

import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import com.muhabbet.messaging.domain.model.PushNotification
import com.muhabbet.shared.event.PushTokenRejectedEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.context.ApplicationEventPublisher

/**
 * What [FcmPushNotificationAdapter] does with a failed send.
 *
 * Two guarantees. A failure never escapes: the message is already persisted and broadcast by the
 * time push runs, so a push error escaping here would surface as a failed send to a user whose
 * message did in fact arrive. And a token FCM has rejected *for good* is reported, so the device
 * row forgets it (#908) instead of costing a provider call on every message forever.
 *
 * Not covered here: the notification's title/body/collapse-key, because Firebase's `Message` has no
 * accessors to assert against — that composition is tested upstream in `PushNotificationComposer`,
 * `NotificationTextCatalog` and `OfflinePushSender`.
 */
class FcmPushNotificationAdapterTest {

    private val notification = PushNotification(
        title = "Ayşe",
        body = "merhaba",
        collapseKey = "conv-1",
        data = mapOf("conversationId" to "conv-1")
    )

    private val eventPublisher: ApplicationEventPublisher = mockk(relaxed = true)

    /** An adapter whose Firebase client answers every send with [code]. */
    private fun adapterRejectingWith(code: MessagingErrorCode?): FcmPushNotificationAdapter {
        val rejection: FirebaseMessagingException = mockk {
            every { messagingErrorCode } returns code
            every { message } returns "rejected: $code"
        }
        val messaging: FirebaseMessaging = mockk {
            every { send(any<Message>()) } throws rejection
        }
        return FcmPushNotificationAdapter("unused", eventPublisher) { messaging }
    }

    @Test
    fun `sendPush should swallow a Firebase failure instead of propagating it`() {
        // The suite runs with muhabbet.fcm.enabled=false, so this bean is never created and no
        // FirebaseApp is initialised. FirebaseMessaging.getInstance() therefore raises immediately
        // — the cheapest real Firebase failure available offline, and no network is touched. This
        // goes through the production constructor, so it also proves the default client lookup.
        assumeTrue(
            FirebaseApp.getApps().isEmpty(),
            "another test initialised FirebaseApp; this one needs the uninitialised state"
        )

        val adapter = FcmPushNotificationAdapter(credentialsPath = "unused", eventPublisher = eventPublisher)

        assertDoesNotThrow { adapter.sendPush("some-device-token", notification) }
        verify(exactly = 0) { eventPublisher.publishEvent(any<Any>()) }
    }

    @Test
    fun `should report an unregistered token so the device row forgets it`() {
        val adapter = adapterRejectingWith(MessagingErrorCode.UNREGISTERED)

        assertDoesNotThrow { adapter.sendPush("dead-token", notification) }

        verify(exactly = 1) {
            eventPublisher.publishEvent(PushTokenRejectedEvent("dead-token", reason = "UNREGISTERED"))
        }
    }

    @ParameterizedTest
    @EnumSource(MessagingErrorCode::class)
    fun `should report a token only for an answer that condemns the token`(code: MessagingErrorCode) {
        val adapter = adapterRejectingWith(code)

        adapter.sendPush("some-token", notification)

        val expected = if (FcmPushNotificationAdapter.isPermanentTokenRejection(code)) 1 else 0
        verify(exactly = expected) { eventPublisher.publishEvent(any<PushTokenRejectedEvent>()) }
    }

    @Test
    fun `should treat only the three token answers as permanent`() {
        // A quota, outage or credentials problem is about the attempt or about us. Dropping a
        // working token for one of those would silently end that phone's notifications.
        val permanent = MessagingErrorCode.entries.filter { FcmPushNotificationAdapter.isPermanentTokenRejection(it) }

        assertEquals(
            setOf(
                MessagingErrorCode.UNREGISTERED,
                MessagingErrorCode.SENDER_ID_MISMATCH,
                MessagingErrorCode.INVALID_ARGUMENT
            ),
            permanent.toSet()
        )
        assertFalse(FcmPushNotificationAdapter.isPermanentTokenRejection(null))
        assertTrue(FcmPushNotificationAdapter.isPermanentTokenRejection(MessagingErrorCode.UNREGISTERED))
    }

    @Test
    fun `should not report a token when FCM gave no error code at all`() {
        // A transport failure: no answer about the token, so nothing to act on.
        val adapter = adapterRejectingWith(null)

        adapter.sendPush("some-token", notification)

        verify(exactly = 0) { eventPublisher.publishEvent(any<Any>()) }
    }
}
