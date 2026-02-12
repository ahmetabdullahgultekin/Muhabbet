package com.muhabbet.app.di

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.platform.AndroidContactsProvider
import com.muhabbet.app.platform.AndroidPushTokenProvider
import com.muhabbet.app.platform.ContactsProvider
import com.muhabbet.app.platform.PushTokenProvider
import org.koin.core.module.Module
import org.koin.dsl.module

fun androidPlatformModule(context: Context): Module = module {
    single<TokenStorage> { AndroidTokenStorage(context) }
    single<ContactsProvider> { AndroidContactsProvider(context) }
    single<PushTokenProvider> { AndroidPushTokenProvider() }
}

class AndroidTokenStorage(context: Context) : TokenStorage {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        "muhabbet_secure_prefs",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

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
}
