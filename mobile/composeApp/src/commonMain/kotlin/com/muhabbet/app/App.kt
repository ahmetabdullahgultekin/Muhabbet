package com.muhabbet.app

import com.muhabbet.designsystem.theme.MuhabbetTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.muhabbet.app.data.local.ThemeController
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.data.remote.WsClient
import com.muhabbet.app.data.repository.PushTokenRegistrar
import com.muhabbet.app.data.repository.ReceivedMediaAutoSaver
import com.muhabbet.app.crypto.E2EConfig
import com.muhabbet.app.data.repository.E2ESetupService
import com.muhabbet.app.di.bootstrapOrReuseKoin
import com.muhabbet.app.navigation.RootComponent
import com.muhabbet.app.navigation.RootContent
import com.muhabbet.app.navigation.isSessionActive
import com.muhabbet.app.platform.AppVisibility
import com.muhabbet.app.platform.CrashReporter
import com.muhabbet.app.platform.rememberMediaGallerySaver
import com.muhabbet.app.session.SessionWiring
import com.muhabbet.app.util.Log
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.app_language_code
import com.muhabbet.shared.model.MessageStatus
import com.muhabbet.shared.protocol.WsMessage
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.KoinContext
import org.koin.compose.koinInject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.filterIsInstance
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

        // Configuring the crash SDK genuinely is a once-per-process job, so `Unit` is the right key
        // here. Naming the *user* was not, and used to ride along on this line: it read
        // `tokenStorage.getUserId()` at the first composition, which on a fresh install is the login
        // screen, where the answer is null. It is the fifth instance of #349's Unit-key defect and
        // the one #540 left behind on purpose ("`setUser` has the same defect ... flagged rather
        // than done"). It now lives in SessionWiring, keyed on the session like everything else.
        LaunchedEffect(Unit) {
            CrashReporter.init()
        }

        MuhabbetTheme(mode = themeMode, hapticsEnabled = hapticsEnabled) {
            SessionLifecycle(root)
            RootContent(root)
        }
    }
}

/**
 * Everything the app switches on when someone is signed in, and off when they are not.
 *
 * Called `WebSocketLifecycle` until #349 was finished, which was accurate for about one of the six
 * things it does. The name mattered more than it looks: this is where a reader goes to answer "what
 * happens at login", and for a long time nobody looking for that would have opened a file called
 * WebSocketLifecycle — which is part of why five of these effects sat keyed on `Unit`, never
 * re-running after the login they were supposed to react to, for as long as they did.
 *
 * Every effect below is keyed on `loggedIn`, never on `Unit`. That is the whole rule of this file.
 */
