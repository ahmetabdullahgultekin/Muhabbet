# ADR 0011 — The mobile local database is the single source of truth; no ViewModel layer

**Status:** Proposed — pending owner approval; no code may be written against this ADR until approval is recorded in [`docs/architecture/multi-transport/APPROVAL.md`](../architecture/multi-transport/APPROVAL.md) · **Date:** 2026-11-12 · **Source:** [decision brief §3, ADR-0011](../architecture/multi-transport/decision-brief-2026-11-12.md) · **Amends (supersedes the statement in)** [ARCHITECTURE.md §4](../ARCHITECTURE.md) "the local database is a cache and an outbox, not a source of truth", and rewrites §5 "How a message actually travels". Keeps the §4 no-ViewModel decision.

## Context

ARCHITECTURE.md §4 documents the current client honestly: SQLDelight holds `CachedConversation`,
`CachedMessage` and `PendingMessage`; repositories read cache-first; **a message sent while the
socket is up never touches the database**, because the optimistic bubble lives in Compose state.

The review traced four defects to that shape:

| Finding | Defect | Root cause |
|---|---|---|
| MOB-03 | Incoming `message.new` is never persisted; DELIVERED is acked from RAM (`App.kt:126-138`), and `ConversationListScreen` sends a second DELIVERED and refetches the whole list on every event (`ConversationListScreen.kt:183-213`) | No store the UI observes |
| MOB-06 | `ChatScreen` is a ~985-line composable owning state, network calls and ten `WsMessage.SendMessage` sites | UI speaks wire frames |
| MOB-08 | No domain layer: UI consumes DTOs and protocol frames | Same |
| MOB-11 | `WsClient` fuses transport, outbox, dedup and the E2E hook; nothing for a second transport to sit behind | Same |

`grep -rn 'wsClient.send' mobile/composeApp/src` on `dev` @ 2434324 returns **17** call sites:
16 under `ui/` (ChatScreen, ChatDialogs, ConversationListScreen, ActiveCallScreen,
IncomingCallScreen) and one `wsClient.sendAck` in `App.kt`. `WsMessage` is referenced 49 times in 9
files under `ui/`. These, not "ten in ChatScreen", are the migration inventory.

Any additional path (pull, push hint, LAN, Nearby) needs one place to converge. Without a durable
store that every screen observes, each path would need its own merge logic in each screen.

## Decision

1. **SSOT.** SQLDelight (SQLCipher from 0.5, ADR-0020) is the single source of truth for
   conversations, messages, outbox state, inbox dedup and receipts. The §4 statement "the local
   database is a cache and an outbox, not a source of truth" is superseded.
2. **Reads.** Screens observe SQLDelight `Flow` projections (`asFlow().mapToList()` or equivalent)
   only. No screen merges `SharedFlow` events into a local list. No screen issues a REST refetch in
   response to a WS event.
3. **Commands.** Writes go through one Koin-singleton facade, `MessagingEngine`, in
   `:mobile:messaging` (ADR-0012). Its use cases are `SendMessage`, `MarkRead`, `RetryDelivery`,
   `ObserveConversation` and `SessionTeardown`. This is **light CQRS**: one database, separate read
   projections, no command or event buses.
4. **Every write path persists first.** The optimistic row and its outbox entry are written in one
   transaction (ADR-0013). Inbound events from every `InboundSource` are upserted before any ack.
5. **No ViewModel layer.** The documented §4 decision stays. `MessagingEngine` is a domain singleton
   exposing flows, the pattern §4 already sanctions for cross-screen state (`ThemeController`,
   `PrivacySettingsController`). Screen-state survival across configuration change (MOB-02) is a
   separate question; if the owner wants it, it gets its own ADR (open question Q-O10).
6. **UI never imports wire types.** `WsMessage` and `wsClient` disappear from `ui/` and `App.kt`.
7. **Migrations before schema change.** SQLDelight `.sqm` migrations with `verifyMigrations` and
   `deriveSchemaFromMigrations` are wired, with `1.sqm` describing today's schema, before any
   schema change (0.4 prerequisite, ADR-0028).

