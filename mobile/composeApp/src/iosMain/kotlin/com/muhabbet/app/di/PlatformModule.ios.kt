package com.muhabbet.app.di

import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.platform.ContactsProvider
import com.muhabbet.app.platform.IosContactsProvider
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults

fun iosPlatformModule(): Module = module {
    single<TokenStorage> { IosTokenStorage() }
    single<ContactsProvider> { IosContactsProvider() }
}

class IosTokenStorage : TokenStorage {

    private val defaults = NSUserDefaults.standardUserDefaults

    override fun getAccessToken(): String? = defaults.stringForKey("access_token")
    override fun getRefreshToken(): String? = defaults.stringForKey("refresh_token")
    override fun getUserId(): String? = defaults.stringForKey("user_id")
    override fun getDeviceId(): String? = defaults.stringForKey("device_id")

    override fun saveTokens(accessToken: String, refreshToken: String, userId: String, deviceId: String) {
        defaults.setObject(accessToken, forKey = "access_token")
        defaults.setObject(refreshToken, forKey = "refresh_token")
        defaults.setObject(userId, forKey = "user_id")
        defaults.setObject(deviceId, forKey = "device_id")
    }

    override fun clear() {
        listOf("access_token", "refresh_token", "user_id", "device_id").forEach {
            defaults.removeObjectForKey(it)
        }
    }
}
