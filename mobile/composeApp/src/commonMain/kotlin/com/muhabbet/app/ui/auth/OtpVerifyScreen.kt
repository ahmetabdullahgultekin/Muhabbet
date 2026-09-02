package com.muhabbet.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.testTag
import com.muhabbet.app.data.remote.ApiException
import com.muhabbet.app.data.repository.AuthRepository
import com.muhabbet.app.util.Log
import com.muhabbet.designsystem.components.MuhabbetOtpField
import com.muhabbet.designsystem.components.MuhabbetStepRail
import com.muhabbet.designsystem.theme.MuhabbetGradients
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.app.platform.getDeviceModel
import com.muhabbet.app.platform.getPlatformName
import com.muhabbet.app.platform.rememberFirebasePhoneAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.muhabbet.app.BuildInfo
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.muhabbet.designsystem.components.MuhabbetButtonRole
import com.muhabbet.designsystem.components.MuhabbetButton

@Composable
fun OtpVerifyScreen(
    phoneNumber: String,
    mockCode: String? = null,
    firebaseVerificationId: String? = null,
    onOtpVerified: (isNewUser: Boolean) -> Unit,
    onBack: () -> Unit = {},
    authRepository: AuthRepository = koinInject()
) {
    var otp by remember { mutableStateOf(mockCode ?: "") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // The code this screen has already spent an attempt on. Null means nothing is outstanding.
    // A code and not a flag — see [shouldSubmitOtp].
    var submittedCode by remember { mutableStateOf<String?>(null) }
    // The second factor (#566). `twoStepRequired` is set only by the server's own
    // AUTH_2FA_PIN_REQUIRED — never guessed — so an account without one never sees this step.
    var twoStepRequired by remember { mutableStateOf(false) }
    var twoStepPin by remember { mutableStateOf("") }
    // The Firebase ID token from the first attempt, kept so the PIN retry does not have to redeem
    // the SMS code a second time. Firebase refuses a verification code it has already consumed, so
    // without this the retry would fail before it ever reached our own backend.
    var firebaseIdToken by remember { mutableStateOf<String?>(null) }
    var countdown by remember { mutableStateOf(if (firebaseVerificationId != null) 60 else 300) }
    var isResending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val firebasePhoneAuth = rememberFirebasePhoneAuth()
    val useFirebase = firebaseVerificationId != null && firebasePhoneAuth != null

    val verifyFailedMsg = stringResource(Res.string.otp_verify_failed)
    val genericErrorMsg = stringResource(Res.string.error_generic)
    val otpErrors = OtpErrorMessages(
        invalid = stringResource(Res.string.otp_error_invalid),
        expired = stringResource(Res.string.otp_error_expired),
        maxAttempts = stringResource(Res.string.otp_error_max_attempts),
        cooldown = stringResource(Res.string.otp_error_cooldown),
        twoStepInvalid = stringResource(Res.string.otp_error_two_step_invalid),
        twoStepLocked = stringResource(Res.string.otp_error_two_step_locked),
    )

    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
    }

    // Hoisted out of the button's onClick so the boxed field can auto-submit on the sixth digit and
    // the button can still submit, without the verify path existing twice.
    val submit = submit@{
        val code = otp
        // Null until the server has said a PIN is needed, so an ordinary sign-in sends nothing new.
        val pin = twoStepPin.takeIf { twoStepRequired }
        // Guarded HERE, inside the handler, and not by the button's `enabled` alone. `enabled` is a
        // composition input: it is re-read when Compose recomposes, so two dispatches landing in the
        // same frame — the field auto-submitting on the sixth digit plus a tap, or two fast taps —
        // both observe the stale value and both fire. A snapshot write made in an event handler is
        // visible to the very next read on the same thread, so the second caller sees what the first
        // wrote microseconds earlier. `enabled` below still mirrors the same predicate, so the
        // button is visibly dead rather than silently inert.
        if (!shouldSubmitOtp(code, pin, inFlight = isLoading, alreadySubmitted = submittedCode)) {
            return@submit
        }
        submittedCode = attemptKey(code, pin)
        isLoading = true
        error = null
        scope.launch {
            try {
                if (useFirebase && firebasePhoneAuth != null && firebaseVerificationId != null) {
                    // Firebase: verify code → get ID token → exchange with backend
                    // Remembered across attempts: Firebase refuses a verification code it has
                    // already redeemed, so a PIN retry that asked for a second token would fail
                    // before it ever reached our own backend.
                    val idToken = firebaseIdToken
                        ?: firebasePhoneAuth.verifyCode(firebaseVerificationId, code)
                            .also { firebaseIdToken = it }
                    val result = authRepository.verifyFirebaseToken(
                        idToken = idToken,
                        deviceName = getDeviceModel(),
                        platform = getPlatformName(),
                        twoStepPin = pin
                    )
                    onOtpVerified(result.isNewUser)
                } else {
                    // Mock/backend OTP: verify directly with backend
                    val result = authRepository.verifyOtp(
                        phoneNumber = phoneNumber,
                        otp = code,
                        deviceName = getDeviceModel(),
                        platform = getPlatformName(),
                        twoStepPin = pin
                    )
                    onOtpVerified(result.isNewUser)
                }
            } catch (e: Exception) {
                Log.w(TAG, "OTP verification rejected: $e")
                // A code the server actually judged is spent, and resending the same digits could
                // only lose a second attempt. A failure that never reached the counter — no network,
                // a 5xx, a Firebase error — releases the latch, so Verify stays usable without
                // making the user retype a code that was never found wrong.
                if (!consumedAnAttempt(e)) submittedCode = null
                if (requiresTwoStepPin(e)) {
                    // Not a failure to report — a step to show. The code was accepted; the account
                    // simply has a second factor, and the same code goes out again with the PIN.
                    twoStepRequired = true
                    error = null
                } else {
                    error = otpErrors.forFailure(e, verifyFailedMsg)
                }
            } finally {
                isLoading = false
            }
        }
        Unit
    }

    Column(
        // See PhoneInputScreen: no Scaffold here, so the insets must be consumed explicitly, and the
        // backdrop is painted before them so it reaches the screen edges.
        modifier = Modifier
            .fillMaxSize()
            .background(MuhabbetGradients.brandBackdrop)
            .safeDrawingPadding()
            .padding(MuhabbetSpacing.XLarge),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MuhabbetStepRail(current = 2, total = AuthSteps)

        Spacer(Modifier.height(MuhabbetSpacing.XLarge))

        Text(
            text = stringResource(Res.string.otp_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(MuhabbetSpacing.Small))

        Text(
            text = stringResource(Res.string.otp_subtitle, phoneNumber),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        // Gated on the build type, not on the server's answer. This used to render whenever the
        // backend returned a code, so a production build would have shown a verification code on
        // screen the moment mock mode was switched on server-side (#435).
        if (mockCode != null && BuildInfo.DEBUG) {
            Spacer(Modifier.height(MuhabbetSpacing.Medium))
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = stringResource(Res.string.otp_dev_mode_code, mockCode),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(
                        horizontal = MuhabbetSpacing.Medium,
                        vertical = MuhabbetSpacing.XSmall
                    )
                )
            }
        }

        Spacer(Modifier.height(MuhabbetSpacing.XXLarge))

        MuhabbetOtpField(
            value = otp,
            onValueChange = {
                otp = it
                error = null
                // Any edit makes this a different attempt, so the latch is released — including when
                // the user retypes the same six digits, because reaching them means passing through
                // shorter values first. This is what keeps the guard from being a debounce: it is
                // keyed on the code, not on the clock, so a genuine re-entry always goes through and
                // a duplicate never does, however fast or slow it arrives.
                submittedCode = null
            },
            modifier = Modifier.testTag("otp_input"),
            length = OtpLength,
            isError = error != null,
            enabled = !isLoading,
            // The code arrives by SMS and is six digits long; asking for a button press after the
            // sixth one is ceremony. The button stays for the case where autofill puts the code in
            // and the submit fails, so there is still something to press.
            onFilled = { submit() }
        )

        // Appears only after the server has asked for it. The account's own answer decides this,
        // never a client-side guess — a screen that offered a PIN box on spec would be telling the
        // user something about an account it has not been told anything about.
        if (twoStepRequired) {
            Spacer(Modifier.height(MuhabbetSpacing.Large))

            Text(
                text = stringResource(Res.string.otp_two_step_prompt),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(MuhabbetSpacing.Medium))

            MuhabbetOtpField(
                value = twoStepPin,
                onValueChange = {
                    twoStepPin = it
                    error = null
                    // Same latch rule as the code: any edit is a new attempt.
                    submittedCode = null
                },
                modifier = Modifier.testTag("two_step_pin_input"),
                length = TwoStepPinLength,
                isError = error != null,
                enabled = !isLoading,
                // Unlike the SMS code, the PIN is a secret the user already knows, and this screen
                // is used in public. The setup screen masks it; so does this one.
                masked = true,
                onFilled = { submit() }
            )
        }

        // One error slot, showing the newest reason. Once the code has expired, "that code is not
        // correct" is no longer true of anything — the code it referred to is gone — so showing both
        // left the user to work out which of two red messages applied (#403). Expiry is rendered
        // below and supersedes it.
        // `|| twoStepRequired` for the same reason the button ignores the clock there: a wrong-PIN
        // message must not be swallowed by an expiry notice about a code the server just accepted.
        if (countdown > 0 || twoStepRequired) {
            error?.let {
                Spacer(Modifier.height(MuhabbetSpacing.Small))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(MuhabbetSpacing.Small))

        if (countdown > 0) {
            val minutes = countdown / 60
            val seconds = countdown % 60
            val timeStr = "${minutes}:${seconds.toString().padStart(2, '0')}"
            Text(
                text = stringResource(Res.string.otp_countdown, timeStr),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = stringResource(Res.string.otp_expired),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(Modifier.height(MuhabbetSpacing.Small))

            if (!useFirebase) {
                // Resend only for mock/backend OTP (Firebase handles resend internally)
                OutlinedButton(
                    onClick = {
                        isResending = true
                        scope.launch {
                            try {
                                authRepository.requestOtp(phoneNumber)
                                countdown = 300
                                otp = ""
                                error = null
                            } catch (e: Exception) {
                                Log.w(TAG, "OTP resend rejected: $e")
                                error = otpErrors.forFailure(e, genericErrorMsg)
                            } finally {
                                isResending = false
                            }
                        }
                    },
                    enabled = !isResending
                ) {
                    if (isResending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(MuhabbetSizes.IconSmall),
                            strokeWidth = MuhabbetSizes.ProgressStrokeThin
                        )
                    } else {
                        Text(stringResource(Res.string.otp_resend))
                    }
                }
            }
        }

        Spacer(Modifier.height(MuhabbetSpacing.Large))

        Button(
            onClick = submit,
            enabled = shouldSubmitOtp(
                otp,
                twoStepPin.takeIf { twoStepRequired },
                inFlight = isLoading,
                alreadySubmitted = submittedCode
            // The countdown gate does not apply once the PIN step is open. It is a client-side
            // clock, and reaching this step means the server accepted the code seconds ago — on the
            // Firebase path that clock is 60s, which is not long to be handed a second factor and
            // asked to remember a PIN. If the code really has expired the server says so, and the
            // screen shows that instead of a dead button with no explanation.
            ) && (countdown > 0 || twoStepRequired),
            modifier = Modifier.fillMaxWidth().testTag("otp_verify")
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(MuhabbetSizes.IconMedium),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = MuhabbetSizes.ProgressStrokeThin
                )
            } else {
                Text(stringResource(Res.string.otp_verify))
            }
        }

        Spacer(Modifier.height(MuhabbetSpacing.Medium))

        MuhabbetButton(
            text = stringResource(Res.string.otp_change_number),
            onClick = onBack,
            role = MuhabbetButtonRole.Text
        )
    }
}

