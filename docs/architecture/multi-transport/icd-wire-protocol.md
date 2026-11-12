# Muhabbet multi-transport delivery — Interface Control Document (wire protocol)

> **Status: Proposed — pending owner approval.** No code may be written against any interface in
> this document until the owner records approval for the matching scope in
> [`APPROVAL.md`](APPROVAL.md). This ICD is derived from, and must agree with, the
> [decision brief](decision-brief-2026-11-12.md) (the single source of truth) and
> [ADR-0022](../../adr/0022-wire-protocol-envelope.md). Where they disagree, the brief wins and the
> disagreement is a defect here, recorded as a reviewer-note callout.

| | |
|---|---|
| **Date** | 2026-11-12 |
| **Base** | `dev` @ 2434324 (branch `claude/muhabbet-p2p-architecture-wdan5r`) |
| **Owning ADRs** | [0022](../../adr/0022-wire-protocol-envelope.md) (envelope, planes), [0013](../../adr/0013-delivery-semantics-and-ticks.md) (acks, idempotency), [0014](../../adr/0014-ordering-seq-change-feed.md) (seq, change feed), [0016](../../adr/0016-e2e-fail-closed-type-gate.md) (type gate), [0018](../../adr/0018-identity-keys-device-certificates.md) (keys, certificates), [0019](../../adr/0019-wake-only-push-and-server-plaintext.md) (wake push), [0021](../../adr/0021-adr0008-amendment-storage-modes-e2e.md) (mailbox), [0025](../../adr/0025-store-and-forward-custody.md) (uplink) |
| **Companion docs** | [`data-model.md`](data-model.md) (tables behind these interfaces), [`threat-model.md`](threat-model.md), [`sad.md`](sad.md) §6 (runtime views) |
| **Evidence** | [Findings register](../../reviews/2026-11-12-findings-register.md): KMP-07, KMP-11, KMP-03, BE-02, BE-03, BE-06, BE-07, BE-12, BE-15, BE-18, BE-19, MOB-03, MOB-05, MOB-10, C7, C9, GEM-08, GEM-13, GEM-16, MISS-03 (verifier, MTU row: "MTU is negotiated, never constant") |
| **Audience** | Implementers (backend, `:shared`, `:mobile:messaging`, `:mobile:crypto`), crypto reviewer (§6–§9) |

---

## 0. Conventions

1. **MUST / MUST NOT / SHOULD / MAY** are used in the RFC 2119 sense.
2. **Milestones** use the ROADMAP names: 0.4 (truth), 0.5.0 Honest, 0.6.0 Reachable, 0.7.0 Live,
   1.0.0 Encrypted, 1.1 Direct, 1.2 Nearby, 1.3 Relay, 1.4 Off-grid hardware. Every interface row
   states the milestone that introduces it. An interface for a gated milestone (1.1–1.4) is a
   design reservation; it is not implemented until that milestone's gate is met (brief §13).
3. **Identifiers.**
   - `UUID` on the JSON plane is the canonical lowercase hyphenated string.
   - `bytes16` on the protobuf planes is the 16 raw bytes of the same UUID (big-endian, RFC 4122
     byte order).
   - `clientMessageId` is a **UUIDv7**, minted by the sender device. It is allowed on the JSON plane
     (TLS to our server), inside the link-AEAD-protected `SealedFrame.Routing` (§7.2) and inside E2E
     plaintext; **never** in a cleartext `TransportFrame` header field or an uplink outer field
     (`UplinkSubmission`, §8.2) (P9).
   - `frameId` is **16 random bytes**, never a UUID of any version (ADR-0022).
4. **Time** on the JSON plane is epoch milliseconds (`Long`), as today (`serverTimestamp`). There
   are no timestamps on `TransportFrame` (P9); lifetimes travel as a relative `ttlSec`.
5. **Byte strings on the JSON plane** are standard Base64 with padding (RFC 4648 §4), field names
   suffixed `B64`.
6. **Sizes**: KiB = 1024 B, MiB = 1024 KiB. Numeric limits marked *(config)* live in
   `application.yml` or a companion-object constant, never inline (CLAUDE.md "Magic numbers").
7. **"Shared"** means `shared/src/commonMain/kotlin/com/muhabbet/shared/protocol/` (MIT, ADR-0017).
   Every type in this document is defined there once and used by backend and mobile; the 17
   duplicated backend DTOs are removed first (KMP-07).

---

## 1. Interface inventory

| ID | Interface | Plane | Encoding | Transport / auth | Introduced | Owning ADR |
|---|---|---|---|---|---|---|
| IF-01 | WS session handshake (`hello` / `welcome`) | A | JSON | WSS to our server; bearer JWT in upgrade header or `hello` | 0.4 (header auth) / 0.5 (`hello`) | 0022 |
| IF-02 | WS send path (`message.send` → `ack`) | A | JSON | same | existing → 0.4 / 0.5 extensions; v2 at 1.0 | 0013, 0022 |
| IF-03 | WS fan-out and hints (`message.new`, `sync.hint`, `message.status`) | A | JSON | same | existing → 0.5 | 0014, 0022 |
| IF-04 | WS receipts (`message.ack`) | A | JSON | same | existing → 0.4 (authorisation) / 0.5 (cumulative) | 0013 |
| IF-05 | WS security events (`security.key_changed`, `crl.delta`) | A | JSON | same | existing → 0.6 / 1.2 | 0018 |
| IF-06 | REST change feed, heads, receipts | A | JSON envelope `{data, timestamp}` | HTTPS, bearer JWT | 0.5 (plaintext items) / 1.0 (envelope items) | 0014, 0021 |
| IF-07 | Wake push | — | FCM data message (string map) | Google FCM (APNs when iOS exists) | 0.5 | 0019 |
| IF-08 | Inner `Content` | B | protobuf | inside libsignal ciphertext only | 1.0 | 0022 |
| IF-09 | `SealedFrame` + outer `TransportFrame` | C | protobuf; link AEAD | LAN, Nearby, RF links | 1.2 (gated) | 0022, 0024 |
| IF-10 | `UplinkBundle` | C | protobuf; HPKE | HTTPS `POST /api/v1/uplink` by the mule | 1.3 (gated) | 0022, 0025 |

