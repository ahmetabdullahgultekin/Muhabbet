package com.muhabbet.app.data.local

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeTokenStorageTest {

    @Test
    fun should_be_logged_out_initially() {
        val storage = FakeTokenStorage()
        assertFalse(storage.isLoggedIn())
        assertNull(storage.getAccessToken())
        assertNull(storage.getRefreshToken())
        assertNull(storage.getUserId())
        assertNull(storage.getDeviceId())
    }

    @Test
    fun should_persist_tokens_after_save() {
        val storage = FakeTokenStorage()
        storage.saveTokens(
            accessToken = "access-123",
            refreshToken = "refresh-456",
            userId = "user-789",
            deviceId = "device-abc"
        )

        assertTrue(storage.isLoggedIn())
        assertEquals("access-123", storage.getAccessToken())
        assertEquals("refresh-456", storage.getRefreshToken())
        assertEquals("user-789", storage.getUserId())
        assertEquals("device-abc", storage.getDeviceId())
    }

    @Test
    fun should_clear_all_tokens_on_clear() {
        val storage = FakeTokenStorage()
        storage.saveTokens("a", "r", "u", "d")
        assertTrue(storage.isLoggedIn())

        storage.clear()

        assertFalse(storage.isLoggedIn())
        assertNull(storage.getAccessToken())
        assertNull(storage.getRefreshToken())
    }

    @Test
    fun should_store_language_preference() {
        val storage = FakeTokenStorage()
        assertNull(storage.getLanguage())
        storage.setLanguage("tr")
        assertEquals("tr", storage.getLanguage())
    }

    /**
     * The language-restart flag is consume-once. It has to be: the flag survives in storage across
     * the Activity restart, and a read that did not clear it would put the user back into Settings
     * on every launch from then on (#505).
     */
    @Test
    fun should_report_the_pending_language_restart_once_and_then_forget_it() {
        val storage = FakeTokenStorage()
        assertFalse(storage.consumePendingLanguageRestart())
        storage.setPendingLanguageRestart()
        assertTrue(storage.consumePendingLanguageRestart())
        assertFalse(storage.consumePendingLanguageRestart())
    }

    @Test
    fun should_store_theme_preference() {
        val storage = FakeTokenStorage()
        assertNull(storage.getTheme())
        storage.setTheme("dark")
        assertEquals("dark", storage.getTheme())
    }

    /**
     * #380: every wallpaper getter/setter used to be a defaulted no-op on [TokenStorage], so this
     * exact assertion — a written value reading back — would have failed on every implementation.
     * `AndroidTokenStorage` and `IosTokenStorage` cannot be exercised here (they need a real
     * Context / NSUserDefaults), but a defaulted no-op body would have made this fail on the fake
     * too — the trap in this interface, once, at the cheapest possible layer.
     */
    @Test
    fun should_persist_wallpaper_preferences_after_write() {
        val storage = FakeTokenStorage()
        assertNull(storage.getWallpaperType())
        assertNull(storage.getSolidColor())
        assertNull(storage.getCustomWallpaperPath())
        assertFalse(storage.getDarkModeWallpaperEnabled())

        storage.setWallpaperType("SOLID")
        storage.setSolidColor("#112233")
        storage.setCustomWallpaperPath("/data/user/0/com.muhabbet.app/files/wallpapers/wall.jpg")
        storage.setDarkModeWallpaperEnabled(true)

        assertEquals("SOLID", storage.getWallpaperType())
        assertEquals("#112233", storage.getSolidColor())
        assertEquals(
            "/data/user/0/com.muhabbet.app/files/wallpapers/wall.jpg",
            storage.getCustomWallpaperPath()
        )
        assertTrue(storage.getDarkModeWallpaperEnabled())
    }
}
