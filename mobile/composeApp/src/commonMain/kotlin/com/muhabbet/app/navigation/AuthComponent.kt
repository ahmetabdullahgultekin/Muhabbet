package com.muhabbet.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.navigate
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.value.Value
import com.muhabbet.app.presentation.LocalScreenBackHandler
import com.muhabbet.app.presentation.belowScreenModes
import com.muhabbet.app.ui.auth.OtpVerifyScreen
import com.muhabbet.app.ui.auth.PhoneInputScreen
import com.muhabbet.app.ui.auth.ProfileSetupScreen
import kotlinx.serialization.Serializable

/**
 * Sign-in. Same shape as [MainComponent]: every entry is a child with its own `ComponentContext`
 * (ADR 0010), every navigation goes through [navigatedTo], and the pop belongs to the predictive-back
 * animation.
 */
class AuthComponent(
    componentContext: ComponentContext,
    private val onAuthComplete: () -> Unit
) : ComponentContext by componentContext {

    private val navigation = StackNavigation<Config>()

    val childStack: Value<ChildStack<Config, Child>> = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialConfiguration = Config.PhoneInput,
        handleBackButton = false,
        childFactory = ::Child
    )

    /**
     * A second submit — a double tap, or a resend that produced a new verification id — returns to
     * the one OTP entry with the newer arguments rather than stacking a second OTP screen.
     */
    fun onPhoneSubmitted(
        phoneNumber: String,
        mockCode: String? = null,
        firebaseVerificationId: String? = null
    ) {
        val target = Config.OtpVerify(phoneNumber, mockCode, firebaseVerificationId)
        navigation.navigate { stack -> stack.navigatedTo(target) { _, newer -> newer } }
    }

    fun onOtpVerified(isNewUser: Boolean) {
        if (isNewUser) {
            navigation.navigate { stack -> stack.navigatedTo(Config.ProfileSetup) }
        } else {
            onAuthComplete()
        }
    }

    fun onProfileSetupComplete() {
        onAuthComplete()
    }

    /**
     * Pop one step. Named generically because predictive back calls it for whichever screen is on
     * top, not only for the OTP step.
     */
    fun goBack() {
        navigation.pop()
    }

    fun onBackFromOtp() {
        goBack()
    }

    @Serializable
    sealed interface Config : NavDestination {
        @Serializable data object PhoneInput : Config
        @Serializable data class OtpVerify(
            val phoneNumber: String,
            val mockCode: String? = null,
            val firebaseVerificationId: String? = null
        ) : Config
        @Serializable data object ProfileSetup : Config
    }

    /** A created entry: its configuration and its own `ComponentContext`. */
    class Child(val config: Config, val context: ComponentContext)
}

@Composable
fun AuthContent(component: AuthComponent) {
    val animation = remember(component) {
        predictiveBack<AuthComponent.Config, AuthComponent.Child>(component.backHandler.belowScreenModes(), component::goBack)
    }
    Children(stack = component.childStack, animation = animation) { created ->
        val child = created.instance
        CompositionLocalProvider(LocalScreenBackHandler provides child.context.backHandler) {
            when (val config = child.config) {
                is AuthComponent.Config.PhoneInput -> PhoneInputScreen(
                    onPhoneSubmitted = { phone, mockCode, firebaseVerificationId ->
                        component.onPhoneSubmitted(phone, mockCode, firebaseVerificationId)
                    },
                    onFirebaseAutoVerified = { isNewUser -> component.onOtpVerified(isNewUser) }
                )
                is AuthComponent.Config.OtpVerify -> OtpVerifyScreen(
                    phoneNumber = config.phoneNumber,
                    mockCode = config.mockCode,
                    firebaseVerificationId = config.firebaseVerificationId,
                    onOtpVerified = component::onOtpVerified,
                    onBack = component::onBackFromOtp
                )
                is AuthComponent.Config.ProfileSetup -> ProfileSetupScreen(
                    onComplete = component::onProfileSetupComplete
                )
            }
        }
    }
}
