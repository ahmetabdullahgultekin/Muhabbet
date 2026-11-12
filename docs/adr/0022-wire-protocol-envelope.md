# ADR 0022 — Wire protocol: versioned envelope, authenticated outer header, protobuf, WS hello

**Status:** Proposed — pending owner approval; no code may be written against this ADR until approval is recorded in [`docs/architecture/multi-transport/APPROVAL.md`](../architecture/multi-transport/APPROVAL.md). Plane A binds from 0.4 for authentication (JWT out of the query string, BE-12) and from 0.5 for the handshake and frame extensions; plane B from 1.0; plane C and `UplinkBundle` are **Proposed (gated)** with 1.2 / 1.3 · **Date:** 2026-11-12 · **Source:** [decision brief §3, ADR-0022; §6.7; §8.5; §10](../architecture/multi-transport/decision-brief-2026-11-12.md) · Depends on ADR-0013, ADR-0014, ADR-0016, ADR-0017, ADR-0018, ADR-0028. Amends the WebSocket section of [`docs/api-contract.md`](../api-contract.md) on acceptance. Supersedes nothing.

## Context

- **The WS protocol has no version and no negotiation**; unknown frame types are dropped with a log
  line (KMP-11). Once old clients coexist with new servers — and, from 1.2, relay each other's frames
  — an unversioned format cannot evolve safely.
- **The backend duplicates 17 shared DTOs** as non-`@Serializable` classes: two serializers, one
  contract (KMP-07). A wire format with two implementations will drift.
- **The JWT travels in the query string** (`wss://host/ws?token={jwt}`, `docs/api-contract.md:9,460`)
  and the socket is authenticated only at connect, so sessions outlive expiry, logout and revoke
  (BE-12). Query strings end up in proxy and access logs.
- **Acks and new-message frames carry no `seq`** (BE-06, ADR-0014), and a retried send gets an error
  instead of the original ack (BE-07).
- **Gemini's frame** ("MU" sync word, CRC-32C, per-chunk Reed-Solomon) has no addressing, no TTL and
  no authenticated header, so headers can be forged and DTN cannot work (GEM-08, critical); per-chunk
  RS is the wrong layer (GEM-07, C12); the bytes on the wire carry no header to reassemble from
  (MISS-03, Gemini verifier).
- **UUIDv7 leaks creation time** to anyone who sees it; acceptable inside E2E, not on an outer header
  a carrier or mule can read.
- `ByteArray` in data classes breaks equality (GEM-16); concatenation without length prefixes lets
  different structures hash identically (GEM-13); MTUs are not constants (MISS-03, proximity
  verifier: 512 B is the ATT limit, not the L2CAP CoC SDU).

## Decision

### Three planes, one home

All schemas live in `:shared` under `protocol/` (MIT, ADR-0017), with **golden vectors** run in the
backend tests, `:shared:jvmTest` and mobile tests (and iOS native when present).

| Plane | Carries | Encoding | Where it travels | From |
|---|---|---|---|---|
| **A. Server control plane** (`ServerFrame`) | Session, send/ack, hints, receipts, key and CRL events | JSON (`kotlinx.serialization`), polymorphic `WsMessage` | TLS WS / REST to our server only | 0.5 (v1 extended); v2 at 1.0 |
| **B. Inner `Content`** | Everything a user means: text, media pointer, receipts, edits, reactions, control | **protobuf** (`kotlinx-serialization-protobuf`, explicit `@ProtoNumber`) | Inside libsignal ciphertext only | 1.0 |
| **C. Outer `TransportFrame`** | Sealed off-server hops and the uplink | **protobuf**; header authenticated as AEAD associated data | LAN, Nearby, uplink, RF | 1.2 (gated) |

### A. Server control plane

**Handshake** (first frame; no other frame is accepted before it):

