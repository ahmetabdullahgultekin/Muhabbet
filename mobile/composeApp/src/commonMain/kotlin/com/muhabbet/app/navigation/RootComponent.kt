package com.muhabbet.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.replaceAll
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.value.Value
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.ui.onboarding.FirstRunSurfaces
import com.muhabbet.app.ui.settings.AppLockGate
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
    val stack by root.childStack.subscribeAsState()
    val isMain = stack.active.instance is RootComponent.Child.Main

    // Box, not a bare Children call, so App Lock's cover (#378) can be layered on top of whatever
    // screen is underneath in the SAME composition and the SAME Activity window — not a separate
    // Dialog window, which SecureScreenEffect's FLAG_SECURE would not reliably cover. See
    // AppLockGate's own KDoc for why it must sit here rather than lower in the tree.
    Box(modifier = Modifier.fillMaxSize()) {
        Children(
            stack = root.childStack,
            animation = rootFade()
        ) { child ->
            when (val instance = child.instance) {
                is RootComponent.Child.Auth -> AuthContent(instance.component)
                is RootComponent.Child.Main -> MainContent(instance.component)
            }
        }

        // The first-run surfaces (#519 test-build notice, #547 notification permission, #692
        // welcome flow), sited here rather than inside the Children lambda so they are not part of
        // the Auth -> Main cross-fade: they should appear over a settled conversation list, not
        // fade in alongside it. Inside the Box because the welcome flow is a full-screen Surface
        // layered over whatever is underneath, in the same composition and the same window — the
        // reason AppLockGate is here too. Gated on the Main child, so nothing lands on top of the
        // login screen; because that is the reactive stack value it fires on a fresh login as well
        // as on a relaunch that was already authenticated. FirstRunSurfaces owns the order.
        if (isMain) {
            FirstRunSurfaces()
        }

        // Gated on the Main child, same as the surfaces above: there is nothing to protect on the
        // login screen, and TokenStorage.clear() on logout already wipes the stored App Lock
        // setting (see AndroidTokenStorage.clear()), so it reads back disabled there anyway.
        //
        // Last inside the Box, so the lock cover draws over the welcome flow rather than under it.
        // A lock that an introduction could hide would not be a lock.
        if (isMain) {
            AppLockGate()
        }
    }
}