Interfaces that are **unchanged** by this ICD: presence (`presence.*`), typing, group-management
events (`group.*`), login approval (`auth.login_*`), calls (`call.*`; reworked by ADR-0023 at 0.7,
specified there), and all REST endpoints not named in §4 — except
`POST /api/v1/messages/{messageId}/delivered` (#596), which is a second ack path and is governed
by §3.8.

---

## 2. Protocol versions and capabilities

### 2.1 Versions

| `protocolVersion` | Meaning | Introduced | Removed |
|---|---|---|---|
| *(none — legacy)* | Today's JSON frames, no `hello`. Accepted only until the cut-over release chosen when 0.4 is planned (ADR-0022 consequences) | existing | cut-over release (forced update) |
| **1** | Legacy JSON frames **plus** the 0.5 extensions in §3: `hello`/`welcome`, `seq`/`changeSeq`, `replay`, cumulative `message.ack`, `sync.hint`, `Unknown` handling | 0.5.0 | by ADR only |
| **2** | v1 plus sealed protobuf bodies for E2E conversations: `message.send.envelopes`, `message.new.envelope`, envelope items in `/changes` | 1.0.0 | by ADR only |

Protocol versions change **only by ADR** (ADR-0022 rule 6).

### 2.2 Capability tokens (`hello.capabilities`)

| Token | Client asserts | Server consequence |
|---|---|---|
| `seq` | Understands `seq`/`changeSeq`, `sync.hint`, cumulative `message.ack`, extended `message.status`, `replay` | Sends `sync.hint` instead of the legacy `message.edited` / `message.deleted` / `message.reaction` / `message.expired` frames (§3.10); expects cumulative acks |
| `wake` | Handles data-only wake pushes by fetching and composing locally | Informational only: from 0.5 every push is wake-only for every device (§5.4) |
| `sealed` | Can produce and open E2E envelopes (a `SessionCryptoPort` adapter is present) | Required to send or receive in an E2E conversation. A v2 client **without** `sealed` (e.g. a platform with no libsignal bridge; D2) is refused E2E sends with `MSG_ENCRYPTION_UNSUPPORTED` and receives `envelopeState = NOT_ADDRESSED` items |

**Rule C-1.** The server MUST NOT send a frame type that depends on a capability the session did not
advertise. **Rule C-2.** Unknown capability tokens are ignored by the server (forward compatibility).

### 2.3 Negotiation

```text
server: [minServer .. maxServer]   client: hello.protocolVersions (set), hello.minSupported
if maxServer < hello.minSupported → error{PROTOCOL_UNSUPPORTED}, close 4426   // server too old for this client
common  = { p ∈ hello.protocolVersions | p ≥ hello.minSupported } ∩ [minServer .. maxServer]
if common ≠ ∅                      → welcome{protocolVersion = max(common)}
else if max(hello.protocolVersions) < minServer → error{PROTOCOL_TOO_OLD}, close 4426
else                               → error{PROTOCOL_UNSUPPORTED}, close 4426
```

`hello.minSupported` is the lowest version the **client** will still speak; the server never
selects a version below it.

**Rule N-1.** A client release MUST keep `minSupported ≤` the highest version supported by the
previous **server** release, so a client can still talk to a server that was rolled back one
release (CICD-09 rollback window). **Rule N-2.** A server release MUST support the highest version
shipped in the previous **client** release for at least one further release, then may raise
`minServer` only by ADR and with an announced forced update.

### 2.4 Compatibility matrix during the transition

| Client ↓ / Server → | Server 0.4 | Server 0.5.x | Server 1.0 |
|---|---|---|---|
| Client ≤ 0.3.x (query-string token) | works (token accepted until cut-over) | works until cut-over, then HTTP 401 on upgrade | refused |
| Client 0.4 (header auth, no `hello`) | works | works as **legacy** session: no `seq` frames, legacy ack shape, wake push shows a generic notification (§5.4) | works as legacy until `minServer` is raised; cannot join E2E conversations |
| Client 0.5 (`hello`, v1) | n/a (no server support yet). Fallback trigger: **any `error` frame in reply to `hello`, or no `welcome` within `HELLO_TIMEOUT`** → reconnect in legacy mode. A 0.4 server predates KMP-11: `ChatWebSocketHandler` answers an undecodable frame with `error{VALIDATION_ERROR, "Invalid message format: …"}` and keeps the socket open, so neither `UNSUPPORTED_FRAME` nor a close ever arrives (that reply also leaks exception text, which §3.12 forbids from 0.4) | v1 | v1; E2E conversations unavailable (no `sealed`) |
| Client 1.0 (`hello`, v1+v2, `sealed`) | n/a | v1 (rollback window, Rule N-1) | v2 |

---

## 3. Plane A — server WebSocket control plane

### 3.1 Connection and authentication

| Item | Rule | Milestone | Finding |
|---|---|---|---|
| URL | `wss://<api-host>/ws` — **no** `token` query parameter | 0.4 | BE-12 |
| Auth (preferred) | `Authorization: Bearer <jwt>` on the HTTP upgrade request | 0.4 | BE-12 |
| Auth (alternative) | `hello.auth.token` in the first frame, for clients that cannot set upgrade headers | 0.5 | BE-12 |
| Legacy | `?token=` accepted only until the cut-over release; then the upgrade is refused with HTTP 401. Until then, access logs MUST NOT record query strings for `/ws` | 0.4 → cut-over | BE-12 |
| Device binding | The session binds to `(deviceId, jti, exp)`; access tokens gain a random `jti` claim at 0.4 (ADR-0022; today's claims are `sub`, `deviceId`, `admin`, `iat`, `exp`, `iss`). `hello.deviceId` MUST equal the JWT `deviceId` claim, else `error{AUTH_DEVICE_MISMATCH}` then close 4401 | 0.4 (`jti`) / 0.5 | BE-12 |
| `hello` deadline | A session that is not authenticated and has not sent `hello` within `HELLO_TIMEOUT` *(config)* is closed 4400. Before `hello` (v1+), every other frame is answered `error{HELLO_REQUIRED}` and the socket closed 4400 | 0.5 | KMP-11 |
| Expiry | The server closes the session at JWT `exp` (4401) and within 5 s of logout, device revoke, account deletion or ban (4403). The client refreshes and reconnects | 0.4 | BE-12, RQ-09 |
| Writes | Every outbound write goes through one `ConcurrentWebSocketSessionDecorator` per session (bounded send time and buffer) | existing on `dev` | BE-16 |

**Close codes** (private range 4000–4999):

| Code | Meaning | Client action |
|---|---|---|
| 4400 | Protocol violation: no `hello`, `hello` timeout, malformed first frame | Fix and reconnect with backoff; report |
| 4401 | Authentication invalid or expired | Refresh token, reconnect |
| 4403 | Session revoked (logout elsewhere, device revoked, account deleted, ban) | Run `SessionTeardown(reason)` if the refresh is also rejected (ADR-0020) |
| 4426 | Protocol version not supported | Show "update required"; no reconnect loop |
| 1001 / 1006 | Server going away / abnormal | Reconnect with backoff (1 s → 30 s, full jitter) |

### 3.2 Handshake: `hello` → `welcome` (IF-01, 0.5)

| Frame | Dir | Field | Type | Req | Notes |
|---|---|---|---|---|---|
| `hello` | C→S | `protocolVersions` | `List<Int>` | yes | e.g. `[1]` at 0.5, `[1,2]` at 1.0 |
| | | `minSupported` | `Int` | yes | Rule N-1 |
| | | `capabilities` | `Set<String>` | yes | §2.2 |
| | | `deviceId` | UUID | yes | must match JWT |
| | | `auth` | `{scheme: "bearer", token}` | no | only if no upgrade header |
| `welcome` | S→C | `protocolVersion` | `Int` | yes | negotiated |
| | | `serverTime` | `Long` (ms) | yes | for display-skew clamping only; never a watermark (MOB-10) |
| | | `certRenewal` | `DeviceCertificateB64` | no | 1.2, ADR-0018; protobuf §7.6 |
| | | `crlDelta` | `SignedCrlDeltaB64` | no | 1.2, ADR-0018; protobuf §7.6 |

```json
{"type":"hello","protocolVersions":[1,2],"minSupported":1,
 "capabilities":["seq","wake","sealed"],
 "deviceId":"0193a3f0-1c2d-7e4f-8a9b-0c1d2e3f4a5b"}
```

```json
{"type":"welcome","protocolVersion":2,"serverTime":1762956000123}
```

```json
{"type":"error","code":"PROTOCOL_TOO_OLD","message":"protocol 0 < minimum 1"}
```

After `welcome`, the client link state goes `Hello → Authenticated → Syncing(heads) → Ready`
(brief §6.3): it calls `GET /api/v1/conversations/heads` (§4.3) and pulls every conversation whose
watermark is below its head (§4.6).

### 3.3 Server session state (per socket)

| State | Accepts | On event | Next |
|---|---|---|---|
| `UPGRADED` | `hello` only (v1+) or any legacy frame if the upgrade carried auth and no `hello` arrives first | `hello` valid | `ACTIVE(v)` |
| | | first frame is not `hello` and the upgrade carried auth | `ACTIVE(legacy)` (until cut-over) |
| | | timeout / invalid | closed 4400 / 4401 / 4426 |
| `ACTIVE(v)` | all frames allowed for `v` and capabilities | `exp`, revoke, logout, deletion, ban | closed 4401 / 4403 |
| `ACTIVE(legacy)` | legacy frames | same | same |

### 3.4 Frame catalogue

Type discriminators are the JSON `type` strings (`classDiscriminator = "type"`). Existing names do
not change. "Ext" = extended with optional fields (compatible because `ignoreUnknownKeys = true`).

| `type` | Dir | Status | From | Section |
|---|---|---|---|---|
| `hello` | C→S | new | 0.5 | §3.2 |
| `welcome` | S→C | new | 0.5 | §3.2 |
| `message.send` | C→S | ext | 0.4 / 0.5 / 1.0 | §3.5 |
| `ack` | S→C | ext | 0.4 / 0.5 | §3.6 |
| `message.new` | S→C | ext | 0.5 / 1.0 | §3.7 |
| `message.ack` | C→S | changed (dual shape) | 0.4 / 0.5 | §3.8 |
| `message.status` | S→C | ext | 0.5 | §3.9 |
| `sync.hint` | S→C | new | 0.5 | §3.10 |
| `message.edited`, `message.deleted`, `message.reaction`, `message.expired` | S→C | **sessions without the `seq` capability only** from 0.5 | — | §3.10 |
| `security.key_changed` | S→C | ext (exists today) | 0.6 | §3.11 |
| `crl.delta` | S→C | new | 1.2 | §3.11 |
| `error` | S→C | ext | 0.5 | §3.12 |
| `presence.*`, `group.*`, `call.*`, `auth.login_*`, `ping`/`pong` | both | unchanged here | — | — |

### 3.5 `message.send` (IF-02)

| Field | Type | Req | v | Notes |
|---|---|---|---|---|
| `requestId` | String | legacy: yes; v1+: no | legacy, v1 | Kept for legacy correlation; v1+ correlates by `clientMessageId`. Retyped to `String? = null` in the shared type (§3.15) |
| `messageId` | UUID | legacy: yes; v1+: no | legacy | Legacy name of the client message id. Retyped to `String? = null` (§3.15) |
| `clientMessageId` | UUID (v7) | yes (v1+) | v1, v2 | Idempotency key with the session's `deviceId` (§3.6). For v1+ it also becomes `messages.id` (data-model V29 identity rule) |
| `conversationId` | UUID | yes | all | |
| `encryption` | `"none"` \| `"e2e/v1"` | yes (v1+) | v1, v2 | `"e2e/v1"` only with v2 and `sealed` |
| `content`, `contentType`, `replyToId`, `mediaId`, `mediaUrl`, `thumbnailUrl`, `forwardedFrom`, `viewOnce` | as today | for `"none"` | all | MUST be absent when `encryption = "e2e/v1"`; everything a user means travels inside `Content` (§6). `content` is retyped to `String? = null` (§3.15) |
| `scheduledAt` | Long? (ms) | no | all | `"none"`: as today. `"e2e/v1"`: MUST be absent at 1.0 — scheduled send is **unavailable in E2E conversations** until the owner decides otherwise (reviewer note below) |
| `envelopes` | `List<{recipientDeviceId, protocol, ciphertextB64}>` | for `"e2e/v1"` | v2 | Exactly one per **active device of every member except the sending device**, the sender's own other devices included (brief §8.3) |
| `clientTs` | Long (ms) | yes (v1+) | v1, v2 | Advisory; stored in `client_timestamp`; never used for order |

`protocol` values (closed set, extended only by ADR): `signal-prekey/1`, `signal-message/1`,
`sender-key/1`, `sender-key-distribution/1`.

> **Resolved (2026-11-12):** for every E2E row, `messages.expires_at` is computed server-side from the
> conversation timer at insert; `Content.expires_at_ms` is advisory display only (§6.2).
> **Open item [OI-12](open-items.md#oi-12):** owner decision before 1.0 entry (ICD freeze) on scheduled send in E2E conversations: (a) server-held envelopes released at `scheduledAt`; (b) unavailable in E2E (this ICD's default); (c) client-side scheduling, sent only if the device is online. Under D3 every conversation eventually becomes E2E, so (b) removes scheduled send from the whole product.

Server validation order (each failure is an `ack{status: ERROR, errorCode}`; nothing is persisted):

1. Session state and rate limit (`RATE_LIMITED`).
2. Schema and field validation; no silent coercion (`VALIDATION_ERROR`, BE-19).
3. Membership (`MSG_NOT_MEMBER`, `MSG_CONVERSATION_NOT_FOUND`); announcement-only (`MSG_ANNOUNCEMENT_ONLY`).
4. Encryption policy: an E2E conversation requires `"e2e/v1"` (`MSG_ENCRYPTION_REQUIRED`); a
   non-E2E conversation requires `"none"`; `"e2e/v1"` without `sealed` → `MSG_ENCRYPTION_UNSUPPORTED`.
5. Device set (v2): the set of `recipientDeviceId`s MUST equal the server's active-device set;
   otherwise `MSG_DEVICE_SET_STALE` with `details {missing: [...], extra: [...]}`. The client
   refreshes bundles (`ClaimPreKeyBundleUseCase`), re-seals (outbox `PENDING_SEAL` → `QUEUED`) and
   resubmits with the **same** `clientMessageId`.
6. Idempotent insert (§3.6).

Example, v1, plaintext conversation (0.5 → 1.0 for non-E2E conversations and channels):

```json
{"type":"message.send","clientMessageId":"0193a3f1-0a0b-7c0d-8e0f-101112131415",
 "conversationId":"0192ffee-aaaa-7bbb-8ccc-dddddddddddd","encryption":"none",
 "content":"Akşam 7'de görüşelim mi?","contentType":"TEXT","clientTs":1762956001000}
```

Example, v2, E2E 1:1 conversation (recipient has two devices, sender has one other device):

```json
{"type":"message.send","clientMessageId":"0193a3f1-0a0b-7c0d-8e0f-101112131416",
 "conversationId":"0192ffee-aaaa-7bbb-8ccc-dddddddddddd","encryption":"e2e/v1",
 "envelopes":[
  {"recipientDeviceId":"0191aaaa-0000-7000-8000-000000000001","protocol":"signal-message/1","ciphertextB64":"Mwg..."},
  {"recipientDeviceId":"0191aaaa-0000-7000-8000-000000000002","protocol":"signal-prekey/1","ciphertextB64":"MwJ..."},
  {"recipientDeviceId":"0191bbbb-0000-7000-8000-000000000009","protocol":"signal-message/1","ciphertextB64":"Mwh..."}],
 "clientTs":1762956002000}
```

### 3.6 `ack` (ServerAck)

| Field | Type | v | Notes |
|---|---|---|---|
| `requestId` | String? | legacy, v1 | Echoed when present. Always populated for legacy sessions (§3.15) |
| `clientMessageId` | UUID | v1+ | Correlation key |
| `messageId` | UUID | all | Equals `clientMessageId` for v1+ sends (data-model V29) |
| `status` | `OK` \| `ERROR` | all | |
| `seq` | Long? | v1+ | Present on `OK` for an immediate send; **absent** for a scheduled send until release (§3.6 rule A-4) |
| `serverTs` | Long | v1+ | Commit time of the original insert; legacy name `serverTimestamp` also sent |
| `replay` | Boolean | v1+ | `true` when this ack replays an earlier commit |
| `scheduledAt` | Long? | v1+ | Echoed for a scheduled send (rule A-4) |
| `errorCode` | String? | all | An `ErrorCode` value (§3.12); **never** exception text (BE-07) |
| `details` | Object? | v2 | Only for `MSG_DEVICE_SET_STALE` |

**Rules.**
- **A-1 Idempotency, order of operations** (data-model V29). The key is
  `(sender_device_id, client_message_id)`. In one transaction:
  1. `SELECT seq, server_timestamp FROM messages WHERE sender_device_id = ? AND client_message_id = ?`
     — found → replay the original `{seq, serverTs}` with `replay = true`, status `OK`, **without
     allocating** (BE-07, ADR-0013).
  2. Not found → allocate (`UPDATE conversations SET last_seq = last_seq + 1 … RETURNING`) and
     `INSERT` (no `ON CONFLICT`).
  3. The INSERT raises a unique violation (concurrent retry of the same send, or an id collision) →
     roll the whole transaction back (the allocation is rolled back with it, so no seq value is ever
     spent) and re-run step 1 on a fresh read; if still not found, apply A-2 / A-2b.
- **A-2 Foreign collision.** A conflict on the PK `messages.id` with a row of a **different user** is
  rejected and never replays another device's ack. Before 1.2 no other user can learn a
  `clientMessageId` before the server commits it, so this is a client bug: `MSG_ID_CONFLICT`. From 1.2
  (an off-server recipient learns `Routing.client_message_id` first) it is reported as
  `MSG_ID_SQUATTED`, never `FAILED_PERMANENT` (reviewer note below).
- **A-2b Pre-V29 / N-1 replay.** A PK conflict whose existing row has `sender_device_id IS NULL` and
  `sender_id` = the requester is a **replay** (the 0.5 upgrade copies `pendingMessage` rows sent by
  0.4 code into the outbox, data-model §5.3 step 4): `OK`, `replay = true`, original `{seq, serverTs}`.
  The server MAY then set the row's `sender_device_id` to the requesting device.
- **A-2c Legacy session duplicate (from 0.4).** A legacy session's duplicate send is answered
  `status OK` with the original `messageId` and `serverTimestamp` (the legacy shape has no `replay` or
  `seq` field, §3.15). `MSG_DUPLICATE` is retired from 0.4.
- **A-3 Ack after commit.** The ack is written to the socket only after the transaction commits
  (`TransactionRunner` after-commit hook).
- **A-4 Scheduled.** A scheduled send is acknowledged `OK` with `seq` absent and `scheduledAt`
  echoed; it receives its `seq` at release (ADR-0014) and the sender learns it through the change
  feed like everyone else. The outbox treats this ack as `SERVER_ACCEPTED` with a pending seq.

> **Open item [OI-10](open-items.md#oi-10):** decide before §6.2 freezes (1.0 entry) between the kept identity rule (`messages.id = client_message_id`, A-2/A-2b) and the brief-literal pair reference with a server-minted id, which removes the `MSG_ID_SQUATTED` path. V29 at 0.4 forecloses neither.

```json
{"type":"ack","clientMessageId":"0193a3f1-0a0b-7c0d-8e0f-101112131415",
 "messageId":"0193a3f1-0a0b-7c0d-8e0f-101112131415","status":"OK",
 "seq":4812,"serverTs":1762956001140,"serverTimestamp":1762956001140,"replay":false}
```

```json
{"type":"ack","clientMessageId":"0193a3f1-0a0b-7c0d-8e0f-101112131416",
 "messageId":"0193a3f1-0a0b-7c0d-8e0f-101112131416","status":"ERROR",
 "errorCode":"MSG_DEVICE_SET_STALE",
 "details":{"missing":["0191aaaa-0000-7000-8000-000000000003"],"extra":[]}}
```

### 3.7 `message.new` (IF-03)

| Field | Type | v | Notes |
|---|---|---|---|
| existing fields (`messageId`, `conversationId`, `senderId`, `senderName`, `content`, `contentType`, `serverTimestamp`, …) | as today | all | For `"none"` conversations only from v2 |
| `clientMessageId` | UUID | v1+ | Lets the receiver match an off-server provisional row (1.2) without decrypting |
| `senderDeviceId` | UUID | v1+ | |
| `seq` | Long | v1+ | |
| `changeSeq` | Long | v1+ | Equals `seq` for a new message |
| `envelope` | `{protocol, ciphertextB64}` | v2 | The one envelope addressed to **this** device; `content` and `contentType` absent |

**Rules.**
- **N-1** `message.new` is a **hint that happens to carry data** (P6). The receiver persists it
  through the Inbox (upsert, then commit), and only after commit enqueues `DELIVERED` with
  `upto = C`, the conversation's complete-through point (rule R-ACK, §3.8) — **never** the pushed
  `seq`. A pushed row that advanced `w` contiguously also advanced `C`; one that did not (gap) is not
  acknowledged until the pull it schedules completes.
- **N-2** A frame larger than `INLINE_MAX_BYTES` *(config)* is not inlined: the server sends
  `sync.hint` instead and the client pulls.
- **N-3** Watermark handling follows §3.10 (a pushed row never advances the watermark past a gap).

```json
{"type":"message.new","messageId":"0193a3f1-0a0b-7c0d-8e0f-101112131416",
 "clientMessageId":"0193a3f1-0a0b-7c0d-8e0f-101112131416",
 "conversationId":"0192ffee-aaaa-7bbb-8ccc-dddddddddddd",
 "senderId":"0190cccc-0000-7000-8000-00000000000c","senderName":"Ayşe",
 "senderDeviceId":"0191bbbb-0000-7000-8000-000000000008",
 "seq":4813,"changeSeq":4813,"serverTimestamp":1762956002150,
 "envelope":{"protocol":"signal-message/1","ciphertextB64":"Mwg..."}}
```

### 3.8 `message.ack` (IF-04)

Two shapes are accepted; the shape is chosen by the session, not per frame.

| Shape | Sessions | Fields | Semantics |
|---|---|---|---|
| **Cumulative** | v1+ with `seq` | `conversationId`, `uptoSeq: Long`, `kind: DELIVERED \| READ` | Every message in the conversation with `seq ≤ uptoSeq` not sent by this device is acknowledged **for this device** |
| **Legacy** | legacy, v1 without `seq` | `messageId`, `conversationId`, `status` | One message only. MUST NOT be mapped to a cumulative ack: a legacy client acks out of order, and widening would claim delivery of rows it never stored |
| **Legacy REST** | any session, FCM path | `POST /api/v1/messages/{messageId}/delivered` (`MessageController`, #596) | Same semantics and **same authorisation** as the legacy WS shape below; never widened to a cumulative ack. Deprecated once wake-only push (§5) removes `messageId` from the push payload: from 0.5 no client can learn a `messageId` from a push, so the endpoint stops being called by 0.4 clients on the FCM path (§5.4); removed one release after the legacy cut-over |

The shared `AckMessage` type carries both WS shapes with all shape-specific fields optional
(`messageId: String? = null`, `status: MessageStatus? = null`, `uptoSeq: Long? = null`,
`kind: ReceiptKind? = null`); the server validates the field set against the **session's** shape and
rejects a mismatch with `MSG_ACK_INVALID` (§3.15).

**Rule R-ACK (complete-through point; normative for every client, P4).** A device MAY send
`ack{conversationId, uptoSeq = s, kind = DELIVERED}` only for `s ≤ C`, where `C` is the
conversation's **complete-through point**, persisted on the device:

| Event | Effect on `C` |
|---|---|
| A pull (§4.6) ends with a page whose `hasMore = false` | `C = page.lastSeq` (committed in that page's transaction) |
| `message.new` with `changeSeq == w + 1` and `w == C` (§3.10) | `C = changeSeq`, in the upsert transaction |
| Any other `message.new`, any mid-pull page (`hasMore = true`) | `C` unchanged |

Why: pages are ordered by `change_seq`, and an edited old message has a low `seq` and a high
`change_seq`, so it arrives on a later page. Acking the highest **committed seq** after page 1 would
acknowledge it unstored; from 1.0 the mailbox deletes that device's envelope on the ack (ADR-0021),
turning it into a permanent `PURGED` loss. A mid-pull watermark `w` does not imply completeness either,
because superseded `change_seq` values hide unstored rows. Once a pull completes at `lastSeq`, every
message with `seq ≤ lastSeq` has been stored in some version (a later edit only re-versions it). READ
acks are bounded by `C` as well.

**Authorisation (all three shapes, 0.4, BE-03, BE-18).** Accepted only if (a) the session's device
belongs to a current member of the conversation, (b) for **tick-affecting** DELIVERED/READ, the
member is not the sender of the acked messages (watermark/custody acks are accepted from any
registered, non-revoked device of any member, the sender's own included, drive mailbox deletion from
1.0 and are excluded from tick aggregation — ADR-0013 §4), (c) the referenced `seq`/`messageId` exists in that conversation,
and (d) the change is monotonic (`rank(status) < rank(new)`, one conditional UPDATE). A rejected ack
is answered `error{MSG_ACK_INVALID}` and **never broadcast**; an accepted ack that changes nothing is
not broadcast either.

**Policy at publish time (#377).** If the acking user has `readReceiptsEnabled = false`, a READ is
stored (their own unread state clears) but published to others as DELIVERED, in WS and in REST
(ADR-0019).

```json
{"type":"message.ack","conversationId":"0192ffee-aaaa-7bbb-8ccc-dddddddddddd","uptoSeq":4813,"kind":"DELIVERED"}
```

> **Note:** resolved with the ADR-0013 split (Decision §4): sender-own-device acks are
> watermark/custody acks, used for mailbox deletion and excluded from ticks.

### 3.9 `message.status` (S→C, receipts to the sender)

| Field | Type | v | Notes |
|---|---|---|---|
| `messageId`, `conversationId`, `userId`, `status`, `timestamp` | as today | all | Legacy sessions: one frame per message |
| `kind` | `DELIVERED` \| `READ` | v1+ `seq` | |
| `uptoSeq` | Long | v1+ `seq` | The **user-level** cumulative watermark: the highest seq for which at least one of `userId`'s devices committed (DELIVERED) or read (READ), after the #377 downgrade |

Lost status frames are repaired by `GET …/receipts` (§4.4). The client derives ticks from those
watermarks only; it never promotes to READ because of one member's update (group fix, ADR-0013).

### 3.10 `sync.hint` and the client hint rule

| Field | Type | Notes |
|---|---|---|
| `conversationId` | UUID | |
| `seq` | Long | The conversation's head (`conversations.last_seq`) after the change that triggered the hint |

```json
{"type":"sync.hint","conversationId":"0192ffee-aaaa-7bbb-8ccc-dddddddddddd","seq":4815}
```

**Server rule H-1.** For sessions **with the `seq` capability**, every change that allocates a
`change_seq` (data-model §3.1: edit, delete, reaction, poll vote, expiry, scheduled release), other
than a new inlined message, produces exactly one `sync.hint` after commit. The legacy frames
`message.edited|deleted|reaction|expired` are sent to every session **without the `seq`
capability** (legacy and v1-without-`seq` alike), so no session receives neither.

Not in the change feed, and therefore not hinted: membership (`group.*` frames, repaired by
`GET /api/v1/conversations` on `Syncing(heads)` and foreground), pinned messages (V20; repaired by
`GET …/pinned-messages` on chat open) and receipts (§4.4). These are conversation state, not
message changes; their lost frames are repaired by those pulls (P6).

**Client rule H-2 (watermark advance).** The watermark `w` is over `change_seq` (ADR-0014). Holes
are expected because superseded edits overwrite a row's `change_seq`, so a gap is never inferred
from a hole alone:

| Event | Condition | Action |
|---|---|---|
| `message.new` | `changeSeq == w + 1` | Upsert; set `w = changeSeq` in the same transaction |
| `message.new` | `changeSeq > w + 1` | Upsert (do not move `w`); schedule `pull(afterSeq = w)` |
| `message.new` | `changeSeq ≤ w` | Upsert only if newer than the stored `change_seq` (idempotent) |
| `sync.hint` | `seq > w` | Schedule `pull(afterSeq = w)` (debounced per conversation) |
| `sync.hint` | `seq ≤ w` | Ignore |
| `heads()` | `lastSeq > w` | Pull |

This is the rule SAD §6.2 writes as "a hint with `seq > watermark + 1`"; it is conservative (a hole
may cause one unnecessary pull) and never skips a change.

### 3.11 Security events

| `type` | Fields | From | Notes |
|---|---|---|---|
| `security.key_changed` | existing `userId`, `conversationId`, `timestamp`; **new** `deviceId: String? = null` (defaulted so older decoders and older encoders interoperate); `conversationId` retyped `String? = null` (§3.15) | 0.6 | Emitted on `IdentityKeyChanged` (a new device with a new identity key; replacement for an existing device is rejected, ADR-0018). Client: safety-number-changed system row; blocking interstitial for verified contacts |
| `crl.delta` | `deltaB64` (protobuf `SignedCrlDelta`, §7.6) | 1.2 (gated) | Applied through `DeviceCertificateVerifier.applyCrl`; also fetched on connect via `welcome.crlDelta` |

> **Resolved (2026-11-12):** verified (`WsMessage.kt:351`: `userId`, `conversationId`, `timestamp`).
> Brief §10 and the ADR-0022 frame table read "`security.key_changed` (existing) extended with
> `deviceId`: `{userId, deviceId, conversationId?, timestamp}`" (brief erratum E-25).

### 3.12 `error` frame and error-code registry

`error{code, message?, ref?, details?}`. `code` is an `ErrorCode` value; `message` is a non-localised
developer string and MUST NOT contain exception text; `ref` echoes `requestId`/`clientMessageId`
when the error answers a specific frame.

New `ErrorCode` entries (the existing enum lives in `backend/.../shared/exception/Exceptions.kt`;
after KMP-07 the codes the client must branch on are mirrored in `:shared`):

| Code | Plane / where | Trigger | Client action | From |
|---|---|---|---|---|
| `HELLO_REQUIRED` | WS | Frame before `hello` on a v1+ upgrade | Bug; reconnect | 0.5 |
| `UNSUPPORTED_FRAME` | WS | Server decoded a client frame to `Unknown` (KMP-11) | Log; do not retry that frame | 0.5 |
| `PROTOCOL_TOO_OLD` | WS | §2.3 | "Update required" | 0.5 |
| `PROTOCOL_UNSUPPORTED` | WS | §2.3, client newer than server range | Retry later; report | 0.5 |
| `AUTH_DEVICE_MISMATCH` | WS | `hello.deviceId` ≠ JWT `deviceId` | Bug; re-authenticate | 0.5 |
| `MSG_ACK_INVALID` | WS | §3.8 authorisation failure (BE-03) | Drop the ack; resync | 0.4 |
| `MSG_ID_CONFLICT` | WS `ack` | §3.6 A-2, before 1.2 (client bug) | Mint a new `clientMessageId` only if the entry never reached any recipient; else `FAILED_PERMANENT` | 0.4 |
| `MSG_ID_SQUATTED` | WS `ack` | §3.6 A-2, from 1.2: another user's row holds this id | Re-mint and resubmit; not `FAILED_PERMANENT`; report to moderation (reviewer note §3.6) | 1.2 (gated) |
| `MSG_ENCRYPTION_REQUIRED` | WS `ack` | Plaintext send into an E2E conversation | Outbox → `PENDING_SEAL`; never resend plaintext (ADR-0016) | 1.0 |
| `MSG_ENCRYPTION_UNSUPPORTED` | WS `ack` | `"e2e/v1"` from a session without `sealed` | Honest UI | 1.0 |
| `MSG_DEVICE_SET_STALE` | WS `ack` | §3.5 step 5 | Refresh bundles, re-seal, resubmit same id | 1.0 |
| `ENCRYPTION_IDENTITY_REPLACEMENT_REJECTED` | REST keys | Different identity key for an existing `device_id` (ADR-0018) | Register as a new device | 0.6 |
| `UPLINK_REJECTED` | REST uplink | Undecryptable, bad signature, unknown key, replay of a foreign bundle (§8.4) | Mule deletes the blob | 1.3 |
| `UPLINK_QUOTA_EXCEEDED` | REST uplink | Per-mule or per-sender quota | Mule keeps the blob until 24 h | 1.3 |

Existing codes reused unchanged: `RATE_LIMITED`, `VALIDATION_ERROR`, `MSG_NOT_MEMBER`,
`MSG_CONVERSATION_NOT_FOUND`, `MSG_ANNOUNCEMENT_ONLY`, `MSG_INVALID_CURSOR`, `AUTH_TOKEN_EXPIRED`,
`AUTH_TOKEN_REVOKED`. `MSG_DUPLICATE` is **retired** for the send path (replaced by replay, A-1).

### 3.13 Unknown frames

- Decoding uses `polymorphicDefaultDeserializer` to `Unknown(type: String)`; `Unknown` is never
  encodable (KMP-11).
- **Server** receiving `Unknown` → `error{UNSUPPORTED_FRAME, ref?}`; the socket stays open.
- **Client** receiving `Unknown` → logs at debug and ignores it; it never replies (a newer server
  cannot make an older client chatty).
- A frame whose `type` is known but whose body fails validation is **not** `Unknown`: it is
  `VALIDATION_ERROR` (server) or a dropped frame plus a pull (client).

### 3.14 Limits (all *(config)*)

| Limit | Applies to | Behaviour when exceeded |
|---|---|---|
| `MAX_WS_FRAME_BYTES` | every inbound WS text frame | close 1009 (message too big) |
| `MAX_ENVELOPES_PER_SEND` | `message.send.envelopes` | `VALIDATION_ERROR`; bounded by `conversations.max_members` × devices per user (primary + 4, ADR 0007) |
| `INLINE_MAX_BYTES` | outbound `message.new` | send `sync.hint` instead |
| `HELLO_TIMEOUT` | unauthenticated sockets | close 4400 |
| Per-device send and ack rates | WS | `RATE_LIMITED` (in-process today; Redis when scaled out, BE-17) |

### 3.15 Shared type changes (`WsMessage.kt`)

Several fields that this ICD makes optional are **non-null without a default** in today's shared
types; an old decoder fails with `MissingFieldException` on a frame that omits them. The rule that
keeps §10 rule 3 intact: **the wire contract of each session shape never changes.** Fields become
nullable only in the Kotlin type; omission is a property of protocol v1/v2, which are new versions.

| Type.field | Today | New Kotlin type | First omitted in | Rule |
|---|---|---|---|---|
| `SendMessage.requestId` | `String` | `String? = null` | v1 (client → server) | Server requires it from legacy sessions |
| `SendMessage.messageId` | `String` | `String? = null` | v1 (replaced by `clientMessageId`) | Server requires it from legacy sessions |
| `SendMessage.content` | `String` | `String? = null` | v2, `"e2e/v1"` sends | Required for `"none"` |
| `ServerAck.requestId` | `String` | `String? = null` | v1 (server → client) | **Always populated for legacy sessions** |
| `NewMessage.content` | `String` | `String? = null` | v2, E2E conversations | **Always populated for legacy sessions** (they cannot join E2E conversations, §2.4) |
| `NewMessage.contentType` | `ContentType` | `ContentType? = null` | v2, E2E conversations | Same |
| `AckMessage.messageId` | `String` | `String? = null` | v1 cumulative shape | Required from sessions using the legacy shape (§3.8) |
| `AckMessage.status` | `MessageStatus` | `MessageStatus? = null` | v1 cumulative shape | Same |
| `AckMessage.uptoSeq`, `.kind` | — | `Long? = null`, `ReceiptKind? = null` | — (new) | Required from `seq` sessions |
| `Error.message` | `String` | `String? = null` | v1 | **Always populated for legacy sessions** (a non-localised developer string, never exception text) |
| `SecurityKeyChanged.conversationId` | `String?` (no default) | `String? = null` | v1 | Always sent (possibly `null`) to legacy sessions |
| `SecurityKeyChanged.deviceId` | — | `String? = null` | — (new) | §3.11 |

Validation by session shape replaces type-level non-nullability: a frame whose field set does not
match the session's shape is `VALIDATION_ERROR` (send) or `MSG_ACK_INVALID` (ack). Golden vectors
(G-1) cover each shape in both directions, including a legacy-shaped vector decoded by **today's**
type definitions to prove old clients still decode what the server sends them.

---

## 4. REST: change feed, heads, receipts (IF-06)

All responses use the envelope `{ "data": …, "timestamp": "…" }` / `{ "error": {code, message}, "timestamp": "…" }`.
The requesting device is the JWT `deviceId`.

### 4.1 `GET /api/v1/conversations/{id}/changes?afterSeq={n}&limit={k}`

| Parameter | Type | Rule |
|---|---|---|
| `afterSeq` | Long ≥ 0 | The client watermark over `change_seq`. Malformed → 400 `MSG_INVALID_CURSOR` (BEV-M2) |
| `limit` | Int | Default and maximum *(config)*; larger values are clamped, not rejected |

Response `data`:

| Field | Type | Notes |
|---|---|---|
| `items` | `List<MessageChange>` | Ordered by `changeSeq` ascending; `afterSeq < changeSeq ≤ lastSeq` |
| `lastSeq` | Long | The conversation head read **before** the items (§4.5) |
| `nextAfterSeq` | Long | The cursor for the next page: the highest `change_seq` **scanned** by this page, visible to the requester or not, `≤ lastSeq`; equals `lastSeq` when `hasMore = false`. The client sets `w := nextAfterSeq` (ADR-0014 §2; brief erratum E-04) |
| `hasMore` | Boolean | More **visible** items exist in `(nextAfterSeq, lastSeq]` |

`MessageChange`:

| Field | Type | When |
|---|---|---|
| `messageId`, `clientMessageId` | UUID | always |
| `conversationId` | UUID | always |
| `seq`, `changeSeq` | Long | always (`seq` is fixed at release; `changeSeq` moves) |
| `senderId`, `senderDeviceId` | UUID | always (`senderDeviceId` null for rows sent before V29) |
| `serverTs` | Long | always |
| `deleted` | Boolean | tombstone: content fields absent |
| `purged` | Boolean | the server's plaintext copy was removed at the 1.0 cutover (ADR-0021 §4); content fields absent, **not** a deletion — a client that already holds the content keeps it |
| `editedAt`, `editVersion` | Long?, Int | plaintext conversations |
| `expiresAt`, `viewOnce` | Long?, Boolean | |
| `reactions` | `Map<emoji, List<UUID>>` | full current state (last-writer-wins by `changeSeq`) |
| `contentType`, `content`, `replyToId`, `mediaId`, `thumbnailUrl`, `forwardedFrom` | as today | plaintext conversations and channels |
| `envelope` | `{protocol, ciphertextB64}` | E2E conversations, v2, when `envelopeState = PRESENT` |
| `envelopeState` | `PRESENT` \| `PURGED` \| `NOT_ADDRESSED` | E2E conversations only. `NOT_ADDRESSED`: `seq < addressed_from_seq` of this (conversation, device), or the device was never addressed (linked later, joined later, or no `sealed`); `PURGED`: otherwise, when no envelope row exists (acked by this device or past TTL). Computed from `conversation_device_cursor.addressed_from_seq`, never from per-message rows (data-model §3.6). `PURGED` is ADR-0021's `envelopeExpired` marker ("message expired before this device received it"); ADR-0021 should adopt the name `PURGED` |

Example (pre-1.0, plaintext):

```json
{"data":{"items":[
   {"messageId":"0193a3f1-0a0b-7c0d-8e0f-101112131415","clientMessageId":"0193a3f1-0a0b-7c0d-8e0f-101112131415",
    "conversationId":"0192ffee-aaaa-7bbb-8ccc-dddddddddddd","seq":4812,"changeSeq":4816,
    "senderId":"0190dddd-0000-7000-8000-00000000000d","senderDeviceId":"0191bbbb-0000-7000-8000-000000000009",
    "serverTs":1762956001140,"deleted":false,"editedAt":1762956030000,"editVersion":1,
    "contentType":"TEXT","content":"Akşam 8'de görüşelim mi?","reactions":{"👍":["0190cccc-0000-7000-8000-00000000000c"]}},
   {"messageId":"0193a3f0-ffff-7000-8000-000000000001","clientMessageId":"0193a3f0-ffff-7000-8000-000000000001",
    "conversationId":"0192ffee-aaaa-7bbb-8ccc-dddddddddddd","seq":4790,"changeSeq":4817,
    "senderId":"0190cccc-0000-7000-8000-00000000000c","senderDeviceId":null,
    "serverTs":1762950000000,"deleted":true,"reactions":{}}],
  "lastSeq":4817,"nextAfterSeq":4817,"hasMore":false},
 "timestamp":"2026-11-12T14:00:05.120Z"}
```

Visibility rules:
1. Membership-checked (`MSG_NOT_MEMBER` / `MSG_CONVERSATION_NOT_FOUND`).
2. Unreleased scheduled messages have no `seq`/`changeSeq` and are invisible to **everyone**,
   including the sender (BE-02 closed structurally; the sender sees drafts through the existing
   scheduled-messages surface, not this feed).
3. The #377 READ downgrade and `aboutVisibility` are not part of this payload; receipts come from §4.4.
4. Blocked users: rows from a user the requester has blocked are delivered as today's policy
   dictates; the block list is additionally enforced in the client Inbox on every transport
   (ADR-0029 rule 4).

### 4.2 `GET /api/v1/conversations` (existing list)

Each item gains `lastSeq: Long`. Nothing else changes.

### 4.3 `GET /api/v1/conversations/heads`

Lightweight projection backing `ChangeFeedPort.heads()` (brief §5):
`{"data":{"items":[{"conversationId":"…","lastSeq":4817}]},"timestamp":"…"}`. Only conversations the
requester is a member of. Used on `Syncing(heads)`, wake, foreground and WorkManager runs.

### 4.4 `GET /api/v1/conversations/{id}/receipts`

`{"data":{"members":[{"userId":"…","deliveredUpToSeq":4813,"readUpToSeq":4810}]},"timestamp":"…"}`.
User-level watermarks (max over the user's devices, sender's own devices excluded), after the #377
downgrade. For conversations above `RECEIPTS_MEMBER_CAP` *(config)* and for channels the endpoint
returns `members: []` (no per-member receipts, as today for channels).

> **Resolved (2026-11-12):** `heads` (§4.3) and `receipts` (§4.4) are accepted; both are required by
> P6, and `heads` also serves the opaque wake payload (ADR-0019). Brief §10 lists both REST endpoints
> at 0.5 (erratum E-26).

### 4.5 Consistency contract (server)

1. Allocation (`UPDATE conversations SET last_seq = last_seq + 1 … RETURNING`) holds the
   conversation row lock until commit, so values commit in allocation order per conversation
   (ADR-0014 invariant).
2. The feed reads `lastSeq` **first**, then selects `afterSeq < change_seq ≤ lastSeq`. Under READ
   COMMITTED every value `≤ lastSeq` belongs to a committed row or to a superseded `change_seq`, so a
   page never skips a change. No path spends a value without a row: a replayed send never allocates,
   and a send whose INSERT conflicts rolls its allocation back with the transaction (§3.6 A-1). A
   hole left by any other cause (a superseded value, a hard-deleted row) is harmless, because the
   client never infers a gap from a hole (§3.10 H-2).
3. The visibility predicate (unreleased scheduled rows, rows hidden from this requester) is applied
   **inside the SQL query, before `LIMIT`**, and `hasMore` is computed over visible rows. A page that
   scans only hidden changes still returns `nextAfterSeq` past them, so the client always advances.
   `hasMore = false` returns `nextAfterSeq = lastSeq`, which entitles the client to set `w = lastSeq`.
4. Edits, deletes, reactions, expiry and scheduled release each allocate one new value and stamp
   `change_seq` (bulk expiry allocates a contiguous range, data-model V28).

### 4.6 Client pull algorithm (`SyncCoordinator`)

```text
pull(conv):
  w = store.watermark(conv)
  loop:
    page = changeFeed.changes(conv, afterSeq = w, limit)
    store.tx {                                   // one transaction per page
      for item in page.items: inbox.upsert(item) // newer change_seq wins
      w = page.nextAfterSeq                     // = lastSeq when !hasMore; never max(item.changeSeq)
      store.setWatermark(conv, w)
      if (!page.hasMore) store.setCompleteThrough(conv, page.lastSeq)   // C, rule R-ACK
    }
    if (!page.hasMore) {
      receipts.enqueueDelivered(conv, upto = page.lastSeq)   // after commit (P4); never mid-pull
      break
    }
```

A pull interrupted mid-way (crash, link loss) resumes from the persisted `w`; `C` stays at its last
complete value, so no ack covers a row the device has not stored (§3.8 R-ACK).

Triggers: reconnect (`Syncing(heads)`), wake push, foreground, WorkManager/BGTask (driven by seq,
never the device clock — MOB-10), §3.10 rule H-2, and a `deviceCounter` gap in a provisional
off-server row (1.2).

### 4.7 Deprecated: `GET /api/v1/messages/since`

Kept for exactly one release after `/changes` ships (ADR-0014), returning `Deprecation` and `Sunset`
headers; removed afterwards. Its `lastSync` timestamp cursor is the MOB-10 defect and is not
extended.

---

## 5. Wake push (IF-07, from 0.5)

### 5.1 FCM message (HTTP v1 API shape)

```json
{"message":{
  "token":"<fcm-registration-token>",
  "android":{"priority":"HIGH","collapse_key":"mbt-wake","ttl":"<WAKE_TTL>s"},
  "data":{"t":"wake","v":"1"}}}
```

### 5.2 Rules

| # | Rule | Finding / ADR |
|---|---|---|
| W-1 | **Data-only.** No `notification` block, no `android.notification`, no sender name, body, group name, avatar, message id or timestamp. FCM data values are strings | BE-15, ADR-0019 |
| W-2 | `data` keys are exactly `t` and `v`, optionally plus `h`, a random per-device hint token mapped server-side (ADR-0019 as amended 2026-11-12, brief erratum E-46). **No `conversationId` and no `seq`**: they would let the push provider link co-members of a conversation and count messages. `v` is the payload-format version, so a later key change is detectable. Adding a key requires an ICD change and a DPIA screening note (ADR-0029 rule 6) | ADR-0019, ADR-0029 |
| W-3 | High priority; one **constant** collapse key `mbt-wake` per app. FCM limits how many distinct collapse keys it keeps per device, and the client pulls **all** heads on wake, so per-conversation keys buy nothing | P6 |
| W-4 | The wake names nothing. On wake the client runs `heads()` (§4.3) and pulls every conversation with `lastSeq > w`, then composes notifications **locally** from the committed DB rows | P6, ADR-0019 |
| W-5 | Sent after commit, never inside the send transaction, to recipient devices — the sender's own other devices included, **the sending device excluded** — and **not** to any user whose focused conversation (`WsMessage.ConversationFocus`, held by `WebSocketSessionManager`, #618) is `conversationId`. The existing #618 suppression in `OfflinePushSender` is preserved, not deferred; broader presence-aware suppression for devices with a live WS session is the later refinement (brief §4.2) | BE-04, #618 |
| W-6 | `WAKE_TTL` *(config)*; a dropped or expired wake is repaired by foreground pull and WorkManager | R11 |

### 5.3 OEM fallback (ADR-0019)

Pull on foreground; WorkManager periodic sync driven by `heads()`; in-app battery-optimisation
notice on known aggressive OEMs. Latency is measured, not assumed (R11, owner-observed P13).

### 5.4 Compatibility with 0.4 clients

The current Android handler (`MuhabbetFirebaseMessagingService.onMessageReceived`) already falls
back to the app name as title and the localised "new message" string as body when `notification`
and the `senderName`/`body` data keys are absent. A 0.4 client therefore shows a **content-free
generic notification** for a wake push. No per-device push-mode column is needed; wake-only applies
to every device from 0.5.

Two consequences for 0.4 clients, accepted: (1) they post one generic notification per wake,
including wakes caused by their user's own sends from another device (W-5 excludes only the sending
device); (2) the #596 FCM-path REST delivered ack (`POST /messages/{messageId}/delivered`, §3.8)
**stops firing**, because the wake carries no `messageId`. A 0.4 client's DELIVERED therefore arrives
only when it next opens a WS session and receives the frame there.

### 5.5 iOS (when the iOS app exists)

APNs with a Notification Service Extension: the visible alert is a localised placeholder
(`loc-key`), `mutable-content: 1`, and a payload carrying only `{t: wake, v}` (no `conversationId`, no `seq`, as on
Android); the extension calls `heads()`, fetches over TLS and replaces the text. Specified when KMP-01 is resolved (0.6 gate).

---

## 6. Plane B — inner `Content` (IF-08, 1.0)

### 6.1 Placement

`Content` is serialized with `kotlinx-serialization-protobuf` (explicit `@ProtoNumber` on every
field), padded (§6.4), then sealed by `EnvelopeSealer` into one libsignal ciphertext per recipient
device. It is never sent outside a ciphertext. The JSON `E2EEnvelope` currently in
`shared/.../port/E2EEnvelope.kt` (magic `mhbt-e2e-1` embedded in `content`) is **retired** at 1.0:
it embeds ciphertext in a plaintext field and is the structure behind the silent fallback (C9).

### 6.2 Schema (proto3 notation; field numbers are normative)

```proto
syntax = "proto3";
package muhabbet.content.v1;

message Content {
  CausalHeader    causal       = 1;
  ConversationRef conversation = 2;
  Body            body         = 3;
  optional int64  expires_at_ms = 4;   // disappearing-message deadline (absolute, sender clock; advisory, clamped)
  optional bool   view_once     = 5;
  reserved 6 to 9;                     // reserved for header growth
}

message CausalHeader {
  bytes  sender_device_id  = 1;  // bytes16
  uint64 device_counter    = 2;  // monotonic per (sender device, conversation); gaps → pull; replays dropped
  bytes  client_message_id = 3;  // bytes16, UUIDv7 (allowed here only)
  int64  sender_time_ms    = 4;  // advisory; display-clamped to [receivedAt − 5 min, receivedAt]
}

message ConversationRef {
  bytes        conversation_id   = 1;  // bytes16
  StorageMode  storage_mode      = 2;
  optional uint64 expected_seq_hint = 3; // sender's last known head; receiver's anchor_seq for provisional rows
}
enum StorageMode { STORAGE_MODE_UNSPECIFIED = 0; CLOUD = 1; BYO = 2; LOCAL_ONLY = 3; }

message Body {
  oneof kind {
    Text         text     = 1;
    MediaPointer media    = 2;
    Receipt      receipt  = 3;
    Edit         edit     = 4;
    Delete       delete   = 5;
    Reaction     reaction = 6;
    Control      control  = 7;
  }
}

message Text {
  string           text        = 1;
  repeated Mention mentions    = 2;
  optional bytes   reply_to    = 3;  // client_message_id of the quoted message
  optional LinkPreview preview = 4;  // generated by the sender (ADR-0019)
  optional bytes   forwarded_from = 5;
}
message Mention     { bytes user_id = 1; uint32 start = 2; uint32 length = 3; }
message LinkPreview { string url = 1; string title = 2; string description = 3; optional MediaPointer image = 4; }

message MediaPointer {
  string blob_id  = 1;   // object key in MinIO; the blob is ciphertext
  string mime     = 2;
  uint64 size     = 3;   // ciphertext bytes
  bytes  sha256   = 4;   // 32 bytes, of the ciphertext blob
  bytes  key      = 5;   // 32 bytes, AES-256-GCM (existing MediaEncryptor)
  bytes  iv       = 6;   // 12 bytes
  optional bytes  thumb       = 7;  // small inline thumbnail, size-capped (config)
  optional uint32 width       = 8;
  optional uint32 height      = 9;
  optional uint32 duration_ms = 10;
  optional string file_name   = 11;
}

message Receipt {
  ReceiptKind kind = 1;
  oneof scope { uint64 upto_seq = 2; IdList ids = 3; }   // ids: provisional rows without seq (1.2)
}
message IdList { repeated bytes client_message_ids = 1; }
enum ReceiptKind { RECEIPT_KIND_UNSPECIFIED = 0; DELIVERED = 1; READ = 2; }

message Edit     { bytes message_id = 1; uint32 edit_version = 2; string text = 3; }
message Delete   { bytes message_id = 1; }
message Reaction { bytes message_id = 1; string emoji = 2; bool remove = 3; }

message Control {
  oneof kind {
    SessionReset        session_reset         = 1;
    SafetyNumberChanged safety_number_changed = 2;
    // 3: reserved for 1:1 call signalling under ADR-0023 at 1.1 (not defined before that gate)
  }
  reserved 3;
}
message SessionReset {}
message SafetyNumberChanged { bytes user_id = 1; bytes device_id = 2; }
```

Kotlin side (sketch; one class per message, `ByteArray` fields wrapped, §9 rule E-1):

```kotlin
@Serializable class Content(
    @ProtoNumber(1) val causal: CausalHeader,
    @ProtoNumber(2) val conversation: ConversationRef,
    @ProtoNumber(3) val body: Body,
    @ProtoNumber(4) val expiresAtMs: Long? = null,
    @ProtoNumber(5) val viewOnce: Boolean? = null,
)
```

### 6.3 Field rules

1. `message_id` in `Edit`/`Delete`/`Reaction` and `reply_to` are **client message ids**; they are
   resolvable offline because `messages.id = client_message_id` (data-model V29). This depends on the
   open identity-rule decision (§3.6 reviewer note); if `messages.id` becomes server-minted, each of
   these references gains a `sender_device_id` field and is resolved by the pair.
2. A receiver rejects (drops, logs, pulls) a `Content` whose `causal.sender_device_id` differs from
   the libsignal-authenticated sender device of the session that decrypted it.
3. `device_counter` is assigned **at seal time, in dispatch order** (not at message creation: media
   sits outside the FIFO and send order follows ready order, brief §6.6), and is **kept unchanged**
   when the same `clientMessageId` is re-sealed after `MSG_DEVICE_SET_STALE`. Replay = the counter is
   already in the receiver's per-(sender device, conversation) **seen-window** (`inbound_dedup` keyed
   by `(sender_device_id, client_message_id)` plus the counter); a value below the highest seen but
   not in the window is a legitimate reorder and is accepted. A value above highest + 1 schedules a
   pull (ADR-0014); a spurious pull is harmless.
4. `oneof` support in `kotlinx-serialization-protobuf` (`@ProtoOneOf`) MUST be verified on every KMP
   target before 1.0; if it is unavailable on a target, `Body` is encoded as optional fields with a
   one-set invariant checked in the decoder. Golden vectors make the two encodings byte-identical.
5. Strings are UTF-8; the decoder rejects invalid UTF-8.
6. `RECEIPT_KIND_UNSPECIFIED` and `STORAGE_MODE_UNSPECIFIED` are invalid on receipt. An enum number
   the receiver does not know maps to `UNSPECIFIED` (§10 rule 4) and is therefore rejected here,
   because both fields are required.

### 6.4 Inner padding (all E2E envelopes)

Before sealing: `padded = serialize(Content) ‖ 0x80 ‖ 0x00 … ` up to
`max(INNER_MIN_BYTES, padme(len + 1))` *(config, companion object)*. The receiver strips trailing
`0x00`s and exactly one `0x80`; anything else is a decode error. Padmé is defined in §7.4. This
limits what ciphertext length tells the server (ADR-0019 "what the server still sees": sizes).

---

## 7. Plane C — `SealedFrame` and `TransportFrame` (IF-09, 1.2, gated)

### 7.1 Layering

```text
Content ─pad─► libsignal per-device ciphertext ─► DeviceEnvelope ─► SealedFrame   (:mobile:crypto only)
SealedFrame ─Padmé─► link AEAD (vetted handshake channel, ADR-0018) ─► TransportFrame.body
```

`SealedFrame` is the only type a `SealedLink` accepts (ADR-0016). Its constructor is `internal` to
`:mobile:crypto`; this section defines its **bytes**, not who may build it.

### 7.2 `SealedFrame` and `DeviceEnvelope`

```proto
package muhabbet.sealed.v1;

message SealedFrame {
  uint32 v = 1;                         // = 1
  Routing routing = 2;
  repeated DeviceEnvelope envelopes = 3; // 1:1 direct link: exactly the peer device's envelope
  optional BundleInfo bundle = 4;        // 1.3 custody only
}
message Routing {
  bytes conversation_id   = 1;  // bytes16 — visible only to the authenticated link peer
  bytes client_message_id = 2;  // bytes16 — lets the receiver dedup before decrypt (libsignal rejects repeats)
  bytes sender_device_id  = 3;  // bytes16
}
message DeviceEnvelope {
  bytes recipient_device_id = 1;   // bytes16
  EnvelopeProtocol protocol = 2;
  bytes ciphertext          = 3;
}
enum EnvelopeProtocol {
  ENVELOPE_PROTOCOL_UNSPECIFIED = 0; SIGNAL_PREKEY_1 = 1; SIGNAL_MESSAGE_1 = 2;
  SENDER_KEY_1 = 3; SENDER_KEY_DISTRIBUTION_1 = 4;
}
message BundleInfo {               // BPv7 concepts only (ADR-0025), no BPv7 codec
  uint32 lifetime_sec = 1;         // remaining lifetime at hand-over
  uint32 age_sec      = 2;         // accumulated holding time across custodians
}
```

The JSON `protocol` strings of §3.5 map 1:1 to `EnvelopeProtocol`. A `serverOwed` re-upload (ADR-0013)
sends the **same** ciphertext over `message.send` with the same `clientMessageId`; the server and the
recipient dedup it.

### 7.3 `TransportFrame`

```proto
package muhabbet.transport.v1;

message TransportFrame {
  uint32    v           = 1;   // = 1
  FrameType frame_type  = 2;
  bytes     frame_id    = 3;   // exactly 16 random bytes (CSPRNG); NEVER a UUID
  uint32    ttl_sec     = 4;   // remaining lifetime; ≤ 30 d (DATA), ≤ 60 s (CONTROL)
  bytes     routing_tag = 5;   // 8..16 bytes, rotating pairwise tag (brief §8.5)
  uint32    link_seq    = 6;   // per link session and direction, strictly increasing from 1
  bytes     body        = 7;   // AEAD ciphertext (incl. tag) of the padded body
}
enum FrameType { FRAME_TYPE_UNSPECIFIED = 0; DATA = 1; ACK = 2; CONTROL = 3; CREDIT = 4; }
```

| `frame_type` | Plaintext body (before padding and AEAD) | Produced by |
|---|---|---|
| `DATA` | `SealedFrame` | `:mobile:crypto` (type gate) |
| `ACK` | `HopAck { repeated bytes frame_ids = 1; }` — sent only **after** the receiver's store commit (P4) | link adapter |
| `CONTROL` | `LinkControl { oneof { Ping ping = 1; Close close = 2; MaxFrame max_frame = 3; } }` | link adapter |
| `CREDIT` | `Credit { uint32 bytes = 1; uint32 frames = 2; }` — receiver-advertised window (brief §6.9) | link adapter |

**Framing per medium** (brief §6.7): LAN — `u32be length ‖ TransportFrame`, one record ≤ 64 KiB,
offset-resume for BULK; Nearby — one `TransportFrame` per BYTES payload, BULK as FILE/STREAM
payloads whose metadata travels in a DATA frame; Meshtastic (1.4) — one frame ≤ the negotiated
single-packet size (about 200 B), no media. No generic fragmenter and no FEC.

### 7.4 Padding (Padmé)

```text
padme(L):                          // L ≥ 2
  E = floor(log2 L); S = floor(log2 E) + 1
  z = E − S; mask = (1 << z) − 1
  return (L + mask) & ~mask        // overhead ≤ ~12%, leaks O(log log L) bits
```

| Where | Padded length | Parameter |
|---|---|---|
| Inner E2E plaintext (§6.4) | `max(INNER_MIN_BYTES, padme(len + 1))` | `INNER_MIN_BYTES` *(config)* |
| `TransportFrame` body (DATA) | `max(LINK_MIN_BYTES, padme(len + lenPrefix))` | `LINK_MIN_BYTES` *(config)* |
| Meshtastic DATA | always exactly the link's `maxFrameBytes` | negotiated |
| `UplinkBundle` plaintext (§8) | the smallest bucket in `UPLINK_BUCKETS` that fits | `UPLINK_BUCKETS` *(config)*, ascending, largest = per-bundle size cap |

Body plaintext layout: `varint(len(payload)) ‖ payload ‖ 0x00…`. The receiver requires the length to
fit and every padding byte to be zero. Parameter values are fixed by the 1.2 / 1.3 crypto review and
pinned by golden vectors.

### 7.5 Associated data and receiver processing

The header fields 1–6 are associated data (ADR-0022). Because protobuf encoding is not canonical,
the AD is **rebuilt from the parsed values**, never taken from received bytes:

```text
AD = lp("mbt-tf-v1") ‖ u32be(v) ‖ u8(frame_type) ‖ frame_id[16] ‖ u32be(ttl_sec)
     ‖ lp(routing_tag) ‖ u32be(link_seq)            // lp(x) = u16be(len x) ‖ x
```

Receiver order (reject = drop the frame, count it, never answer with detail):

| Step | Check | On failure |
|---|---|---|
| 1 | Record length ≤ link `maxFrameBytes`; protobuf parse with size-bounded decoder | reject |
| 2 | `v` supported; `frame_type ≠ UNSPECIFIED`; `frame_id` is 16 bytes; `routing_tag` 8–16 bytes | reject |
| 3 | `ttl_sec > 0` and within the class bound | reject |
| 4 | `link_seq` > highest **authenticated** in this session and direction (the high-water mark is read here, **updated only after step 6 succeeds**, so a forged frame with a large `link_seq` cannot poison it) | reject (replay / reorder on a reliable medium) |
| 5 | AEAD open of `body` with AD | reject; three consecutive failures close the link |
| 6 | Unpad strictly (§7.4) | reject |
| 7 | `frame_id` not in `inbound_dedup` | ACK again (idempotent), do not re-deliver |
| 8 | DATA: decode `SealedFrame`; `routing.sender_device_id` equals the certified peer (direct link) or is a device of the peer's user (own-device custody) | reject |
| 9 | Hand to the Inbox; commit; then send `ACK` | — |

A relay or custodian **never re-serializes** a `SealedFrame` it forwards; it forwards the bytes it
received inside a new `TransportFrame` with a new `frame_id`, a decremented `ttl_sec` and a new
`link_seq`.

**Retransmission and dedup.** A hop retransmission after a lost `ACK` reuses the **same `frame_id`**
with a **new `link_seq`** (step 7 then re-ACKs without re-delivery). Because a relay assigns a new
`frame_id` per hop, `frame_id` dedup is per hop only; **end-to-end** dedup keys on
`(sender_device_id, client_message_id)` from `SealedFrame.Routing` via `inbound_dedup`.

> **Resolved (2026-11-12):** ADR-0022 plane C names both bindings: Noise — fields 1–6 are the AEAD AD
> of each frame; TLS 1.3 with a pinned RPK — the whole `TransportFrame` travels inside record
> protection, so the AD requirement is met by construction. The `link_seq` replay check is kept in
> both bindings because it covers reconnects. The spike choice itself stays at 1.2.

### 7.6 Certificate and CRL encodings (carried in `welcome`, `crl.delta`, link handshakes)

```proto
package muhabbet.pki.v1;
message DeviceCertificate {          // ADR-0018 §5
  uint32 v = 1; bytes serial = 2;    // serial: 16 random bytes
  bytes user_id = 3; bytes device_id = 4;
  bytes identity_key = 5; bytes link_key = 6;   // link-key type is fixed by v (chosen by the 1.2 spike)
  int64 not_before_ms = 7; int64 not_after_ms = 8;
  string issuer_key_id = 9;
  bytes signature = 15;              // Ed25519 over the canonical TBS (§9), not over these bytes
}
message IntermediateRecord { uint32 v = 1; string key_id = 2; bytes public_key = 3;
  int64 not_before_ms = 4; int64 not_after_ms = 5; string root_key_id = 6; bytes root_signature = 15; }
message SignedCrlDelta { uint32 v = 1; string issuer_key_id = 2; uint64 delta_id = 3;
  uint64 prev_delta_id = 4; int64 issued_at_ms = 5; repeated bytes revoked_serials = 6; bytes signature = 15; }
```

A client applies CRL deltas in `delta_id` order and fetches the missing range when
`prev_delta_id ≠` its last applied id.

---

## 8. `UplinkBundle` (IF-10, 1.3, gated)

### 8.1 Construction

```text
pt      = pad_to_bucket( UplinkPlaintext )                        // §7.4, UPLINK_BUCKETS
info    = "mbt-uplink-v1"
aad     = lp("mbt-uplink-aad-v1") ‖ u32be(v) ‖ lp(key_id) ‖ u32be(bucket)
(enc, ct) = HPKE.SealBase(pkR = uplink_keys[key_id].public_key, info, aad, pt)
```

- HPKE is RFC 9180, base mode. Proposed suite: DHKEM(X25519, HKDF-SHA256) / HKDF-SHA256 /
  ChaCha20-Poly1305. The library is a 1.3 crypto-review selection (P12, ADR-0025 note); the suite is
  pinned by `uplink_keys` columns and golden vectors.
- The brief's `aad = bucketSize`; this ICD also binds `v` and `key_id` into the AAD so a bundle
  cannot be replayed under another key or version. That is a strict extension of the brief.

### 8.2 Wire objects

```proto
package muhabbet.uplink.v1;

message UplinkSubmission {          // HTTPS body, Content-Type application/x-protobuf
  uint32 v = 1;                     // = 1
  string key_id = 2;                // current or next pinned uplink key
  uint32 bucket = 3;                // padded plaintext length; must be in UPLINK_BUCKETS
  bytes  enc = 4;                   // HPKE encapsulated key
  bytes  ct = 5;                    // HPKE ciphertext (bucket + AEAD tag)
}

message UplinkPlaintext {
  SenderProof sender = 1;
  muhabbet.sealed.v1.Routing routing = 2;          // conversation, client_message_id, sender device
  repeated muhabbet.sealed.v1.DeviceEnvelope envelopes = 3;
  uint32 lifetime_sec = 4;
}
message SenderProof {
  bytes  sender_device_id = 1;
  bytes  cert_serial      = 2;      // the sender's current device certificate
  bytes  signature        = 15;     // over lp("mbt-uplink-sig-v1") ‖ canonical(routing) ‖ H(canonical(envelopes))
}
```

### 8.3 Endpoints

| Endpoint | Auth | Request | Response |
|---|---|---|---|
| `GET /api/v1/uplink/keys` | bearer | — | `{data:{keys:[{keyId, publicKeyB64, kemId, kdfId, aeadId, notBefore, notAfter}]}}` (current and next); the app also pins them with a rotation list |
| `POST /api/v1/uplink` | bearer (**the mule's** account) | `UplinkSubmission` | 202 `{data:{accepted:true}}` — identical for a first submission and a replay |

### 8.4 Server processing and mule rules

| Step | Server | Failure |
|---|---|---|
| 1 | Mule quota (per mule per day) and size cap; `bucket ∈ UPLINK_BUCKETS`; `len(ct) = bucket + tag` | 429 `UPLINK_QUOTA_EXCEEDED` / 413 / 400 |
| 2 | `key_id` known and within validity | 422 `UPLINK_REJECTED` |
| 3 | HPKE open; strict unpad | 422 `UPLINK_REJECTED` |
| 4 | Sender certificate valid (not revoked, not expired); signature verifies; sender device's account not banned; sender quota | 422 / 429 |
| 5 | Ingest through `SendMessageUseCase` with `(sender_device_id, client_message_id)` — the normal idempotent path (ADR-0013) | as §3.6 |
| 6 | Respond 202 without revealing recipient, `seq` or whether it was a replay | — |

Mule: deletes the blob on any 2xx or on 400/413/422; keeps and retries with backoff on 429/5xx; deletes
after 24 h regardless (ADR-0025). The mule cannot read, route or tell bundles apart beyond size bucket
and time.

> **Open item [OI-03](open-items.md#oi-03):** field and label `mbt-uplink-sig-v1` reserved; key and scheme (signature vs. a deniable MAC) are chosen by the 1.3 crypto review as an ADR-0018 amendment.

---

## 9. Canonical encodings for anything signed or hashed

| Rule | Statement | Finding |
|---|---|---|
| E-1 | Every `ByteArray` field is wrapped in a value class with content equality and a redacted `toString` | GEM-16 |
| E-2 | Signatures and hashes are never computed over protobuf or JSON bytes. Inputs are `lp(label) ‖ lp(field₁) ‖ …` with fixed field order, `u16be` lengths for labels/keys, `u32be` for blobs, `u64be` for integers | GEM-13 |
| E-3 | Every label is ASCII `mbt-<purpose>-v<n>` and registered below; a label is never reused for a second purpose | GEM-13 |
| E-4 | A verifier rebuilds the canonical input from parsed values; it never verifies over received bytes | — |

| Label | Purpose | Defined in |
|---|---|---|
| `mbt-tf-v1` | `TransportFrame` AD | §7.5 |
| `mbt-devcert-v1` | Device certificate TBS | ADR-0018 |
| `mbt-linkkey-v1` | Link-key identity signature | ADR-0018 |
| `mbt-crl-v1` | CRL delta signature | §7.6 |
| `mbt-intermediate-v1` | Intermediate record root signature | §7.6 |
| `mbt-prox-v1` | Rotating proximity tag (HMAC input) | brief §8.5 |
| `mbt-uplink-v1` | HPKE `info` | §8.1 |
| `mbt-uplink-aad-v1` | HPKE AAD | §8.1 |
| `mbt-uplink-sig-v1` | Uplink sender signature (key open) | §8.2 |

---

## 10. Versioning and compatibility rules

1. **JSON plane, additive change** (new optional field): allowed within a version. Receivers use
   `ignoreUnknownKeys = true`; senders MUST NOT require the field from older peers. Because
   `encodeDefaults = true`, a new defaulted field changes golden vectors — update them in the same PR.
2. **JSON plane, new frame type**: allowed within a version only behind a capability token (Rule C-1).
3. **JSON plane, rename/remove/retype**: never within a version; requires an ADR and a version bump.
4. **Protobuf**: field numbers are never reused; removed fields become `reserved`; every enum has a
   `…_UNSPECIFIED = 0`; unknown fields are skipped on decode. **Enums:** `kotlinx-serialization-protobuf`
   throws `ProtobufDecodingException` ("… is not among valid … enum proto numbers") on an unknown enum
   number (`ProtobufDecoding.kt`, `findIndexByTagSlowPath`:
   <https://raw.githubusercontent.com/Kotlin/kotlinx.serialization/master/formats/protobuf/commonMain/src/kotlinx/serialization/protobuf/internal/ProtobufDecoding.kt>),
   which would make every older decoder — including relays forwarding opaquely — reject the whole
   frame when a value is added. Therefore every enum-typed field (`FrameType`, `EnvelopeProtocol`,
   `ReceiptKind`, `StorageMode`, `LinkControl` kinds, …) is declared as `uint32` in the Kotlin
   `@Serializable` class (wire-identical varint) and mapped to the Kotlin enum by our own accessor;
   an unmapped value becomes `UNSPECIFIED`/`Unknown`, and the message is rejected only where the
   field is required. The `.proto` listings keep the enum names for documentation.
5. **Forwarded bytes are opaque**: a relay never re-serializes a frame or envelope (§7.5), so unknown
   fields survive custody.
6. **`v` fields** inside `SealedFrame`, `TransportFrame`, `UplinkSubmission`, certificates and CRL
   deltas version their own message independently of `protocolVersion`; a receiver rejects a `v` it
   does not know. Bumps by ADR.
7. **Support window**: Rules N-1 and N-2 (§2.3). Removal of a version is announced one release
   ahead and enforced with `PROTOCOL_TOO_OLD` + close 4426.
8. **Locally persisted wire data** (outbox `OutgoingEnvelope`, custody blobs) carries its own
   version; a client MUST decode every version that can still be in its outbox (≤ 30-day lifetime).
9. **Error codes** are append-only; a client treats an unknown `errorCode` as a retryable generic
   failure, never as success (ApiClient lesson, #374).

---

## 11. Golden vectors

| # | Requirement |
|---|---|
| G-1 | Every frame and message in §3 (each `type`, each shape, legacy and v1/v2), §4 (`MessageChange` variants incl. tombstone and each `envelopeState`), §5 (wake data map), §6, §7, §8 has at least one **positive** vector |
| G-2 | Negative vectors: unknown `type` → `Unknown`; unknown JSON field ignored; unknown protobuf field skipped; `UNSPECIFIED` enum rejected; each enum field with value max + 1 decodes to `UNSPECIFIED` **without throwing** (and is rejected only where required); bad padding rejected; truncated AEAD body rejected; `frame_id` of 15/17 bytes rejected; tampered header field (each of 1–6) fails AEAD |
| G-3 | Vectors live once in `:shared` (e.g. `shared/src/commonTest/golden/<plane>/<name>.{json,hex}` plus an expectation file) and are executed by `:shared:jvmTest`, the backend test suite (reading the same files) and, when present, the iOS native test target |
| G-4 | JSON vectors assert both `decode(bytes) == expected` and `encode(expected) == bytes` with the shared `Json` configuration; protobuf vectors assert byte-exact encoding |
| G-5 | Crypto vectors (AEAD, HPKE, signatures, Padmé, proximity tags) use fixed test keys and nonces from `:mobile:crypto-testkit` and run against every adapter (Android, iOS, fake) through the commonTest contract suite (ADR-0017) |
| G-6 | Changing an existing vector's bytes for an already-released `v`/`protocolVersion` fails review unless an ADR authorises it; new vectors are added freely |
| G-7 | CI runs them on every PR that touches `shared/**/protocol/**`, `:mobile:crypto` or the backend WS/REST adapters (KMP-03) |

## 12. Fuzzing

| # | Requirement |
|---|---|
| F-1 | One fuzz target per parser: JSON `WsMessage` (server and client decoders), `MessageChange`, `Content`, `SealedFrame`, `TransportFrame` + unpad, `HopAck`/`LinkControl`/`Credit`, `DeviceCertificate`, `SignedCrlDelta`, `UplinkSubmission`/`UplinkPlaintext`, Padmé unpad |
| F-2 | JVM targets use a coverage-guided JUnit-integrated fuzzer (e.g. Jazzer); corpora are seeded from the golden vectors |
| F-3 | Oracle: the parser either returns a value or throws the declared decode exception; any other throwable, a hang, or an allocation above the declared bound is a failure |
| F-4 | Every decoder enforces bounds **before** allocating: record length, repeated-field counts, string and bytes lengths, nesting depth (`MAX_*` constants) |
| F-5 | Accepted inputs must round-trip: `decode(encode(decode(x))) == decode(x)` |
| F-6 | A short time-boxed run on PRs that touch a parser; longer scheduled runs only once CI is off the production host (ADR-0031, R19) |
| F-7 | Every crash found becomes a negative golden vector |

---

## 13. Verification

| Req | Check | Method | When |
|---|---|---|---|
| RQ-09 | No token in any request URL; session closes at `exp` and within 5 s of revoke (4401/4403) | Integration test + access-log inspection | 0.4 |
| — | No frame accepted before `hello`; `PROTOCOL_TOO_OLD` below `minServer`; legacy sessions still served until cut-over | Integration tests | 0.5 |
| — | `Unknown` handling on both sides (§3.13) | Unit tests | 0.5 |
| RQ-02 | Duplicate `message.send` replays `{seq, serverTs}` with `replay = true` and allocates no seq (head unchanged); concurrent duplicate retries leave `last_seq` gapless; pre-V29 row retried by its sender replays (A-2b); legacy duplicate → `OK` with original id (A-2c); foreign `messages.id` collision → `MSG_ID_CONFLICT` | Testcontainers | 0.4 |
| RQ-03 | Paged pull where an old message (low `seq`) was edited (high `change_seq`, later page) never sends `ack uptoSeq` covering an unstored seq; DELIVERED is sent only at `C`; from 1.0 the envelope is not purged before the device stores it (R-ACK) | Testcontainers + `SimulatedLink` | 0.5 / 1.0 |
| — | Wake push not sent to the sending device nor to a user focused on the conversation (#618 preserved) | Integration test | 0.5 |
| — | Legacy-shaped frames from the server decode with today's (pre-§3.15) `WsMessage` types | Golden vectors | 0.5 |
| — | Ack from a non-member, for a foreign seq, or non-monotonic → `MSG_ACK_INVALID`, nothing broadcast; legacy single-message acks never widened | Testcontainers | 0.4 |
| RQ-04, RQ-05 | `/changes` never skips a change under 50 concurrent writers (§4.5); 100% hint loss still converges | Testcontainers; `SimulatedLink` | 0.4 / 0.5 |
| — | Unreleased scheduled messages absent from `/changes` for every requester | Integration test | 0.4 |
| RQ-07 | Captured FCM payload has exactly the four data keys and no `notification` | Payload capture (owner) + adapter unit test | 0.5 |
| RQ-13 | E2E items carry only the requesting device's envelope; `NOT_ADDRESSED`/`PURGED` states correct | Testcontainers | 1.0 |
| RQ-06 | Only `SealedFrame` reaches a `SealedLink`; DATA bodies decode to `SealedFrame` | Compile + arch rule + vectors | 1.0 / 1.2 |
| RQ-17 | No stable or timestamp-bearing identifier in any `TransportFrame` header across epochs | Vectors + over-the-air capture (owner, P13) | 1.2 exit |
| — | Tampering any header field fails AEAD; strict unpad | Vectors (G-2) | 1.2 |
| — | Uplink: replay returns the same 202 and creates one row; foreign key id/version in AAD fails | Integration test | 1.3 |
| — | All parsers fuzzed without crash or unbounded allocation | Fuzz CI job | 0.5 onward |

---

## 14. Open items and reviewer notes (summary)

| # | Item | Where | Status |
|---|---|---|---|
| 1 | Sender-own-device acks: custody/watermark, not ticks | §3.8 (from ADR-0013) | Resolved 2026-11-12 (ADR-0013 split) |
| 2 | `security.key_changed` already exists; extend, not add | §3.11 | Resolved: extended (brief erratum E-25) |
| 3 | `heads` and `receipts` REST endpoints added to satisfy P6 | §4.4 | Resolved: adopted (brief erratum E-26) |
| 4 | AD binding if the 1.2 spike chooses TLS 1.3 | §7.5 | Resolved: both bindings named in ADR-0022 |
| 5 | Uplink signing key | §8.4 (from ADR-0022/0025) | Deferred to 1.3 crypto review ([OI-03](open-items.md#oi-03)) |
| 6 | Uplink AAD extended with `v` and `key_id` | §8.1 | Strict extension of the brief; crypto review to confirm |
| 7 | Padding parameters (`INNER_MIN_BYTES`, `LINK_MIN_BYTES`, `UPLINK_BUCKETS`) | §6.4, §7.4 | Set by crypto review; pinned by vectors |
| 8 | `@ProtoOneOf` availability on all targets | §6.3 rule 4 | Verify before 1.0 |
| 9 | Exact cut-over release for legacy (no-`hello`, query-token) clients | §2.1, §3.1 | Chosen when 0.4 is planned (ADR-0022) |
| 10 | Wake `data` key `v` added beyond the brief / ADR-0019 key set | §5.2 W-2 | ICD extension; owner to confirm |
| 11 | `messages.id = client_message_id` kept beside the brief's device-scoped key (deviation from brief §11.1 V29); squat path from 1.2 | §3.6 | Deferred to 1.0 entry ([OI-10](open-items.md#oi-10)) |
| 12 | Scheduled send in E2E conversations (server-held envelopes vs. unavailable) | §3.5 | Owner decision before 1.0 entry ([OI-12](open-items.md#oi-12)); ICD assumes (b) |
| 13 | ADR-0021 `envelopeExpired` ↔ ICD `PURGED` naming | §4.1 | Align ADR-0021 |

## 15. Rollout, rollback and risks

| Concern | Where |
|---|---|
| Version negotiation and support window (rollback of either side by one release) | §2.3 rules N-1, N-2; §10 rule 7 |
| Mixed client/server versions during the transition | §2.4 matrix (incl. the 0.5-client-on-0.4-server fallback trigger) |
| Shared type evolution without breaking old decoders | §3.15; §10 rules 1–4 |
| Schema behind these interfaces (expand → contract, N-1-on-N) | [`data-model.md`](data-model.md) §2.1 rules 3, 4, 8 and §9 |
| Wake-only push latency | R11 (risk register); §5.3 OEM fallback |
| Hot `last_seq` row from hints and allocations | R12; data-model §3.1 |
| Provisional off-server ordering | R16; §6.2 `expected_seq_hint`, data-model §5.3 S-5 |
| Fuzzing and long CI runs on the production host | R19; §12 F-6 |
