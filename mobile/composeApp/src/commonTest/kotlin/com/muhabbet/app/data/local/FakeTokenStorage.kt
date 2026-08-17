package com.muhabbet.app.data.local

class FakeTokenStorage : TokenStorage {
    private var accessToken: String? = null
    private var refreshToken: String? = null
    private var userId: String? = null
    private var deviceId: String? = null
    private var language: String? = null
    private var theme: String? = null

    override fun getAccessToken(): String? = accessToken
    override fun getRefreshToken(): String? = refreshToken
    override fun getUserId(): String? = userId
    override fun getDeviceId(): String? = deviceId

    override fun saveTokens(accessToken: String, refreshToken: String, userId: String, deviceId: String) {
        this.accessToken = accessToken
        this.refreshToken = refreshToken
        this.userId = userId
        this.deviceId = deviceId
    }

    override fun clear() {
        accessToken = null
        refreshToken = null
        userId = null
        deviceId = null
    }

    override fun isLoggedIn(): Boolean = accessToken != null

    override fun getLanguage(): String? = language
    override fun setLanguage(lang: String) { language = lang }

    private var pendingLanguageRestart: Boolean = false
    override fun setPendingLanguageRestart() { pendingLanguageRestart = true }
    override fun consumePendingLanguageRestart(): Boolean {
        val pending = pendingLanguageRestart
        pendingLanguageRestart = false
        return pending
    }

    private var hapticsEnabled: Boolean = true
    override fun getHapticsEnabled(): Boolean = hapticsEnabled
    override fun setHapticsEnabled(enabled: Boolean) { hapticsEnabled = enabled }

    override fun getTheme(): String? = theme
    override fun setTheme(theme: String) { this.theme = theme }

    private var mediaQuality: String? = null
    override fun getMediaQuality(): String? = mediaQuality
    override fun setMediaQuality(quality: String) { mediaQuality = quality }

    private var contactSyncConsentAt: String? = null
    override fun getContactSyncConsentAt(): String? = contactSyncConsentAt
    override fun setContactSyncConsentAt(timestamp: String) { contactSyncConsentAt = timestamp }
    override fun clearContactSyncConsent() { contactSyncConsentAt = null }

    private var wallpaperType: String? = null
    override fun getWallpaperType(): String? = wallpaperType
    override fun setWallpaperType(type: String) { wallpaperType = type }

    private var solidColor: String? = null
    override fun getSolidColor(): String? = solidColor
    override fun setSolidColor(color: String?) { solidColor = color }

    private var customWallpaperPath: String? = null
    override fun getCustomWallpaperPath(): String? = customWallpaperPath
    override fun setCustomWallpaperPath(path: String?) { customWallpaperPath = path }

    private var darkModeWallpaperEnabled: Boolean = false
    override fun getDarkModeWallpaperEnabled(): Boolean = darkModeWallpaperEnabled
    override fun setDarkModeWallpaperEnabled(enabled: Boolean) { darkModeWallpaperEnabled = enabled }

    private var testBuildNoticeAckVersion: String? = null
    override fun getTestBuildNoticeAckVersion(): String? = testBuildNoticeAckVersion
    override fun setTestBuildNoticeAckVersion(version: String) { testBuildNoticeAckVersion = version }

    private var notificationPermissionAsked: Boolean = false
    override fun getNotificationPermissionAsked(): Boolean = notificationPermissionAsked
    override fun setNotificationPermissionAsked() { notificationPermissionAsked = true }
}
