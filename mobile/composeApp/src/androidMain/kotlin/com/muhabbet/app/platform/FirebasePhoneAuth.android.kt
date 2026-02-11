package com.muhabbet.app.platform

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.FirebaseException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
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
                        cont.resume(PhoneVerificationResult.Error(e.message ?: "Verification failed"))
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

@Composable
actual fun rememberFirebasePhoneAuth(): FirebasePhoneAuth? {
    val context = LocalContext.current
    val activity = context as? ComponentActivity ?: return null
    return remember { AndroidFirebasePhoneAuth(activity) }
}
