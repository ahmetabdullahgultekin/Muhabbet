package com.muhabbet.app.ui.notification

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.platform.NotificationPermission
import com.muhabbet.app.platform.rememberNotificationPermissionRequester
import com.muhabbet.app.platform.shouldRequestNotificationPermission
import com.muhabbet.app.util.Log
import org.koin.compose.koinInject

private const val TAG = "NotificationPermissionGate"

/**
 * Asks for notification permission once, on the first launch that reaches a signed-in session.
 *
 * Draws nothing. It exists as a composable only because the system dialog is launched through an
 * activity-result launcher, which has to be remembered in a composition.
 *
 * **When.** Sited next to the test-build notice in `RootContent`, under the same "the Main child is
 * active" gate, so it fires after login rather than on the login screen — the moment notifications
 * start meaning something, and after the user has seen what the app is. On Android 13+ a fresh
 * install is denied by default (#547), so without this the backend delivers push to a phone that
 * throws it away, which is exactly what happened until the permission was granted by hand over adb.
 *
 * The system dialog can land on top of the first-run test-build notice, and that is the accepted
 * trade. Deferring the ask to a second launch to keep the first screen tidy would mean the first
 * session — the one where someone messages a new user to check the app works — is the session with
 * no notifications.
 *
 * **Once.** The flag is written *before* the dialog is shown, not in its callback. A user who
 * answers by swiping the app away never delivers a result, and a flag written in the callback would
 * leave the ask un-recorded and repeat it on every launch. Android stops showing the dialog after
 * two denials, so that repeat would not even be visible — it would be a request that silently does
 * nothing, forever.
 *
 * **After a no.** Nothing here retries. Settings carries a row that reports the current state and
 * opens the system notification page for the app, which is the only route that still works once
 * Android has stopped showing the dialog.
 */
@Composable
fun NotificationPermissionGate(
    tokenStorage: TokenStorage = koinInject(),
    notificationPermission: NotificationPermission = koinInject()
) {
    val request = rememberNotificationPermissionRequester { granted ->
        // Not persisted: the answer lives in the OS, and Settings reads it back from there on every
        // visit. Logged because "did the prompt appear, and what did they tap" is the first question
        // asked when someone reports getting no notifications.
        Log.d(TAG, if (granted) "Notification permission granted" else "Notification permission denied")
    }

    LaunchedEffect(Unit) {
        val ask = shouldRequestNotificationPermission(
            alreadyAsked = tokenStorage.getNotificationPermissionAsked(),
            state = notificationPermission.state(),
            runtimePermissionExists = notificationPermission.runtimePermissionExists()
        )
        if (!ask) return@LaunchedEffect

        tokenStorage.setNotificationPermissionAsked()
        request()
    }
}
