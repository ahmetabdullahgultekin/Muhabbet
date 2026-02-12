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
import com.muhabbet.app.di.appModule
import com.muhabbet.app.navigation.RootComponent
import com.muhabbet.app.navigation.RootContent
import com.muhabbet.app.platform.PushTokenProvider
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

    // Register push token after WS connect
    LaunchedEffect(Unit) {
        if (tokenStorage.isLoggedIn()) {
            try {
                val pushToken = pushTokenProvider.getToken()
                if (pushToken != null) {
                    authRepository.registerPushToken(pushToken)
                    println("MUHABBET: Push token registered: ${pushToken.take(10)}...")
                }
            } catch (e: Exception) {
                println("MUHABBET: Push token registration failed: ${e.message}")
            }
        }
    }
}
