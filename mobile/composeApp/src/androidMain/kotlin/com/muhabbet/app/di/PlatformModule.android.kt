package com.muhabbet.app.di

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.muhabbet.app.data.local.DatabaseDriverFactory
import com.muhabbet.app.data.local.LocalCache
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.platform.AndroidContactsProvider
import com.muhabbet.app.platform.AndroidNotificationPermission
import com.muhabbet.app.platform.AndroidPushTokenProvider
import com.muhabbet.app.platform.BackgroundSyncManager
import com.muhabbet.app.platform.ContactsProvider
import com.muhabbet.app.platform.NotificationPermission
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
    single<NotificationPermission> { AndroidNotificationPermission(context) }
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

    // The *encrypted* prefs, not plainPrefs: unlike theme/haptics/wallpaper, this is a security
    // setting (#378), so it belongs where AndroidTokenStorage already keeps tokens rather than
    // alongside cosmetic preferences. It is also why these two are cleared on logout along with the
    // tokens (see clear() above) rather than surviving it — a shared device signing a new account
    // in should not inherit the previous account's lock choice.
    override fun getAppLockEnabled(): Boolean = prefs.getBoolean("app_lock_enabled", false)

    override fun setAppLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("app_lock_enabled", enabled).apply()
    }

    override fun getAppLockTimeout(): String? = prefs.getString("app_lock_timeout", null)

    override fun setAppLockTimeout(timeout: String) {
        prefs.edit().putString("app_lock_timeout", timeout).apply()
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

    // Plain prefs, not the encrypted ones: this is read on the first frame after login, and it says
    // nothing about the user that a stranger with the phone could not learn by opening the app.
    override fun getTestBuildNoticeAckVersion(): String? =
        plainPrefs.getString("test_build_notice_ack_version", null)

    override fun setTestBuildNoticeAckVersion(version: String) {
        plainPrefs.edit().putString("test_build_notice_ack_version", version).apply()
    }

    // Plain prefs, for the same reason as the notice above: read on the first frame after login,
    // and it says nothing about the user. `apply()` is enough even though the very next thing that
    // happens is a system dialog the user could answer by killing the app — showing that dialog
    // pauses the Activity, and Android flushes pending `apply()` writes on the way through onPause.
    override fun getNotificationPermissionAsked(): Boolean =
        plainPrefs.getBoolean("notification_permission_asked", false)

    override fun setNotificationPermissionAsked() {
        plainPrefs.edit().putBoolean("notification_permission_asked", true).apply()
    }

    // Plain prefs, for the same reasons as the two above. Both are read on the first frame after
    // login and neither says anything about the user that a stranger holding the phone could not
    // learn by opening the app.
    override fun getContactsPermissionAsked(): Boolean =
        plainPrefs.getBoolean("contacts_permission_asked", false)

    override fun setContactsPermissionAsked() {
        plainPrefs.edit().putBoolean("contacts_permission_asked", true).apply()
    }

    override fun getWelcomeSeen(): Boolean = plainPrefs.getBoolean("welcome_seen", false)

    override fun setWelcomeSeen() {
        plainPrefs.edit().putBoolean("welcome_seen", true).apply()
    }
}
