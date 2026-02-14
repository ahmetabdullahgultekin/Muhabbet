package com.muhabbet.app.di

import com.muhabbet.app.data.local.DatabaseDriverFactory
import com.muhabbet.app.data.local.LocalCache
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.platform.BackgroundSyncManager
import com.muhabbet.app.platform.ContactsProvider
import com.muhabbet.app.platform.IosContactsProvider
import com.muhabbet.app.platform.IosPushTokenProvider
import com.muhabbet.app.platform.PushTokenProvider
import com.muhabbet.shared.port.E2EKeyManager
import com.muhabbet.shared.port.EncryptionPort
import com.muhabbet.shared.port.NoOpEncryption
import com.muhabbet.shared.port.NoOpKeyManager
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults

fun iosPlatformModule(): Module = module {
    single<TokenStorage> { IosTokenStorage() }
    single { DatabaseDriverFactory() }
    single { LocalCache(driverFactory = get()) }
    single<ContactsProvider> { IosContactsProvider() }
    single<PushTokenProvider> { IosPushTokenProvider() }
    single { BackgroundSyncManager() }
    single<E2EKeyManager> { NoOpKeyManager() }
    single<EncryptionPort> { NoOpEncryption() }
}

class IosTokenStorage : TokenStorage {

    private val keychain = com.muhabbet.app.crypto.KeychainHelper
    // Non-sensitive prefs (language, theme) stay in NSUserDefaults
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun getAccessToken(): String? = keychain.load("access_token")
    override fun getRefreshToken(): String? = keychain.load("refresh_token")
    override fun getUserId(): String? = keychain.load("user_id")
    override fun getDeviceId(): String? = keychain.load("device_id")

    override fun saveTokens(accessToken: String, refreshToken: String, userId: String, deviceId: String) {
        keychain.save("access_token", accessToken)
        keychain.save("refresh_token", refreshToken)
        keychain.save("user_id", userId)
        keychain.save("device_id", deviceId)
    }

    override fun clear() {
        listOf("access_token", "refresh_token", "user_id", "device_id").forEach {
            keychain.delete(it)
        }
    }

    override fun getLanguage(): String? = defaults.stringForKey("app_language")

    override fun setLanguage(lang: String) {
        defaults.setObject(lang, forKey = "app_language")
    }

    override fun getTheme(): String? = defaults.stringForKey("app_theme")

    override fun setTheme(theme: String) {
        defaults.setObject(theme, forKey = "app_theme")
    }

    override fun getLastSyncTimestamp(): String? = defaults.stringForKey("last_sync_timestamp")

    override fun setLastSyncTimestamp(timestamp: String) {
        defaults.setObject(timestamp, forKey = "last_sync_timestamp")
    }
}
