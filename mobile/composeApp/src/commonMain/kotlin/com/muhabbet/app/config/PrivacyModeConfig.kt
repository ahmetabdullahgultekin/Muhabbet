package com.muhabbet.app.config

/**
 * Feature flag for **Mahrem Mod (Privacy Mode)** — the flagship Turkey-privacy differentiator
 * (Pillar C in `docs/design/PRODUCT_DESIGN_INNOVATION_VISION.md`).
 *
 * DEFAULT = false → **zero behaviour change**. While false:
 *  - The "Mahrem Mod" entry point is hidden from Settings.
 *  - No app-lock PIN gate is ever shown on foreground, regardless of any stored toggle.
 *  - The window is never marked secure (`FLAG_SECURE` is never set).
 *  - Notification previews are shown exactly as today (the FCM service short-circuits the hide path).
 *  → The app is byte-identical to HEAD.
 *
 * When true: the Settings section is surfaced and each individually-persisted toggle
 * (preview-hiding, app-lock PIN, screenshot guard) takes effect. The toggles themselves are stored
 * via [com.muhabbet.app.data.local.TokenStorage] and read through
 * [com.muhabbet.app.data.repository.PrivacyModeRepository].
 *
 * Mirrors [com.muhabbet.app.crypto.E2EConfig] / [com.muhabbet.app.multidevice.MultiDeviceConfig]:
 * a compile-time constant (KISS), flipped deliberately once the slice is reviewed and verified on a
 * device. NOTE: this flag does NOT touch any crypto/E2E flag — Mahrem Mod is purely client-side
 * privacy ergonomics (no key material, no Signal dependency).
 */
object PrivacyModeConfig {
    /** Master switch for the Mahrem Mod UI + behaviour. Keep false until the slice is promoted. */
    const val ENABLED: Boolean = false
}