| Direction | Frame | Fields |
|---|---|---|
| C→S | `hello` | `protocolVersions: [1,2]`, `minSupported`, `capabilities: ["seq","wake","sealed"]`, `deviceId`, auth (bearer token in the first frame, or the `Sec-WebSocket-Protocol`/`Authorization` header) |
| S→C | `welcome` | `protocolVersion` (highest common), `serverTime`, `certRenewal?` (1.2, ADR-0018), `crlDelta?` (1.2) |
| S→C | `error{PROTOCOL_TOO_OLD}` then close | client below the server's minimum (replaces the `E2EConfig` flags as the server-side floor, ADR-0016) |

**The JWT never appears in the query string** (from 0.4, with BE-12: header or first-frame auth;
from 0.5 the first frame is `hello` and carries it). The session is bound to `(deviceId, jti, exp)` in
`WsSessionRegistry`; the server closes it at `exp`, logout, device revoke and account deletion
(BE-12, `SessionRevocationPort`), and the client re-authenticates.

**Frames** (type discriminators are the JSON `type` strings; existing ones keep their names):

| Type | Dir | Change | Fields (new in **bold**) |
|---|---|---|---|
| `message.send` | C→S | extended | `clientMessageId`, `conversationId`, **`encryption: "none" \| "e2e/v1"`**, `content?` \| **`envelopes: [{recipientDeviceId, protocol, ciphertextB64}]`**, `clientTs` |
| `ack` (ServerAck) | S→C | extended | `clientMessageId`, **`seq`**, `serverTs`, **`replay`** — a duplicate submit replays the original ack (BE-07, ADR-0013) |
| `message.new` | S→C | extended | `messageId`, `conversationId`, **`senderDeviceId`**, **`seq`**, **`changeSeq`**, `content?` \| **`envelope`** (the one addressed to this device) |
| `message.ack` | C→S | changed | **`conversationId`, `uptoSeq`, `kind: DELIVERED \| READ`** (per device, ADR-0013) |
| `sync.hint` | S→C | new | `conversationId`, `seq` |
| `security.key_changed` | S→C | new (0.6) | `userId`, `deviceId` (ADR-0018 `IdentityKeyChanged`) |
| `crl.delta` | S→C | new (1.2) | signed delta (ADR-0018) |
| `error` | S→C | extended | `code` incl. **`UNSUPPORTED_FRAME`**, **`PROTOCOL_TOO_OLD`** |

**REST read:** `GET /api/v1/conversations/{id}/changes?afterSeq&limit` → `{items: [MessageChange], lastSeq, hasMore}` (ADR-0014, ADR-0021).

**Unknown frames.** Decoding goes through a `polymorphicDefaultDeserializer` to `Unknown(type)`.
The **server** answers an unknown client frame with `error{UNSUPPORTED_FRAME}`; the **client**
ignores an unknown server frame (logs it, never replies), so a newer server cannot make an older
client chatty.

```kotlin
// :shared protocol/ (sketch)
@Serializable @SerialName("hello")
data class Hello(val protocolVersions: List<Int>, val minSupported: Int,
                 val capabilities: Set<String>, val deviceId: String) : WsMessage
data class Unknown(val type: String) : WsMessage   // produced by the default deserializer only
```

**Prerequisite:** the 17 duplicated backend DTOs are replaced by the shared ones first (KMP-07),
with a backend contract test decoding each controller response with the shared type.

### B. Inner `Content` (inside libsignal ciphertext, protocolVersion 2)

```text
Content { 1 causal:  CausalHeader{ senderDeviceId, deviceCounter:uint64,
                                    clientMessageId:bytes16 (UUIDv7), senderTimeMs }
          2 conversationRef{ conversationId, storageMode, expectedSeqHint? }
          3 body: oneof Text{text, mentions, replyTo?}
                      | MediaPointer{blobId, mime, size, sha256, key, iv, thumb?}
                      | Receipt{kind, uptoSeq | ids}
                      | Edit{messageId, editVersion, text} | Delete{messageId}
                      | Reaction{messageId, emoji}
                      | Control{sessionReset | safetyNumberChanged | callOffer …}
          4 expiresAt?   5 viewOnce? }
```

