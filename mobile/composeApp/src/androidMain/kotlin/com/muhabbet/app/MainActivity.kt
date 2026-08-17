package com.muhabbet.app

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        // Fully transparent bars, with the framework's own contrast scrim disabled, so the app
        // paints edge to edge and the theme decides what shows through. Both styles are declared
        // `dark` only to stop the framework auto-flipping icon colours from the system's night
        // setting — the icons follow the in-app theme instead, via SystemBarsEffect.
        //
        // This is not opt-in behaviour: targetSdk is 36, and from API 35 the platform draws the app
        // edge to edge regardless. Calling it explicitly is what makes the pre-35 path match, and
        // what replaces the android:statusBarColor the platform now ignores.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        applyStoredLanguage()

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

    /**
     * Re-asserted on the way back to the foreground, not only at start-up (#548).
     *
     * The chosen language reaches Compose through exactly one channel: `Locale.getDefault()`, which
     * `LocaleList.getDefault()` derives from and which compose-resources reads to pick a
     * `strings.xml`. That global belongs to the platform, and the platform writes it back to the
     * device's language whenever it applies a configuration to this process — which it can do while
     * the Activity is merely stopped, so `onCreate` does not run again to put it back. From then on
     * the preference still says Turkish and every string the next recomposition resolves is English,
     * which is what #548 reported after a theme change repainted the whole tree at once.
     *
     * `onResume` is the hook because there is no other: the Activity declares no `configChanges`, so
     * `onConfigurationChanged` is never delivered — a real configuration change recreates it and
     * runs `onCreate`. The gap this closes is the one that leaves no callback behind at all.
     *
     * NOT device-verified: there is no emulator on this host.
     */
    override fun onResume() {
        super.onResume()
        applyStoredLanguage()
    }

    private fun applyStoredLanguage() {
        val savedLang = applicationContext
            .getSharedPreferences("muhabbet_prefs", MODE_PRIVATE)
            .getString("app_language", null)
            ?.takeIf { it.isNotBlank() }
            ?: return
        // Already in effect. Skipped rather than re-applied because updateConfiguration throws away
        // the Activity's cached resources, and doing that on every resume would cost every screen a
        // reload for nothing.
        if (Locale.getDefault().language == savedLang) return

        val locale = Locale(savedLang)
        Locale.setDefault(locale)
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }
}
