package com.muhabbet.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.arkivanov.decompose.defaultComponentContext
import com.muhabbet.app.di.androidPlatformModule

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val componentContext = defaultComponentContext()
        val platformModule = androidPlatformModule(applicationContext)
        setContent {
            App(componentContext, platformModule)
        }
    }
}
