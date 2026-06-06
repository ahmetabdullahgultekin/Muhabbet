package com.muhabbet.app.multidevice

import com.muhabbet.shared.port.DeviceLinkCrypto
import com.muhabbet.shared.port.NotYetImplementedDeviceLinkCrypto

/**
 * Client-side accessor for the device-link crypto seam.
 *
 * The contract lives in the shared module ([com.muhabbet.shared.port.DeviceLinkCrypto]) so backend
 * and mobile agree on it. The shipped default is the loud [NotYetImplementedDeviceLinkCrypto] stub:
 * per-device Signal session transfer is BLOCKED on the libsignal upgrade (frozen at 0.86.5;
 * androidMain Signal code targets a ≤0.70 API — see CLAUDE.md "libsignal upgrade (BLOCKED)").
 *
 * When that block is cleared, the real implementation will live in `androidMain` (backed by the
 * upgraded libsignal, alongside `SignalKeyManager`) and be injected here per-platform — mirroring
 * the existing Signal-on-Android / NoOp-on-iOS split. This common default keeps the registry +
 * transport scaffolding compiling and testable WITHOUT faking any encryption.
 */
object DeviceLinkCryptoProvider {
    /** The current (blocked) implementation. Replace per-platform once libsignal is upgraded. */
    val current: DeviceLinkCrypto = NotYetImplementedDeviceLinkCrypto()
}