### Read model (0.5, sketch)

```kotlin
interface ObserveConversation { operator fun invoke(id: ConversationId): Flow<List<MessageRow>> }
interface ObserveConversationList { operator fun invoke(): Flow<List<ConversationRow>> }
// MessageRow carries: seq?, synced, deliveryState (derived from receipt rows), editVersion, deleted
```

Search, ConversationList and Chat read from these projections. Message bodies for E2E conversations
are searchable only through on-device FTS inside the encrypted DB (1.0, ADR-0019/0020).

## Why

- It removes the shared root cause of MOB-03, MOB-06, MOB-08 and MOB-11 instead of patching each.
- One store lets server push, server pull, and later LAN and Nearby converge without per-path merge
  logic (precondition for ADR-0012 and ADR-0013).
- "Persist before acknowledge" (principle P4) is only expressible if there is a store to persist to.
- None of the three design proposals superseded §4 explicitly; the architecture judge required the
  amendment to be written, not implied.
- Keeping no-ViewModel avoids contradicting a documented decision for no delivery benefit (KISS).

## Consequences

- ARCHITECTURE.md §4 (storage paragraph) and §5 (message flow) are rewritten on approval. A banner
  is added now.
- The behaviour "a message sent while the socket is up never touches the database" is removed.
- All 17 `wsClient.send*` sites and the 49 `WsMessage` references in `ui/` are migrated to engine
  use cases and DB flows. The `verifyUi` guardrail gains a rule counting `WsMessage`/`wsClient` in
  its scan roots, ratcheted to 0. `App.kt` is not under the current scan roots
  (`gradle/ui-guardrails.gradle.kts` scans `ui/`, `navigation/` and `designsystem`), so the rule
  must add it explicitly.
- `CachedConversation`, `CachedMessage` and `PendingMessage` are replaced by the SSOT schema in a
  fresh encrypted DB at 0.5 (ADR-0020) and dropped after the `engine.enabled` overlap release.
- `ConversationListScreen`'s refetch storm and duplicate DELIVERED ack disappear (MOB-03).
- `MessagingEngine` becomes the cross-screen state holder for messaging; the §4 rule "a value two
  screens show must come from one singleton" now covers messages.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Introduce ViewModels / MVI now | Orthogonal to delivery; contradicts a documented decision without a delivery benefit (MOB-02 is a separate ADR if wanted) |
| Full feature modularisation of the 26.5k-line `composeApp` (KMP-08) | No second consumer; YAGNI. Only `:mobile:messaging` and `:mobile:crypto` are extracted (ADR-0028) |
| Package-only boundary inside `composeApp` | Unenforced boundaries rot; BE-08 is the backend precedent |
| Keep cache semantics and add an inbox processor only | Leaves the optimistic bubble in Compose state, so MOB-04 and MOB-06 remain |

## Evidence

- Findings: MOB-02, MOB-03, MOB-04, MOB-06, MOB-08, MOB-11, MISS-01 (mobile verifier: group read
  receipts aggregated in a composable), KMP-08, BE-08.
- Code (on `dev` @ 2434324): `mobile/composeApp/src/commonMain/kotlin/com/muhabbet/app/App.kt`,
  `ui/conversations/ConversationListScreen.kt`, `ui/chat/ChatScreen.kt`, `data/remote/WsClient.kt`.

## Verification

| Check | Method | When |
|---|---|---|
| `WsMessage`/`wsClient` count in `ui/` and `App.kt` reaches 0 | `verifyUi` ratchet rule | 0.5 exit |
| `:mobile:messaging` has no Compose, Koin-annotation or `WsMessage` dependency | Build-enforced module dependencies + guardrail | 0.5 exit |
| An incoming message is in the DB before DELIVERED is sent | Crash-point test between commit and ack (ADR-0013) | 0.4 (MOB-03 fix) and 0.5 |
| A message received while the app is killed is in the DB before the notification is tapped | Owner observation on emulator-5554 (P13) | 0.6 exit |
| `verifyMigrations` passes in CI | CI job | 0.4 onward |
