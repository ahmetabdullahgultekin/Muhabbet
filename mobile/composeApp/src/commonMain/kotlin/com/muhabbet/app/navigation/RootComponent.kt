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
import com.muhabbet.app.ui.notification.NotificationPermissionGate
import kotlinx.serialization.Serializable

class RootComponent(
    componentContext: ComponentContext,
    private val tokenStorage: TokenStorage
) : ComponentContext by componentContext {

    private val navigation = StackNavigation<Config>()

    /**
     * Whether this process was started by the language switch, and so owes the user Settings rather
     * than the conversation list (#505).
     *
     * Read here, before [childStack] builds the first child, because that is the one moment it is
     * true. It is cleared as soon as it is handed over: [createChild] also runs on logout followed
     * by login, and a flag that survived that would drop a returning user into Settings.
     */
    private var pendingLanguageSettings = tokenStorage.consumePendingLanguageRestart()

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
            Config.Main -> {
                val openAtLanguageSettings = pendingLanguageSettings
                pendingLanguageSettings = false
                Child.Main(MainComponent(componentContext, ::onLogout, openAtLanguageSettings))
            }
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

        // Asks for notification permission once (#547), under the same gate and for the same
        // reason: it belongs after login, not on the login screen. Draws nothing — see the
        // composable's own note on why the system dialog is allowed to land over the notice above.
        NotificationPermissionGate()
    }
}
