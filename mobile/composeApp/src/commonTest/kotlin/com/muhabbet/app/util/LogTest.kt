package com.muhabbet.app.util

import com.muhabbet.app.BuildInfo
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A release build must not write to the device log: on Android `println` lands in logcat, where
 * any tool with `adb` access, and on older devices other apps, can read it.
 */
class LogTest {

    @Test
    fun `should be enabled only when the build is a debug build`() {
        assertEquals(BuildInfo.DEBUG, Log.enabled)
    }
}
