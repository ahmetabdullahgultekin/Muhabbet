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
import com.muhabbet.app.di.bootstrapOrReuseKoin
import com.muhabbet.app.navigation.ChatOpenRequest
import com.muhabbet.app.navigation.PendingChatOpen
import com.muhabbet.app.platform.MuhabbetNotifications
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

        // Before setContent, so a notification that started the process has already parked its
        // request by the time the composition first reads it.
        handleChatIntent(intent, platformModule)

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
    /**
     * The half that was missing entirely (#594).
     *
     * `MainActivity` is `singleTask`, so an intent that arrives while the app is already running
     * — which is the ordinary case for a tapped notification — is delivered here and **never**
     * through `onCreate`. Handling only `onCreate` fixes the rarer path and leaves the common one
     * broken, which is indistinguishable from not fixing it.
     *
     * `setIntent` so that `getIntent()` afterwards returns the new one rather than the stale
     * launch intent; the platform does not do this for us.
     */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleChatIntent(intent, androidPlatformModule(applicationContext))
    }

    /**
     * Parks a request to open a conversation, from either source that can carry one.
     *
     * Until #594 the conversation id was written onto the notification's intent and read by nobody
     * — `MainActivity` never called `getStringExtra`, so every tap landed on the conversation list.
     * The `muhabbet://chat/{id}` intent-filter in the manifest had the same shape of problem: it
     * was declared, so the link opened the app, and nothing then looked at the URL.
     *
     * Deliberately tolerant: a malformed link or an intent with nothing on it parks nothing and the
     * app opens where it always does. There is no user-visible failure mode worth inventing here.
     */
    private fun handleChatIntent(intent: android.content.Intent?, platformModule: org.koin.core.module.Module) {
        val intent = intent ?: return

        val fromNotification = intent.getStringExtra(MuhabbetNotifications.EXTRA_CONVERSATION_ID)
        val fromDeepLink = intent.data
            ?.takeIf { it.host == DEEP_LINK_CHAT_HOST || it.pathSegments.firstOrNull() == DEEP_LINK_CHAT_HOST }
            ?.pathSegments
            ?.lastOrNull()

        val conversationId = (fromNotification ?: fromDeepLink)?.takeIf { it.isNotBlank() } ?: return

        // Consumed on the way in so a configuration change — or the language switch, which recreates
        // this Activity on purpose — cannot replay the same navigation a second time.
        intent.removeExtra(MuhabbetNotifications.EXTRA_CONVERSATION_ID)

        val isGroup = intent.getStringExtra(MuhabbetNotifications.EXTRA_CONVERSATION_TYPE) ==
            MuhabbetNotifications.CONVERSATION_TYPE_GROUP

        bootstrapOrReuseKoin(platformModule)
            .get<PendingChatOpen>()
            .request(
                ChatOpenRequest(
                    conversationId = conversationId,
                    // Present for a notification, absent for a deep link — the consumer resolves it.
                    displayName = intent.getStringExtra(MuhabbetNotifications.EXTRA_SENDER_NAME),
                    isGroup = isGroup
                )
            )
    }

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

    private companion object {
        /**
         * `muhabbet://chat/{id}` puts `chat` in the host; the `https://` app link puts it in the
         * first path segment. Both filters are in the manifest, so both are checked.
         */
        const val DEEP_LINK_CHAT_HOST = "chat"
    }
}
