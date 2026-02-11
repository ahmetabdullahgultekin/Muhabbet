package com.muhabbet.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.muhabbet.app.data.repository.AuthRepository
import com.muhabbet.app.platform.getDeviceModel
import com.muhabbet.app.platform.getPlatformName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun OtpVerifyScreen(
    phoneNumber: String,
    onOtpVerified: (isNewUser: Boolean) -> Unit,
    onBack: () -> Unit = {},
    authRepository: AuthRepository = koinInject()
) {
    var otp by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var countdown by remember { mutableStateOf(300) }
    var isResending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val verifyFailedMsg = stringResource(Res.string.otp_verify_failed)
    val genericErrorMsg = stringResource(Res.string.error_generic)

    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(Res.string.otp_title),
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(Res.string.otp_subtitle, phoneNumber),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = otp,
            onValueChange = {
                if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                    otp = it
                    error = null
                }
            },
            label = { Text(stringResource(Res.string.otp_label)) },
            placeholder = { Text(stringResource(Res.string.otp_placeholder)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

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

            Spacer(Modifier.height(8.dp))

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
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(Res.string.otp_resend))
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                isLoading = true
                error = null
                scope.launch {
                    try {
                        val result = authRepository.verifyOtp(
                            phoneNumber = phoneNumber,
                            otp = otp,
                            deviceName = getDeviceModel(),
                            platform = getPlatformName()
                        )
                        onOtpVerified(result.isNewUser)
                    } catch (e: Exception) {
                        error = e.message ?: verifyFailedMsg
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading && otp.length == 6 && countdown > 0,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(stringResource(Res.string.otp_verify))
            }
        }

        Spacer(Modifier.height(12.dp))

        TextButton(onClick = onBack) {
            Text(stringResource(Res.string.otp_change_number))
        }
    }
}
