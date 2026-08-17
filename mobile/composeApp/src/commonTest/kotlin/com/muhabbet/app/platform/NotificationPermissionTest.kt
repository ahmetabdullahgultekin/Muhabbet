package com.muhabbet.app.platform

import com.muhabbet.app.data.local.FakeTokenStorage
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationPermissionTest {

    @Test
    fun `asks on a fresh install where the permission exists and is not granted`() {
        assertTrue(
            shouldRequestNotificationPermission(
                alreadyAsked = false,
                state = NotificationPermissionState.Disabled,
                runtimePermissionExists = true
            )
        )
    }

    @Test
    fun `never asks twice`() {
        assertFalse(
            shouldRequestNotificationPermission(
                alreadyAsked = true,
                state = NotificationPermissionState.Disabled,
                runtimePermissionExists = true
            )
        )
    }

    @Test
    fun `does not ask when notifications are already on`() {
        assertFalse(
            shouldRequestNotificationPermission(
                alreadyAsked = false,
                state = NotificationPermissionState.Enabled,
                runtimePermissionExists = true
            )
        )
    }

    /**
     * Android 8-12. `POST_NOTIFICATIONS` is API 33 and `minSdk` is 26, so there is nothing to
     * request; notifications are on unless the user turned them off, and the only way back is the
     * system settings page the Settings row opens. Launching the permission below API 33 throws, so
     * this is the guard, not a nicety.
     */
    @Test
    fun `does not ask below the API level that has the permission`() {
        assertFalse(
            shouldRequestNotificationPermission(
                alreadyAsked = false,
                state = NotificationPermissionState.Disabled,
                runtimePermissionExists = false
            )
        )
    }

    /** iOS: push is not wired at all, so there is nothing to ask permission for. */
    @Test
    fun `does not ask on a platform that cannot deliver notifications`() {
        assertFalse(
            shouldRequestNotificationPermission(
                alreadyAsked = false,
                state = NotificationPermissionState.Unsupported,
                runtimePermissionExists = false
            )
        )
    }

    /**
     * The round trip through storage, for the same reason `TestBuildNoticeTest` has one: this
     * codebase has shipped `TokenStorage` members with defaulted no-op bodies that no implementation
     * overrode, twice (#380, #383). A flag that reads back false after being set would make the app
     * ask on every launch — and because Android stops showing the dialog after two denials, that
     * would present as a request that silently does nothing rather than as a repeated prompt.
     */
    @Test
    fun `the asked flag survives being written`() {
        val storage = FakeTokenStorage()

        assertFalse(storage.getNotificationPermissionAsked())
        assertTrue(
            shouldRequestNotificationPermission(
                alreadyAsked = storage.getNotificationPermissionAsked(),
                state = NotificationPermissionState.Disabled,
                runtimePermissionExists = true
            )
        )

        storage.setNotificationPermissionAsked()

        assertTrue(storage.getNotificationPermissionAsked())
        assertFalse(
            shouldRequestNotificationPermission(
                alreadyAsked = storage.getNotificationPermissionAsked(),
                state = NotificationPermissionState.Disabled,
                runtimePermissionExists = true
            )
        )
    }
}
