package com.muhabbet.app.di

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.muhabbet.app.data.local.DatabaseDriverFactory
import com.muhabbet.app.data.local.LocalCache
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.platform.AndroidContactsProvider
import com.muhabbet.app.platform.AndroidPushTokenProvider
import com.muhabbet.app.platform.BackgroundSyncManager
import com.muhabbet.app.platform.ContactsProvider
import com.muhabbet.app.platform.PushTokenProvider
import com.muhabbet.app.platform.SpeechTranscriber
import com.muhabbet.shared.port.E2EKeyManager
import com.muhabbet.shared.port.EncryptionPort
import com.muhabbet.shared.port.NoOpEncryption
import com.muhabbet.shared.port.NoOpKeyManager
import org.koin.core.module.Module
import org.koin.dsl.module

fun androidPlatformModule(context: Context): Module = module {
    single<Context> { context }
    single<TokenStorage> { AndroidTokenStorage(context) }
    single { DatabaseDriverFactory(context) }
    single { LocalCache(driverFactory = get()) }
    single<ContactsProvider> { AndroidContactsProvider(context) }
    single<PushTokenProvider> { AndroidPushTokenProvider() }
    single { BackgroundSyncManager(context) }
    single { SpeechTranscriber(context) }
    // NOTE: The libsignal Signal Protocol implementation (SignalKeyManager / SignalEncryption /
    // *SignalProtocolStore) is BLOCKED — it does not compile against the pinned
    // libsignal-android:0.86.5 and requires an owner-driven, on-device-verified rewrite
    // (see CLAUDE.md → "libsignal upgrade (BLOCKED)"). Those 4 files are disabled (*.kt.disabled).
    // Android therefore falls back to the same NoOp path iOS already uses. This is byte-identical
    // to current prod behavior because E2E is flag-OFF by default (E2EConfig.ENABLED = false).
    // E2E MUST remain OFF on this build: NoOp returns plaintext, so flipping the flag here would
    // send plaintext labelled as encrypted. Do not enable E2E until the libsignal rewrite lands.
    single<E2EKeyManager> { NoOpKeyManager() }
    single<EncryptionPort> { NoOpEncryption() }
}

class AndroidTokenStorage(private val context: Context) : TokenStorage {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        "muhabbet_secure_prefs",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // Language is stored in plain prefs (not encrypted) so it's readable before crypto init
    private val plainPrefs: SharedPreferences =
        context.getSharedPreferences("muhabbet_prefs", Context.MODE_PRIVATE)

    override fun getAccessToken(): String? = prefs.getString("access_token", null)
    override fun getRefreshToken(): String? = prefs.getString("refresh_token", null)
    override fun getUserId(): String? = prefs.getString("user_id", null)
    override fun getDeviceId(): String? = prefs.getString("device_id", null)

    override fun saveTokens(accessToken: String, refreshToken: String, userId: String, deviceId: String) {
        prefs.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .putString("user_id", userId)
            .putString("device_id", deviceId)
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    override fun getLanguage(): String? = plainPrefs.getString("app_language", null)

    override fun setLanguage(lang: String) {
        plainPrefs.edit().putString("app_language", lang).apply()
    }

    override fun getTheme(): String? = plainPrefs.getString("app_theme", null)

    override fun setTheme(theme: String) {
        plainPrefs.edit().putString("app_theme", theme).apply()
    }

    override fun getLastSyncTimestamp(): String? = plainPrefs.getString("last_sync_timestamp", null)

    override fun setLastSyncTimestamp(timestamp: String) {
        plainPrefs.edit().putString("last_sync_timestamp", timestamp).apply()
    }

    // --- App-lock + Mahrem Mod (Privacy Mode) ---
    // App-lock enable/timeout live in plainPrefs so the gate decision is readable at cold start.
    override fun getAppLockEnabled(): Boolean = plainPrefs.getBoolean("app_lock_enabled", false)

    override fun setAppLockEnabled(enabled: Boolean) {
        plainPrefs.edit().putBoolean("app_lock_enabled", enabled).apply()
    }

    override fun getAppLockTimeout(): String? = plainPrefs.getString("app_lock_timeout", null)

    override fun setAppLockTimeout(timeout: String) {
        plainPrefs.edit().putString("app_lock_timeout", timeout).apply()
    }

    // PIN hash + salt are sensitive → encrypted prefs only.
    override fun getPrivacyPinHash(): String? = prefs.getString("privacy_pin_hash", null)

    override fun setPrivacyPinHash(hash: String?) {
        prefs.edit().apply {
            if (hash == null) remove("privacy_pin_hash") else putString("privacy_pin_hash", hash)
        }.apply()
    }

    override fun getPrivacyPinSalt(): String? = prefs.getString("privacy_pin_salt", null)

    override fun setPrivacyPinSalt(salt: String?) {
        prefs.edit().apply {
            if (salt == null) remove("privacy_pin_salt") else putString("privacy_pin_salt", salt)
        }.apply()
    }

    // Notification-preview + screenshot-guard flags live in plainPrefs because the FCM service reads
    // them before the encrypted store is initialised (mirrors the language/theme pattern).
    override fun getHideNotificationPreview(): Boolean =
        plainPrefs.getBoolean("privacy_hide_notification_preview", false)

    override fun setHideNotificationPreview(enabled: Boolean) {
        plainPrefs.edit().putBoolean("privacy_hide_notification_preview", enabled).apply()
    }

    override fun getScreenshotGuardEnabled(): Boolean =
        plainPrefs.getBoolean("privacy_screenshot_guard", false)

    override fun setScreenshotGuardEnabled(enabled: Boolean) {
        plainPrefs.edit().putBoolean("privacy_screenshot_guard", enabled).apply()
    }
}
