package com.muhabbet.app.di

import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.app.data.remote.WsClient
import com.muhabbet.app.data.repository.AuthRepository
import com.muhabbet.app.data.repository.CallRepository
import com.muhabbet.app.data.repository.ChannelRepository
import com.muhabbet.app.data.repository.CommunityRepository
import com.muhabbet.app.data.repository.ConversationRepository
import com.muhabbet.app.data.repository.DeviceLinkRepository
import com.muhabbet.app.data.repository.E2ESetupService
import com.muhabbet.app.data.repository.EncryptionRepository
import com.muhabbet.app.data.repository.GroupRepository
import com.muhabbet.app.data.repository.InviteLinkRepository
import com.muhabbet.app.data.repository.MediaRepository
import com.muhabbet.app.data.repository.MediaUploadHelper
import com.muhabbet.app.data.repository.MessageRepository
import com.muhabbet.app.data.repository.StatusRepository
import com.muhabbet.app.data.repository.PrivacyModeRepository
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
    single { WsClient(apiClient = get(), tokenProvider = { get<com.muhabbet.app.data.local.TokenStorage>().getAccessToken() }, localCache = get(), messageEncryptor = get()) }
    single { AuthRepository(apiClient = get(), tokenStorage = get()) }
    single { ConversationRepository(apiClient = get(), localCache = get()) }
    single { MessageRepository(apiClient = get(), localCache = get()) }
    // Media-blob E2E (Tier 1.4) — flag-gated (E2EConfig.mediaEncryptionActive), default OFF.
    single { com.muhabbet.app.crypto.MediaEncryptor() }
    single { MediaRepository(apiClient = get(), mediaEncryptor = get()) }
    single { MediaUploadHelper(mediaRepository = get(), mediaEncryptor = get()) }
    single { GroupRepository(apiClient = get()) }
    single { StatusRepository(apiClient = get()) }
    single { ChannelRepository(apiClient = get()) }
    single { CallRepository(apiClient = get()) }
    single { EncryptionRepository(apiClient = get()) }
    single { E2ESetupService(keyManager = get(), encryptionRepository = get()) }
    single { CommunityRepository(apiClient = get()) }
    single { InviteLinkRepository(apiClient = get()) }
    single { WallpaperRepository(tokenStorage = get()) }
    single { PrivacyModeRepository(tokenStorage = get()) }
    // Multi-device linking (Tier 2, NON-CRYPTO slice) — gated by MultiDeviceConfig.ENABLED, default OFF.
    single { DeviceLinkRepository(apiClient = get()) }
    // E2EKeyManager and EncryptionPort are provided by platform modules:
    // Android: SignalKeyManager + SignalEncryption (libsignal-android)
    // iOS: NoOpKeyManager + NoOpEncryption (stub)
}