/** Digits in a verification code. Both the field and the submit guard read it. */
internal const val OtpLength = 6

/**
 * Digits in a two-step PIN. Kept equal to `ValidationRules.TWO_STEP_PIN_LENGTH`, which is what both
 * the setup screen and the server enforce — a field that accepted a different length would offer a
 * PIN no account could ever hold.
 */
internal const val TwoStepPinLength = 6

private const val TAG = "OtpVerifyScreen"

/**
 * Whether [code] may be sent to the server right now.
 *
 * Three conditions, and the third is the one #400 was missing. A verification code is worth one of
 * five attempts, the server claims that attempt *before* it compares anything, and this screen has
 * two ways to submit: the field auto-submits on the sixth digit and the button submits on a tap.
 * Nothing stopped both from firing for the same six digits.
 *
 * [alreadySubmitted] is a **code and not a flag**, deliberately. A flag has to be cleared, and the
 * only thing available to clear it is a timer — which is a debounce, and a debounce is wrong in both
 * directions: too short and a slow duplicate still gets through, too long and a user who genuinely
 * wants to try again is told to wait. Keyed on the code there is no window at all. A duplicate is
 * refused however fast it arrives, and a re-entry is accepted however fast it arrives, because
 * entering a code means editing the field and editing the field clears the latch.
 *
 * [inFlight] closes the same-frame race on its own terms: it is read inside the event handler rather
 * than through the button's `enabled`, which Compose only re-evaluates on recomposition.
 */
