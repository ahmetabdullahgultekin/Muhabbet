package com.muhabbet.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.muhabbet.app.data.remote.ApiException
import com.muhabbet.app.data.repository.TwoStepRepository
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import com.muhabbet.designsystem.components.MuhabbetTopBar
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.shared.validation.ValidationRules
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.muhabbet.designsystem.components.MuhabbetButton
import com.muhabbet.designsystem.components.MuhabbetButtonRole
import com.muhabbet.designsystem.components.MuhabbetScaffold
import com.muhabbet.designsystem.theme.containerColor
import com.muhabbet.designsystem.theme.depth
import com.muhabbet.designsystem.theme.MuhabbetDepth
import com.muhabbet.designsystem.components.MuhabbetErrorState
import com.muhabbet.designsystem.components.MuhabbetLoadingState

private const val TAG = "TwoStepSetupScreen"

/**
 * Whether two-step verification is on — or whether we could not find out.
 *
 * [Unavailable] is a state of its own rather than a flag beside `enabled`, because the two are not
 * interchangeable and the screen renders something completely different for each. Treating a failed
 * status read as "off" showed the enable form to an account that already has two-step on, which
 * ends in `AUTH_2FA_ALREADY_ENABLED` after the user has typed a PIN twice.
 */
private sealed interface TwoStepState {
    data object Loading : TwoStepState
    data object Unavailable : TwoStepState
    data class Known(val enabled: Boolean) : TwoStepState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TwoStepSetupScreen(
    onBack: () -> Unit,
    twoStepRepository: TwoStepRepository = koinInject()
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var currentPin by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<TwoStepState>(TwoStepState.Loading) }
    var reloadToken by remember { mutableStateOf(0) }
    var isSaving by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val errors = TwoStepErrorMessages(
        generic = stringResource(Res.string.error_generic),
        alreadyEnabled = stringResource(Res.string.two_step_error_already_enabled),
        pinInvalid = stringResource(Res.string.two_step_error_pin_invalid),
        notEnabled = stringResource(Res.string.two_step_error_not_enabled),
        sessionExpired = stringResource(Res.string.two_step_error_session_expired),
        malformedPin = stringResource(Res.string.two_step_pin_length),
        lockedOut = stringResource(Res.string.two_step_error_locked),
    )
    val pinMismatchMsg = stringResource(Res.string.two_step_pin_mismatch)
    val pinLengthMsg = stringResource(Res.string.two_step_pin_length)
    val enabledMsg = stringResource(Res.string.two_step_enabled)
    val disabledMsg = stringResource(Res.string.two_step_disabled)

    // Named rather than inlined into the button, so the keyboard's Done key on the last field can
    // reach the same action. Guarded on exactly what the button's `enabled` is guarded on — a
    // keyboard route into a disabled action is how a form gets submitted blank (#479).
    val setUpTwoStep: () -> Unit = {
        if (!isSaving && pin.isNotBlank() && confirmPin.isNotBlank()) {
            scope.launch {
                when {
                    !ValidationRules.isValidTwoStepPin(pin) -> snackbarHostState.showSnackbar(pinLengthMsg)
                    pin != confirmPin -> snackbarHostState.showSnackbar(pinMismatchMsg)
                    else -> {
                        isSaving = true
                        val failure = runCatchingCancellable {
                            twoStepRepository.enable(pin)
                        }.exceptionOrNull()
                        if (failure == null) state = TwoStepState.Known(enabled = true)
                        else Log.e(TAG, "Two-step setup failed", failure)
                        // Clear the spinner BEFORE reporting — showSnackbar suspends until
                        // dismissed (~4s). This is the success path too: the button spun for
                        // the whole time the "two-step enabled" confirmation was on screen.
                        isSaving = false
                        snackbarHostState.showSnackbar(
                            if (failure == null) enabledMsg else errors.forFailure(failure)
                        )
                    }
                }
            }
        }
    }

