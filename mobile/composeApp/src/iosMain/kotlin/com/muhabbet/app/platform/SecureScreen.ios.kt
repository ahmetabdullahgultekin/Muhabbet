package com.muhabbet.app.platform

import androidx.compose.runtime.Composable

/**
 * iOS actual: **honest no-op stub** — screenshot suppression is NOT active on iOS yet.
 *
 * iOS has no direct `FLAG_SECURE` equivalent; the real implementation (overlaying a hidden
 * `UITextField(isSecureTextEntry = true)` layer on the window, plus reacting to
 * `UIApplication.userDidTakeScreenshotNotification`) must be written + verified against a live Xcode
 * build, which is not available on this host. Per owner guidance "a documented no-op is fine, a
 * silent fake is not": this does nothing and is intentionally inert. The Mahrem Mod UI only offers
 * the screenshot-guard toggle where it can be honoured (the Settings screen notes iOS is not yet
 * covered). TODO(ios): wire UIKit secure-overlay screenshot suppression.
 */
@Composable
actual fun SecureScreenEffect(enabled: Boolean) {
    // No-op on iOS — see KDoc. Do not pretend to protect.
}
