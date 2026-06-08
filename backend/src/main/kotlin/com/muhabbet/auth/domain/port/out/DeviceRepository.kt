package com.muhabbet.auth.domain.port.out

import com.muhabbet.auth.domain.model.Device
import java.util.UUID

interface DeviceRepository {
    fun save(device: Device): Device
    fun findByUserIdAndPlatform(userId: UUID, platform: String): Device?
    fun findByUserId(userId: UUID): List<Device>

    /**
     * Clear the stored push token on every device that currently holds [pushToken].
     * Invoked when the push provider (FCM) reports the token is terminally dead
     * (app uninstalled / token rotated / malformed) so it is never re-selected for a
     * guaranteed-to-fail send. No-op when no device holds the token.
     */
    fun clearPushToken(pushToken: String)

    // ─── Multi-device (Tier 2) ───
    fun findById(id: UUID): Device?

    /** All of a user's non-revoked devices — the device set used for (future) fan-out. */
    fun findActiveByUserId(userId: UUID): List<Device>
}
