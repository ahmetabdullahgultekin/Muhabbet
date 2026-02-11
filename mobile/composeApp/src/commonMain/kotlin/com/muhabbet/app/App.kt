package com.muhabbet.app

import com.muhabbet.app.ui.theme.MuhabbetTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.arkivanov.decompose.ComponentContext
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.data.remote.WsClient
import com.muhabbet.app.di.appModule
import com.muhabbet.app.navigation.RootComponent
import com.muhabbet.app.navigation.RootContent
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.core.module.Module

@Composable
fun App(componentContext: ComponentContext, platformModule: Module) {
    KoinApplication(application = {
        modules(platformModule, appModule())
    }) {
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

    DisposableEffect(Unit) {
        val token = tokenStorage.getAccessToken()
        if (token != null) {
            wsClient.connect(token)
        }
        onDispose {
            wsClient.disconnect()
        }
    }
}
