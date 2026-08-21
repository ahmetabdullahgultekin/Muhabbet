package com.muhabbet.messaging.adapter.out.external

import com.google.firebase.FirebaseApp
import com.muhabbet.messaging.domain.model.PushNotification
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/**
 * Covers the one thing about [FcmPushNotificationAdapter] that is observable without a Firebase
 * project: a failing send is swallowed.
 *
 * That matters because the message is already persisted and broadcast by the time push runs — a
 * push error escaping here would surface as a failed send to a user whose message did in fact
 * arrive. The adapter's `catch (e: Exception)` is the whole guarantee, and it is one deletion away
 * from being gone.
 *
 * Not covered here: the notification's title/body/collapse-key, because Firebase's `Message` has no
 * accessors to assert against — that composition is tested upstream in `PushNotificationComposer`,
 * `NotificationTextCatalog` and `OfflinePushSender`. Dead-token cleanup is not covered either: the
 * `PushTokenInvalidationPort` this adapter would need does not exist in the tree (issue #671, item
 * 3).
 */
class FcmPushNotificationAdapterTest {

    private val notification = PushNotification(
        title = "Ayşe",
        body = "merhaba",
        collapseKey = "conv-1",
        data = mapOf("conversationId" to "conv-1")
    )

    @Test
    fun `sendPush should swallow a Firebase failure instead of propagating it`() {
        // The suite runs with muhabbet.fcm.enabled=false, so this bean is never created and no
        // FirebaseApp is initialised. FirebaseMessaging.getInstance() therefore raises immediately
        // — the cheapest real Firebase failure available offline, and no network is touched.
        assumeTrue(
            FirebaseApp.getApps().isEmpty(),
            "another test initialised FirebaseApp; this one needs the uninitialised state"
        )

        val adapter = FcmPushNotificationAdapter(credentialsPath = "unused")

        assertDoesNotThrow { adapter.sendPush("some-device-token", notification) }
    }
}
