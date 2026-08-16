package com.muhabbet.app.util

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WallpaperColorTest {

    @Test
    fun hexToColorOrNull_withHashPrefix_parsesTheColor() {
        assertEquals(Color(red = 0x11, green = 0x22, blue = 0x33), "#112233".hexToColorOrNull())
    }

    @Test
    fun hexToColorOrNull_withoutHashPrefix_stillParses() {
        assertEquals(Color(red = 0xFF, green = 0x00, blue = 0x00), "FF0000".hexToColorOrNull())
    }

    @Test
    fun hexToColorOrNull_withWrongLength_returnsNull() {
        assertNull("#FFF".hexToColorOrNull())
    }

    @Test
    fun hexToColorOrNull_withNonHexCharacters_returnsNull() {
        assertNull("#ZZZZZZ".hexToColorOrNull())
    }
}
