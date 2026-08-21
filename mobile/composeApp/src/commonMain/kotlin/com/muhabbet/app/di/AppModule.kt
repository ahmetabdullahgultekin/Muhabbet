package com.muhabbet.app.di

import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.app.data.remote.WsClient
import com.muhabbet.app.data.repository.AuthRepository
import com.muhabbet.app.data.repository.CallRepository
import com.muhabbet.app.data.repository.ChannelRepository
import com.muhabbet.app.data.repository.BroadcastListRepository
import com.muhabbet.app.data.repository.CommunityRepository
import com.muhabbet.app.data.repository.ConversationRepository
import com.muhabbet.app.data.repository.DeviceLinkRepository
import com.muhabbet.app.data.repository.E2ESetupService
import com.muhabbet.app.data.repository.EncryptionRepository
import com.muhabbet.app.data.repository.GroupRepository
import com.muhabbet.app.data.repository.ConversationDirectory
import com.muhabbet.app.data.repository.InviteLinkRepository
import com.muhabbet.app.data.repository.MediaRepository
import com.muhabbet.app.data.repository.MediaUploadHelper
import com.muhabbet.app.data.repository.MessageRepository
import com.muhabbet.app.data.repository.ModerationRepository
import com.muhabbet.app.data.repository.KnownPeopleSource
import com.muhabbet.app.data.repository.PhoneNumberLookup
import com.muhabbet.app.data.repository.PushTokenRegistrar
import com.muhabbet.app.data.repository.StatusRepository
import com.muhabbet.app.data.repository.TwoStepRepository
import com.muhabbet.app.data.repository.WallpaperRepository
import org.koin.core.module.Module
import org.koin.dsl.module

