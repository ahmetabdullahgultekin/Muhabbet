package com.muhabbet.app.ui.notice

import com.muhabbet.app.data.local.FakeTokenStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestBuildNoticeTest {

    @Test
    fun `should show the notice when nothing has been acknowledged`() {
        // First launch after install.
        assertTrue(shouldShowTestBuildNotice(acknowledgedVersion = null, currentVersion = "0.3.4"))
    }

    @Test
    fun `should not show the notice again on the same version`() {
        assertFalse(shouldShowTestBuildNotice(acknowledgedVersion = "0.3.4", currentVersion = "0.3.4"))
    }

    @Test
    fun `should show the notice again after an update`() {
        // The whole reason the flag stores a version rather than a boolean: a later build is the one
        // whose new breakage nobody has been warned about yet.
        assertTrue(shouldShowTestBuildNotice(acknowledgedVersion = "0.3.4", currentVersion = "0.3.5"))
    }

    @Test
    fun `should persist the acknowledgement through TokenStorage`() {
        // Guards the trap this interface has fallen into three times: a member added with a default
        // no-op body that no implementation overrides. If setTestBuildNoticeAckVersion did nothing,
        // the read below would still be null and the notice would reopen on every single launch.
        val storage = FakeTokenStorage()
        assertEquals(null, storage.getTestBuildNoticeAckVersion())
        assertTrue(shouldShowTestBuildNotice(storage.getTestBuildNoticeAckVersion(), "0.3.4"))

        storage.setTestBuildNoticeAckVersion("0.3.4")

        assertEquals("0.3.4", storage.getTestBuildNoticeAckVersion())
        assertFalse(shouldShowTestBuildNotice(storage.getTestBuildNoticeAckVersion(), "0.3.4"))
        assertTrue(shouldShowTestBuildNotice(storage.getTestBuildNoticeAckVersion(), "0.3.5"))
    }
}
