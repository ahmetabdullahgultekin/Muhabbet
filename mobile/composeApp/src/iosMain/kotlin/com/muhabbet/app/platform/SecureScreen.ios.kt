package com.muhabbet.app.platform

import androidx.compose.runtime.Composable

/**
 * iOS actual: **honest no-op stub** — screenshot suppression is NOT active on iOS yet.
 *
 * iOS has no direct `FLAG_SECURE` equivalent; the real implementation (overlaying a hidden
 * `UITextField(isSecureTextEntry = true)` layer on the window, plus reacting to
 * `UIApplication.userDidTakeScreenshotNotification`) must be written + verified against a live Xcode
 * build, which is not available on this host. Per the standing rule "a documented no-op is fine, a
 * silent fake is not" (see PR #61 for the same call on E2E): this does nothing and is intentionally
 * inert. It is currently unreachable in practice — `AppLockAuthenticator.ios.kt` reports no
 * capability, so `AppLockScreen` never lets App Lock be turned on on iOS in the first place.
 * TODO(ios): wire UIKit secure-overlay screenshot suppression alongside the LocalAuthentication
 * mechanism.
 */
@Composable
actual fun SecureScreenEffect(enabled: Boolean) {
    // No-op on iOS — see KDoc. Do not pretend to protect.
}