internal fun shouldSubmitOtp(
    code: String,
    pin: String?,
    inFlight: Boolean,
    alreadySubmitted: String?
): Boolean =
    code.length == OtpLength &&
        (pin == null || pin.length == TwoStepPinLength) &&
        !inFlight &&
        attemptKey(code, pin) != alreadySubmitted

/**
 * What the latch actually holds: the code, plus the PIN when there is one (#566).
 *
 * The latch could not stay keyed on the code alone once a second factor existed. The two-step step
 * resends the **same** six digits with a different PIN, so a code-only key would refuse every
 * correction the user made to a mistyped PIN — the screen would go dead holding a wrong answer.
 */
internal fun attemptKey(code: String, pin: String?): String =
    if (pin == null) code else "$code:$pin"

/**
 * The rejections that mean the server already counted an attempt against the code just sent.
 *
 * `AuthService.verifyOtp` claims an attempt before it compares the code, so both of these are
 * answers *about that code* and the guess is spent. `AUTH_OTP_EXPIRED` is thrown before the claim
 * and so is absent: nothing was counted, though nothing can be retried either.
 */
private val AttemptConsumingCodes = setOf("AUTH_OTP_INVALID", "AUTH_OTP_MAX_ATTEMPTS")

/**
 * Whether [e] means an attempt was spent, and so whether the same digits must never go out again.
 *
 * Anything that is not an answer from the OTP endpoint — no network, a 5xx, a Firebase failure —
 * never reached the counter, so the latch is released and the user can press Verify again without
 * retyping a code that was never found wrong.
 */
