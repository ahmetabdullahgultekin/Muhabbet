package com.muhabbet.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.replaceAll
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.value.Value
import com.muhabbet.app.BuildInfo
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.ui.notice.shouldShowTestBuildNotice
import com.muhabbet.app.ui.onboarding.FirstRunSurfaces
import com.muhabbet.app.ui.settings.AppLockGate
import com.muhabbet.app.ui.whatsnew.versionToRecordOnFirstLaunch
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

    /**
     * Whether the test-build notice is due this launch.
     *
     * Read here, at construction, because this is the last moment it is still true: the dialog
     * writes the acknowledgement the instant it is dismissed, and the release-notes sheet needs to
     * know whether it is queued behind one. Both are once-per-version, so an update brings both due
     * together and something has to decide the order.
     */
    val testBuildNoticePending: Boolean =
        shouldShowTestBuildNotice(tokenStorage.getTestBuildNoticeAckVersion(), BuildInfo.VERSION)

    init {
        // A device that has never recorded a version records one now, before any screen composes and
        // therefore before the release-notes sheet can ask (#672). Doing it here rather than in the
        // sheet keeps the two apart: the sheet decides whether to *show*, and it never has to reason
        // about a null that means two different things.
        if (tokenStorage.getLastSeenVersion() == null) {
            tokenStorage.setLastSeenVersion(
                versionToRecordOnFirstLaunch(
                    acknowledgedTestBuildVersion = tokenStorage.getTestBuildNoticeAckVersion(),
                    currentVersion = BuildInfo.VERSION
                )
            )
        }
    }

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

/**
 * Whether a signed-in session is on screen right now.
 *
 * The one definition of "logged in" that the composition is allowed to use, because it is the only
 * one that is *reactive*. `tokenStorage.isLoggedIn()` answers correctly but only once, at the moment
 * it is read; an effect keyed on `Unit` above the auth/main switch therefore samples the login
 * screen's answer and keeps it for the life of the process. That is #349, and it cost the app its
 * WebSocket, push token, E2E keys and background sync for every first session after signing in.
 *
 * This flips the instant `RootComponent.onAuthComplete` swaps `Config.Auth` -> `Config.Main`, and
 * back on logout — so effects keyed on it fire in both directions. Named and shared rather than
 * re-derived at each call site, so "is anyone signed in" has one answer rather than one per caller.
 */
@Composable
fun isSessionActive(root: RootComponent): Boolean {
    val stack by root.childStack.subscribeAsState()
    return stack.active.instance is RootComponent.Child.Main
}

@Composable
fun RootContent(root: RootComponent) {
    val isMain = isSessionActive(root)

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
            FirstRunSurfaces(noticePending = root.testBuildNoticePending)
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
