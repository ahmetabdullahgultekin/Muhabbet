package com.muhabbet.app.di

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.muhabbet.app.crypto.SignalEncryption
import com.muhabbet.app.crypto.SignalKeyManager
import com.muhabbet.app.data.local.DatabaseDriverFactory
import com.muhabbet.app.data.local.LocalCache
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.platform.AndroidContactsProvider
import com.muhabbet.app.platform.AndroidPushTokenProvider
import com.muhabbet.app.platform.ContactsProvider
import com.muhabbet.app.platform.PushTokenProvider
import com.muhabbet.shared.port.E2EKeyManager
import com.muhabbet.shared.port.EncryptionPort
import org.koin.core.module.Module
import org.koin.dsl.module

fun androidPlatformModule(context: Context): Module = module {
    single<TokenStorage> { AndroidTokenStorage(context) }
    single { DatabaseDriverFactory(context) }
    single { LocalCache(driverFactory = get()) }
    single<ContactsProvider> { AndroidContactsProvider(context) }
    single<PushTokenProvider> { AndroidPushTokenProvider() }
    single<E2EKeyManager> { SignalKeyManager() }
    single<EncryptionPort> { SignalEncryption(keyManager = get()) }
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
}
