package com.muhabbet.app.platform

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Firebase phone auth for Android (#860).
 *
 * The outcome logic lives in the platform-neutral [PhoneVerificationFlow], which is unit-tested
 * with a fake driver; this class only adapts Firebase to [PhoneVerificationDriver]. Every Task is
 * bridged with `kotlinx-coroutines-play-services`' `await()`, so a failed Task throws instead of —
 * as the hand-written listeners did — silently never resuming.
 */
class AndroidFirebasePhoneAuth(
    private val activity: ComponentActivity
) : FirebasePhoneAuth {

    private val flow = PhoneVerificationFlow(FirebaseDriver(activity))

    /**
     * Was a hardcoded `true`, even in a build carrying the CI dummy `google-services.json`. Now true
     * only when a FirebaseApp was initialised from real options and Play services is usable; when it
     * is false the login screen goes straight to the backend OTP instead of failing into it.
     */
    override fun isAvailable(): Boolean {
        val app = FirebaseApp.getApps(activity).firstOrNull() ?: return false
        if (app.options.apiKey.isBlank() || app.options.apiKey == CI_DUMMY_API_KEY) return false
        return GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(activity) == ConnectionResult.SUCCESS
    }

    override suspend fun startVerification(phoneNumber: String): PhoneVerificationResult =
        flow.start(phoneNumber)

    override suspend fun verifyCode(verificationId: String, code: String): String =
        flow.verifyCode(verificationId, code)

    private companion object {
        /** The api_key of the placeholder google-services.json that CI writes when no secret is set. */
        const val CI_DUMMY_API_KEY = "CI_DUMMY_KEY"
    }
}

/** The thin Firebase half of [PhoneVerificationFlow]. No outcome decisions are made here. */
private class FirebaseDriver(
    private val activity: ComponentActivity,
    private val auth: FirebaseAuth = Firebase.auth,
) : PhoneVerificationDriver<PhoneAuthCredential, PhoneAuthProvider.ForceResendingToken> {

    override fun requestCode(
        phoneNumber: String,
        resendToken: PhoneAuthProvider.ForceResendingToken?,
        onEvent: (PhoneVerificationEvent<PhoneAuthCredential, PhoneAuthProvider.ForceResendingToken>) -> Unit
    ) {
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) =
                onEvent(PhoneVerificationEvent.CodeSent(id, token))

            override fun onVerificationCompleted(credential: PhoneAuthCredential) =
                onEvent(PhoneVerificationEvent.Completed(credential))

            override fun onVerificationFailed(e: FirebaseException) =
                onEvent(PhoneVerificationEvent.Failed(classifyFirebaseError(e)))
        }
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(AUTO_RETRIEVAL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .apply { resendToken?.let { setForceResendingToken(it) } }
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    override suspend fun signIn(credential: PhoneAuthCredential): String? = idTokenFor(credential)

    override suspend fun signInWithCode(verificationId: String, code: String): String? =
        idTokenFor(PhoneAuthProvider.getCredential(verificationId, code))

    private suspend fun idTokenFor(credential: AuthCredential): String? = try {
        val user = auth.signInWithCredential(credential).await().user
        user?.getIdToken(true)?.await()?.token
    } catch (e: FirebaseException) {
        // Keep Firebase's own classification (e.g. a rejected code) instead of a generic failure.
        throw PhoneAuthException(classifyFirebaseError(e))
    }

    private companion object {
        const val AUTO_RETRIEVAL_TIMEOUT_SECONDS = 60L
    }
}

/**
 * Maps a Firebase exception to a locale-independent [PhoneAuthErrorCode].
 *
 * Prefers `FirebaseAuthException.errorCode` (a stable, locale-invariant `ERROR_*` constant) over
 * the human-readable `message` — the message is localized and so unreliable for branching on
 * Turkish-locale devices. Falls back to a Locale.ROOT substring scan of the message only when no
 * structured code is present (e.g. a non-auth FirebaseException).
 */
internal fun classifyFirebaseError(e: FirebaseException): PhoneAuthErrorCode {
    val errorCode = (e as? FirebaseAuthException)?.errorCode?.uppercase(java.util.Locale.ROOT)
    if (errorCode != null) {
        return when (errorCode) {
            "ERROR_TOO_MANY_REQUESTS",
            "ERROR_QUOTA_EXCEEDED" -> PhoneAuthErrorCode.RATE_LIMITED

            "ERROR_API_NOT_AVAILABLE",
            "ERROR_INVALID_API_KEY",
            "ERROR_APP_NOT_AUTHORIZED",
            "ERROR_INTERNAL_ERROR",
            "ERROR_WEB_CONTEXT_CANCELLED",
            "ERROR_MISSING_CLIENT_IDENTIFIER",
            "ERROR_APP_NOT_VERIFIED" -> PhoneAuthErrorCode.CONFIGURATION

            "ERROR_INVALID_PHONE_NUMBER",
            "ERROR_MISSING_PHONE_NUMBER" -> PhoneAuthErrorCode.INVALID_PHONE

            else -> PhoneAuthErrorCode.UNKNOWN
        }
    }
    // No structured code (rare): fall back to a locale-invariant scan of the message.
    return classifyFirebaseMessage(e.message)
}

/**
 * Last-resort, locale-invariant classification from raw message text. Used only when no structured
 * Firebase error code is available. Uses [java.util.Locale.ROOT] so Turkish `i`/`I` folding can't
 * break the match.
 */
internal fun classifyFirebaseMessage(rawMessage: String?): PhoneAuthErrorCode {
    val msg = (rawMessage ?: "").lowercase(java.util.Locale.ROOT)
    return when {
        listOf("block", "too many", "unusual", "quota").any { msg.contains(it) } ->
            PhoneAuthErrorCode.RATE_LIMITED
        listOf(
            "api key", "not valid", "internal error", "configuration",
            "developer error", "app not authorized", "invalid-api-key"
        ).any { msg.contains(it) } -> PhoneAuthErrorCode.CONFIGURATION
        else -> PhoneAuthErrorCode.UNKNOWN
    }
}

@Composable
actual fun rememberFirebasePhoneAuth(): FirebasePhoneAuth? {
    val context = LocalContext.current
    val activity = context as? ComponentActivity ?: return null
    return remember { AndroidFirebasePhoneAuth(activity) }
}
