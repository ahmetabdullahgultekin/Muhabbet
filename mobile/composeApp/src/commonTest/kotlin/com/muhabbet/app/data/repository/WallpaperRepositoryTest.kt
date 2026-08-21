package com.muhabbet.app.data.repository

import com.muhabbet.app.data.local.FakeTokenStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
        repo.setWallpaperType(WallpaperRepository.TYPE_SOLID)
        repo.setSolidColor("#112233")

        assertEquals(
            WallpaperRepository.ChatWallpaper.Solid("#112233"),
            repo.resolveWallpaper(isDarkTheme = false)
        )
    }

    @Test
    fun resolveWallpaper_whenSolidChosenButNoColorStored_fallsBackToDefault() {
        val repo = repository()
        repo.setWallpaperType(WallpaperRepository.TYPE_SOLID)

        assertEquals(WallpaperRepository.ChatWallpaper.Default, repo.resolveWallpaper(isDarkTheme = false))
    }

    @Test
    fun resolveWallpaper_whenGradientChosen_returnsTheStoredId() {
        val repo = repository()
        repo.setWallpaperType(WallpaperRepository.TYPE_GRADIENT)
        repo.setGradientId("harbour")

        assertEquals(
            WallpaperRepository.ChatWallpaper.Gradient("harbour"),
            repo.resolveWallpaper(isDarkTheme = false)
        )
    }

    /**
     * The id is carried, not resolved: the colours live in the design system and this layer does not
     * import it. Which means an id this build cannot paint has to survive as far as the UI, where the
     * lookup fails and the chat falls back — the repository must not try to be clever about it here.
     */
    @Test
    fun resolveWallpaper_whenGradientIdIsNotOneThisBuildShips_stillReturnsIt() {
        val repo = repository()
        repo.setWallpaperType(WallpaperRepository.TYPE_GRADIENT)
        repo.setGradientId("a-gradient-from-a-later-build")

        assertEquals(
            WallpaperRepository.ChatWallpaper.Gradient("a-gradient-from-a-later-build"),
            repo.resolveWallpaper(isDarkTheme = false)
        )
    }

    @Test
    fun resolveWallpaper_whenGradientChosenButNoIdStored_fallsBackToDefault() {
        val repo = repository()
        repo.setWallpaperType(WallpaperRepository.TYPE_GRADIENT)

        assertEquals(WallpaperRepository.ChatWallpaper.Default, repo.resolveWallpaper(isDarkTheme = false))
    }

    /**
     * A gradient and a solid are stored in separate slots on purpose, so moving between the two type
     * buttons and back does not lose the other choice. If they ever shared one, this is the test that
     * would notice.
     */
    @Test
    fun gradientAndSolidSelections_doNotOverwriteEachOther() {
        val repo = repository()
        repo.setSolidColor("#112233")
        repo.setGradientId("sage")

        repo.setWallpaperType(WallpaperRepository.TYPE_SOLID)
        assertEquals(
            WallpaperRepository.ChatWallpaper.Solid("#112233"),
            repo.resolveWallpaper(isDarkTheme = false)
        )

        repo.setWallpaperType(WallpaperRepository.TYPE_GRADIENT)
        assertEquals(
            WallpaperRepository.ChatWallpaper.Gradient("sage"),
            repo.resolveWallpaper(isDarkTheme = false)
        )
    }

    @Test
    fun resolveWallpaper_whenCustomChosen_returnsTheStoredImagePath() {
        val repo = repository()
        repo.setWallpaperType(WallpaperRepository.TYPE_CUSTOM)
        repo.setCustomPath("/data/user/0/com.muhabbet.app/files/wallpapers/wall.jpg")

        assertEquals(
            WallpaperRepository.ChatWallpaper.Custom("/data/user/0/com.muhabbet.app/files/wallpapers/wall.jpg"),
            repo.resolveWallpaper(isDarkTheme = false)
        )
    }

    @Test
    fun resolveWallpaper_whenCustomChosenButNoPathStored_fallsBackToDefault() {
        val repo = repository()
        repo.setWallpaperType(WallpaperRepository.TYPE_CUSTOM)

        assertEquals(WallpaperRepository.ChatWallpaper.Default, repo.resolveWallpaper(isDarkTheme = false))
    }

    @Test
    fun resolveWallpaper_inDarkThemeWithDarkModeToggleOff_ignoresTheSelection() {
        val repo = repository()
        repo.setWallpaperType(WallpaperRepository.TYPE_SOLID)
        repo.setSolidColor("#112233")
        repo.setDarkModeWallpaperEnabled(false)

        assertEquals(WallpaperRepository.ChatWallpaper.Default, repo.resolveWallpaper(isDarkTheme = true))
    }

    @Test
    fun resolveWallpaper_inDarkThemeWithDarkModeToggleOn_honoursTheSelection() {
        val repo = repository()
        repo.setWallpaperType(WallpaperRepository.TYPE_SOLID)
        repo.setSolidColor("#112233")
        repo.setDarkModeWallpaperEnabled(true)

        assertEquals(
            WallpaperRepository.ChatWallpaper.Solid("#112233"),
            repo.resolveWallpaper(isDarkTheme = true)
        )
    }

    /**
     * #548: the reported "the wallpaper disappeared when I switched to OLED", stated as a question
     * the picker can ask.
     *
     * The suppression above is intended and stays. What was missing is any way for a screen to know
     * it is happening, so the picker went on marking a swatch chosen while the chat painted the
     * theme default — and the only control that governs it sat two rows below, unexplained.
     */
    @Test
    fun isSelectionHiddenByDarkTheme_inDarkThemeWithASelectionAndTheToggleOff_isTrue() {
        val repo = repository()
        repo.setWallpaperType(WallpaperRepository.TYPE_SOLID)
        repo.setSolidColor("#112233")
        repo.setDarkModeWallpaperEnabled(false)

        assertTrue(repo.isSelectionHiddenByDarkTheme(isDarkTheme = true))
    }

    /** Nothing is being hidden in a light theme — the selection is on screen. */
    @Test
    fun isSelectionHiddenByDarkTheme_inLightTheme_isFalse() {
        val repo = repository()
        repo.setWallpaperType(WallpaperRepository.TYPE_SOLID)
        repo.setSolidColor("#112233")
        repo.setDarkModeWallpaperEnabled(false)

        assertFalse(repo.isSelectionHiddenByDarkTheme(isDarkTheme = false))
    }

    /** The toggle is the way out, so turning it on must silence the notice. */
    @Test
    fun isSelectionHiddenByDarkTheme_whenTheDarkModeToggleIsOn_isFalse() {
        val repo = repository()
        repo.setWallpaperType(WallpaperRepository.TYPE_CUSTOM)
        repo.setCustomPath("/data/user/0/com.muhabbet.app/files/wallpapers/wall.jpg")
        repo.setDarkModeWallpaperEnabled(true)

        assertFalse(repo.isSelectionHiddenByDarkTheme(isDarkTheme = true))
    }

    /**
     * No selection, nothing hidden. Without this the notice would fire for every user who has never
     * opened the picker, on a screen whose whole job is to tell them something true.
     */
    @Test
    fun isSelectionHiddenByDarkTheme_whenNothingWasEverChosen_isFalse() {
        val repo = repository()

        assertFalse(repo.isSelectionHiddenByDarkTheme(isDarkTheme = true))
    }

    /** A type with nothing behind it is not a selection either — same reasoning as the two
     *  fall-back-to-Default cases above, which is why this is derived from resolveWallpaper. */
    @Test
    fun isSelectionHiddenByDarkTheme_whenTheStoredSelectionIsIncomplete_isFalse() {
        val repo = repository()
        repo.setWallpaperType(WallpaperRepository.TYPE_CUSTOM)

        assertFalse(repo.isSelectionHiddenByDarkTheme(isDarkTheme = true))
    }
}
