package com.muhabbet.app.di

import com.muhabbet.app.data.local.DatabaseDriverFactory
import com.muhabbet.app.data.local.LocalCache
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.platform.BackgroundSyncManager
import com.muhabbet.app.platform.ContactsProvider
import com.muhabbet.app.platform.IosContactsProvider
import com.muhabbet.app.platform.IosPushTokenProvider
import com.muhabbet.app.platform.PushTokenProvider
import com.muhabbet.app.platform.SpeechTranscriber
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
    single { SpeechTranscriber() }
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

    override fun getHapticsEnabled(): Boolean =
        if (defaults.objectForKey("haptics_enabled") == null) true
        else defaults.boolForKey("haptics_enabled")

    override fun setHapticsEnabled(enabled: Boolean) {
        defaults.setBool(enabled, forKey = "haptics_enabled")
        defaults.synchronize()
    }

    override fun getTheme(): String? = defaults.stringForKey("app_theme")

    override fun setTheme(theme: String) {
        defaults.setObject(theme, forKey = "app_theme")
    }

    override fun getContactSyncConsentAt(): String? = defaults.stringForKey("contact_sync_consent_at")

    override fun setContactSyncConsentAt(timestamp: String) {
        defaults.setObject(timestamp, forKey = "contact_sync_consent_at")
    }

    override fun clearContactSyncConsent() {
        defaults.removeObjectForKey("contact_sync_consent_at")
    }

    override fun getMediaQuality(): String? = defaults.stringForKey("media_quality")

    override fun setMediaQuality(quality: String) {
        defaults.setObject(quality, forKey = "media_quality")
    }

    override fun getLastSyncTimestamp(): String? = defaults.stringForKey("last_sync_timestamp")

    override fun setLastSyncTimestamp(timestamp: String) {
        defaults.setObject(timestamp, forKey = "last_sync_timestamp")
    }

    override fun getWallpaperType(): String? = defaults.stringForKey("wallpaper_type")

    override fun setWallpaperType(type: String) {
        defaults.setObject(type, forKey = "wallpaper_type")
    }

    override fun getSolidColor(): String? = defaults.stringForKey("wallpaper_solid_color")

    override fun setSolidColor(color: String?) {
        if (color == null) defaults.removeObjectForKey("wallpaper_solid_color")
        else defaults.setObject(color, forKey = "wallpaper_solid_color")
    }

    override fun getCustomWallpaperPath(): String? = defaults.stringForKey("wallpaper_custom_path")

    override fun setCustomWallpaperPath(path: String?) {
        if (path == null) defaults.removeObjectForKey("wallpaper_custom_path")
        else defaults.setObject(path, forKey = "wallpaper_custom_path")
    }

    override fun getDarkModeWallpaperEnabled(): Boolean =
        if (defaults.objectForKey("wallpaper_dark_mode") == null) false
        else defaults.boolForKey("wallpaper_dark_mode")

    override fun setDarkModeWallpaperEnabled(enabled: Boolean) {
        defaults.setBool(enabled, forKey = "wallpaper_dark_mode")
    }
}
