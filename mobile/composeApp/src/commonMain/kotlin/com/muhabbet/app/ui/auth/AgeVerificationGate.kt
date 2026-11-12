package com.muhabbet.app.ui.auth

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.muhabbet.app.data.repository.AuthRepository
import com.muhabbet.app.util.Log
import com.muhabbet.shared.model.UserProfile
import kotlinx.coroutines.CancellationException
import org.koin.compose.koinInject

/**
 * Asks a signed-in account with no assured age for its date of birth (#751).
 *
 * Mounted in `RootContent` over the signed-in app, like `AppLockGate`, and composed afresh on every
 * session start — a cold start that was already signed in as much as a fresh login. That is the
 * point: the server's `ageVerificationRequired` is read each time until it is answered, so killing
 * the app between sign-in and answer does not skip the question, and accounts signed in before the
 * gate shipped are asked too.
 *
 * Not a lockout. If the flag cannot be read (offline, server error) nothing is shown and the next
 * session start asks again. While it is shown, the way out is signing out.
 */
@Composable
fun AgeVerificationGate(
    onSignedOut: () -> Unit,
    authRepository: AuthRepository = koinInject()
) {
    var required by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        required = ageDeclarationDue(authRepository::getProfile)
    }

    if (required) {
        // A Surface, not the bare step: it takes the touches, so nothing underneath can be used
        // around the question.
        Surface(modifier = Modifier.fillMaxSize()) {
            DeclareBirthDateScreen(
                onDeclared = { required = false },
                onSignedOut = onSignedOut,
                authRepository = authRepository
            )
        }
    }
}

/**
 * Whether the account still owes a date of birth. A profile that cannot be loaded answers false:
 * the gate asks again next session rather than blocking on a network error.
 */
internal suspend fun ageDeclarationDue(loadProfile: suspend () -> UserProfile): Boolean =
    try {
        loadProfile().ageVerificationRequired
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Age verification status unavailable: $e")
        false
    }

private const val TAG = "AgeVerificationGate"
