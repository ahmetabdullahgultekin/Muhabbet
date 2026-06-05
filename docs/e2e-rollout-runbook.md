# E2E Encryption Rollout Runbook

> **Status:** E2E send/receive path is WIRED but shipped **OFF** (`E2EConfig.ENABLED = false`).
> **Scope of this runbook:** how to promote 1:1 text E2E from canary to broad safely, what to
> verify at each gate, and how to roll back without a redeploy of the backend.
> **Owner:** solo maintainer · **Last updated:** 2026-06-05 (PR #31 canary).

This is a **core-path, security-critical** change. It follows the project's reversible-rollout
rule: default OFF = legacy plaintext-under-TLS (byte-identical to pre-wiring HEAD), promote
deliberately dark → canary → broad, and keep a kill-switch instead of a redeploy.

---

## 1. What is wired today (PR #31)

| Piece | File | Behavior |
|-------|------|----------|
| Master flag | `mobile/.../app/crypto/E2EConfig.kt` | `const val ENABLED = false`. When false the whole path is a pass-through. |
| Encrypt-on-send | `mobile/.../app/crypto/MessageEncryptor.encryptOutgoing` | Wraps eligible bodies in an `E2EEnvelope`; falls back to plaintext on any failure. |
| Decrypt-on-receive | `mobile/.../app/crypto/MessageEncryptor.decryptIncoming` | Detects envelopes, decrypts; leaves plaintext untouched; leaves un-decryptable envelopes as-is (visible failure, never silent loss). |
| Send chokepoint | `mobile/.../app/data/remote/WsClient.send` | Only `WsMessage.SendMessage` bodies are touched. |
| Receive chokepoint | `WsClient` incoming frame loop | Only `WsMessage.NewMessage` bodies are touched. |
| Wire format | `shared/.../port/E2EEnvelope.kt` | Self-describing, versioned (`MAGIC = "mhbt-e2e-1"`), carries opaque ciphertext only — no key material. |

### Eligibility (deliberately conservative for the first cut)
- **Only `ContentType.TEXT` in DIRECT (1:1) conversations.** Media/voice reference MinIO blobs;
  encrypting those is a separate task (see Tier 1.4 in `ROADMAP.md`).
- **Groups are skipped.** Signal sessions are pairwise; group needs sender-key fan-out (Tier 3).
- **Requires a resolvable recipient + an established Signal session.** Missing either → plaintext
  fallback (never dropped, never thrown).
- **iOS is NoOp** (`NoOpEncryption` in `PlatformModule.ios.kt`) — iOS would send plaintext even
  with the flag on. Do NOT enable broadly until the libsignal Kotlin/Native bridge lands, or gate
  the flag Android-only.

---

## 2. Pre-flight (before flipping anything)

- [ ] **Keys register on login.** Confirm `E2ESetupService` runs and the backend
      `encryption_keys` / `one_time_pre_keys` rows exist for both test accounts
      (`+905000000001`, `+905000000002`).
- [ ] **Envelope contract tests green:** `:shared` `E2EEnvelopeTest` (round-trip + the
      false-positive regression guards) and `:mobile` `MessageEncryptorTest`
      (encrypt→decrypt round-trip, plaintext pass-through, flag-OFF no-op, group/non-text skip).
- [ ] **Backend is encryption-agnostic.** The server stores and relays `content` verbatim and
      never inspects it — so ciphertext-in-DB is purely a client-side flip. Confirm no server
      validation rejects the envelope length (`ValidationRules.isValidMessageContent` runs only
      for `ContentType.TEXT` on send; verify max length accommodates Base64 ciphertext ≈ 1.4×
      plaintext + envelope overhead).

---

## 3. Rollout gates

### Gate 0 — Dark (current)
`E2EConfig.ENABLED = false`. Path is byte-identical to legacy. No user impact. **This is HEAD.**

### Gate 1 — Canary (two devices, debug build)
1. Build a debug APK with `ENABLED = true` (flip the constant locally; do **not** merge the flip).
2. Two real Android devices, both logged in, both with registered keys.
3. Send a 1:1 text message device A → device B.
4. **Verify ciphertext in the DB**, not readable text:
   ```sql
   SELECT id, content FROM messages ORDER BY server_timestamp DESC LIMIT 1;
   -- content must be a JSON envelope: {"v":"mhbt-e2e-1","ciphertext":"...","senderDeviceId":"..."}
   -- NOT the plaintext you typed.
   ```
5. **Verify decryption** on device B: the bubble shows the original plaintext.
6. **Verify fallback:** log out device B (no session) → send from A → message must still arrive as
   plaintext (graceful fallback), never an error toast, never a dropped message.
7. **Verify legacy interop:** a device on the OLD build (flag OFF) sending plaintext to a flag-ON
   device must render fine (envelope `decodeOrNull` returns null on plaintext → passthrough).

**DONE =** all six checks pass; ciphertext confirmed at rest.

### Gate 2 — Canary (one tenant / friends-and-family, signed build)
- Ship a signed build with the flag ON to a handful of real users (depends on ROADMAP P0 keystore).
- Watch for: stuck single-tick (encrypt failure path), "can't decrypt" bubbles (session mismatch),
  crash-free rate. Crash/error visibility requires the **Sentry DSN** (ROADMAP P1) — set it first.

**DONE =** ≥48h soak with no decryption-failure spike and crash-free ≥ baseline.

### Gate 3 — Broad
- Flip `ENABLED = true` as the default and merge; OR wire it to a `BuildConfig`/remote flag so it
  can be toggled without a store release. Keep iOS gated until its bridge lands.

---

## 4. Kill-switch / rollback

Because the flag is **client-side** and the server stores `content` verbatim:

- **Pre-broad (canary builds):** stop distributing the flag-ON build. Existing plaintext history is
  unaffected; already-sent envelopes remain decryptable by recipients that have the session.
- **Post-broad (flag wired to remote config):** set the remote flag to OFF — clients revert to
  plaintext send on the next message with **no redeploy**. In-flight envelopes already in the DB
  stay as envelopes; recipients decrypt them with their existing session. New messages go plaintext.
- **If the flag is a compile-time constant at broad rollout:** rollback = ship the previous build /
  revert the one-line constant. This is why wiring the flag to remote config BEFORE broad rollout is
  recommended (see ROADMAP Tier 1.1).

> Do **not** attempt to "decrypt and rewrite" historical envelopes on the server — the server has
> no keys by design. Mixed plaintext/envelope history is expected and handled by `decodeOrNull`.

---

## 5. Known limitations to close before calling E2E "done" (tracked in ROADMAP/TODO)

1. **Group E2E** — sender-key fan-out (Tier 3).
2. ~~**Media encryption** — encrypt blobs before MinIO upload + key in the message body (Tier 1.4).~~
   **WIRED (default OFF).** `MediaEncryptor` + `SymmetricCipher` (AES-256-GCM) + `MediaKeyMaterial`
   ship behind `E2EConfig.MEDIA_ENABLED` (additionally gated by `ENABLED`). 1:1 Android only; iOS is a
   NoOp stub (fails closed to plaintext upload). Per-media fresh key+nonce, ciphertext-only in MinIO,
   key rides inside the Signal-encrypted message body, authenticated decrypt fails closed. **Flip needs
   the same canary gates below + a crypto review.** See ROADMAP §1.4.
3. **iOS parity** — libsignal-client Kotlin/Native bridge (Tier 2 / P1) — also needed for iOS media
   (CryptoKit `AES.GCM`) since `SymmetricCipher.ios.kt` is currently a NoOp stub.
4. **Safety numbers / key-change UI** — `security.key_changed` WS event exists
   (`WsMessage.SecurityKeyChanged`) and the backend bumps `keyVersion` on identity change
   (`EncryptionService.registerKeyBundle`), but there is no client verification UI yet (Tier 1.1).
5. **`senderDeviceId` on inbound `NewMessage`** — `decryptIncoming` reads the device id from the
   envelope, but multi-device session selection needs the server to preserve/forward the sender's
   device context; verify during Gate 1 multi-device testing.
