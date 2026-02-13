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

    @Test
    fun should_store_theme_preference() {
        val storage = FakeTokenStorage()
        assertNull(storage.getTheme())
        storage.setTheme("dark")
        assertEquals("dark", storage.getTheme())
    }
}
