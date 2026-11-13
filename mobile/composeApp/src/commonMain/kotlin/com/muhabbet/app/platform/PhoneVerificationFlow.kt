package com.muhabbet.app.platform

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The first thing a phone-verification SDK tells us about a request, as plain data (#860).
 *
 * Firebase reports through three callbacks, and the old adapter resumed its coroutine from inside
 * each one by hand. One of those paths — auto-verification whose follow-up sign-in failed — had no
 * `else` branch, so the coroutine was never resumed and the login screen, the app's very first
 * screen, spun forever. Reducing the callbacks to one event and deciding the outcome afterwards, in
 * one exhaustive `when`, is what makes "a path that never answers" impossible to write.
 *
 * [C] is the SDK's credential type and [R] its resend token, opaque here so the flow is testable on
 * the JVM without Play services.
 */
sealed interface PhoneVerificationEvent<out C, out R> {
    data class CodeSent<R>(val verificationId: String, val resendToken: R?) :
        PhoneVerificationEvent<Nothing, R>

    data class Completed<C>(val credential: C) : PhoneVerificationEvent<C, Nothing>

    data class Failed(val code: PhoneAuthErrorCode) : PhoneVerificationEvent<Nothing, Nothing>
}

/** What [PhoneVerificationFlow] needs from the SDK. Each suspend call returns or throws. */
interface PhoneVerificationDriver<C, R> {
    /**
     * Starts verification and reports events through [onEvent]; only the first one is used.
     * [resendToken] is the token from an earlier `CodeSent` for the same number, if any — passing it
     * is what makes the SDK treat the call as a resend instead of a fresh, quota-burning start.
     */
    fun requestCode(
        phoneNumber: String,
        resendToken: R?,
        onEvent: (PhoneVerificationEvent<C, R>) -> Unit
    )

    /** Signs in with an auto-retrieved credential and returns the ID token, or null if none. */
    suspend fun signIn(credential: C): String?

    /** Signs in with a typed code and returns the ID token, or null if none. */
    suspend fun signInWithCode(verificationId: String, code: String): String?
}

/**
 * Phone verification with exhaustive outcomes (#860). Every path ends in exactly one
 * [PhoneVerificationResult]: a code was sent, a non-blank ID token was obtained, or a typed
 * [PhoneAuthErrorCode] — including [PhoneAuthErrorCode.TIMEOUT] when the SDK never answers at all,
 * and [PhoneAuthErrorCode.SIGN_IN_FAILED] where the old code either hung or resumed with
 * `AutoVerified("")` for the backend to reject as if the code were wrong.
 *
 * It also keeps the resend token of the last `CodeSent` for a number, which the old adapter
 * discarded, so asking again for the same number is a resend rather than a new verification.
 */
class PhoneVerificationFlow<C, R>(
    private val driver: PhoneVerificationDriver<C, R>,
    private val firstEventTimeout: Duration = DEFAULT_FIRST_EVENT_TIMEOUT,
) {
    private var lastCodeSent: Pair<String, R>? = null

    suspend fun start(phoneNumber: String): PhoneVerificationResult {
        val first = CompletableDeferred<PhoneVerificationEvent<C, R>>()
        val resend = lastCodeSent?.takeIf { it.first == phoneNumber }?.second
        try {
            driver.requestCode(phoneNumber, resend) { first.complete(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The SDK refused to even start — no Play services, no FirebaseApp, a bad option.
            return PhoneVerificationResult.Error(PhoneAuthErrorCode.CONFIGURATION)
        }
        val event = withTimeoutOrNull(firstEventTimeout) { first.await() }
            ?: return PhoneVerificationResult.Error(PhoneAuthErrorCode.TIMEOUT)

        return when (event) {
            is PhoneVerificationEvent.CodeSent -> {
                lastCodeSent = event.resendToken?.let { phoneNumber to it }
                PhoneVerificationResult.CodeSent(event.verificationId)
            }
            is PhoneVerificationEvent.Completed -> when (val out = idToken { driver.signIn(event.credential) }) {
                is TokenOutcome.Token -> PhoneVerificationResult.AutoVerified(out.value)
                is TokenOutcome.Failure -> PhoneVerificationResult.Error(out.code)
            }
            is PhoneVerificationEvent.Failed -> PhoneVerificationResult.Error(event.code)
        }
    }

    /** Returns a non-blank ID token, or throws [PhoneAuthException]. Never an empty token. */
    suspend fun verifyCode(verificationId: String, code: String): String =
        when (val out = idToken { driver.signInWithCode(verificationId, code) }) {
            is TokenOutcome.Token -> out.value
            is TokenOutcome.Failure -> throw PhoneAuthException(out.code)
        }

    private sealed interface TokenOutcome {
        data class Token(val value: String) : TokenOutcome
        data class Failure(val code: PhoneAuthErrorCode) : TokenOutcome
    }

    /**
     * A non-blank token, or a typed failure: the driver's own [PhoneAuthException] code when it
     * classified the error, [PhoneAuthErrorCode.SIGN_IN_FAILED] for anything else — a thrown
     * sign-in, a null token or a blank one.
     */
    private suspend inline fun idToken(signIn: () -> String?): TokenOutcome =
        try {
            signIn()?.takeIf { it.isNotBlank() }?.let { TokenOutcome.Token(it) }
                ?: TokenOutcome.Failure(PhoneAuthErrorCode.SIGN_IN_FAILED)
        } catch (e: CancellationException) {
            throw e
        } catch (e: PhoneAuthException) {
            TokenOutcome.Failure(e.code)
        } catch (_: Exception) {
            TokenOutcome.Failure(PhoneAuthErrorCode.SIGN_IN_FAILED)
        }

    companion object {
        /**
         * How long to wait for the SDK's first answer. `onCodeSent` normally arrives within seconds;
         * Firebase's own auto-retrieval window is 60 s, so 90 s of silence means it is not going to
         * answer, and the user is better served by the backend-OTP fallback than by a spinner.
         */
        val DEFAULT_FIRST_EVENT_TIMEOUT: Duration = 90.seconds
    }
}
