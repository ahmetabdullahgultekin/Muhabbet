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

    // Same reasoning as the theme: read on every frame at the composition root, so a null-returning
    // default would silently disable haptics app-wide with nothing to show for it.
    fun getHapticsEnabled(): Boolean
    fun setHapticsEnabled(enabled: Boolean)
    fun getLastSyncTimestamp(): String? = null
    fun setLastSyncTimestamp(timestamp: String) {}
    fun getAppLockEnabled(): Boolean = false
    fun setAppLockEnabled(enabled: Boolean) {}
    fun getAppLockTimeout(): String? = null
    fun setAppLockTimeout(timeout: String) {}

    // Abstract for the same reason as the theme and haptics above. These two were defaulted, no
    // implementation overrode either, and so the HD option in Settings wrote to an empty body and
    // read back null on every platform — the picker reset to "standard" each time it opened and
    // every upload compressed at the standard profile regardless. A no-op that compiles is exactly
    // the failure this file already guards against twice.
    fun getMediaQuality(): String?
    fun setMediaQuality(quality: String)

    fun getWallpaperType(): String? = null
    fun setWallpaperType(type: String) {}
    fun getSolidColor(): String? = null
    fun setSolidColor(color: String?) {}
    fun getCustomWallpaperPath(): String? = null
    fun setCustomWallpaperPath(path: String?) {}
    fun getDarkModeWallpaperEnabled(): Boolean = false
    fun setDarkModeWallpaperEnabled(enabled: Boolean) {}
}
