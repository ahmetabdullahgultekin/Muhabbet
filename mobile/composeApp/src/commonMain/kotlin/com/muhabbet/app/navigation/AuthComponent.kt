package com.muhabbet.app.navigation

import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.DelicateDecomposeApi
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.push
import com.arkivanov.decompose.router.stack.replaceAll
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.slide
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
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
    fun onPhoneSubmitted(phoneNumber: String) {
        navigation.push(Config.OtpVerify(phoneNumber))
    }

    @OptIn(DelicateDecomposeApi::class)
    fun onOtpVerified(isNewUser: Boolean) {
        if (isNewUser) {
            navigation.push(Config.ProfileSetup)
        } else {
            onAuthComplete()
        }
    }

    fun onProfileSetupComplete() {
        onAuthComplete()
    }

    fun onBackFromOtp() {
        navigation.pop()
    }

    @Serializable
    sealed interface Config {
        @Serializable data object PhoneInput : Config
        @Serializable data class OtpVerify(val phoneNumber: String) : Config
        @Serializable data object ProfileSetup : Config
    }
}

@Composable
fun AuthContent(component: AuthComponent) {
    Children(
        stack = component.childStack,
        animation = stackAnimation(slide())
    ) { child ->
        when (val config = child.instance) {
            is AuthComponent.Config.PhoneInput -> PhoneInputScreen(
                onPhoneSubmitted = component::onPhoneSubmitted
            )
            is AuthComponent.Config.OtpVerify -> OtpVerifyScreen(
                phoneNumber = config.phoneNumber,
                onOtpVerified = component::onOtpVerified,
                onBack = component::onBackFromOtp
            )
            is AuthComponent.Config.ProfileSetup -> ProfileSetupScreen(
                onComplete = component::onProfileSetupComplete
            )
        }
    }
}
