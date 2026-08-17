package com.muhabbet.app.platform

import androidx.compose.runtime.Composable

/**
 * The App Lock (#378) mechanism: whether this device can actually enforce a lock, and how to ask
 * the platform to unlock it.
 *
 * Two platform-specific pieces, both `expect`/`actual` composables so each can read what it needs
 * from the local composition (`LocalContext` on Android; nothing yet on iOS):
 *
 * - [rememberAppLockCapability] answers "can a lock even be offered here" — the check `AppLockScreen`
 *   makes before letting the enable toggle turn on, and `AppLockGate` makes before treating the
 *   stored `enabled` flag as live. This is the direct answer to #545's requirement: "a phone with no
 *   enrolled fingerprint must not be offered a lock it cannot open." If the device loses its
 *   biometric/credential enrollment *after* App Lock was turned on (fingerprints wiped, screen lock
 *   removed), capability flips to false and the gate fails OPEN rather than stranding the user
 *   behind a lock nothing can open — see `AppLockGate.kt`.
 * - [rememberAppLockLauncher] returns a callback that triggers the platform's authentication UI.
 *
 * ## Why this is not a PIN
 *
 * #378's audit and #545 both call for a *real* mechanism, not a re-hash of #544's two-step PIN
 * (`TwoStepSetupScreen`) — and that PIN would be the wrong secret to reuse even if it were tempting
 * to save the work: it is server-verified (`TwoStepRepository.enable()` posts it to
 * `/api/v1/auth/two-step`), it is not stored on the device at all, and it exists to gate a
 * *different* thing (an extra factor the backend can demand). App Lock has to work fully offline,
 * on demand, the instant the app resumes — round-tripping to the backend to check a PIN on every
 * foreground would be slower, would brick the lock the moment the phone has no signal, and would
 * still not be "real" security, since anyone with the unlocked phone could read network traffic or
 * simply screen-record the PIN entry. `androidx.biometric.BiometricPrompt` with
 * `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` delegates to the OS's own biometric stack and, when no
 * biometric is enrolled, the device's own lock-screen PIN/pattern/password — so there is no new PIN
 * for this app to store, hash, or ever get wrong. See `AppLockAuthenticator.android.kt` for how the
 * result is tied to a real Keystore key rather than being a bare "did the callback fire" boolean.
 */
@Composable
expect fun rememberAppLockCapability(): Boolean

/**
 * Returns a callback that, when invoked, shows the platform's authentication UI and reports the
 * result exactly once via [onResult]. [title] and [subtitle] are shown by the platform's own
 * prompt UI (the system biometric sheet on Android), so they are passed in rather than resolved
 * internally — `stringResource` is `@Composable` and belongs to the caller, not to platform glue.
 */
@Composable
expect fun rememberAppLockLauncher(title: String, subtitle: String, onResult: (Boolean) -> Unit): () -> Unit
