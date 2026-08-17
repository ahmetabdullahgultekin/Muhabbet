package com.muhabbet.app.platform

import androidx.compose.runtime.Composable

/**
 * What the phone will actually do with a notification this app posts, right now.
 *
 * Deliberately about the *outcome*, not about a permission string. On Android 13+ a denied
 * `POST_NOTIFICATIONS` and a user who switched the app's notifications off in system settings are
 * two different causes with one consequence — the tray stays empty — and the app has exactly one
 * thing to say about either.
 */
enum class NotificationPermissionState {
    /** The system will show what the app posts. */
    Enabled,

    /**
     * The system will drop what the app posts. Recoverable: the system's own notification settings
     * page for this app turns it back on, which on Android 13+ also grants `POST_NOTIFICATIONS`.
     */
    Disabled,

    /** This platform does not deliver push notifications at all yet. iOS, today — see the iOS actual. */
    Unsupported
}

/**
 * The app's view of the OS notification switch.
 *
 * Exists because `POST_NOTIFICATIONS` was declared in the Android manifest and never requested
 * (#547). On Android 13+ that means a fresh install is denied by default, so every notification the
 * backend delivered was posted into a void; push only ever appeared to work because the permission
 * had been granted by hand with `adb shell pm grant`, which nobody installing from Play gets.
 */
interface NotificationPermission {

    /** Read on demand rather than cached — the user can change it from outside the app at any time. */
    fun state(): NotificationPermissionState

    /**
     * Whether this OS version has a notification permission the app can put a system dialog in front
     * of the user for: Android 13 (API 33) and above, false everywhere else.
     *
     * `minSdk` is 26, so on Android 8–12 there is nothing to request — notifications are on unless
     * the user turned them off, and the only route back is the system settings page. Requesting
     * there is a no-op at best, which is why [shouldRequestNotificationPermission] refuses to.
     */
    fun runtimePermissionExists(): Boolean

    /** Opens the system's notification settings page for this app. */
    fun openSystemSettings()
}

/**
 * Whether to put the system permission dialog in front of the user on this launch.
 *
 * Asked at most once per install. Android itself stops showing the dialog after two denials, so a
 * re-ask loop is both irritating and useless; a user who said no gets back through the Settings
 * entry, which deep-links to the system page and works no matter how many times they have refused.
 *
 * Pure and public so the rule is testable without a device — `NotificationPermissionTest` covers it.
 */
fun shouldRequestNotificationPermission(
    alreadyAsked: Boolean,
    state: NotificationPermissionState,
    runtimePermissionExists: Boolean
): Boolean =
    runtimePermissionExists && !alreadyAsked && state == NotificationPermissionState.Disabled

/**
 * Returns a function that shows the system permission dialog once, following the idiom
 * `rememberContactsPermissionRequester` already sets.
 *
 * The returned function does nothing on a platform or OS version with no runtime permission to ask
 * for, and in that case [onResult] is never called. Callers must gate on
 * [shouldRequestNotificationPermission] rather than treating a missing callback as a denial.
 */
@Composable
expect fun rememberNotificationPermissionRequester(
    onResult: (Boolean) -> Unit
): () -> Unit
