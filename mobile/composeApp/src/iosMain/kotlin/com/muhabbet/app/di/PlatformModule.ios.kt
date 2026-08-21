package com.muhabbet.app.di

import com.muhabbet.app.data.local.DatabaseDriverFactory
import com.muhabbet.app.data.local.LocalCache
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.platform.BackgroundSyncManager
import com.muhabbet.app.platform.ContactsProvider
import com.muhabbet.app.platform.IosContactsProvider
import com.muhabbet.app.platform.IosNotificationPermission
import com.muhabbet.app.platform.IosPushTokenProvider
import com.muhabbet.app.platform.NotificationPermission
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
    single<NotificationPermission> { IosNotificationPermission() }
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

    // Synchronised on write, unlike most of its neighbours: the iOS language restart is exit(0), so
    // an unflushed value would not survive to the launch that has to read it.
    override fun setPendingLanguageRestart() {
        defaults.setBool(true, forKey = "pending_language_restart")
        defaults.synchronize()
    }

    override fun consumePendingLanguageRestart(): Boolean {
        val pending = defaults.boolForKey("pending_language_restart")
        if (pending) {
            defaults.removeObjectForKey("pending_language_restart")
            defaults.synchronize()
        }
        return pending
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

    // Keychain, not NSUserDefaults: same reasoning as AndroidTokenStorage's encrypted prefs — App
    // Lock (#378) is a security setting, so it belongs alongside the tokens rather than the
    // cosmetic prefs below. `clear()` already deletes every key this instance ever saved
    // (`listOf("access_token", ...).forEach { keychain.delete(it) }` above does NOT cover these two
    // keys, so unlike Android they intentionally survive logout here — there is no capability check
    // wired on iOS yet (see AppLockAuthenticator.ios.kt), so `getAppLockEnabled()` cannot currently
    // be turned on from `AppLockScreen` in the first place; these accessors exist so persistence is
    // ready the day the iOS mechanism lands, not because anything can set them to `true` today.
    override fun getAppLockEnabled(): Boolean = keychain.load("app_lock_enabled") == "true"

    override fun setAppLockEnabled(enabled: Boolean) {
        keychain.save("app_lock_enabled", if (enabled) "true" else "false")
    }

    override fun getAppLockTimeout(): String? = keychain.load("app_lock_timeout")

    override fun setAppLockTimeout(timeout: String) {
        keychain.save("app_lock_timeout", timeout)
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

    override fun getWallpaperGradientId(): String? = defaults.stringForKey("wallpaper_gradient_id")

    override fun setWallpaperGradientId(id: String?) {
        if (id == null) defaults.removeObjectForKey("wallpaper_gradient_id")
        else defaults.setObject(id, forKey = "wallpaper_gradient_id")
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

    override fun getTestBuildNoticeAckVersion(): String? =
        defaults.stringForKey("test_build_notice_ack_version")

    override fun setTestBuildNoticeAckVersion(version: String) {
        defaults.setObject(version, forKey = "test_build_notice_ack_version")
    }

    // Stored, though nothing on iOS reads it yet: IosNotificationPermission reports Unsupported, so
    // the gate never asks and never writes here. It is a real implementation rather than a no-op so
    // that wiring APNs later needs no change on this side.
    override fun getNotificationPermissionAsked(): Boolean =
        defaults.boolForKey("notification_permission_asked")

    override fun setNotificationPermissionAsked() {
        defaults.setBool(true, forKey = "notification_permission_asked")
    }
}
