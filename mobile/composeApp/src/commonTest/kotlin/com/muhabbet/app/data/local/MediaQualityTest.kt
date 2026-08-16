package com.muhabbet.app.data.local

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaQualityTest {

    @Test
    fun `should resolve stored key back to the selected quality`() {
        assertEquals(MediaQuality.Standard, MediaQuality.fromStorageKey("standard"))
        assertEquals(MediaQuality.Hd, MediaQuality.fromStorageKey("hd"))
    }

    @Test
    fun `should fall back to standard when nothing has been stored`() {
        // First launch: the picker has never been opened.
        assertEquals(MediaQuality.Standard, MediaQuality.fromStorageKey(null))
    }

    @Test
    fun `should fall back to standard when the stored key is unknown`() {
        // A value written by an older or newer build must not crash or silently pick HD.
        assertEquals(MediaQuality.Standard, MediaQuality.fromStorageKey("ultra"))
        assertEquals(MediaQuality.Standard, MediaQuality.fromStorageKey(""))
    }

    @Test
    fun `should round-trip every quality through its storage key`() {
        MediaQuality.entries.forEach { quality ->
            assertEquals(quality, MediaQuality.fromStorageKey(quality.storageKey))
        }
    }

    @Test
    fun `should make HD strictly larger than standard`() {
        // The whole point of the setting: if these ever matched, picking HD would change nothing —
        // which is the defect this enum was introduced to fix.
        assertTrue(MediaQuality.Hd.maxDimension > MediaQuality.Standard.maxDimension)
        assertTrue(MediaQuality.Hd.jpegQuality > MediaQuality.Standard.jpegQuality)
    }

    @Test
    fun `should persist and read back through TokenStorage`() {
        // Guards the original defect directly: the interface members were defaulted and no
        // implementation overrode them, so the value never survived the write.
        val storage = FakeTokenStorage()
        assertEquals(null, storage.getMediaQuality())

        storage.setMediaQuality(MediaQuality.Hd.storageKey)

        assertEquals(MediaQuality.Hd, MediaQuality.fromStorageKey(storage.getMediaQuality()))
    }
}
