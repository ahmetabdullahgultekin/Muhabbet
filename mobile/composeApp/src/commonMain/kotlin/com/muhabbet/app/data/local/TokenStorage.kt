package com.muhabbet.app.data.local

interface TokenStorage {
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun getUserId(): String?
    fun getDeviceId(): String?
    fun saveTokens(accessToken: String, refreshToken: String, userId: String, deviceId: String)
    fun clear()
    fun isLoggedIn(): Boolean = getAccessToken() != null
    fun getLanguage(): String? = null
    fun setLanguage(lang: String) {}
    fun getTheme(): String? = null
    fun setTheme(theme: String) {}
    fun getLastSyncTimestamp(): String? = null
    fun setLastSyncTimestamp(timestamp: String) {}
    fun getAppLockEnabled(): Boolean = false
    fun setAppLockEnabled(enabled: Boolean) {}
    fun getAppLockTimeout(): String? = null
    fun setAppLockTimeout(timeout: String) {}

    // --- Mahrem Mod (Privacy Mode) — see com.muhabbet.app.config.PrivacyModeConfig ---
    // Secure (encrypted) storage: the salted PIN hash + its random salt must NEVER be plaintext.
    fun getPrivacyPinHash(): String? = null
    fun setPrivacyPinHash(hash: String?) {}
    fun getPrivacyPinSalt(): String? = null
    fun setPrivacyPinSalt(salt: String?) {}
    // Plain (unencrypted) prefs — read by the FCM service before crypto init, mirrors language/theme.
    fun getHideNotificationPreview(): Boolean = false
    fun setHideNotificationPreview(enabled: Boolean) {}
    fun getScreenshotGuardEnabled(): Boolean = false
    fun setScreenshotGuardEnabled(enabled: Boolean) {}
    fun getMediaQuality(): String? = null
    fun setMediaQuality(quality: String) {}
    fun getWallpaperType(): String? = null
    fun setWallpaperType(type: String) {}
    fun getSolidColor(): String? = null
    fun setSolidColor(color: String?) {}
    fun getCustomWallpaperPath(): String? = null
    fun setCustomWallpaperPath(path: String?) {}
    fun getDarkModeWallpaperEnabled(): Boolean = false
    fun setDarkModeWallpaperEnabled(enabled: Boolean) {}
}