@Composable
private fun SessionLifecycle(root: RootComponent) {
    val wsClient: WsClient = koinInject()
    val tokenStorage: TokenStorage = koinInject()
    val pushTokenRegistrar: PushTokenRegistrar = koinInject()

    // Reactive login state, unlike tokenStorage.isLoggedIn() below — that is a snapshot read once
    // when a Unit-keyed effect first runs. This tracks the navigation stack, so it flips the
    // moment RootComponent.onAuthComplete() swaps Config.Auth -> Config.Main. Shared with
    // RootContent rather than re-derived here, so there is one answer to "is anyone signed in".
    val loggedIn = isSessionActive(root)

    // The generation this composition connected as, handed straight back to disconnect().
    //
    // `onDispose` belongs to a composition that is already gone, but it still runs whenever Android
    // gets round to it — which, on an Activity recreation, is *after* the replacement Activity has
    // composed and connected. An unguarded disconnect() at that point tore down the connection the
    // new composition had just opened, and the app then sat disconnected with no reconnect loop
    // running for as long as it stayed open (#511). The language switch recreates the Activity on
    // purpose, so this was reachable by ordinary use, not just by rotation.
    // Keyed on `loggedIn`, not `Unit` — see the push-token effect below for the same reasoning and
    // the same history. A Unit-keyed effect samples isLoggedIn() once, at the first composition,
    // which on a fresh install is the auth screen; it never re-evaluates, so a user who signs in
    // during the session gets no socket at all until they force-quit and relaunch (#349). Verified
    // on the emulator: after login, zero WsClient lines in logcat and the connection strip stuck on
    // "no connection"; after a restart, Connecting -> Connected one second apart.
    //
    // Re-keying is only safe because of #511's generation guard: disconnect() now refuses a
    // teardown that does not belong to the live connection, so the outgoing composition's onDispose
    // cannot close a socket the incoming one has just opened. It also means logout now disconnects,
    // which it never did.
    DisposableEffect(loggedIn) {
        val generation = if (loggedIn) wsClient.connect() else null
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

    // Global DELIVERED ack: send DELIVERED for every incoming message regardless of active screen.
    // Keyed on `loggedIn` for the same reason as the socket above (#349) — on a Unit key this pump
    // never started for a user who signed in during the session, so their first session delivered
    // no receipts at all and the sender's ticks never moved.
    LaunchedEffect(loggedIn) {
        if (loggedIn) {
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

    // Media visibility (#593): copy received photos and videos into the phone's gallery, when the
    // user has switched that on.
    //
    // A collector of its own rather than a branch inside the ack pump above, for two reasons. A
    // download takes seconds and `collect` is sequential, so sharing the pump would delay every
    // DELIVERED receipt behind whatever photo is being fetched — the ticks would visibly lag the
    // message. And the two have different failure tolerances: the pump must never die, while an
    // auto-save that throws should be absorbed and logged. `incoming` is a SharedFlow, so a second
    // collector sees the same frames rather than competing for them.
    //
    // `buffer(DROP_OLDEST)` is load-bearing, not decoration. `_incoming` is a MutableSharedFlow with
    // 64 slots and the default SUSPEND overflow, so a collector that takes seconds per frame
    // back-pressures the socket read loop once those fill — every chat on the device would stop
    // receiving while one photo downloaded. The buffer decouples this collector from that; under a
    // burst big enough to fill it, a photo is skipped rather than the app stalling, which is the
    // right trade for a convenience feature. Saving each frame in its own `launch` would avoid the
    // stall too, but with no bound at all: two hundred forwarded photos would be two hundred
    // concurrent downloads held in memory.
    //
    // Keyed on `loggedIn` for the third time on this screen and the same reason (#349): mounted
    // above the auth switch, a Unit key would sample isLoggedIn() once, on the login screen.
    val autoSaver: ReceivedMediaAutoSaver = koinInject()
    val gallerySaver = rememberMediaGallerySaver()
    LaunchedEffect(loggedIn, gallerySaver) {
        if (loggedIn) {
            val currentUserId = tokenStorage.getUserId() ?: return@LaunchedEffect
            wsClient.incoming
                .filterIsInstance<WsMessage.NewMessage>()
                .buffer(capacity = AUTO_SAVE_QUEUE, onBufferOverflow = BufferOverflow.DROP_OLDEST)
                .collect { message ->
                    autoSaver.onMessageReceived(message, currentUserId, gallerySaver)
                }
        }
    }

    // The language the app is *rendering*, read out of strings.xml itself, so it accounts for the
    // in-app language picker and — when nothing has been picked — for the phone's own language.
    // Push text is composed on the server, which has no other way to learn either: every
    // notification went out in Turkish until this value started travelling with the token (#469).
    // Deliberately the rendered language rather than the stored preference, for the same reason
    // `selectedLanguage()` answers with what is on screen (#548): the two can disagree, and the one
    // the user is actually reading is the one to notify in.
    val renderedLanguage = stringResource(Res.string.app_language_code)

    // Register the push token whenever the session becomes active (#398). Keyed on `loggedIn`,
    // not `Unit`: this composable is mounted ABOVE the auth/main navigation switch (see App()),
    // so a Unit-keyed effect samples tokenStorage.isLoggedIn() once, before the login screen has
    // even run, and never re-evaluates it. That made push-token registration work only for a user
    // who force-quit and relaunched an already-authenticated app — a path zero of six production
    // devices had taken. Keying on `loggedIn` re-fires the moment RootComponent swaps to
    // Config.Main, so first login registers a token exactly like an app start that was already
    // logged in. #349 tracks the identical Unit-key defect for WS connect, the E2E effect below
    // and background sync; this call site is scoped to push token registration only.
    // Also keyed on the rendered language, so switching language re-registers rather than leaving
    // the server notifying in the language the user just moved away from.
    LaunchedEffect(loggedIn, renderedLanguage) {
        if (loggedIn) {
            pushTokenRegistrar.registerIfLoggedIn(languageTag = renderedLanguage)
        }
    }

    // Register E2E encryption keys on startup — only when E2E is actually on.
    //
    // This used to run on every launch for every logged-in user, gated on nothing but isLoggedIn().
    // With E2E flag-OFF and NoOpKeyManager wired, that published placeholder key material to the
    // production backend each time the process started. A feature that is switched off should put
    // nothing on the server.
    val e2eSetupService: E2ESetupService = koinInject()
    LaunchedEffect(loggedIn) {
        if (E2EConfig.ENABLED && loggedIn) {
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

    // Everything a session leaves OUTSIDE the composition: the WorkManager sync job, which survives
    // process death and a reboot, and the crash reporter's user, which is global state in the SDK.
    //
    // The only effect here with an `else`, and it needs one. The four above are all switched off by
    // the composition going away — the socket by its own onDispose, the ack pump and the two
    // registrations by their coroutines being cancelled. These two are not: nothing in the app
    // called cancelPeriodicSync() (zero call sites in the repository), so a device that logged in
    // once kept waking every 15 minutes for as long as the app stayed installed, and the crash
    // reporter kept naming the account that had logged out. See SessionWiring.
    val sessionWiring: SessionWiring = koinInject()
    LaunchedEffect(loggedIn) {
        if (loggedIn) sessionWiring.onSessionActive() else sessionWiring.onSessionEnded()
    }
}

/**
 * How many freshly arrived media frames may queue for the gallery writer before the oldest is
 * dropped. Deliberately far smaller than the socket's own 64-slot buffer, so this collector is
 * never the thing holding the read loop back — see the effect that uses it.
 */
private const val AUTO_SAVE_QUEUE = 16
