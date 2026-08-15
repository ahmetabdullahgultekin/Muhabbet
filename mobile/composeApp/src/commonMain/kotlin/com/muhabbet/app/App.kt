package com.muhabbet.app

import com.muhabbet.designsystem.theme.MuhabbetTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.arkivanov.decompose.ComponentContext
import com.muhabbet.app.data.local.ThemeController
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.data.remote.WsClient
import com.muhabbet.app.data.repository.AuthRepository
import com.muhabbet.app.data.repository.E2ESetupService
import com.muhabbet.app.di.appModule
import com.muhabbet.app.navigation.RootComponent
import com.muhabbet.app.navigation.RootContent
import com.muhabbet.app.platform.CrashReporter
import com.muhabbet.app.platform.PushTokenProvider
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import com.muhabbet.shared.model.MessageStatus
import com.muhabbet.shared.protocol.WsMessage
import org.koin.compose.KoinContext
import org.koin.compose.koinInject
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform
import org.koin.core.module.Module

@Composable
fun App(componentContext: ComponentContext, platformModule: Module) {
    val koin = remember {
        runCatching {
            startKoin { modules(platformModule, appModule()) }.koin
        }.getOrElse { KoinPlatform.getKoin() }
    }

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
            WebSocketLifecycle()
            RootContent(root)
        }
    }
}

@Composable
private fun WebSocketLifecycle() {
    val wsClient: WsClient = koinInject()
    val tokenStorage: TokenStorage = koinInject()
    val pushTokenProvider: PushTokenProvider = koinInject()
    val authRepository: AuthRepository = koinInject()

    DisposableEffect(Unit) {
        if (tokenStorage.isLoggedIn()) {
            wsClient.connect()
        }
        onDispose {
            wsClient.disconnect()
        }
    }

    // Global DELIVERED ack: send DELIVERED for every incoming message regardless of active screen
    LaunchedEffect(Unit) {
        if (tokenStorage.isLoggedIn()) {
            val currentUserId = tokenStorage.getUserId() ?: return@LaunchedEffect
            wsClient.incoming.collect { message ->
                if (message is WsMessage.NewMessage && message.senderId != currentUserId) {
                    runCatchingCancellable {
                        wsClient.send(
                            WsMessage.AckMessage(
                                messageId = message.messageId,
                                conversationId = message.conversationId,
                                status = MessageStatus.DELIVERED
                            )
                        )
                    }.onFailure { e ->
                        // Must not rethrow: this collector is the app-wide delivery-ack pump, and
                        // letting it die would silently stop every future DELIVERED tick.
                        // Nothing to show the user — the sender's tick simply stays at one.
                        // Cancellation is the one exception that still propagates, so tearing the
                        // pump down does not log a failure that never happened.
                        Log.w("App", "Failed to send DELIVERED ack for ${message.messageId}: ${e.message}")
                    }
                }
            }
        }
    }

    // Register push token after WS connect
    LaunchedEffect(Unit) {
        if (tokenStorage.isLoggedIn()) {
            try {
                val pushToken = pushTokenProvider.getToken()
                if (pushToken != null) {
                    authRepository.registerPushToken(pushToken)
                    Log.d("App", "Push token registered: ${pushToken.take(10)}...")
                }
            } catch (e: Exception) {
                // Deliberately absorbed. This is startup bootstrap with no screen of its own; a
                // snackbar on launch, over whatever the user opened the app to do, would say
                // nothing they can act on. The failure is now real rather than swallowed by
                // ApiClient, so the log line is the record — push simply will not arrive.
                Log.e("App", "Push token registration failed: ${e.message}")
            }
        }
    }

    // Register E2E encryption keys on startup
    val e2eSetupService: E2ESetupService = koinInject()
    LaunchedEffect(Unit) {
        if (tokenStorage.isLoggedIn()) {
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
