package com.muhabbet.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the classes and methods the app executes on its critical user journey, so they are
 * compiled ahead of time on install (#867). DEVICE-PENDING — see this module's build.gradle.kts.
 *
 * The journey is cold start to the first screen, which on a fresh install is the phone-number
 * screen. Conversation-list scroll and opening a chat need a signed-in account; they are added once
 * the test can sign in against a test number (CLAUDE.md, "You CAN log in as a test number").
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = TARGET_PACKAGE) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
    }
}
