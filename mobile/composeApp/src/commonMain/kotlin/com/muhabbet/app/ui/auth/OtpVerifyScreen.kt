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
import com.muhabbet.app.data.repository.AuthRepository
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
    var countdown by remember { mutableStateOf(if (firebaseVerificationId != null) 60 else 300) }
    var isResending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val firebasePhoneAuth = rememberFirebasePhoneAuth()
    val useFirebase = firebaseVerificationId != null && firebasePhoneAuth != null

    val verifyFailedMsg = stringResource(Res.string.otp_verify_failed)
    val genericErrorMsg = stringResource(Res.string.error_generic)

    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
    }

    // Hoisted out of the button's onClick so the boxed field can auto-submit on the sixth digit and
    // the button can still submit, without the verify path existing twice.
    val submit = {
        isLoading = true
        error = null
        scope.launch {
            try {
                if (useFirebase && firebasePhoneAuth != null && firebaseVerificationId != null) {
                    // Firebase: verify code → get ID token → exchange with backend
                    val idToken = firebasePhoneAuth.verifyCode(firebaseVerificationId, otp)
                    val result = authRepository.verifyFirebaseToken(
                        idToken = idToken,
                        deviceName = getDeviceModel(),
                        platform = getPlatformName()
                    )
                    onOtpVerified(result.isNewUser)
                } else {
                    // Mock/backend OTP: verify directly with backend
                    val result = authRepository.verifyOtp(
                        phoneNumber = phoneNumber,
                        otp = otp,
                        deviceName = getDeviceModel(),
                        platform = getPlatformName()
                    )
                    onOtpVerified(result.isNewUser)
                }
            } catch (e: Exception) {
                error = e.message ?: verifyFailedMsg
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

        if (mockCode != null) {
            Spacer(Modifier.height(MuhabbetSpacing.Medium))
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "Dev Mode — Code: $mockCode",
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

        error?.let {
            Spacer(Modifier.height(MuhabbetSpacing.Small))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
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
                                error = e.message ?: genericErrorMsg
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
            enabled = !isLoading && otp.length == OtpLength && countdown > 0,
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
private const val OtpLength = 6
