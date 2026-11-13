package com.muhabbet.app

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.fragment.app.FragmentActivity
import com.arkivanov.decompose.defaultComponentContext
import com.muhabbet.app.di.androidPlatformModule
import com.muhabbet.app.di.bootstrapOrReuseKoin
import com.muhabbet.app.navigation.ChatOpenRequest
import com.muhabbet.app.navigation.DeepLink
import com.muhabbet.app.navigation.DeepLinkParser
import com.muhabbet.app.navigation.PendingChatOpen
import com.muhabbet.app.platform.MuhabbetNotifications
import com.muhabbet.app.platform.NotificationLaunchToken
import java.util.Locale

/**
 * `FragmentActivity`, not the bare `ComponentActivity` this was before App Lock (#378):
 * `androidx.biometric.BiometricPrompt` hosts a headless Fragment internally to survive
 * configuration changes, and requires a `FragmentActivity` (or `Fragment`) host to attach it to —
 * see `AppLockAuthenticator.android.kt`. `FragmentActivity` extends `ComponentActivity`, so
 * `setContent`, `enableEdgeToEdge`, `defaultComponentContext()` and everything below are unaffected.
 */
class MainActivity : FragmentActivity() {
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
        // request by the time MainComponent builds its initial stack (#823).
        //
        // Only on a fresh start (#815). A recreation — rotation, the language switch — or a restore
        // after process death hands this Activity its original launch intent again, while Decompose
        // restores a stack that already contains whatever that intent opened. Acting on it again
        // pushed the chat a second time, and made a linked chat impossible to leave by rotating.
        if (savedInstanceState == null) handleLaunchIntent(intent, platformModule)

        setContent {
            // Publish every Modifier.testTag as the Android view resource-id that uiautomator
            // reads. Without this the tags exist only inside Compose's semantics tree, so
            // instrumentation can address a control only by its visible text — which breaks
            // for icon-only controls and for every locale but the one the test was written in.
            Box(modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                // Back behind App Lock's cover sends the app to the background, as back from a start
                // screen would, without touching the stack the cover hides (#803).
                App(componentContext, platformModule, onBackWhileLocked = { moveTaskToBack(true) })
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
     * [handleLaunchIntent] replaces `getIntent()` with a consumed copy, so nothing later reads the
     * request a second time; the platform does not do this for us.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLaunchIntent(intent, androidPlatformModule(applicationContext))
    }

    /**
     * Parks a request to open a conversation, if the intent carries a trustworthy one, and consumes
     * the intent either way.
     *
     * Consumed means `getIntent()` is replaced by a copy with no data and no extras. Removing only the
     * conversation extra, as this used to, left `intent.data` in place, so a `muhabbet://chat/{id}`
     * link replayed on every recreation (#815).
     */
    private fun handleLaunchIntent(intent: Intent, platformModule: org.koin.core.module.Module) {
        val request = chatOpenRequestFrom(intent)
        setIntent(Intent(intent).apply {
            data = null
            replaceExtras(Bundle())
        })
        request ?: return
        bootstrapOrReuseKoin(platformModule).get<PendingChatOpen>().request(request)
    }

    /**
     * The two sources that may open a chat:
     *  - one of this app's own notifications, recognised by [NotificationLaunchToken] — the Activity
     *    is exported, so a bare `conversationId` extra could come from any installed app;
     *  - a link, parsed by [DeepLinkParser], which accepts a well-formed conversation id and nothing
     *    else.
     *
     * Neither supplies a name. The chat resolves its own title (#815).
     */
    private fun chatOpenRequestFrom(intent: Intent): ChatOpenRequest? {
        val fromNotification = intent.getStringExtra(MuhabbetNotifications.EXTRA_CONVERSATION_ID)
            ?.takeIf { NotificationLaunchToken.matches(this, intent.getStringExtra(MuhabbetNotifications.EXTRA_LAUNCH_TOKEN)) }
            ?.takeIf(DeepLinkParser::isUuid)
        if (fromNotification != null) return ChatOpenRequest(conversationId = fromNotification)

        val uri = intent.data ?: return null
        return when (val link = DeepLinkParser.parse(uri.scheme, uri.host, uri.pathSegments)) {
            is DeepLink.Chat -> ChatOpenRequest(conversationId = link.conversationId)
            // No in-app join flow exists yet; an invite link opens the app where it always does.
            is DeepLink.Invite, DeepLink.Unknown -> null
        }
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

}
