package com.muhabbet.auth.domain.port.out

import com.muhabbet.auth.domain.model.Device
import com.muhabbet.auth.domain.model.DevicePlatform
import java.util.UUID

interface DeviceRepository {
    fun save(device: Device): Device

    /**
     * The account's **active** device for one app installation (#882), or null. Unique by V29's
     * `ux_devices_user_installation`. A revoked row is never returned: signing in again on a device
     * that was revoked starts a new device rather than resurrecting the old one's identity.
     */
    fun findActiveByInstallation(userId: UUID, installationId: UUID): Device?

    /**
     * For a client too old to send an installation id: the most recently active, non-revoked,
     * non-companion device of [platform] that has no installation id either.
     *
     * This is the pre-#882 behaviour kept for those clients only, minus its two defects: it can no
     * longer throw on several matching rows (it picks the newest instead of declaring a single
     * result), and it can no longer pick a revoked row or a companion.
     */
    fun findLegacyPrimaryCandidate(userId: UUID, platform: DevicePlatform): Device?

    fun findByUserId(userId: UUID): List<Device>

    /**
     * Every device of every one of [userIds], in one query.
     *
     * The push fan-out needs this: it used to ask for one recipient's devices at a time, from
     * inside the loop that walks the recipients, so a group of two hundred issued two hundred
     * queries to send one message (#492). The result is not grouped by user because the only caller
     * does not care whose device a token belongs to — it groups by language and sends.
     */
    fun findByUserIdIn(userIds: Collection<UUID>): List<Device>

    // ─── Multi-device (Tier 2) ───
    fun findById(id: UUID): Device?

    /** All of a user's non-revoked devices — the device set used for (future) fan-out. */
    fun findActiveByUserId(userId: UUID): List<Device>

    /**
     * Increments the device's access-token epoch (#923), invalidating every access token already
     * issued to it on the next request. One UPDATE, so concurrent bumps cannot lose an increment.
     */
    fun bumpTokenVersion(deviceId: UUID)

    /** [bumpTokenVersion] for every device of [userId] — suspension and erasure end every session. */
    fun bumpTokenVersionForUser(userId: UUID)
}
