package com.muhabbet.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import com.muhabbet.designsystem.components.MuhabbetTopBar
import com.muhabbet.designsystem.theme.MuhabbetElevation
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.shared.dto.SetupTwoStepRequest
import com.muhabbet.shared.dto.TwoStepStatusResponse
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetScaffold
import com.muhabbet.designsystem.components.MuhabbetTextField
import com.muhabbet.designsystem.theme.containerColor
import com.muhabbet.designsystem.theme.depth
import com.muhabbet.designsystem.theme.MuhabbetDepth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TwoStepSetupScreen(
    onBack: () -> Unit,
    apiClient: ApiClient = koinInject()
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var isEnabled by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val genericErrorMsg = stringResource(Res.string.error_generic)
    val pinMismatchMsg = stringResource(Res.string.two_step_pin_mismatch)
    val pinLengthMsg = stringResource(Res.string.two_step_pin_length)
    val enabledMsg = stringResource(Res.string.two_step_enabled)

    LaunchedEffect(Unit) {
        val failure = runCatchingCancellable {
            val response = apiClient.get<TwoStepStatusResponse>("/api/v1/auth/two-step/status")
            isEnabled = response.data?.enabled ?: false
        }.exceptionOrNull()
        // Clear the spinner BEFORE reporting — showSnackbar suspends until dismissed (~4s).
        isLoading = false
        if (failure != null) {
            // Security-relevant: on failure isEnabled stays false and the screen claims two-step
            // verification is OFF when it may well be ON. Never let that pass unannounced.
            Log.e("TwoStepSetupScreen", "Failed to load two-step status", failure)
            snackbarHostState.showSnackbar(genericErrorMsg)
        }
    }

    MuhabbetScaffold(
        topBar = {
            MuhabbetTopBar(
                title = stringResource(Res.string.two_step_title),
                onBack = onBack,
                backContentDescription = stringResource(Res.string.action_back)
            )
        },
        snackbarHostState = snackbarHostState
    ) { padding ->
        if (isLoading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(MuhabbetSpacing.XXLarge))
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(MuhabbetSpacing.XLarge)
            ) {
                if (isEnabled) {
                    val statusCardShape = MaterialTheme.shapes.medium
                    Surface(
                        color = MuhabbetDepth.Raised.containerColor(),
                        shape = statusCardShape,
                        modifier = Modifier.fillMaxWidth().depth(MuhabbetDepth.Raised, statusCardShape)
                    ) {
                        Column(modifier = Modifier.padding(MuhabbetSpacing.Large)) {
                            Text(
                                text = stringResource(Res.string.two_step_enabled),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(MuhabbetSpacing.Large))
                            Button(
                                onClick = {
                                    scope.launch {
                                        isSaving = true
                                        try {
                                            apiClient.delete<Unit>("/api/v1/auth/two-step")
                                            isEnabled = false
                                        } catch (_: Exception) {
                                            snackbarHostState.showSnackbar(genericErrorMsg)
                                        }
                                        isSaving = false
                                    }
                                },
                                enabled = !isSaving,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(MuhabbetSizes.IconMedium),
                                        color = MaterialTheme.colorScheme.onError,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(stringResource(Res.string.two_step_disable))
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = stringResource(Res.string.two_step_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(MuhabbetSpacing.XLarge))

                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pin = it },
                        label = { Text(stringResource(Res.string.two_step_pin_hint)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(MuhabbetSpacing.Medium))

                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) confirmPin = it },
                        label = { Text(stringResource(Res.string.two_step_confirm_pin)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(MuhabbetSpacing.Medium))

                    MuhabbetTextField(
                        value = email,
                        onValueChange = { email = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(Res.string.two_step_email_hint),
                        singleLine = true,
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done
                    )

                    Spacer(Modifier.height(MuhabbetSpacing.XLarge))

                    Button(
                        onClick = {
                            scope.launch {
                                when {
                                    pin.length != 6 -> snackbarHostState.showSnackbar(pinLengthMsg)
                                    pin != confirmPin -> snackbarHostState.showSnackbar(pinMismatchMsg)
                                    else -> {
                                        isSaving = true
                                        try {
                                            apiClient.post<Unit>(
                                                "/api/v1/auth/two-step",
                                                SetupTwoStepRequest(
                                                    pin = pin,
                                                    email = email.ifBlank { null }
                                                )
                                            )
                                            isEnabled = true
                                            snackbarHostState.showSnackbar(enabledMsg)
                                        } catch (_: Exception) {
                                            snackbarHostState.showSnackbar(genericErrorMsg)
                                        }
                                        isSaving = false
                                    }
                                }
                            }
                        },
                        enabled = !isSaving && pin.isNotBlank() && confirmPin.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(MuhabbetSizes.IconMedium),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(stringResource(Res.string.two_step_enable))
                        }
                    }
                }
            }
        }
    }
}
