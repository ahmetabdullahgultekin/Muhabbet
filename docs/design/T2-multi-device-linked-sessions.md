# Design: Multi-Device Linked Sessions (Muhabbet Tier 2)

| | |
|---|---|
| **Status** | **In progress** — S1 NON-CRYPTO scaffolding shipped 2026-06-06 (data model + endpoints + UX); crypto BLOCKED on libsignal |
| **Author** | Engineering (2026-06-05) |
| **Reviewers** | (owner) |
| **Feature flag** | `multi-device.enabled` (backend) / `MultiDeviceConfig.ENABLED` (mobile) — default **OFF** |
| **ADRs** | [ADR-0007 Companion-device trust model](../adr/0007-companion-device-trust.md) (non-crypto slice) |
| **Tracking** | ROADMAP T2.4 → ships as 4 vertical slices (S1–S4) |

> **2026-06-06 implementation note.** The *non-crypto* part of **S1** is shipped behind the flag
> (default OFF): the device registry (`V18__multi_device_linking.sql`), the QR link-session
> handshake state machine (`DeviceLinkingService` + `DeviceLinkController`
> `POST /devices/link/{begin,complete}`, `GET /devices/link`, `POST /devices/link/{id}/revoke`),
> and the mobile transport/UX (`DeviceLinkRepository`, `LinkedDevicesScreen`, `LinkDeviceScreen`).
> The **crypto** part of S1 (per-device X3DH-on-link) and all of S2–S4 fan-out/forward-secrecy are
> **BLOCKED on the libsignal upgrade** and stubbed at the `DeviceLinkCrypto` /
> `NotYetImplementedDeviceLinkCrypto` boundary (shared module) — which **throws**, never fakes
> crypto. The migration that actually shipped is **`V18__multi_device_linking.sql`** (the §5
> `message_device_delivery` table is deferred to the S2 fan-out slice, which is non-crypto but not
> yet built). The companion's PUBLIC bundle is accepted + stored opaquely so the future crypto slice
> has what it needs without any encryption running today.

## 1. Context & problem
Muhabbet today binds one account to **one** device: a single libsignal identity, one WS session per user, messages addressed to a user resolve to that one device. WhatsApp's #1 retention feature is **companion devices** (Web/Desktop/secondary phone) that share the account, each with its own E2E identity, where every message fans out to *all* of a user's devices and history syncs. This is the largest single parity gap and is a prerequisite for the Web/Desktop client (ROADMAP T2.4).

