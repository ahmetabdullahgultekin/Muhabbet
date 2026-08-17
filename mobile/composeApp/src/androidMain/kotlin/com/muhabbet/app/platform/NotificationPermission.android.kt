package com.muhabbet.app.platform

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.app.NotificationManagerCompat
import com.muhabbet.app.util.Log

private const val TAG = "NotificationPermission"

class AndroidNotificationPermission(private val context: Context) : NotificationPermission {

    /**
     * `areNotificationsEnabled()` rather than a `checkSelfPermission` on `POST_NOTIFICATIONS`,
     * because it is the question that matters and it has an answer on every version this app runs
     * on. From Android 13 it already returns false when the permission is denied, so one call
     * covers both the new runtime permission and the switch that has existed since Android 8.
     */
    override fun state(): NotificationPermissionState =
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationPermissionState.Enabled
        } else {
            NotificationPermissionState.Disabled
        }

    override fun runtimePermissionExists(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /**
     * The app's own page in the system notification settings, which is the one place a denial can be
     * undone — Android will not show the permission dialog again after two refusals, so without this
     * a "no" is permanent from the user's point of view.
     *
     * `FLAG_ACTIVITY_NEW_TASK` because this is handed the application context, not the Activity.
     * The fallback is the app's details page: `ACTION_APP_NOTIFICATION_SETTINGS` is documented from
     * API 26 but some OEM builds still do not resolve it, and dropping the user nowhere at all is
     * worse than dropping them one screen away from the switch.
     */
    override fun openSystemSettings() {
        val notificationSettings = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(notificationSettings)
            return
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "App notification settings did not resolve; falling back to app details", e)
        }

        val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(appDetails)
        } catch (e: ActivityNotFoundException) {
            // Nothing left to try. Logged rather than swallowed so a report of "the row does
            // nothing" is distinguishable from a report of "the row is not there".
            Log.e(TAG, "App details settings did not resolve either", e)
        }
    }
}

/**
 * The system permission dialog for `POST_NOTIFICATIONS`.
 *
 * The launcher is created unconditionally and the version check happens inside the returned
 * function, not around the `remember`: `rememberLauncherForActivityResult` claims a slot in the
 * composition, and a conditional call is the shape that breaks a composition when the condition
 * moves. `SDK_INT` does not move within a process, but writing it the fragile way invites the next
 * edit to make the condition something that does.
 */
@Composable
actual fun rememberNotificationPermissionRequester(
    onResult: (Boolean) -> Unit
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onResult(it) }
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Android 8-12: POST_NOTIFICATIONS does not exist, and launching it here throws.
            // Callers gate on shouldRequestNotificationPermission(), so reaching this is a bug in
            // the caller rather than a state a user can get into.
            Log.d(TAG, "No POST_NOTIFICATIONS below API 33; nothing requested")
        }
    }
}
