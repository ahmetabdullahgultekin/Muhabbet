# ADR 0019 — Push is wake-only from 0.5; the server stops being plaintext-dependent for E2E conversations at 1.0

**Status:** Proposed — pending owner approval; no code may be written against this ADR until approval is recorded in [`docs/architecture/multi-transport/APPROVAL.md`](../architecture/multi-transport/APPROVAL.md) · **Date:** 2026-11-12 · **Source:** [decision brief §3, ADR-0019; ADR-0013 consequences; §9; §13 (0.5.0, 1.0.0); §17 Q-L6](../architecture/multi-transport/decision-brief-2026-11-12.md) · Depends on ADR-0011, ADR-0013, ADR-0014, ADR-0016, ADR-0021. Supersedes nothing. Keeps the #377 privacy enforcement server-side.

## Context

The server today depends on reading message content in four places (BE-15, verified):

| Dependency | Where | Effect |
|---|---|---|
| Content stored in clear | `V1__initial_schema.sql:125` `content TEXT NOT NULL -- plaintext MVP` | Server holds readable history |
| Push carries content | `PushNotificationComposer.kt:117` `message.content.take(MAX_BODY_LENGTH)` (100 chars); `FcmPushNotificationAdapter.kt:48` `.setBody(notification.body)` | Up to 100 characters of every message go to Google FCM as a visible notification payload |
| Server search | `SearchController`, `SpringDataMessageRepository.searchInConversation/searchGlobal` | Requires readable content |
| Server link preview | `LinkPreviewController` | Requires readable URLs |

Content in a push payload is a transfer of message content to a foreign processor. KVKK Art. 9 does
not allow consent as the systematic basis for such transfers (LEG-04; the repo's own legal draft
misuses consent). Removing content from push needs **no cryptography** — so it does not have to
wait for 1.0. The other three dependencies are incompatible with E2E and must end for E2E
conversations when E2E starts.

