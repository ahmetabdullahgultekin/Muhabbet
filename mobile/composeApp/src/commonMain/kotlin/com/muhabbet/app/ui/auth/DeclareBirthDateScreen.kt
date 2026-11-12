package com.muhabbet.app.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.muhabbet.app.data.remote.ApiException
import com.muhabbet.app.data.repository.AuthRepository
import com.muhabbet.app.util.Log
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.birth_date_error_below_minimum
import com.muhabbet.composeapp.generated.resources.birth_date_error_invalid
import com.muhabbet.composeapp.generated.resources.error_generic
import com.muhabbet.composeapp.generated.resources.settings_logout
import com.muhabbet.shared.validation.ValidationRules
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Asks an account from before the age gate for its date of birth, once (#751).
 *
 * Shown after sign-in when the server reports `ageVerificationRequired`. The account is already
 * signed in; this declares the date through `PATCH /users/me/birth-date`. A date under the minimum
 * is still sent, so the server records it and a later attempt with another year is refused as well.
 */
@Composable
fun DeclareBirthDateScreen(
    onDeclared: () -> Unit,
    onSignedOut: () -> Unit,
    authRepository: AuthRepository = koinInject()
) {
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val invalidMsg = stringResource(Res.string.birth_date_error_invalid)
    val belowMinimumMsg = stringResource(Res.string.birth_date_error_below_minimum, ValidationRules.MIN_AGE_YEARS)
    val genericMsg = stringResource(Res.string.error_generic)

    BirthDateStep(
        isLoading = isLoading,
        error = error,
        sendBelowMinimum = true,
        onSubmit = { date ->
            isLoading = true
            error = null
            scope.launch {
                try {
                    authRepository.declareBirthDate(date)
                    onDeclared()
                } catch (e: Exception) {
                    Log.w(TAG, "Birth date declaration rejected: $e")
                    if (isAlreadyDeclared(e)) {
                        // Another device got there first; the account is already assured.
                        onDeclared()
                    } else {
                        error = birthDateErrorFor(e, invalidMsg, belowMinimumMsg) ?: genericMsg
                    }
                } finally {
                    isLoading = false
                }
            }
        },
        secondaryActionLabel = stringResource(Res.string.settings_logout),
        onSecondaryAction = {
            authRepository.logout()
            onSignedOut()
        }
    )
}

private const val TAG = "DeclareBirthDateScreen"

internal fun isAlreadyDeclared(e: Throwable): Boolean =
    (e as? ApiException)?.code == "AUTH_BIRTH_DATE_ALREADY_SET"
