package com.muhabbet.app

import com.muhabbet.designsystem.theme.MuhabbetTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.muhabbet.app.data.local.ThemeController
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.data.remote.WsClient
import com.muhabbet.app.data.repository.PushTokenRegistrar
import com.muhabbet.app.crypto.E2EConfig
import com.muhabbet.app.data.repository.E2ESetupService
import com.muhabbet.app.di.bootstrapOrReuseKoin
import com.muhabbet.app.navigation.RootComponent
import com.muhabbet.app.navigation.RootContent
import com.muhabbet.app.platform.AppVisibility
import com.muhabbet.app.platform.CrashReporter
import com.muhabbet.app.util.Log
import com.muhabbet.shared.model.MessageStatus
import com.muhabbet.shared.protocol.WsMessage
import org.koin.compose.KoinContext
import org.koin.compose.koinInject
import org.koin.core.module.Module

@Composable
fun App(componentContext: ComponentContext, platformModule: Module) {
    val koin = remember { bootstrapOrReuseKoin(platformModule) }

    KoinContext(koin = koin) {
        val tokenStorage: TokenStorage = koinInject()
        val themeController: ThemeController = koinInject()

        // RootComponent owns the entire navigation stack, so it is remembered ABOVE the theme.
        // Inside it, the theme mode becoming state would put it under a boundary that recomposes on
        // every theme change — and a re-created RootComponent silently resets the user to the start
        // of the app.
        val root = remember { RootComponent(componentContext, tokenStorage) }
        val themeMode by themeController.mode.collectAsState()
        val hapticsEnabled by themeController.hapticsEnabled.collectAsState()

        // Initialize crash reporter and set user
        LaunchedEffect(Unit) {
            CrashReporter.init()
            tokenStorage.getUserId()?.let { CrashReporter.setUser(it) }
        }

        MuhabbetTheme(mode = themeMode, hapticsEnabled = hapticsEnabled) {
            WebSocketLifecycle(root)
            RootContent(root)
        }
    }
}

@Composable
private fun WebSocketLifecycle(root: RootComponent) {
    val wsClient: WsClient = koinInject()
    val tokenStorage: TokenStorage = koinInject()
    val pushTokenRegistrar: PushTokenRegistrar = koinInject()

    // Reactive login state, unlike tokenStorage.isLoggedIn() below — that is a snapshot read once
    // when a Unit-keyed effect first runs. This tracks the navigation stack, so it flips the
    // moment RootComponent.onAuthComplete() swaps Config.Auth -> Config.Main.
    val stack by root.childStack.subscribeAsState()
    val loggedIn = stack.active.instance is RootComponent.Child.Main

    // The generation this composition connected as, handed straight back to disconnect().
    //
    // `onDispose` belongs to a composition that is already gone, but it still runs whenever Android
    // gets round to it — which, on an Activity recreation, is *after* the replacement Activity has
    // composed and connected. An unguarded disconnect() at that point tore down the connection the
    // new composition had just opened, and the app then sat disconnected with no reconnect loop
    // running for as long as it stayed open (#511). The language switch recreates the Activity on
    // purpose, so this was reachable by ordinary use, not just by rotation.
    DisposableEffect(Unit) {
        val generation = if (tokenStorage.isLoggedIn()) wsClient.connect() else null
        onDispose {
            generation?.let { wsClient.disconnect(it) }
        }
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

    // Global DELIVERED ack: send DELIVERED for every incoming message regardless of active screen
    LaunchedEffect(Unit) {
        if (tokenStorage.isLoggedIn()) {
            val currentUserId = tokenStorage.getUserId() ?: return@LaunchedEffect
            wsClient.incoming.collect { message ->
                if (message is WsMessage.NewMessage && message.senderId != currentUserId) {
                    // sendAck() never throws for a send failure — it queues the receipt and replays
                    // it on the next connect — so this collector, the app-wide delivery-ack pump,
                    // cannot be killed by a dropped socket. Cancellation still propagates, which is
                    // correct: it means the pump itself is being torn down.
                    val sentNow = wsClient.sendAck(
                        WsMessage.AckMessage(
                            messageId = message.messageId,
                            conversationId = message.conversationId,
                            status = MessageStatus.DELIVERED
                        )
                    )
                    if (!sentNow) {
                        Log.d("App", "DELIVERED receipt for ${message.messageId} queued for the next reconnect")
                    }
                }
            }
        }
    }

    // Register the push token whenever the session becomes active (#398). Keyed on `loggedIn`,
    // not `Unit`: this composable is mounted ABOVE the auth/main navigation switch (see App()),
    // so a Unit-keyed effect samples tokenStorage.isLoggedIn() once, before the login screen has
    // even run, and never re-evaluates it. That made push-token registration work only for a user
    // who force-quit and relaunched an already-authenticated app — a path zero of six production
    // devices had taken. Keying on `loggedIn` re-fires the moment RootComponent swaps to
    // Config.Main, so first login registers a token exactly like an app start that was already
    // logged in. #349 tracks the identical Unit-key defect for WS connect, the E2E effect below
    // and background sync; this call site is scoped to push token registration only.
    LaunchedEffect(loggedIn) {
        if (loggedIn) {
            pushTokenRegistrar.registerIfLoggedIn()
        }
    }

    // Register E2E encryption keys on startup — only when E2E is actually on.
    //
    // This used to run on every launch for every logged-in user, gated on nothing but isLoggedIn().
    // With E2E flag-OFF and NoOpKeyManager wired, that published placeholder key material to the
    // production backend each time the process started. A feature that is switched off should put
    // nothing on the server.
    val e2eSetupService: E2ESetupService = koinInject()
    LaunchedEffect(Unit) {
        if (E2EConfig.ENABLED && tokenStorage.isLoggedIn()) {
            try {
                e2eSetupService.registerKeys()
                Log.d("App", "E2E encryption keys registered")
            } catch (e: Exception) {
                // Deliberately absorbed, same reasoning. E2E is flag-OFF in production
                // (E2EConfig.ENABLED), so a failed key registration changes nothing a user can see
                // today; it must not be silent when the flag is turned on.
                Log.e("App", "E2E key registration failed: ${e.message}")
            }
        }
    }

    // Schedule background message sync
    val syncManager: com.muhabbet.app.platform.BackgroundSyncManager = koinInject()
    LaunchedEffect(Unit) {
        if (tokenStorage.isLoggedIn()) {
            syncManager.schedulePeriodicSync()
        }
    }
}