## 2. Goals / Non-goals
**Goals**
- A primary phone can **link** up to 4 companion devices via a QR handshake.
- Each device has its **own** Signal identity & prekeys (no key material is ever copied between devices).
- A message sent to user U is **encrypted once per recipient device** and per *sender* companion device, and delivered to all of U's online devices; offline devices drain on reconnect.
- **Linked-device management**: list, name, last-active, and **revoke** (which tombstones the device's sessions everywhere).
- Works with the existing E2E text path (`MessageEncryptor`) and media path (`MediaEncryptor`) unchanged in spirit.

**Non-goals (this tier)**
- Companion device **without** the primary present (WhatsApp's "multi-device 2.0" independent devices) — primary stays the source of truth for linking. Deferred to T2-bis.
- Group sender-key fan-out across devices (that composes with T3 group-E2E).
- iOS companion (rides the T2.5 iOS-bridging work).

## 3. Current state (what exists)
- `SignalKeyManager` (X3DH + Double Ratchet) + `PersistentSignalProtocolStore` (one identity).
- `device` table + device management (auth module) — currently 1 active device per session.
- `WsClient` single session; `RedisMessageBroadcaster` already fans WS frames across backend instances (horizontal scaling) — we extend its *addressing*, not its transport.
- `E2EEnvelope` carries `senderDeviceId` already (designed for this).

## 4. Proposed design

```
        Primary phone (A1)                    Companion (A2, e.g. Web)
        ─────────────────                     ────────────────────────
        own Signal identity                   own Signal identity
              │  (1) QR: link-token + A1 pub    ▲
              └─────────────────────────────────┘  X3DH between A1↔A2
                          │
                          ▼
                  ┌──────────────────┐     device registry (Postgres)
   sender U ──►   │  Messaging core  │ ──► resolve recipient U's *device set*
   (per device)   │  (fan-out)       │     {U.d1, U.d2, …} (not-revoked)
                  └──────────────────┘
                          │ encrypt-per-device (Signal session A.sender→U.di)
                          ▼
              Redis pub/sub  ──►  every online U.di's WsSession
                          │
                          ▼  offline U.di → SQLDelight PendingMessage (per device)
```

**Linking handshake (S1)** — QR shown on primary encodes `{linkToken, primaryIdentityPub, ts, sig}`; companion scans, runs X3DH against the primary, registers its own prekey bundle to the server under the same `userId` with a new `deviceId`, and the primary signs an **approval** that the server records. No key material crosses the wire — only public bundles. (Mirrors the existing `approve-login` number-matching UX for the human-verify step.)

**Fan-out (S2)** — `ResolveRecipientDevices` (new out-port) returns the recipient's non-revoked device set. `MessageEncryptor.encryptOutgoing` becomes `encryptForDeviceSet`: one `E2EEnvelope` per `(recipientDeviceId)` carried in a `WsMessage.SendMessage.fanout: List<DeviceCiphertext>`. The backend persists one row per `(message, recipientDeviceId)` delivery and broadcasts to each device's session.

**Self-sync (S3)** — the sender's *other* devices also receive the message (encrypted to them) so the conversation appears on the Web client too — the "carbon-copy" semantics.

**Revoke (S4)** — revoking `deviceId` marks it tombstoned, deletes its sessions, and emits `WsMessage.DeviceRevoked` so peers drop sessions to it (forward secrecy on revoke).

## 5. Data model (Flyway `V18__multi_device.sql`)
```sql
ALTER TABLE devices
  ADD COLUMN linked_by_device_id UUID NULL REFERENCES devices(id),
  ADD COLUMN display_name        TEXT NULL,
  ADD COLUMN last_active_at      TIMESTAMPTZ NULL,
  ADD COLUMN revoked_at          TIMESTAMPTZ NULL;            -- soft-tombstone

CREATE TABLE message_device_delivery (                        -- replaces per-user delivery for E2E msgs
  message_id     UUID NOT NULL REFERENCES messages(id),
  recipient_device_id UUID NOT NULL REFERENCES devices(id),
  status         TEXT NOT NULL DEFAULT 'SENT',                -- SENT|DELIVERED|READ
  PRIMARY KEY (message_id, recipient_device_id)
);
CREATE INDEX idx_mdd_device_status ON message_device_delivery(recipient_device_id, status);
```
Aggregate receipt rule (extends §1.2 logic): a message is `READ` for the sender only when **every** non-revoked recipient device has READ; `DELIVERED` when ≥1 has.

## 6. Files to add / change
```
backend/src/main/kotlin/com/muhabbet/
  device/domain/model/LinkedDevice.kt                     (+)  device-set value object
  device/domain/port/in/LinkDeviceUseCase.kt              (+)
  device/domain/port/in/RevokeDeviceUseCase.kt            (+)
  device/domain/port/out/DeviceRegistryPort.kt            (+)  resolveDeviceSet(userId)
  device/domain/service/DeviceLinkingService.kt           (+)  QR token issue/verify + approval
  device/adapter/in/web/DeviceLinkController.kt           (+)  POST /devices/link/{begin,complete}
  device/adapter/in/websocket/DeviceEventsHandler.kt      (+)  DeviceRevoked broadcast
  device/adapter/out/persistence/DeviceJpaAdapter.kt      (~)  +revoke, +deviceSet query
  messaging/domain/service/MessageFanoutService.kt        (+)  encrypt-per-device + persist deliveries
  shared/.../protocol/WsMessage.kt                        (~)  +LinkDeviceBegin/Complete, DeviceRevoked, fanout field
  backend/src/main/resources/db/migration/V18__multi_device.sql (+)

mobile/composeApp/src/commonMain/kotlin/com/muhabbet/app/
  crypto/CompanionLinker.kt                               (+)  X3DH-on-link, prekey publish
  data/repository/DeviceRepository.kt                     (+)  list/link/revoke
  ui/settings/LinkedDevicesScreen.kt                      (+)  list + revoke + "Link a device"
  ui/settings/LinkDeviceQrScreen.kt                       (+)  show QR (primary) / scan QR (companion)
  data/remote/WsClient.kt                                 (~)  handle fanout + DeviceRevoked
  di/AppModule.kt                                         (~)  wire the above; gate on E2EConfig + multiDevice.enabled
  composeResources/values/strings.xml + values-en/...     (~)  i18n keys (TR+EN)
docs/design/T2-multi-device-linked-sessions.md            (+)  this doc, in-repo
docs/adr/0007-*, 0008-*.md                                (+)
docs/diagrams/multi-device-sequence.mmd                   (+)  mermaid sequence
```

## 7. Rollout & flags (reversible, per the project's posture)
- `multiDevice.enabled` default **OFF** → behaviour byte-identical to today (single device). With the flag ON but a user having only 1 device, the fan-out set is size-1 → identical wire output.
- **dark → staging → canary one account (the dev's own linked Web) → broad.** Kill-switch by flag, no redeploy.
- Backwards compatible: pre-flag clients ignore the `fanout` field (it's additive); the server falls back to single-device addressing when the set is size-1.

## 8. Agile iteration plan (vertical slices — each independently shippable behind the flag)
- **S1 — Linking handshake** (1 sprint): QR begin/complete, companion registers its own identity, primary approval, `LinkedDevicesScreen` (list + link). *Done = a 2nd device links and appears; no messaging yet.*
- **S2 — Fan-out send/receive** (1 sprint): `MessageFanoutService`, per-device delivery rows, WsClient fanout handling. *Done = a message reaches both of a recipient's devices; receipts aggregate.*
- **S3 — Self-sync** (½ sprint): sender's own companion sees sent messages. *Done = conversation mirrors on Web.*
- **S4 — Revoke + management** (½ sprint): revoke tombstones + `DeviceRevoked` propagation + forward secrecy. *Done = revoked device loses access immediately.*

## 9. Test plan
- **Unit (commonTest/JVM):** linking X3DH round-trip; fan-out produces N envelopes for N devices; revoked device excluded from set; receipt aggregation across device set (all-READ→READ, partial→DELIVERED).
- **Backend (Testcontainers):** `message_device_delivery` rows created/updated; revoke cascades sessions; `DeviceRegistryPort.resolveDeviceSet` excludes tombstoned.
- **Integration:** two WS sessions for one user both receive a message; offline device drains PendingMessage on reconnect.
- **Security:** assert no private key material appears in any request/response; tampered link-token rejected; revoked device's old ciphertext no longer decryptable.

## 10. Risks & open questions
- **Key-state divergence** across devices (the classic multi-device hard problem) — mitigated by *per-device* sessions (no shared ratchet) + server as session-bundle registry only.
- History backfill to a freshly-linked device — out of scope for S1–S4 (new messages only); a `HistorySync` slice is T2-bis (encrypted history transfer primary→companion).
- Receipt fan-out amplifies write volume → the `message_device_delivery` index + batch acks (reuse §1.2 batch path).

## 11. Rollback
Flag → OFF (no redeploy); the `fanout` field and new tables are additive (no destructive migration); single-device path is untouched and remains the default.