- UUIDv7 and `senderTimeMs` are allowed **here**, because only the recipient devices see them.
- `CausalHeader` semantics (counter gaps trigger a pull, replayed counters are dropped, sender time
  clamped for display) are ADR-0014's. Link previews are generated by the sender and travel inside
  `Text` (ADR-0019).
- Field numbers are never reused; removed fields are `reserved`.

### C. Outer `TransportFrame` (sealed off-server hops; 1.2, gated)

```text
TransportFrame { 1 v:uint32 = 1
                 2 frameType: DATA | ACK | CONTROL | CREDIT
                 3 frameId: bytes16      // random, NEVER UUIDv7
                 4 ttlSec: uint32        // ≤ outbox lifetime (30 d); CONTROL 60 s
                 5 routingTag: bytes8..16 // rotating pairwise tag (brief §8.5)
                 6 linkSeq: uint32
                 7 body: bytes }          // Padmé-padded SealedFrame (ADR-0016)
```

| Property | Rule |
|---|---|
| Integrity | The link channel (ADR-0018 `LinkAuthenticator`) AEAD-encrypts `body`; fields 1–6 are associated data. **No sync word, no CRC** — the AEAD tag is the integrity check |
| Addressing | Server hop: `deviceId` over TLS. Peer hop: rotating `routingTag`. Inside the envelope: `recipientDeviceId` |
| Identifiers | `frameId` is 16 random bytes; no stable or timestamp-bearing identifier outside TLS-to-server (P9) |
| Padding | Body padded to buckets (Padmé), so size reveals only the bucket |
| Flow control | `CREDIT` frames (brief §6.9) |
| Fragmentation | Delegated to the medium: LAN length-prefixed records ≤ 64 KiB; Nearby BYTES/FILE/STREAM payloads; Meshtastic one frame ≤ 200 B (brief §6.7). No generic fragmenter, no FEC |
| MTU | Negotiated per link at runtime, never a constant |

### `UplinkBundle` (1.3, gated; mechanism owned by ADR-0025)

`HPKE-Seal(serverUplinkPk, info = "mbt-uplink-v1", aad = bucketSize, pt = senderDeviceSignature ‖ routing ‖ DeviceEnvelopes)`,
POSTed over HTTPS to `/api/v1/uplink` by the mule. HPKE comes from a vetted library (P12); the
server unseals, verifies the sender-device signature, and ingests through the normal idempotent send
path.

### Cross-cutting encoding rules

1. Every `ByteArray` field is wrapped in a value class with content equality (GEM-16).
2. Anything hashed or signed uses a **length-prefixed, domain-separated** encoding with a
   `"mbt-<purpose>-v<n>"` label (GEM-13).
3. MTU is negotiated, never constant.
4. **Golden vectors** for every JSON and protobuf frame, run on the JVM, the backend and iOS native.
5. **Fuzz targets** for every parser (JSON `WsMessage`, `Content`, `TransportFrame`,
   `DeviceCertificate`, `UplinkBundle`).
6. **Protocol version bumps happen only by ADR.** v1 = today's JSON plus the 0.5 extensions; v2 =
   sealed protobuf bodies for E2E conversations (1.0).

## Why

- One schema across transports, compact enough for BLE and LoRa.
- Versioned and negotiated, because old clients will coexist with new servers and, later, relay
  each other's frames.
- GEM-08: an outer header must be authenticated and carry a TTL; an AEAD with the header as
  associated data does both and makes CRC redundant.
- Random outer ids because UUIDv7 leaks creation time to carriers.
- **Protobuf over CBOR:** first-class `kotlinx` support on every KMP target, evolution by field
  number, compactness, and golden-vector tooling shared with the backend. CBOR/COSE is viable but
  adds a second schema discipline for no current gain. All three proposals agreed.