    val disableTwoStep: () -> Unit = {
        if (!isSaving && currentPin.isNotBlank()) {
            scope.launch {
                if (!ValidationRules.isValidTwoStepPin(currentPin)) {
                    snackbarHostState.showSnackbar(pinLengthMsg)
                } else {
                    isSaving = true
                    val failure = runCatchingCancellable {
                        twoStepRepository.disable(currentPin)
                    }.exceptionOrNull()
                    if (failure == null) {
                        state = TwoStepState.Known(enabled = false)
                        currentPin = ""
                    } else {
                        Log.e(TAG, "Two-step disable failed", failure)
                    }
                    isSaving = false
                    snackbarHostState.showSnackbar(
                        if (failure == null) disabledMsg else errors.forFailure(failure)
                    )
                }
            }
        }
    }

    LaunchedEffect(reloadToken) {
        state = TwoStepState.Loading
        val result = runCatchingCancellable { twoStepRepository.status() }
        val status = result.getOrNull()
        state = if (status != null) {
            TwoStepState.Known(enabled = status.enabled)
        } else {
            // Security-relevant: rendering the enable form here would tell the user two-step is OFF
            // when it may well be ON. Say we do not know, and offer to ask again.
            Log.e(TAG, "Failed to load two-step status", result.exceptionOrNull())
            TwoStepState.Unavailable
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
        val contentModifier = Modifier.fillMaxSize().padding(padding)
        when (val current = state) {
            is TwoStepState.Loading -> MuhabbetLoadingState(contentModifier)

            is TwoStepState.Unavailable -> MuhabbetErrorState(
                message = stringResource(Res.string.two_step_status_unavailable),
                modifier = contentModifier,
                retryLabel = stringResource(Res.string.action_retry),
                onRetry = { reloadToken++ }
            )

            is TwoStepState.Known -> Column(
                modifier = contentModifier
                    .verticalScroll(rememberScrollState())
                    .padding(MuhabbetSpacing.XLarge)
            ) {
                if (current.enabled) {
                    EnabledCard(
                        currentPin = currentPin,
                        onCurrentPinChange = { currentPin = it },
                        isSaving = isSaving,
                        onDisable = disableTwoStep
                    )
                } else {
                    SetupForm(
                        pin = pin,
                        onPinChange = { pin = it },
                        confirmPin = confirmPin,
                        onConfirmPinChange = { confirmPin = it },
                        isSaving = isSaving,
                        onSubmit = setUpTwoStep
                    )
                }
            }
        }
    }
}

/** The "it is on" card, plus the current PIN the server requires before it will turn it off. */
@Composable
private fun EnabledCard(
    currentPin: String,
    onCurrentPinChange: (String) -> Unit,
    isSaving: Boolean,
    onDisable: () -> Unit
) {
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
            PinField(
                value = currentPin,
                onValueChange = onCurrentPinChange,
                label = stringResource(Res.string.two_step_current_pin),
                imeAction = ImeAction.Done,
                onImeAction = onDisable
            )
            Spacer(Modifier.height(MuhabbetSpacing.Large))
            val spinner: @Composable RowScope.() -> Unit = {
                CircularProgressIndicator(
                    modifier = Modifier.size(MuhabbetSizes.IconMedium),
                    color = MaterialTheme.colorScheme.onError,
                    strokeWidth = 2.dp
                )
            }
            MuhabbetButton(
                text = stringResource(Res.string.two_step_disable),
                onClick = onDisable,
                role = MuhabbetButtonRole.Destructive,
                enabled = !isSaving && currentPin.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                content = if (isSaving) spinner else null
            )
        }
    }
}