At the same time, the 2026-08 work made two privacy settings **server-enforced** (#377):
`readReceiptsEnabled` (READ downgraded to DELIVERED on publish, in both WS broadcast and REST
aggregate) and `aboutVisibility`. A naive "server sees nothing" design would move receipt handling
client-side and silently weaken that guarantee (target-state proposal; security judge objection).

## Decision

### 1. Wake-only push from 0.5 (crypto-independent)

| Item | Rule |
|---|---|
| Payload | **Data-only** message: `{ "type": "wake", "conversationId": "<uuid>", "seq": <long> }`. No `notification` block, no title, no body, no sender name |
| Priority | High priority only for wakes that will produce a user-visible notification (new message from another user). Normal priority for receipt, edit, delete and sync hints. Collapse key per conversation |
| Client | `FcmHintSource` (an `InboundSource`, ADR-0012) emits `Hint(conv, seq)` → `SyncCoordinator` pulls `/changes?afterSeq=` over TLS → commits to the DB (ADR-0013 P4) → **composes the notification text locally** from the DB |
| Server port | `WakePushPort.wake(device, hint, collapseKey): PushResult`, implemented by `FcmWakePush` (later `ApnsWakePush`). `PushNotificationComposer`'s body composition is deleted, not flag-gated |
| Pull is truth | A lost or throttled wake is repaired by the next pull (P6); push never carries anything the client cannot fetch |

**OEM-throttling fallback** (aggressive battery managers delay or drop data messages):

1. Pull on app foreground.
2. WorkManager periodic sync driven by the server `seq` watermark, not the device clock (MOB-10).
3. An in-app notice offering battery-optimisation exemption, shown only on known aggressive OEMs.
4. iOS (when the app exists, KMP-01): a Notification Service Extension that fetches and decrypts
   before display; BGAppRefresh as backstop.

### 2. Server plaintext independence at 1.0, for E2E conversations

| Server function | Before 1.0 | From 1.0, E2E conversations | Replacement |
|---|---|---|---|
| Content storage | `messages.content` plaintext | `messages.content` is NULL; ciphertext lives only in `message_envelopes` per recipient device (ADR-0021) | — |
| Search | `searchInConversation`/`searchGlobal` | Not offered | On-device FTS inside the encrypted DB (ADR-0020) |
| Link previews | `LinkPreviewController` fetches | Not offered | Sender's device generates the preview and embeds it **inside** the encrypted content |
| Media | Server may read blobs | Server stores ciphertext blobs only; no thumbnailing or reads | `MediaEncryptor` (AES-256-GCM) with the key inside the sealed body (brief §8.3) |
| Push | Wake-only (from 0.5) | Wake-only | — |

Channels and communities remain **server-plaintext by design** and are labelled as such in the UI
(ADR-0016 wording rule); the table above does not apply to them. Groups use sender keys (brief
§8.3). Which group sizes are E2E at 1.0 follows the brief ("1:1 and small groups"). The off-server
cap separately limits which paths groups may use.

### 3. What the server still sees (explicit metadata list, from 1.0)

| Metadata | Why it is retained | Who enforces against it |
|---|---|---|
| Sender device, recipient devices | Routing, fan-out, block enforcement, 5651 attribution | Server |
| `seq`, `change_seq`, server timestamps | Ordering authority (ADR-0014) | Server |
| Ciphertext sizes | Storage, quotas | Server |
| Delivery and read acks per device | Mailbox deletion on `ack uptoSeq` (ADR-0021) and tick aggregation (ADR-0013) | Server |
| Profile fields (`about`, avatar), presence | Profile data, not message content | Server |

Consequences for #377 settings:

- **`readReceiptsEnabled` stays server-enforced.** Receipts are server-visible metadata at 1.0, and
  the server already has to see them to delete mailbox rows, so the publish-time READ→DELIVERED
  downgrade in `MessageService` (WS) and `resolveDeliveryStatuses` (REST) keeps working unchanged.
- **`aboutVisibility` stays server-enforced** in `UserController.resolveVisibility`; it is profile
  data, not message content.
- This is a deliberate, written trade: moving receipt enforcement client-side would make the
  guarantee depend on the *sender's* client honouring the reader's setting.

## Why

- BE-15 and LEG-04: plaintext in FCM is a content transfer abroad on a basis the law does not
  support systematically; removing it costs no cryptography, so both the security and feasibility
  judges required it before 1.0.
- Pull-by-seq (ADR-0014) already makes every hint optional, so a content-free hint loses no
  delivery guarantee — only notification latency, which is measured.
- E2E with server-side search, previews or content push is not E2E; the replacements exist on the
  client side and are scheduled before the cutover.
- Keeping receipts as server metadata preserves a guarantee users were just given, and is honest:
  the metadata list above is what the transparency text will say.

## Consequences

- Notification latency now includes a TLS fetch. It is **measured** on emulator-5554 and on a
  Xiaomi/Oppo-class device (owner, P13); R11 tracks a p90 bound to be agreed.
- The KVKK *aydınlatma metni* is updated: FCM/APNs receive content-free wake hints; the remaining
  Art. 9 basis for those hints is a counsel question (Q-L6).
- Server search for 1:1 and small groups ends at 1.0, so on-device FTS (ADR-0020) must exist first.
- 5651 reporting for E2E conversations relies on reporter-supplied content (ADR-0021; R18).
- `docs/api-contract.md` push section and the notification code paths are rewritten at 0.5.
- `PushNotificationComposer` loses its content-reading responsibility at 0.5; the server-side
  composer's remaining role (if any) is collapse-key and priority selection.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Content in push for latency | Content transfer to a foreign processor; fails BE-15/LEG-04 |
| Encrypted push payloads before E2E exists | No key to encrypt to; adds complexity for nothing at 0.5 |
| Moving read-receipt enforcement client-side at 1.0 | Silently weakens the #377 guarantee (security judge) |
| Keep server search for E2E chats via server-side indexes of plaintext | Not E2E |
| Keep server link previews | Leaks every URL a user sends and lets the server fetch on their behalf |

## Evidence

- Findings: BE-15, LEG-04, MOB-10, KMP-01, MISS-02 (mobile verifier: background sync never
  cancelled), GOV-08/SSOT-09 (claims must be true).
- Code: `backend/src/main/kotlin/com/muhabbet/messaging/domain/service/PushNotificationComposer.kt:44,117`,
  `backend/src/main/kotlin/com/muhabbet/messaging/adapter/out/external/FcmPushNotificationAdapter.kt:48`,
  `backend/src/main/kotlin/com/muhabbet/messaging/adapter/in/web/{SearchController,LinkPreviewController}.kt`,
  `backend/src/main/resources/db/migration/V1__initial_schema.sql:125`.
- External (from the legal digest): https://www.mevzuat.gov.tr/MevzuatMetin/1.5.6698.pdf (KVKK).

## Verification

| Req | Check | Method | When |
|---|---|---|---|
| RQ-07 | Captured FCM payload contains no content, title, body or sender name | Test (adapter unit test on the built message) + Inspection (on-device payload capture) | 0.5 exit |
| RQ-05 | Dropping 100% of wake hints still converges via foreground/WorkManager pull | `SimulatedLink` test | 0.5 exit |
| — | Notification arrives with the app killed, text composed from the local DB | Demonstration on emulator-5554, owner (P13) | 0.5 exit |
| — | p90 notification latency on an aggressive-OEM device within the agreed bound | Demonstration, owner | 0.5 exit (R11) |
| RQ-13 | No plaintext content for E2E conversations in `messages`, search indexes or preview caches | SQL assertion + DB dump inspection | 1.0 exit |
| — | READ is still downgraded to DELIVERED for a reader with `readReceiptsEnabled = false`, over WS and REST, for an E2E conversation | Integration test | 1.0 |

> **Reviewer note (open):** the wake payload carries `conversationId` in clear to Google (and later
> Apple). That is not content, but it is a stable pseudonymous identifier: the push provider can see
> that two devices receive wakes for the same conversation at the same moments, i.e. who talks to
> whom and when. A per-device opaque hint (e.g. only `{type:"wake"}`, or a random per-device token
> mapped server-side) would remove that link at the cost of per-conversation collapse keys and a
> slightly larger pull. The clear `seq` also exposes per-conversation message counts to the push
> provider; both fields fall under the same decision. The brief's payload is kept; the owner and
> counsel (Q-L6) should decide whether `conversationId` and `seq` are acceptable or should be
> replaced before 0.5 ships.