fun appModule(): Module = module {
    single { ApiClient(tokenStorage = get()) }
    // E2E encrypt-on-send / decrypt-on-receive. Gated by E2EConfig.ENABLED (default OFF).
    // Resolves the 1:1 peer from the local conversation cache and ensures a Signal session
    // before encrypting; group / unresolved / non-text bodies fall back to plaintext.
    single {
        val tokenStorage = get<com.muhabbet.app.data.local.TokenStorage>()
        val localCache = get<com.muhabbet.app.data.local.LocalCache>()
        val e2eSetup = get<E2ESetupService>()
        com.muhabbet.app.crypto.MessageEncryptor(
            encryptionPort = get(),
            recipientResolver = { conversationId ->
                val selfId = tokenStorage.getUserId()
                val conv = localCache.getConversations().firstOrNull { it.id == conversationId }
                if (conv == null ||
                    conv.type != com.muhabbet.shared.model.ConversationType.DIRECT ||
                    selfId == null
                ) {
                    null
                } else {
                    conv.participants.firstOrNull { it.userId != selfId }?.let { peer ->
                        com.muhabbet.app.crypto.MessageEncryptor.RecipientInfo(
                            recipientId = peer.userId,
                            // Signal sessions are keyed by recipientId; deviceId is informational
                            // for the current SignalEncryption impl. Default to "1".
                            deviceId = "1"
                        )
                    }
                }
            },
            ensureSession = { recipientId -> e2eSetup.ensureSession(recipientId) },
            selfDeviceId = { tokenStorage.getDeviceId() }
        )
    }
    // localCache resolved as LocalCache **explicitly**. WsClient's parameter is the narrower
    // PendingMessageCache interface, which nothing registers, and Koin resolves get() by the
    // parameter type — so a bare get() here compiles and then throws NoDefinitionFoundException the
    // first time anything injects WsClient, which is at launch. That shipped as 0.3.7 and crashed
    // the app on start for every user (#633). The same note already sat two definitions below this
    // one, for ConversationRepository, and the parameter type was narrowed in #579 without anyone
    // coming back here.
    single {
        WsClient(
            apiClient = get(),
            tokenProvider = { get<com.muhabbet.app.data.local.TokenStorage>().getAccessToken() },
            localCache = get<com.muhabbet.app.data.local.LocalCache>(),
            messageEncryptor = get()
        )
    }
    single { AuthRepository(apiClient = get(), tokenStorage = get()) }
    // Single source of truth for push token registration — used by both the login/app-start
    // effect in App.kt and MuhabbetFirebaseMessagingService.onNewToken (androidMain). See #398.
    single {
        PushTokenRegistrar(
            pushTokenProvider = get(),
            authRepository = get(),
            tokenStorage = get()
        )
    }
    // localCache is resolved as LocalCache explicitly: the parameter's type is now the narrower
    // ConversationCache interface, which nothing registers, and Koin resolves get() by the
    // parameter type. Left implicit this would fail at startup, not at compile time.
    single { ConversationRepository(apiClient = get(), localCache = get<com.muhabbet.app.data.local.LocalCache>()) }
    // Reaching someone by typed phone number (#389). Client-only: a one-number lookup is a contact
    // sync with a one-element list, so it needs no endpoint of its own.
    single { PhoneNumberLookup(conversationRepository = get(), authRepository = get()) }
    // Where every member picker gets its candidates (#520). ContactsProvider comes from the
    // platform module, which is loaded alongside this one.
    single {
        KnownPeopleSource(
            conversationRepository = get(),
            contactsProvider = get(),
            tokenStorage = get()
        )
    }
    // How a screen holding only a conversation id gets the name, avatar and participants that go
    // with it (#543). Narrower than KnownPeopleSource on purpose — see that class's note.
    single { ConversationDirectory(conversationRepository = get()) }
    // The parking space between a tapped notification and the navigation that can act on it. A
    // singleton because the two ends are in different worlds: the Activity writes into it, a
    // composable that may not exist yet reads out of it (#594).
    single { com.muhabbet.app.navigation.PendingChatOpen() }
    // localCache resolved as LocalCache explicitly, for the same reason ConversationRepository is:
    // the parameter's type is the narrower MessageCache interface, which nothing registers, and
    // Koin resolves get() by the parameter type — left implicit this fails at startup, not at
    // compile time.
    single { MessageRepository(apiClient = get(), localCache = get<com.muhabbet.app.data.local.LocalCache>()) }
    // Media-blob E2E (Tier 1.4) — flag-gated (E2EConfig.mediaEncryptionActive), default OFF.
    single { com.muhabbet.app.crypto.MediaEncryptor() }
    single { MediaRepository(apiClient = get(), mediaEncryptor = get()) }
    single { MediaUploadHelper(mediaRepository = get(), tokenStorage = get(), mediaEncryptor = get()) }
    single { GroupRepository(apiClient = get()) }
    single { StatusRepository(apiClient = get()) }
    single { ChannelRepository(apiClient = get()) }
    single { CallRepository(apiClient = get()) }
    single { EncryptionRepository(apiClient = get()) }
    single { E2ESetupService(keyManager = get(), encryptionRepository = get()) }
    single { CommunityRepository(apiClient = get()) }
    single { BroadcastListRepository(apiClient = get()) }
    single { InviteLinkRepository(apiClient = get()) }
    single { TwoStepRepository(apiClient = get()) }
    single { ModerationRepository(apiClient = get()) }
    single { WallpaperRepository(tokenStorage = get()) }
    // Read at the composition root, above MuhabbetTheme — see App.kt.
    single { com.muhabbet.app.data.local.ThemeController(tokenStorage = get()) }
    // Singleton on purpose: read receipts appear on two screens and must not disagree.
    single { com.muhabbet.app.data.local.PrivacySettingsController(authRepository = get()) }
    // App Lock (#378) — singleton for the same reason: AppLockScreen writes it, AppLockGate
    // (mounted once, above the whole authenticated app) reads it, and a second copy could disagree.
    single { com.muhabbet.app.data.local.AppLockController(tokenStorage = get()) }
    // Foreground/background state. Written in exactly one place (App.kt, from the lifecycle
    // RootComponent already carries) and read by any screen that must act when the user comes back
    // — an open chat re-asserting its read receipt, today. A singleton because a second copy could
    // report a different answer than the one the writer is updating.
    single { com.muhabbet.app.platform.AppVisibility() }
    // Contacts access (#691) — singleton for the third time and the third identical reason. The
    // answer used to live in five per-screen `remember` slots that could not tell each other apart;
    // granting the permission from system settings and coming back changed none of them, because
    // returning to the foreground tears down no composition. One flow, re-read on every foreground
    // by ContactsAccessRefreshEffect.
    single {
        com.muhabbet.app.data.local.ContactsAccessController(
            contactsProvider = get(),
            tokenStorage = get()
        )
    }
    // Multi-device linking (Tier 2, NON-CRYPTO slice) — gated by MultiDeviceConfig.ENABLED, default OFF.
    single { DeviceLinkRepository(apiClient = get()) }
    // E2EKeyManager and EncryptionPort are provided by platform modules.
    //
    // BOTH platforms currently wire NoOpKeyManager + NoOpEncryption. This comment used to claim
    // Android wires SignalKeyManager; that stopped being true in #49, when the four libsignal
    // sources were renamed *.kt.disabled because they do not compile against the pinned API. See
    // CLAUDE.md -> "libsignal upgrade (BLOCKED)".
    //   Android: NoOpKeyManager + NoOpEncryption (PlatformModule.android.kt)
    //   iOS:     NoOpKeyManager + NoOpEncryption (PlatformModule.ios.kt)
    // NoOp returns PLAINTEXT and placeholder key material — see E2EKeyManager.producesRealKeyMaterial.
}
