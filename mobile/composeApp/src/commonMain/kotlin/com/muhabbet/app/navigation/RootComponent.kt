package com.muhabbet.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.replaceAll
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.value.Value
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.ui.notice.TestBuildNoticeDialog
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
        animation = rootFade()
    ) { child ->
        when (val instance = child.instance) {
            is RootComponent.Child.Auth -> AuthContent(instance.component)
            is RootComponent.Child.Main -> MainContent(instance.component)
        }
    }

    // The test-build notice (#519), sited here rather than inside the Children lambda so it is not
    // part of the Auth -> Main cross-fade: it should appear over a settled conversation list, not
    // fade in alongside it. Gated on the Main child, so it never lands on top of the login screen —
    // and because that is the reactive stack value, it fires on a fresh login as well as on a
    // relaunch that was already authenticated.
    val stack by root.childStack.subscribeAsState()
    if (stack.active.instance is RootComponent.Child.Main) {
        TestBuildNoticeDialog()
    }
}
