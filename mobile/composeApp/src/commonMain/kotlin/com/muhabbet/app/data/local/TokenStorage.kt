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

    /**
     * Whether the process that is about to start should reopen Settings at the language picker.
     *
     * Set immediately before the language restart and consumed exactly once by `RootComponent` on
     * the way back up, so the user lands where they were instead of on the conversation list — which
     * is also the only place they can see that the new language took effect (#505).
     *
     * Abstract rather than defaulted, for the same reason as the theme below. An implementation that
     * inherited a no-op would restart the app and drop the user on the home screen every time, which
     * is precisely the defect this exists to fix, and it would compile.
     */
    fun setPendingLanguageRestart()
    fun consumePendingLanguageRestart(): Boolean
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

    /**
     * When the user agreed to contact matching, as an ISO-8601 instant; null means they never have.
     *
     * Abstract for the same reason as media quality above, and with more at stake. A defaulted
     * no-op would read back null forever, so the consent screen would either re-ask on every visit
     * or — worse, if the gate were written the other way round — never record a refusal and upload
     * the address book anyway. That is the one control here carrying a legal obligation (#425), so
     * it must not be possible to satisfy this interface by doing nothing.
     */
    fun getContactSyncConsentAt(): String?
    fun setContactSyncConsentAt(timestamp: String)
    fun clearContactSyncConsent()

    // Abstract for the same reason as media quality above (#380): these four were defaulted, no
    // implementation overrode any of them, and so WallpaperPickerScreen wrote to an empty body and
    // read back null/false on every platform every time — the picker looked like it remembered a
    // choice only because that choice lived in the screen's own `remember{}` for the one composition
    // it was open. A no-op that compiles is exactly the failure this file already guards against.
    fun getWallpaperType(): String?
    fun setWallpaperType(type: String)
    fun getSolidColor(): String?
    fun setSolidColor(color: String?)
    fun getCustomWallpaperPath(): String?
    fun setCustomWallpaperPath(path: String?)
    fun getDarkModeWallpaperEnabled(): Boolean
    fun setDarkModeWallpaperEnabled(enabled: Boolean)
}
