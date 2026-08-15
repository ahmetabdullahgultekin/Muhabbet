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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.testTag
import com.muhabbet.app.data.repository.AuthRepository
import com.muhabbet.designsystem.components.MuhabbetBrandMark
import com.muhabbet.designsystem.components.MuhabbetStepRail
import com.muhabbet.designsystem.components.MuhabbetTextField
import com.muhabbet.designsystem.theme.MuhabbetGradients
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.app.platform.PhoneAuthErrorCode
import com.muhabbet.app.platform.PhoneVerificationResult
import com.muhabbet.app.platform.getDeviceModel
import com.muhabbet.app.platform.getPlatformName
import com.muhabbet.app.platform.rememberFirebasePhoneAuth
import com.muhabbet.shared.validation.ValidationRules
import kotlinx.coroutines.launch
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun PhoneInputScreen(
    onPhoneSubmitted: (phoneNumber: String, mockCode: String?, firebaseVerificationId: String?) -> Unit,
    onFirebaseAutoVerified: (isNewUser: Boolean) -> Unit = {},
    authRepository: AuthRepository = koinInject()
) {
    var phoneNumber by remember { mutableStateOf("+90") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val firebasePhoneAuth = rememberFirebasePhoneAuth()
    val useFirebase = firebasePhoneAuth?.isAvailable() == true

    val invalidPhoneMsg = stringResource(Res.string.phone_invalid)
    // Resolved here (not inside scope.launch) because stringResource is @Composable.
    val authFailedMsg = stringResource(Res.string.phone_auth_failed)

    Column(
        // safeDrawingPadding, not a Scaffold: this screen has no app bar to consume insets, and
        // since targetSdk 36 the content draws behind the system bars whether it asks to or not.
        // The backdrop is painted before the inset padding so it reaches the screen edges.
        modifier = Modifier
            .fillMaxSize()
            .background(MuhabbetGradients.brandBackdrop)
            .safeDrawingPadding()
            .padding(MuhabbetSpacing.XLarge),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MuhabbetBrandMark()

        Spacer(Modifier.height(MuhabbetSpacing.XLarge))

        Text(
            text = stringResource(Res.string.phone_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(MuhabbetSpacing.Small))

        Text(
            text = stringResource(Res.string.phone_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(MuhabbetSpacing.XLarge))

        MuhabbetStepRail(current = 1, total = AuthSteps)

        Spacer(Modifier.height(MuhabbetSpacing.XLarge))

        MuhabbetTextField(
            value = phoneNumber,
            onValueChange = {
                if (it.length <= 13) phoneNumber = it
                error = null
            },
            modifier = Modifier.fillMaxWidth().testTag("phone_input"),
            label = stringResource(Res.string.phone_label),
            placeholder = stringResource(Res.string.phone_placeholder),
            error = error,
            keyboardType = KeyboardType.Phone
        )

        Spacer(Modifier.height(MuhabbetSpacing.Large))

        Button(
            onClick = {
                if (!ValidationRules.isValidTurkishPhone(phoneNumber)) {
                    error = invalidPhoneMsg
                    return@Button
                }
                isLoading = true
                error = null
                scope.launch {
                    try {
                        if (useFirebase && firebasePhoneAuth != null) {
                            // Firebase Phone Auth flow
                            when (val result = firebasePhoneAuth.startVerification(phoneNumber)) {
                                is PhoneVerificationResult.CodeSent -> {
                                    onPhoneSubmitted(phoneNumber, null, result.verificationId)
                                }
                                is PhoneVerificationResult.AutoVerified -> {
                                    // Auto-verified, exchange Firebase token for our JWT
                                    val authResult = authRepository.verifyFirebaseToken(
                                        idToken = result.idToken,
                                        deviceName = getDeviceModel(),
                                        platform = getPlatformName()
                                    )
                                    onFirebaseAutoVerified(authResult.isNewUser)
                                }
                                is PhoneVerificationResult.Error -> {
                                    // Firebase rate-limited OR misconfigured — fallback to backend OTP.
                                    // Branch on the STRUCTURED code (locale-independent), not message text.
                                    if (shouldFallbackToBackendOtp(result.code)) {
                                        try {
                                            val response = authRepository.requestOtp(phoneNumber)
                                            onPhoneSubmitted(phoneNumber, response.mockCode, null)
                                        } catch (_: Exception) {
                                            error = authFailedMsg
                                        }
                                    } else {
                                        error = authFailedMsg
                                    }
                                }
                            }
                        } else {
                            // Mock/backend OTP flow
                            val response = authRepository.requestOtp(phoneNumber)
                            onPhoneSubmitted(phoneNumber, response.mockCode, null)
                        }
                    } catch (e: Exception) {
                        // Firebase may throw directly (rate limiting or misconfiguration). No
                        // structured code here — last-resort, locale-invariant message scan.
                        if (useFirebase && shouldFallbackForRawMessage(e.message)) {
                            try {
                                val response = authRepository.requestOtp(phoneNumber)
                                onPhoneSubmitted(phoneNumber, response.mockCode, null)
                            } catch (_: Exception) {
                                error = authFailedMsg
                            }
                        } else {
                            error = authFailedMsg
                        }
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading && phoneNumber.length >= 13,
            modifier = Modifier.fillMaxWidth().testTag("phone_continue")
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(MuhabbetSizes.IconMedium),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = MuhabbetSizes.ProgressStrokeThin
                )
            } else {
                Text(stringResource(Res.string.phone_continue))
            }
        }
    }
}

/**
 * Whether a Firebase phone-auth failure should degrade gracefully to the backend OTP flow.
 *
 * Branches on the STRUCTURED [PhoneAuthErrorCode] (mapped from Firebase's locale-invariant
 * `errorCode` on the platform side) — NOT on localized message text, which false-negatives on
 * Turkish-locale devices. Covers transient throttling (rate limiting) AND Firebase
 * configuration/internal errors, so a misconfigured build still lets the user log in via the
 * backend. INVALID_PHONE is surfaced (not hidden by a fallback) so the user can fix the number.
 * Firebase remains the primary path.
 */
private fun shouldFallbackToBackendOtp(code: PhoneAuthErrorCode): Boolean = when (code) {
    PhoneAuthErrorCode.RATE_LIMITED,
    PhoneAuthErrorCode.CONFIGURATION -> true
    PhoneAuthErrorCode.INVALID_PHONE,
    PhoneAuthErrorCode.UNKNOWN -> false
}

/**
 * Last-resort fallback decision when Firebase throws directly (no structured code available).
 *
 * Uses Kotlin's locale-invariant [String.lowercase] (Unicode default case mapping — NOT the
 * platform locale, so safe under Turkish `i`/`I` folding) for the substring scan. This path is
 * only hit on a thrown exception; the normal [PhoneVerificationResult.Error] path uses the
 * structured code above.
 */
private fun shouldFallbackForRawMessage(rawMessage: String?): Boolean {
    val msg = (rawMessage ?: "").lowercase()
    val triggers = listOf(
        // Rate limiting / abuse protection
        "block", "too many", "unusual", "quota",
        // Configuration / internal errors
        "api key", "not valid", "internal error", "configuration",
        "developer error", "app not authorized", "invalid-api-key"
    )
    return triggers.any { msg.contains(it) }
}

/**
 * How many screens sign-up is. Read by all three of them so the rail cannot disagree with itself —
 * a progress indicator that says "2 of 3" on a two-step flow is worse than no indicator.
 */
internal const val AuthSteps = 3
