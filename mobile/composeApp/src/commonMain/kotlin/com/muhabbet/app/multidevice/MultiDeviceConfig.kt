package com.muhabbet.app.multidevice

/**
 * Feature flag for Tier-2 multi-device linked sessions on the client.
 *
 * DEFAULT = false → the "Linked devices" entry point is hidden and the single-device experience is
 * exactly as today. Mirrors the backend `muhabbet.multi-device.enabled` flag — both must be ON for
 * the feature to work end-to-end. This is a compile-time constant (KISS): flip it (or wire it to a
 * build-config field) when promoting the feature past canary.
 *
 * NOTE: even with this ON, the actual per-device E2E *session transfer* is still blocked
 * (see [com.muhabbet.app.multidevice.DeviceLinkCrypto] / CLAUDE.md libsignal block). With the flag
 * ON a companion can be registered + listed + revoked (the registry + transport scaffolding), but
 * cross-device message decryption needs the crypto slice that plugs into the NotYetImplemented
 * boundary.
 */
object MultiDeviceConfig {
    /** Master switch for the linked-device UI + repository calls. Keep false until canary sign-off. */
    const val ENABLED: Boolean = false
}
