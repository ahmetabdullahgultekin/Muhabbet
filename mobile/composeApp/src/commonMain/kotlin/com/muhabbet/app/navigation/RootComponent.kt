package com.muhabbet.app.navigation

import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.replaceAll
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.value.Value
import com.muhabbet.app.data.local.TokenStorage
import kotlinx.serialization.Serializable

class RootComponent(
    componentContext: ComponentContext,
    private val tokenStorage: TokenStorage
) : ComponentContext by componentContext {

    private val navigation = StackNavigation<Config>()

    val childStack: Value<ChildStack<Config, Child>> = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialConfiguration = if (tokenStorage.isLoggedIn()) Config.Main else Config.Auth,
        handleBackButton = true,
        childFactory = ::createChild
    )

    private fun createChild(config: Config, componentContext: ComponentContext): Child =
        when (config) {
            Config.Auth -> Child.Auth(AuthComponent(componentContext, ::onAuthComplete))
            Config.Main -> Child.Main(MainComponent(componentContext, ::onLogout))
        }

    private fun onAuthComplete() {
        navigation.replaceAll(Config.Main)
    }

    private fun onLogout() {
        tokenStorage.clear()
        navigation.replaceAll(Config.Auth)
    }

    @Serializable
    sealed interface Config {
        @Serializable data object Auth : Config
        @Serializable data object Main : Config
    }

    sealed interface Child {
        data class Auth(val component: AuthComponent) : Child
        data class Main(val component: MainComponent) : Child
    }
}

@Composable
fun RootContent(root: RootComponent) {
    Children(
        stack = root.childStack,
        animation = stackAnimation(fade())
    ) { child ->
        when (val instance = child.instance) {
            is RootComponent.Child.Auth -> AuthContent(instance.component)
            is RootComponent.Child.Main -> MainContent(instance.component)
        }
    }
}
