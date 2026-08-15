package com.muhabbet.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.muhabbet.app.data.repository.AuthRepository
import com.muhabbet.designsystem.components.MuhabbetStepRail
import com.muhabbet.designsystem.components.MuhabbetTextField
import com.muhabbet.designsystem.theme.MuhabbetGradients
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.shared.validation.ValidationRules
import kotlinx.coroutines.launch
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.muhabbet.designsystem.Muhabbet

@Composable
fun ProfileSetupScreen(
    onComplete: () -> Unit,
    authRepository: AuthRepository = koinInject()
) {
    var displayName by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val nameErrorMsg = stringResource(Res.string.profile_name_error)
    val updateFailedMsg = stringResource(Res.string.profile_update_failed)

    // safeDrawingPadding lifts the content above the keyboard (the IME is part of safeDrawing) and
    // verticalScroll makes the button reachable when it still does not fit. Without either, opening
    // the keyboard pushed "Get Started" off-screen with no way back to it — immediately after a user
    // had successfully verified their number, which is the worst possible place to strand someone.

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MuhabbetGradients.brandBackdrop)
            // safeDrawing already includes the IME, so this replaces the imePadding that used to sit
            // inside the scroll container — and additionally keeps the content clear of the system
            // bars, which nothing was doing before.
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(MuhabbetSpacing.XLarge),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar placeholder. Kept as a Surface rather than a UserAvatar: there is no name to derive
        // an initial from yet — supplying one is the entire purpose of this screen.
        Surface(
            modifier = Modifier.size(MuhabbetSizes.AvatarHero),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Muhabbet.icons.Person,
                    contentDescription = null,
                    modifier = Modifier.size(MuhabbetSizes.AvatarMedium),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(MuhabbetSpacing.XLarge))

        Text(
            text = stringResource(Res.string.profile_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(MuhabbetSpacing.Small))

        Text(
            text = stringResource(Res.string.profile_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(MuhabbetSpacing.XLarge))

        MuhabbetStepRail(current = 3, total = AuthSteps)

        Spacer(Modifier.height(MuhabbetSpacing.XLarge))

        MuhabbetTextField(
            value = displayName,
            onValueChange = {
                if (it.length <= 64) displayName = it
                error = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(Res.string.profile_name_label),
            error = error
        )

        Spacer(Modifier.height(MuhabbetSpacing.Large))

        Button(
            onClick = {
                if (!ValidationRules.isValidDisplayName(displayName)) {
                    error = nameErrorMsg
                    return@Button
                }
                isLoading = true
                error = null
                scope.launch {
                    try {
                        authRepository.updateProfile(displayName)
                        onComplete()
                    } catch (e: Exception) {
                        error = e.message ?: updateFailedMsg
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading && displayName.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(MuhabbetSizes.IconMedium),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = MuhabbetSizes.ProgressStrokeThin
                )
            } else {
                Text(stringResource(Res.string.profile_start))
            }
        }
    }
}
