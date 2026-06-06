# ADR-0007: Companion-Device Trust Model (non-crypto linking slice)

**Date:** 2026-06-06
**Status:** Accepted (scoped to the non-crypto slice; crypto deferred — see Consequences)
**Decision Makers:** Solo engineer

## Context
Tier 2 introduces companion devices (Web/Desktop/secondary phone) that share an account. The full
feature requires per-device Signal identities and message fan-out — but that crypto is **BLOCKED on
the libsignal upgrade** (frozen at 0.86.5; androidMain targets a ≤0.70 API — see `CLAUDE.md`). We
still want to ship the **registry + transport + management** half now, reversibly, without faking
any encryption, so the remaining work is a clean plug-in once the block clears.

## Decision
1. **Primary-anchored trust.** A companion is linked only via a QR handshake initiated by a device
   the user already owns (the primary). The QR carries a single-use, short-lived (120s) random
   `link_token` and the API base — **never any key material**. The server records which primary
   approved the companion (`linked_by_device_id`).
2. **Server is a registry, not a key broker.** The backend stores device rows + the link-session
   state machine. The companion's **public** prekey bundle may be supplied at link-complete and is
   stored **opaquely** (`device_link_sessions.public_bundle`) for the future crypto slice; the
   server never sees or stores private key material.
3. **Crypto is an explicit, throwing boundary.** Per-device session transfer plugs in behind
   `DeviceLinkCrypto` (shared module). The shipped default is `NotYetImplementedDeviceLinkCrypto`,
   which **throws** on every method (pointing at the libsignal block). It is never a silent no-op
   and never home-grown crypto.
4. **Flag-gated, reversible.** Entire feature behind `multi-device.enabled` (backend) /
   `MultiDeviceConfig.ENABLED` (mobile), default **OFF** → single-device path byte-identical. The
   flag is the kill-switch; the migration is additive (nullable columns + new tables only).
5. **Bounded + revocable.** Max 4 companion devices per account. Revoke is a soft-tombstone
   (`revoked_at`) that immediately drops the device from the active set; the primary cannot be
   revoked from this surface.

## Rationale
- Mirrors the existing `LoginApproval` human-verify UX and the project's Android-Signal / iOS-NoOp
  per-platform split — low conceptual surface area for a solo maintainer.
- Lets the largest WhatsApp-parity gap make real progress while the crypto dependency is blocked,
  with zero risk to the live single-device path (flag OFF) and zero fake-crypto debt.
- Public-bundle-only + registry-only keeps the server out of the trusted-computing base for keys,
  consistent with the privacy-first / KVKK posture.

## Consequences
- **Deferred:** real per-device E2E (X3DH-on-link), fan-out encrypt-per-device, forward-secrecy on
  revoke (`dropSession`), and the `message_device_delivery` fan-out table — all gated on libsignal.
  A companion can be registered/listed/revoked, but cross-device message **decryption** does not yet
  work; the UI says so honestly (`link_device_crypto_pending`).
- The QR render/scan is a platform `expect`/adapter seam (not crypto-blocked) still to be wired.
- When libsignal is upgraded + emulator-verified, the only new work here is implementing
  `DeviceLinkCrypto` and the fan-out delivery rows — the data model, endpoints, and UX stay.
- A future ADR-0008 will cover per-device fan-out + receipt aggregation once that slice is built.
