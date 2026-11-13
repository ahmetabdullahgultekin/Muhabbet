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

    // ─── Push registration (#908) ───
    //
    // A push token addresses one app install on one phone, so it can belong to at most one device
    // row. `devices.push_token` carries a unique index (V29) that enforces it; these two methods
    // are the only writes that move a token *between* rows, so every caller goes through them
    // rather than nulling the column on a row it happened to load.

    /**
     * Takes [pushToken] away from every device except [keepDeviceId], and returns how many rows
     * lost it.
     *
     * Serialised per token for the rest of the caller's transaction, so two devices registering the
     * same token at the same moment resolve to last-writer-wins instead of one of them failing on
     * the unique index. Must be called inside a transaction, before the caller writes the token to
     * [keepDeviceId].
     */
    fun detachPushTokenFromOtherDevices(pushToken: String, keepDeviceId: UUID): Int

    /** Takes [pushToken] away from whichever device holds it. Returns how many rows lost it. */
    fun clearPushToken(pushToken: String): Int
}
