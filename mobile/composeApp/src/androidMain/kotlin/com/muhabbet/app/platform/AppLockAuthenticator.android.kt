package com.muhabbet.app.platform

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * The combined authenticator set App Lock accepts: a class-3 ("strong") biometric — fingerprint,
 * face, iris — OR, when none is enrolled, the device's own screen-lock credential (PIN, pattern,
 * password). This is exactly what makes "must not offer a lock it cannot open" (#545) automatic:
 * [BiometricManager.canAuthenticate] answers false for a device with neither configured, and
 * `AppLockScreen` / `AppLockGate` both gate on [rememberAppLockCapability] before doing anything.
 *
 * Deliberately NOT `BIOMETRIC_WEAK` (face unlock without liveness on some OEMs) — App Lock is
 * guarding a private-messenger's chat list, and the strong tier is what the platform itself
 * classifies as spoof-resistant enough to unlock secure surfaces.
 */
private const val ALLOWED_AUTHENTICATORS = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

@Composable
actual fun rememberAppLockCapability(): Boolean {
    val context = LocalContext.current
    // Re-checked on every recomposition rather than `remember`-cached forever: capability can
    // change while the app is open (a user removes their fingerprint or their screen lock in system
    // settings), and #545 requires the app notice rather than keep offering a lock it can no longer
    // open. `AppLockGate` re-evaluates this on every recomposition it goes through anyway, which
    // includes every foreground transition — see AppLockGate.kt.
    return BiometricManager.from(context).canAuthenticate(ALLOWED_AUTHENTICATORS) ==
        BiometricManager.BIOMETRIC_SUCCESS
}

@Composable
actual fun rememberAppLockLauncher(title: String, subtitle: String, onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    // `BiometricPrompt` requires a `FragmentActivity` host (it hosts a headless Fragment internally
    // to survive configuration changes) — `MainActivity` extends it for exactly this. If the host
    // is ever something else (a preview environment, a future non-Activity entry point), fail
    // closed to "not authenticated" rather than crash: a security prompt that cannot be shown must
    // not silently unlock.
    val currentOnResult by rememberUpdatedState(onResult)

    val prompt = remember(activity) {
        activity?.let {
            BiometricPrompt(
                it,
                ContextCompat.getMainExecutor(it),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        currentOnResult(true)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        // Covers user cancellation, lockout after too many attempts, and the
                        // prompt being dismissed — all of them leave the app locked, which is the
                        // only safe default for anything this callback cannot positively confirm.
                        currentOnResult(false)
                    }

                    // onAuthenticationFailed (wrong finger/face) is deliberately NOT wired to
                    // onResult: the system prompt stays open and lets the user retry within the
                    // same sheet, exactly like unlocking the phone itself.
                }
            )
        }
    }

    val promptInfo = remember(title, subtitle) {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
            // A negative/cancel button is supplied by the system whenever DEVICE_CREDENTIAL is one
            // of the allowed authenticators (it becomes the "Use PIN instead" affordance) —
            // BiometricPrompt throws IllegalStateException if setNegativeButtonText is also called.
            .build()
    }

    return remember(prompt, promptInfo) {
        {
            if (prompt != null) {
                prompt.authenticate(promptInfo)
            } else {
                currentOnResult(false)
            }
        }
    }
}
