package com.muhabbet.app.platform

import androidx.compose.runtime.Composable

/**
 * Screenshot / screen-recording / recent-apps-thumbnail protection for App Lock (#378).
 *
 * A `@Composable` effect that, while it is in the composition with [enabled] = true, marks the
 * current window as secure so the OS blocks screenshots, screen recording, and — the part #378
 * specifically calls out — excludes the window from the recent-apps thumbnail. It must clean up
 * (clear the flag) when it leaves the composition or when [enabled] flips back to false.
 *
 * Tied to *App Lock being enabled at all*, not to the momentary locked/unlocked state
 * (`AppLockGate` calls this with `enabled = appLockEnabled`, unconditionally, near the top of the
 * gate, not only while the lock cover is showing). The recent-apps thumbnail is captured by the OS
 * at the moment the app leaves the foreground — the exact moment `AppLockGate` also decides whether
 * to re-arm, and a race between "is the flag already set" and "is the OS already snapshotting" is
 * not a race worth having on a security feature. Keeping the flag on for the whole time App Lock is
 * armed, rather than only while the lock cover is visible, removes that race entirely at the cost
 * of also blocking the user's own screenshots of their own chats while the feature is on — the same
 * trade-off Signal's "Screen Security" setting makes.
 *
 * - **Android (real):** sets/clears `WindowManager.LayoutParams.FLAG_SECURE` on the Activity window.
 * - **iOS (honest stub):** no live iOS toolchain on this host, so the actual is a documented no-op
 *   (UIKit screenshot suppression — overlaying a secure field / observing
 *   `userDidTakeScreenshotNotification` — must be wired against a real Xcode build). It does NOT
 *   silently pretend to protect; see the iOS actual's comment. `AppLockScreen` also cannot be turned
 *   on today on iOS (see `AppLockAuthenticator.ios.kt`), so this gap is currently unreachable there.
 */
@Composable
expect fun SecureScreenEffect(enabled: Boolean)
