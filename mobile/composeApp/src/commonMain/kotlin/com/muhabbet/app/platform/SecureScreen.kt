package com.muhabbet.app.platform

import androidx.compose.runtime.Composable

/**
 * Screenshot / screen-recording protection for **Mahrem Mod** (see
 * [com.muhabbet.app.config.PrivacyModeConfig]).
 *
 * A `@Composable` effect that, while it is in the composition with [enabled] = true, marks the
 * current window as secure so the OS blocks screenshots, screen recording, and excludes the window
 * from the recent-apps thumbnail. It must clean up (clear the flag) when it leaves the composition
 * or when [enabled] flips back to false.
 *
 * - **Android (real):** sets/clears `WindowManager.LayoutParams.FLAG_SECURE` on the Activity window.
 * - **iOS (honest stub):** no live iOS toolchain on this host, so the actual is a documented no-op
 *   (UIKit screenshot suppression — overlaying a secure field / observing
 *   `userDidTakeScreenshotNotification` — must be wired against a real Xcode build). It does NOT
 *   silently pretend to protect; see the iOS actual's comment.
 */
@Composable
expect fun SecureScreenEffect(enabled: Boolean)
