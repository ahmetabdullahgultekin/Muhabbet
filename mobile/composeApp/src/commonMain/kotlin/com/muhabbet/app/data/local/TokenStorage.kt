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
    // Abstract, unlike its neighbours: the theme is read at the composition root on every frame, so
    // an implementation that inherited a null-returning default would pin the whole app to the
    // system theme with nothing to show for it. Better to fail to compile.
    fun getTheme(): String?
    fun setTheme(theme: String)
    fun getLastSyncTimestamp(): String? = null
    fun setLastSyncTimestamp(timestamp: String) {}
    fun getAppLockEnabled(): Boolean = false
    fun setAppLockEnabled(enabled: Boolean) {}
    fun getAppLockTimeout(): String? = null
    fun setAppLockTimeout(timeout: String) {}
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
