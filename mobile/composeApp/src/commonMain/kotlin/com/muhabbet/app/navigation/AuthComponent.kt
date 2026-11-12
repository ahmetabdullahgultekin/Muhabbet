package com.muhabbet.app.navigation

import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.DelicateDecomposeApi
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.value.Value
import com.muhabbet.app.ui.auth.OtpVerifyScreen
import com.muhabbet.app.ui.auth.PhoneInputScreen
import com.muhabbet.app.ui.auth.ProfileSetupScreen
import kotlinx.serialization.Serializable

class AuthComponent(
    componentContext: ComponentContext,
    private val onAuthComplete: () -> Unit
) : ComponentContext by componentContext {

    private val navigation = StackNavigation<Config>()

    val childStack: Value<ChildStack<Config, Config>> = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialConfiguration = Config.PhoneInput,
        handleBackButton = true,
        childFactory = { config, _ -> config }
    )

    @OptIn(DelicateDecomposeApi::class)
    fun onPhoneSubmitted(
        phoneNumber: String,
        mockCode: String? = null,
        firebaseVerificationId: String? = null
    ) {
        navigation.push(Config.OtpVerify(phoneNumber, mockCode, firebaseVerificationId))
    }

    /**
     * An account from before the age gate (#751) is not asked here. It is asked by
     * `AgeVerificationGate` over the signed-in app, which reads the flag from the server on every
     * session start — so the question survives the app being killed between sign-in and answer,
     * and reaches accounts that were already signed in when the gate shipped.
     */
    @OptIn(DelicateDecomposeApi::class)
    fun onOtpVerified(isNewUser: Boolean) {
        if (isNewUser) navigation.push(Config.ProfileSetup) else onAuthComplete()
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
    sealed interface Config {
        @Serializable data object PhoneInput : Config
        @Serializable data class OtpVerify(
            val phoneNumber: String,
            val mockCode: String? = null,
            val firebaseVerificationId: String? = null
        ) : Config
        @Serializable data object ProfileSetup : Config
    }
}

@Composable
fun AuthContent(component: AuthComponent) {
    Children(
        stack = component.childStack,
        animation = predictiveBack(component.backHandler, component::goBack)
    ) { child ->
        when (val config = child.instance) {
            is AuthComponent.Config.PhoneInput -> PhoneInputScreen(
                onPhoneSubmitted = { phone, mockCode, firebaseVerificationId ->
                    component.onPhoneSubmitted(phone, mockCode, firebaseVerificationId)
                },
                onFirebaseAutoVerified = component::onOtpVerified
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
