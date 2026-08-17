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

    // Plain prefs, alongside the language it belongs to: the flag is read at the composition root
    // before anything has unlocked the encrypted store. `apply()` is enough — the language restart
    // is finish() plus startActivity() in the SAME process, so the in-memory value is already there
    // when the replacement Activity reads it.
    override fun setPendingLanguageRestart() {
        plainPrefs.edit().putBoolean("pending_language_restart", true).apply()
    }

    override fun consumePendingLanguageRestart(): Boolean {
        val pending = plainPrefs.getBoolean("pending_language_restart", false)
        if (pending) plainPrefs.edit().remove("pending_language_restart").apply()
        return pending
    }

    override fun getHapticsEnabled(): Boolean = plainPrefs.getBoolean("haptics_enabled", true)

    override fun setHapticsEnabled(enabled: Boolean) {
        plainPrefs.edit().putBoolean("haptics_enabled", enabled).apply()
    }

    override fun getTheme(): String? = plainPrefs.getString("app_theme", null)

    override fun setTheme(theme: String) {
        plainPrefs.edit().putString("app_theme", theme).apply()
    }

    override fun getContactSyncConsentAt(): String? = plainPrefs.getString("contact_sync_consent_at", null)

    override fun setContactSyncConsentAt(timestamp: String) {
        plainPrefs.edit().putString("contact_sync_consent_at", timestamp).apply()
    }

    override fun clearContactSyncConsent() {
        plainPrefs.edit().remove("contact_sync_consent_at").apply()
    }

    override fun getMediaQuality(): String? = plainPrefs.getString("media_quality", null)

    override fun setMediaQuality(quality: String) {
        plainPrefs.edit().putString("media_quality", quality).apply()
    }

    override fun getLastSyncTimestamp(): String? = plainPrefs.getString("last_sync_timestamp", null)

    override fun setLastSyncTimestamp(timestamp: String) {
        plainPrefs.edit().putString("last_sync_timestamp", timestamp).apply()
    }

    override fun getWallpaperType(): String? = plainPrefs.getString("wallpaper_type", null)

    override fun setWallpaperType(type: String) {
        plainPrefs.edit().putString("wallpaper_type", type).apply()
    }

    override fun getSolidColor(): String? = plainPrefs.getString("wallpaper_solid_color", null)

    override fun setSolidColor(color: String?) {
        if (color == null) plainPrefs.edit().remove("wallpaper_solid_color").apply()
        else plainPrefs.edit().putString("wallpaper_solid_color", color).apply()
    }

    override fun getCustomWallpaperPath(): String? = plainPrefs.getString("wallpaper_custom_path", null)

    override fun setCustomWallpaperPath(path: String?) {
        if (path == null) plainPrefs.edit().remove("wallpaper_custom_path").apply()
        else plainPrefs.edit().putString("wallpaper_custom_path", path).apply()
    }

    override fun getDarkModeWallpaperEnabled(): Boolean = plainPrefs.getBoolean("wallpaper_dark_mode", false)

    override fun setDarkModeWallpaperEnabled(enabled: Boolean) {
        plainPrefs.edit().putBoolean("wallpaper_dark_mode", enabled).apply()
    }
}
