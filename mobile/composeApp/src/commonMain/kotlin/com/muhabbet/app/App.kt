package com.muhabbet.app

import com.muhabbet.app.ui.theme.MuhabbetTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.arkivanov.decompose.ComponentContext
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
import com.muhabbet.shared.model.MessageStatus
import com.muhabbet.shared.protocol.WsMessage
import org.koin.compose.KoinContext
import org.koin.compose.koinInject
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.module.Module

@Composable
fun App(componentContext: ComponentContext, platformModule: Module) {
    val koin = remember {
        GlobalContext.getOrNull() ?: startKoin {
            modules(platformModule, appModule())
        }.koin
    }

    KoinContext(context = koin) {
        MuhabbetTheme {
            val tokenStorage: TokenStorage = koinInject()
            val root = remember { RootComponent(componentContext, tokenStorage) }

            // Initialize crash reporter and set user
            LaunchedEffect(Unit) {
                CrashReporter.init()
                tokenStorage.getUserId()?.let { CrashReporter.setUser(it) }
            }

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
                    try {
                        wsClient.send(
                            WsMessage.AckMessage(
                                messageId = message.messageId,
                                conversationId = message.conversationId,
                                status = MessageStatus.DELIVERED
                            )
                        )
                    } catch (_: Exception) { }
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
