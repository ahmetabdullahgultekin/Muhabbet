package com.muhabbet.app

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.arkivanov.decompose.defaultComponentContext
import com.muhabbet.app.di.androidPlatformModule
import java.util.Locale

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply saved language preference before rendering UI
        val savedLang = applicationContext
            .getSharedPreferences("muhabbet_prefs", MODE_PRIVATE)
            .getString("app_language", null)
        if (savedLang != null) {
            val locale = Locale(savedLang)
            Locale.setDefault(locale)
            val config = Configuration(resources.configuration)
            config.setLocale(locale)
            @Suppress("DEPRECATION")
            resources.updateConfiguration(config, resources.displayMetrics)
        }

        val componentContext = defaultComponentContext()
        val platformModule = androidPlatformModule(applicationContext)
        setContent {
            // Publish every Modifier.testTag as the Android view resource-id that uiautomator
            // reads. Without this the tags exist only inside Compose's semantics tree, so
            // instrumentation can address a control only by its visible text — which breaks
            // for icon-only controls and for every locale but the one the test was written in.
            Box(modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                App(componentContext, platformModule)
            }
        }
    }
}
