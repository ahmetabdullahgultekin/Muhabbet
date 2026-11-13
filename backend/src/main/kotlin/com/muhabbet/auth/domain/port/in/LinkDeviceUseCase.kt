package com.muhabbet.auth.domain.port.`in`

import com.muhabbet.auth.domain.model.Device
import com.muhabbet.auth.domain.model.DeviceLinkSession
import com.muhabbet.auth.domain.model.IssuedTokens
import java.util.UUID

/** A just-opened link session and its token — the only moment the plain token exists server-side. */
data class DeviceLinkOffer(val session: DeviceLinkSession, val linkToken: String)

/** A completed link: the companion's device row and the credentials it signs in with (#884). */
data class CompletedDeviceLink(val device: Device, val tokens: IssuedTokens)

/**
 * Companion-device linking (Tier 2 multi-device, slice S1 — the NON-CRYPTO scaffolding).
 *
 * This use case owns the device registry + the QR link-session handshake state machine. It does
 * NOT perform any Signal session transfer / key migration — that plugs in later behind
 * [com.muhabbet.shared.port.DeviceLinkCrypto] (NotYetImplemented). The whole capability is gated
 * by the `muhabbet.multi-device.enabled` flag at the controller; with the flag OFF none of these
 * methods are reachable and the single-device path is unchanged.
 */
interface LinkDeviceUseCase {

    /** Primary device opens a link session; the returned token is rendered as a QR code. */
    fun beginLink(userId: UUID, primaryDeviceId: UUID): DeviceLinkOffer

    /**
     * Companion completes the handshake by presenting the scanned [linkToken]. Creates a new
     * companion [Device] row owned by the same user, marks the session COMPLETED and issues the
     * companion its own access/refresh tokens. The opaque [publicBundle] (companion's PUBLIC prekey
     * material) is stored for the future crypto slice.
     */
    fun completeLink(
        linkToken: String,
        companionPlatform: String,
        companionDeviceName: String?,
        publicBundle: String?
    ): CompletedDeviceLink

    /** All of the user's currently-linked (non-revoked) devices, for the management screen. */
    fun listDevices(userId: UUID): List<Device>

    /** Revoke (soft-tombstone) a companion device the user owns. The primary cannot be revoked here. */
    fun revokeDevice(userId: UUID, deviceId: UUID): Device
}
