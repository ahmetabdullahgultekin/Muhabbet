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
    private var hapticsEnabled: Boolean = true
    override fun getHapticsEnabled(): Boolean = hapticsEnabled
    override fun setHapticsEnabled(enabled: Boolean) { hapticsEnabled = enabled }

    override fun getTheme(): String? = theme
    override fun setTheme(theme: String) { this.theme = theme }

    private var mediaQuality: String? = null
    override fun getMediaQuality(): String? = mediaQuality
    override fun setMediaQuality(quality: String) { mediaQuality = quality }
}