/** The "it is off" form: a PIN and its confirmation. */
@Composable
private fun SetupForm(
    pin: String,
    onPinChange: (String) -> Unit,
    confirmPin: String,
    onConfirmPinChange: (String) -> Unit,
    isSaving: Boolean,
    onSubmit: () -> Unit
) {
    Text(
        text = stringResource(Res.string.two_step_description),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(Modifier.height(MuhabbetSpacing.Small))

    // The sign-in gate landed with #566, so the PIN is now genuinely asked for. What replaced the
    // "not enforced yet" line is the other honest thing this screen owes the user: there is no way
    // back. No mail sender exists, so a forgotten PIN cannot be reset, and the same rule from #61
    // applies to a promise of recovery as to a padlock — do not make one the code cannot keep.
    Text(
        text = stringResource(Res.string.two_step_no_recovery_warning),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(Modifier.height(MuhabbetSpacing.XLarge))

    PinField(
        value = pin,
        onValueChange = onPinChange,
        label = stringResource(Res.string.two_step_pin_hint),
        imeAction = ImeAction.Next,
        onImeAction = null
    )

    Spacer(Modifier.height(MuhabbetSpacing.Medium))

    PinField(
        value = confirmPin,
        onValueChange = onConfirmPinChange,
        label = stringResource(Res.string.two_step_confirm_pin),
        imeAction = ImeAction.Done,
        onImeAction = onSubmit
    )

    Spacer(Modifier.height(MuhabbetSpacing.XLarge))

    val spinner: @Composable RowScope.() -> Unit = {
        CircularProgressIndicator(
            modifier = Modifier.size(MuhabbetSizes.IconMedium),
            color = MaterialTheme.colorScheme.onPrimary,
            strokeWidth = 2.dp
        )
    }
    MuhabbetButton(
        text = stringResource(Res.string.two_step_enable),
        onClick = onSubmit,
        enabled = !isSaving && pin.isNotBlank() && confirmPin.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
        content = if (isSaving) spinner else null
    )
}

/**
 * A masked, digits-only field of exactly [ValidationRules.TWO_STEP_PIN_LENGTH] characters.
 *
 * Not `MuhabbetTextField`, which has no visual transformation and so cannot mask. The length and
 * digit filter come from the shared rule the server now enforces too, so the field cannot accept
 * something the endpoint will reject.
 */
@Composable
private fun PinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    imeAction: ImeAction,
    onImeAction: (() -> Unit)?
) {
    OutlinedTextField(
        value = value,
        onValueChange = { typed ->
            // `'0'..'9'`, not `isDigit()`: the latter accepts Arabic-Indic digits, which the shared
            // rule the server checks against does not, so the field would accept a PIN the endpoint
            // then rejects with nothing on screen to explain it.
            if (typed.length <= ValidationRules.TWO_STEP_PIN_LENGTH && typed.all { it in '0'..'9' }) {
                onValueChange(typed)
            }
        },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(onDone = onImeAction?.let { action -> { action() } }),
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * The rejections these endpoints report in normal use, in the device's language.
 *
 * Every one of them used to arrive as "bir hata oluştu" — including the 405 that made setup fail
 * for every user since the feature shipped (#544). `ApiClient` has carried the backend's `ErrorCode`
 * on the exception since #374; the screen simply threw it away in a `catch (_: Exception)`, which is
 * why a user report could say nothing more useful than "it does not work".
 *
 * Resolved at composition because `stringResource` is `@Composable` and the failures arrive inside
 * `scope.launch`.
 */
private class TwoStepErrorMessages(
    val generic: String,
    val alreadyEnabled: String,
    val pinInvalid: String,
    val notEnabled: String,
    val sessionExpired: String,
    val malformedPin: String,
    val lockedOut: String,
) {
    /**
     * [generic] covers everything else — a 500, a dead network, a proxy error page — because those
     * are malfunctions, and naming them precisely helps nobody holding a phone. `METHOD_NOT_ALLOWED`
     * and `ENDPOINT_NOT_FOUND` deliberately land here too: they mean this build is talking to a
     * backend that does not serve what it asks for, which is a deploy problem, not the user's.
     */
    fun forFailure(e: Throwable): String = when ((e as? ApiException)?.code) {
        "AUTH_2FA_ALREADY_ENABLED" -> alreadyEnabled
        "AUTH_2FA_PIN_INVALID" -> pinInvalid
        "AUTH_2FA_NOT_ENABLED" -> notEnabled
        "AUTH_2FA_LOCKED" -> lockedOut
        "AUTH_UNAUTHORIZED", "AUTH_TOKEN_EXPIRED" -> sessionExpired
        "VALIDATION_ERROR" -> malformedPin
        else -> generic
    }
}
