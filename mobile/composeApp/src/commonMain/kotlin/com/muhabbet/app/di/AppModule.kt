package com.muhabbet.app.di

import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.app.data.remote.WsClient
import com.muhabbet.app.data.repository.AuthRepository
import com.muhabbet.app.data.repository.ConversationRepository
import com.muhabbet.app.data.repository.GroupRepository
import com.muhabbet.app.data.repository.MediaRepository
import com.muhabbet.app.data.repository.MessageRepository
import com.muhabbet.app.data.repository.CallRepository
import com.muhabbet.app.data.repository.ChannelRepository
import com.muhabbet.app.data.repository.E2ESetupService
import com.muhabbet.app.data.repository.EncryptionRepository
import com.muhabbet.app.data.repository.StatusRepository
import org.koin.core.module.Module
import org.koin.dsl.module

fun appModule(): Module = module {
    single { ApiClient(tokenStorage = get()) }
    single { WsClient(apiClient = get(), tokenProvider = { get<com.muhabbet.app.data.local.TokenStorage>().getAccessToken() }) }
    single { AuthRepository(apiClient = get(), tokenStorage = get()) }
    single { ConversationRepository(apiClient = get()) }
    single { MessageRepository(apiClient = get()) }
    single { MediaRepository(apiClient = get()) }
    single { GroupRepository(apiClient = get()) }
    single { StatusRepository(apiClient = get()) }
    single { ChannelRepository(apiClient = get()) }
    single { CallRepository(apiClient = get()) }
    single { EncryptionRepository(apiClient = get()) }
    single { E2ESetupService(keyManager = get(), encryptionRepository = get()) }
    // E2EKeyManager and EncryptionPort are provided by platform modules:
    // Android: SignalKeyManager + SignalEncryption (libsignal-android)
    // iOS: NoOpKeyManager + NoOpEncryption (stub)
}
