package com.muhabbet.app.data.repository

import com.muhabbet.app.data.local.TokenStorage

/**
 * Manages wallpaper preferences locally via SharedPreferences/TokenStorage.
 * Wallpaper settings are device-local and not synced to the server.
 */
class WallpaperRepository(
    private val tokenStorage: TokenStorage
) {

    companion object {
        private const val KEY_WALLPAPER_TYPE = "wallpaper_type"
        private const val KEY_SOLID_COLOR = "wallpaper_solid_color"
        private const val KEY_CUSTOM_PATH = "wallpaper_custom_path"
        private const val KEY_DARK_MODE_ENABLED = "wallpaper_dark_mode"
        private const val DEFAULT_TYPE = "DEFAULT"
    }

    fun getWallpaperType(): String {
        return tokenStorage.getWallpaperType() ?: DEFAULT_TYPE
    }

    fun setWallpaperType(type: String) {
        tokenStorage.setWallpaperType(type)
    }

    fun getSolidColor(): String? {
        return tokenStorage.getSolidColor()
    }

    fun setSolidColor(color: String?) {
        tokenStorage.setSolidColor(color)
    }

    fun getCustomPath(): String? {
        return tokenStorage.getCustomWallpaperPath()
    }

    fun setCustomPath(path: String?) {
        tokenStorage.setCustomWallpaperPath(path)
    }

    fun getDarkModeWallpaperEnabled(): Boolean {
        return tokenStorage.getDarkModeWallpaperEnabled()
    }

    fun setDarkModeWallpaperEnabled(enabled: Boolean) {
        tokenStorage.setDarkModeWallpaperEnabled(enabled)
    }
}