internal fun consumedAnAttempt(e: Throwable): Boolean =
    (e as? ApiException)?.code in AttemptConsumingCodes

/**
 * Whether [e] means "the code was right, but this account has a second factor" (#566).
 *
 * Deliberately not in [AttemptConsumingCodes]: the server hands the OTP attempt back in this case,
 * because a correct code is not a guess and the round trip that discovers a PIN is needed must not
 * be charged to the SMS budget. The screen must therefore be free to resend the same digits.
 */
internal fun requiresTwoStepPin(e: Throwable): Boolean =
    (e as? ApiException)?.code == "AUTH_2FA_PIN_REQUIRED"

/**
 * The four rejections the OTP endpoints report in normal use, in the device's language.
 *
 * A mistyped code is not a malfunction, it is the expected answer to a typo, and this screen is the
 * only feedback the user gets — the boxed field auto-submits on the sixth digit. Until now the
 * screen printed `e.message`, which is the backend's own `ErrorCode.defaultMessage`: hardcoded
 * Turkish, shown verbatim to an English-locale user, and free to become an HTTP status line the
 * moment the request fails before reaching the application.
 *
 * Resolved at composition because `stringResource` is `@Composable` and the failures arrive inside
 * `scope.launch`.
 */
private class OtpErrorMessages(
    val invalid: String,
    val expired: String,
    val maxAttempts: String,
    val cooldown: String,
    val twoStepInvalid: String,
    val twoStepLocked: String,
) {
    /**
     * [fallback] covers everything else — a 500, a dead network, a Firebase failure — because those
     * are malfunctions, and naming them precisely helps nobody holding a phone.
     */
    fun forFailure(e: Throwable, fallback: String): String = when ((e as? ApiException)?.code) {
        "AUTH_OTP_INVALID" -> invalid
        "AUTH_OTP_EXPIRED" -> expired
        "AUTH_OTP_MAX_ATTEMPTS" -> maxAttempts
        "AUTH_OTP_COOLDOWN", "AUTH_OTP_RATE_LIMIT" -> cooldown
        "AUTH_2FA_PIN_INVALID" -> twoStepInvalid
        "AUTH_2FA_LOCKED" -> twoStepLocked
        else -> fallback
    }
}
