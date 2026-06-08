package com.muhabbet.app.platform

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Android actual: toggles `FLAG_SECURE` on the host Activity window.
 *
 * While [enabled] is true, the OS blocks screenshots + screen recording for this window and hides it
 * from the recent-apps preview. The flag is cleared when the effect leaves the composition or when
 * [enabled] becomes false, so privacy mode being OFF restores normal behaviour exactly.
 */
@Composable
actual fun SecureScreenEffect(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(enabled) {
        val activity = context as? Activity
        val window = activity?.window
        if (enabled) {
            window?.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            // Always clear on dispose so the flag never leaks to other screens.
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