- JSON stays on the server control plane because it already works, is debuggable, and is not
  bandwidth-constrained.

## Consequences

- JSON remains for `ServerFrame` until 1.0; E2E conversations carry sealed protobuf bodies from 1.0
  (protocolVersion 2). Non-E2E server traffic stays JSON.
- `docs/api-contract.md` (WS URL, auth, frame list, `lastSync` query) and CLAUDE.md's "WsMessage
  type discriminators" list are updated when this ADR is implemented; external clients
  (`infra/scripts/test_bot.py`) must move to first-frame auth.
- From 0.5 every client must send `hello`. Clients older than the 0.4 auth change can no longer
  connect once the server stops accepting query-string tokens; the cut-over release is chosen when
  0.4 is planned and announced as a forced update.
- Parser fuzz targets and golden vectors become required CI steps (KMP-03).
- Version bumps require an ADR, which slows protocol change deliberately.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Gemini "MU" sync word + CRC-32C + per-chunk Reed-Solomon | No authenticated header, no TTL, wrong FEC layer (GEM-08, GEM-07, C12) |
| Full RFC 9171 BPv7 CBOR with BPSec | Concepts borrowed (lifetime, age, status reports), codec not; too heavy for one engineer |
| JSON everywhere | Too large for BLE/LoRa; no field-number evolution |
| A per-transport format | N formats, N parsers, N fuzz targets |
| UUIDv7 on the outer header | Leaks creation time |
| Keep the JWT in the query string | Logged by proxies; BE-12 |

## Evidence

- Findings: KMP-11, KMP-07, KMP-03, BE-06, BE-07, BE-12, BE-16, GEM-07, GEM-08, GEM-13, GEM-16,
  C6, C7, C12, MISS-03 (Gemini verifier: no header on the wire), MISS-03 (proximity verifier: MTU).
- Code/docs: `docs/api-contract.md:9,460,522`, `shared/src/commonMain/kotlin/com/muhabbet/shared/protocol/`.
- External (from the crypto digest): https://www.rfc-editor.org/rfc/rfc9171.html (BPv7, concepts
  only), https://signal.org/blog/sealed-sender/.

## Verification

| Req | Check | Method | When |
|---|---|---|---|
| — | Golden vectors for every frame decode identically on JVM, backend and (when present) iOS | CI | 0.5 (plane A) / 1.0 (B) / 1.2 (C) |
| — | Unknown client frame → `UNSUPPORTED_FRAME`; unknown server frame ignored by the client | Unit tests both sides | 0.5 |
| — | No frame accepted before `hello`; client below `minSupported` gets `PROTOCOL_TOO_OLD` | Integration test | 0.5 |
| RQ-09 | Session closes at JWT `exp` and within 5 s of revoke; no token in any request URL | Integration test + access-log inspection | 0.4 / 0.5 |
| — | Every backend controller response decodes with the shared DTO | Backend contract test | 0.5 |
| RQ-17 | No stable or timestamp-bearing identifier in any `TransportFrame` | Golden vectors + over-the-air capture (owner) | 1.2 exit |
| — | A tampered header field 1–6 fails AEAD verification | Unit test | 1.2 |
| — | Parsers survive fuzzing without crash or unbounded allocation | Fuzz CI job | 0.5 onward |

> **Reviewer note (open):** the brief's `UplinkBundle` carries a `senderDeviceSignature` but does not
> say **which key** produces it. The device identity key is the natural candidate (it is what the
> server already binds to the device), but it must then be used with a dedicated domain label and
> only over the bundle's *ciphertext* envelopes, so the signature cannot be presented as proof of
> authorship of any plaintext (brief §8.3: no long-term signatures on messages). The alternative is a
> dedicated uplink-signing key in the device certificate. ADR-0025 should fix the choice before 1.3;
> this ADR only reserves the field.
