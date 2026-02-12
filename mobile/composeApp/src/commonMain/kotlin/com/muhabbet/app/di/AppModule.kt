package com.muhabbet.app.di

import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.app.data.remote.WsClient
import com.muhabbet.app.data.repository.AuthRepository
import com.muhabbet.app.data.repository.ConversationRepository
import com.muhabbet.app.data.repository.GroupRepository
import com.muhabbet.app.data.repository.MediaRepository
import com.muhabbet.app.data.repository.MessageRepository
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
}
