package com.muhabbet.app

import com.muhabbet.designsystem.theme.MuhabbetTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.muhabbet.app.data.local.ThemeController
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.di.bootstrapOrReuseKoin
import com.muhabbet.app.navigation.RootComponent
import com.muhabbet.app.navigation.RootContent
import com.muhabbet.app.platform.AppVisibility
import com.muhabbet.app.platform.CrashReporter
import com.muhabbet.app.session.SessionCoordinator
import com.muhabbet.app.session.SessionManager
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.app_language_code
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.KoinContext
import org.koin.compose.koinInject
import org.koin.core.module.Module

@Composable
fun App(componentContext: ComponentContext, platformModule: Module) {
    val koin = remember { bootstrapOrReuseKoin(platformModule) }

    KoinContext(koin = koin) {
        val tokenStorage: TokenStorage = koinInject()
        val sessionManager: SessionManager = koinInject()
        val themeController: ThemeController = koinInject()

        // RootComponent owns the entire navigation stack, so it is remembered ABOVE the theme.
        // Inside it, the theme mode becoming state would put it under a boundary that recomposes on
        // every theme change — and a re-created RootComponent silently resets the user to the start
        // of the app.
        val root = remember { RootComponent(componentContext, tokenStorage, sessionManager) }
        val themeMode by themeController.mode.collectAsState()
        val hapticsEnabled by themeController.hapticsEnabled.collectAsState()

        // Configuring the crash SDK genuinely is a once-per-process job, so `Unit` is the right key
        // here. Naming the *user* is a session concern and lives in SessionWiring, driven by
        // SessionCoordinator.
        LaunchedEffect(Unit) {
            CrashReporter.init()
        }

        MuhabbetTheme(mode = themeMode, hapticsEnabled = hapticsEnabled) {
            HostBindings(root)
            RootContent(root)
        }
    }
}

/**
 * What this composition — the UI host — tells the process-wide parts of the app about itself.
 *
 * Not session effects. This used to be `SessionLifecycle`: six effects keyed on whether the Main
 * screen was showing, which is why an expired session that left Main on screen kept them all
 * running (#813). The session's work now starts and stops in [SessionCoordinator], keyed on
 * `SessionManager.state`; all that remains here is what only a composition can know.
 */
@Composable
private fun HostBindings(root: RootComponent) {
    val coordinator: SessionCoordinator = koinInject()

    // The socket needs a screen as well as a session: it was always torn down when the Activity went
    // away, and holding it open for a process with no UI would show the user online after they had
    // closed the app. Reference-counted in the coordinator, so an Activity recreation — where the
    // new composition may attach before or after the old one detaches — can never leave it down
    // (#511).
    DisposableEffect(coordinator) {
        coordinator.start()
        coordinator.attachHost()
        onDispose { coordinator.detachHost() }
    }

    // Foreground/background, republished from the lifecycle Decompose already owns.
    //
    // On Android `defaultComponentContext()` binds RootComponent's Essenty lifecycle to the hosting
    // Activity, so these are real transitions — the phone locking and unlocking, the app being
    // switched away from and back to. Nothing in the composition can see that on its own: locking
    // the screen tears nothing down, so a `LaunchedEffect` keyed on anything stable never re-runs
    // and an open chat's "mark this read" handler fires once per navigation and never again (#478).
    val appVisibility: AppVisibility = koinInject()
    DisposableEffect(root) {
        val lifecycle = root.lifecycle
        val callbacks = object : Lifecycle.Callbacks {
            override fun onResume() = appVisibility.onForeground()
            override fun onPause() = appVisibility.onBackground()
        }
        lifecycle.subscribe(callbacks)
        onDispose { lifecycle.unsubscribe(callbacks) }
    }

    // The language the app is *rendering*, read out of strings.xml itself, so it accounts for the
    // in-app language picker and — when nothing has been picked — for the phone's own language.
    // Push text is composed on the server, which has no other way to learn either: every
    // notification went out in Turkish until this value started travelling with the token (#469).
    // Deliberately the rendered language rather than the stored preference (#548): the two can
    // disagree, and the one the user is actually reading is the one to notify in. Only a composable
    // can read it, so it is handed to the coordinator, which re-registers when it changes.
    val renderedLanguage = stringResource(Res.string.app_language_code)
    LaunchedEffect(coordinator, renderedLanguage) {
        coordinator.onRenderedLanguage(renderedLanguage)
    }
}
