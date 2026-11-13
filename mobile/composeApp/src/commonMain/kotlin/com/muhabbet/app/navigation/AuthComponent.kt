package com.muhabbet.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.replaceAll
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.arkivanov.essenty.instancekeeper.getOrCreate
import com.muhabbet.app.session.SessionEndReason
import com.muhabbet.app.session.SessionState
import com.muhabbet.app.ui.auth.OtpVerifyScreen
import com.muhabbet.app.ui.auth.PhoneInputScreen
import com.muhabbet.app.ui.auth.ProfileSetupScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import androidx.compose.runtime.collectAsState

/**
 * The sign-in flow: phone number, code, and — for a new account — the profile step (#819).
 *
 * The screens are a projection of [AuthFlowState] ([stackFor]); the state lives in a retained
 * [AuthFlowStore] and is what [stateKeeper] saves, so the stack never has to be the source of truth
 * and never carries the one-time code into the saved-state bundle.
 *
 * @param startAtProfile the session is authenticated but onboarding never finished — a new user
 *   whose process died on the profile step. They resume there rather than in the app.
 * @param sessionState read for one thing only: whether the user is here because their session
 *   expired, so the phone screen can say so instead of presenting an unexplained logout (#813).
 * @param onFinishOnboarding reports a saved profile to the session, which is what opens the app.
 */
class AuthComponent(
    componentContext: ComponentContext,
    startAtProfile: Boolean,
    val sessionState: StateFlow<SessionState>,
    private val onFinishOnboarding: () -> Unit,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() }
) : ComponentContext by componentContext {

    private val store: AuthFlowStore = instanceKeeper.getOrCreate {
        AuthFlowStore(
            if (startAtProfile) {
                AuthFlowState.NeedsProfile
            } else {
                stateKeeper.consume(SAVED_STATE_KEY, AuthFlowState.serializer())
                    ?: AuthFlowState.EnterPhone
            }
        )
    }

    /** The flow's state, for what the screens need beyond which one is showing. */
    val state: StateFlow<AuthFlowState> = store.state

    private val navigation = StackNavigation<Config>()

    val childStack: Value<ChildStack<Config, Config>> = childStack(
        source = navigation,
        // Not saved: the stack is derived from the state, and the state is saved below.
        serializer = null,
        initialStack = { stackFor(store.state.value) },
        // Back is an event of the flow, not a pop. The predictive-back animation in AuthContent owns
        // the gesture and reports it through [goBack]; letting the stack also pop on its own would
        // move the screens without moving the state.
        handleBackButton = false,
        childFactory = { config, _ -> config }
    )

    init {
        stateKeeper.register(SAVED_STATE_KEY, AuthFlowState.serializer()) { store.state.value }
    }

    fun onPhoneSubmitted(phoneNumber: String, devCode: String? = null, firebaseVerificationId: String? = null) =
        onEvent(AuthFlowEvent.CodeRequested(phoneNumber, devCode, firebaseVerificationId, clock()))

    fun onCodeResent(devCode: String?) = onEvent(AuthFlowEvent.CodeResent(devCode, clock()))

    fun onOtpVerified(isNewUser: Boolean) = onEvent(AuthFlowEvent.Verified(isNewUser))

    fun onProfileSetupComplete() = onEvent(AuthFlowEvent.ProfileSaved)

    /** Back from whichever step is showing. A no-op on the profile step, which is forward-only. */
    fun goBack() = onEvent(AuthFlowEvent.Back)

    /** Seconds left on the code that was sent, measured from when it was sent rather than shown. */
    fun codeSecondsRemaining(code: AuthFlowState.CodeSent): Int = code.secondsRemaining(clock())

    private fun onEvent(event: AuthFlowEvent) {
        val transition = store.dispatch(event)
        val target = stackFor(transition.state)
        if (childStack.value.items.map { it.configuration } != target) {
            navigation.replaceAll(*target.toTypedArray())
        }
        when (transition.effect) {
            AuthFlowEffect.FinishOnboarding -> onFinishOnboarding()
            null -> Unit
        }
    }

    @Serializable
    sealed interface Config {
        @Serializable data object PhoneInput : Config

        /** No code in here, ever — see [AuthFlowState.CodeSent.devCode]. */
        @Serializable data class OtpVerify(
            val phoneNumber: String,
            val firebaseVerificationId: String? = null
        ) : Config

        @Serializable data object ProfileSetup : Config
    }

    companion object {
        private const val SAVED_STATE_KEY = "auth_flow_state"

        /**
         * The screens for a state. The profile step stands alone, so there is nothing behind it for
         * back to return to: on Android back leaves the app rather than reopening a spent code.
         */
        fun stackFor(state: AuthFlowState): List<Config> = when (state) {
            AuthFlowState.EnterPhone -> listOf(Config.PhoneInput)
            is AuthFlowState.CodeSent -> listOf(
                Config.PhoneInput,
                Config.OtpVerify(state.phoneNumber, state.firebaseVerificationId)
            )
            AuthFlowState.NeedsProfile -> listOf(Config.ProfileSetup)
            // Momentary: the session moves the root to Main as soon as it is signed in. Until then
            // the last screen stays, rather than flashing back to the phone number.
            AuthFlowState.Complete -> listOf(Config.ProfileSetup)
        }
    }
}

/**
 * The flow's state holder, retained across configuration changes by [InstanceKeeper] (ADR 0010).
 */
internal class AuthFlowStore(initial: AuthFlowState) : InstanceKeeper.Instance {

    private val _state = MutableStateFlow(initial)
    val state: StateFlow<AuthFlowState> = _state.asStateFlow()

    fun dispatch(event: AuthFlowEvent): AuthFlowTransition {
        val transition = _state.value.on(event)
        _state.value = transition.state
        return transition
    }
}

@Composable
fun AuthContent(component: AuthComponent) {
    val flowState by component.state.collectAsState()
    val sessionState by component.sessionState.collectAsState()
    val sessionExpired = (sessionState as? SessionState.Expired)?.reason == SessionEndReason.REFRESH_REJECTED

    Children(
        stack = component.childStack,
        animation = predictiveBack(component.backHandler, component::goBack)
    ) { child ->
        when (val config = child.instance) {
            is AuthComponent.Config.PhoneInput -> PhoneInputScreen(
                onPhoneSubmitted = { phone, devCode, firebaseVerificationId ->
                    component.onPhoneSubmitted(phone, devCode, firebaseVerificationId)
                },
                onFirebaseAutoVerified = { isNewUser -> component.onOtpVerified(isNewUser) },
                sessionExpired = sessionExpired
            )
            is AuthComponent.Config.OtpVerify -> {
                val code = flowState as? AuthFlowState.CodeSent
                OtpVerifyScreen(
                    phoneNumber = config.phoneNumber,
                    mockCode = code?.devCode,
                    firebaseVerificationId = config.firebaseVerificationId,
                    initialCountdownSeconds = code?.let(component::codeSecondsRemaining)
                        ?: BACKEND_CODE_LIFETIME_SECONDS,
                    onOtpVerified = component::onOtpVerified,
                    onCodeResent = component::onCodeResent,
                    onBack = component::goBack
                )
            }
            is AuthComponent.Config.ProfileSetup -> ProfileSetupScreen(
                onComplete = component::onProfileSetupComplete
            )
        }
    }
}
