package com.muhabbet.auth.domain.port.out

import com.muhabbet.auth.domain.model.Device
import java.util.UUID

interface DeviceRepository {
    fun save(device: Device): Device
    fun findByUserIdAndPlatform(userId: UUID, platform: String): Device?
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
}
