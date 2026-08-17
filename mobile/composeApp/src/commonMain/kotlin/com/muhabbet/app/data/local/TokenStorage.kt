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

    // Abstract, unlike most of this file's neighbours and for the reason #378 exists: these two
    // were defaulted no-ops, no implementation overrode either, so the App Lock toggle in Settings
    // wrote to nothing and always read back `false`/`null` — a security setting that silently did
    // not persist, in a privacy-first messenger. A default that compiles is exactly the failure this
    // file already guards against for the theme, haptics, media quality and wallpaper fields below.
    // Stored in the *encrypted* store on Android (see AndroidTokenStorage) — this is a security
    // setting, not a cosmetic preference, even though the value itself is not secret material.
    fun getAppLockEnabled(): Boolean
    fun setAppLockEnabled(enabled: Boolean)

    /** One of [com.muhabbet.app.config.AppLockTimeout]'s option keys, or null if never chosen. */
    fun getAppLockTimeout(): String?
    fun setAppLockTimeout(timeout: String)

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

    /**
     * The app version whose test-build notice the user has already acknowledged, or null if they
     * never have. Compared against `BuildInfo.VERSION`, so the notice returns once after an update
     * and stays away on every launch in between.
     *
     * Abstract, for the third time in this file and for the same reason (#380, media quality,
     * contact consent): a defaulted no-op would read back null on every launch, so the notice would
     * reappear every single time the app opened — and a warning that shows up that often is one
     * people learn to dismiss without reading, which is precisely the failure it exists to avoid.
     */
    fun getTestBuildNoticeAckVersion(): String?
    fun setTestBuildNoticeAckVersion(version: String)

    /**
     * Whether the app has already put the system notification-permission dialog in front of this
     * user (#547). Written once, before the dialog is shown, and never cleared.
     *
     * Abstract, for the fourth time in this file and for the same reason (#380, media quality,
     * contact consent, the test-build notice): a defaulted no-op would read back false on every
     * launch, so the app would ask for notification permission every single time it started. Android
     * stops showing the dialog after two denials, so the visible result would not be a repeated
     * prompt — it would be a request that silently does nothing, forever, which is far harder to
     * notice than a broken one.
     *
     * Deliberately not cleared by [clear]: the permission belongs to the app, not to the session, so
     * logging out and back in is not a reason to ask again.
     */
    fun getNotificationPermissionAsked(): Boolean
    fun setNotificationPermissionAsked()
}
