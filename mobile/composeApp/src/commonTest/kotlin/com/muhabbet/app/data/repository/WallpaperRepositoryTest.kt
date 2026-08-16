package com.muhabbet.app.data.repository

import com.muhabbet.app.data.local.FakeTokenStorage
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `resolveWallpaper` is the reader half of #380: WallpaperPickerScreen persisted a selection but
 * nothing consulted it when drawing a chat. These pin down the branching that decision now goes
 * through — including the two "fall back to Default" cases a naive `!!` would have crashed on
 * instead: SOLID with no stored color, and CUSTOM with no stored path.
 */
class WallpaperRepositoryTest {

    private fun repository(): WallpaperRepository = WallpaperRepository(FakeTokenStorage())

    @Test
    fun resolveWallpaper_whenNothingChosen_returnsDefault() {
        val repo = repository()

        assertEquals(WallpaperRepository.ChatWallpaper.Default, repo.resolveWallpaper(isDarkTheme = false))
    }

    @Test
    fun resolveWallpaper_whenSolidChosen_returnsTheStoredColor() {
        val repo = repository()
        repo.setWallpaperType("SOLID")
        repo.setSolidColor("#112233")

        assertEquals(
            WallpaperRepository.ChatWallpaper.Solid("#112233"),
            repo.resolveWallpaper(isDarkTheme = false)
        )
    }

    @Test
    fun resolveWallpaper_whenSolidChosenButNoColorStored_fallsBackToDefault() {
        val repo = repository()
        repo.setWallpaperType("SOLID")

        assertEquals(WallpaperRepository.ChatWallpaper.Default, repo.resolveWallpaper(isDarkTheme = false))
    }

    @Test
    fun resolveWallpaper_whenCustomChosen_returnsTheStoredImagePath() {
        val repo = repository()
        repo.setWallpaperType("CUSTOM")
        repo.setCustomPath("/data/user/0/com.muhabbet.app/files/wallpapers/wall.jpg")

        assertEquals(
            WallpaperRepository.ChatWallpaper.Custom("/data/user/0/com.muhabbet.app/files/wallpapers/wall.jpg"),
            repo.resolveWallpaper(isDarkTheme = false)
        )
    }

    @Test
    fun resolveWallpaper_whenCustomChosenButNoPathStored_fallsBackToDefault() {
        val repo = repository()
        repo.setWallpaperType("CUSTOM")

        assertEquals(WallpaperRepository.ChatWallpaper.Default, repo.resolveWallpaper(isDarkTheme = false))
    }

    @Test
    fun resolveWallpaper_inDarkThemeWithDarkModeToggleOff_ignoresTheSelection() {
        val repo = repository()
        repo.setWallpaperType("SOLID")
        repo.setSolidColor("#112233")
        repo.setDarkModeWallpaperEnabled(false)

        assertEquals(WallpaperRepository.ChatWallpaper.Default, repo.resolveWallpaper(isDarkTheme = true))
    }

    @Test
    fun resolveWallpaper_inDarkThemeWithDarkModeToggleOn_honoursTheSelection() {
        val repo = repository()
        repo.setWallpaperType("SOLID")
        repo.setSolidColor("#112233")
        repo.setDarkModeWallpaperEnabled(true)

        assertEquals(
            WallpaperRepository.ChatWallpaper.Solid("#112233"),
            repo.resolveWallpaper(isDarkTheme = true)
        )
    }
}
