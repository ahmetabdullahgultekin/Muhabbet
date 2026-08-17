package com.muhabbet.app.platform

import androidx.compose.runtime.Composable
import com.muhabbet.app.util.Log

private const val TAG = "NotificationPermission"

/**
 * Honestly stubbed, not faked.
 *
 * iOS has `UNUserNotificationCenter` and its own authorisation prompt, but nothing on this platform
 * would post a notification if it were granted: APNs delivery is not wired (see CLAUDE.md, "iOS APNs
 * delivery, TestFlight, App Store"), and `IosPushTokenProvider` has no token to register. Asking a
 * user for permission to send them something the app cannot send is worse than saying nothing, so
 * this reports [NotificationPermissionState.Unsupported] and Settings says so in plain words.
 *
 * When APNs lands, this class is the seam: implement `state()` against
 * `UNUserNotificationCenter.getNotificationSettings`, `runtimePermissionExists()` becomes true, and
 * the shared gate in `NotificationPermissionGate` starts asking on iOS with no change to it.
 */
class IosNotificationPermission : NotificationPermission {

    override fun state(): NotificationPermissionState = NotificationPermissionState.Unsupported

    override fun runtimePermissionExists(): Boolean = false

    /**
     * Unreachable while [state] is `Unsupported`: the Settings row for that state reports rather
     * than navigates, so nothing calls this. Logged instead of left empty so that if a future call
     * site does reach it, the silence is explained in the console rather than mistaken for a
     * settings page that failed to open.
     */
    override fun openSystemSettings() {
        Log.d(TAG, "No notification settings to open: push is not wired on iOS")
    }
}

@Composable
actual fun rememberNotificationPermissionRequester(
    onResult: (Boolean) -> Unit
): () -> Unit = {
    Log.d(TAG, "No notification permission to request: push is not wired on iOS")
}
