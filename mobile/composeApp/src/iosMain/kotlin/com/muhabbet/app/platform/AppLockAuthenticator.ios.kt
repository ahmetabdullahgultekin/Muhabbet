package com.muhabbet.app.platform

import androidx.compose.runtime.Composable

/**
 * iOS actual: **honest no-op stub** — App Lock has no working mechanism on iOS yet.
 *
 * The real implementation is Apple's `LocalAuthentication` framework (`LAContext.evaluatePolicy`
 * with `.deviceOwnerAuthentication`, which offers Face ID / Touch ID and falls back to the device
 * passcode — the same shape as the Android combined-authenticator design above), bridged through
 * Kotlin/Native. That requires a live Xcode build to write and verify against a real
 * `LAContext`/`Info.plist` `NSFaceIDUsageDescription` entry, neither of which exists on this host
 * (see CLAUDE.md's iOS build notes).
 *
 * [rememberAppLockCapability] always returning `false` is what keeps this honest rather than
 * silently broken: `AppLockScreen` reads it before showing the enable toggle at all, so nothing on
 * iOS can ever set `TokenStorage.getAppLockEnabled()` to `true` in the first place — there is no
 * state where [rememberAppLockLauncher]'s callback is reachable in production. It exists only to
 * satisfy the `expect`/`actual` contract, and it fails closed (reports "not authenticated") on the
 * off chance it is ever invoked, rather than a bare `TODO()` that would crash instead of degrade.
 *
 * TODO(ios): wire `LocalAuthentication` here in the same change that wires
 * `SecureScreen.ios.kt`'s UIKit screenshot suppression — both are gated on the same missing
 * toolchain, not on each other.
 */
@Composable
actual fun rememberAppLockCapability(): Boolean = false

@Composable
actual fun rememberAppLockLauncher(title: String, subtitle: String, onResult: (Boolean) -> Unit): () -> Unit =
    { onResult(false) }
