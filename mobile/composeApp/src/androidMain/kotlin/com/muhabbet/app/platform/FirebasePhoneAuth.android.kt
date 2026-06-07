package com.muhabbet.app.platform

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.Firebase
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AndroidFirebasePhoneAuth(
    private val activity: ComponentActivity
) : FirebasePhoneAuth {

    private val firebaseAuth = Firebase.auth

    override fun isAvailable(): Boolean = true

    override suspend fun startVerification(phoneNumber: String): PhoneVerificationResult =
        suspendCancellableCoroutine { cont ->
            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    if (cont.isActive) {
                        cont.resume(PhoneVerificationResult.CodeSent(verificationId))
                    }
                }

                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    firebaseAuth.signInWithCredential(credential)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful && cont.isActive) {
                                task.result?.user?.getIdToken(true)
                                    ?.addOnSuccessListener { result ->
                                        if (cont.isActive) {
                                            cont.resume(
                                                PhoneVerificationResult.AutoVerified(
                                                    result.token ?: ""
                                                )
                                            )
                                        }
                                    }
                                    ?.addOnFailureListener {
                                        if (cont.isActive) {
                                            cont.resume(PhoneVerificationResult.Error(it.message ?: "Token error"))
                                        }
                                    }
                            }
                        }
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    if (cont.isActive) {
                        cont.resume(
                            PhoneVerificationResult.Error(
                                message = e.message ?: "Verification failed",
                                code = classifyFirebaseError(e)
                            )
                        )
                    }
                }
            }

            val options = PhoneAuthOptions.newBuilder(firebaseAuth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
                .build()

            PhoneAuthProvider.verifyPhoneNumber(options)
        }

    override suspend fun verifyCode(verificationId: String, code: String): String =
        suspendCancellableCoroutine { cont ->
            val credential = PhoneAuthProvider.getCredential(verificationId, code)
            firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        task.result?.user?.getIdToken(true)
                            ?.addOnSuccessListener { result ->
                                val token = result.token
                                if (token != null) {
                                    cont.resume(token)
                                } else {
                                    cont.resumeWithException(Exception("Failed to get ID token"))
                                }
                            }
                            ?.addOnFailureListener { e ->
                                cont.resumeWithException(e)
                            }
                    } else {
                        cont.resumeWithException(
                            task.exception ?: Exception("Verification failed")
                        )
                    }
                }
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
