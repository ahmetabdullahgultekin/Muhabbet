# Perceived-Performance Budget (Pillar B — Speed & Flow)

| | |
|---|---|
| **Status** | Draft (2026-06-08) — fulfils Vision §4 Pillar B "DONE = a documented perf-budget (`docs/qa/perf-budget.md`)" |
| **Scope** | *Perceived* (felt) performance of the top user flows on a **mid-range Android device**. Server-side throughput/latency SLOs live in [`docs/qa/01-performance-efficiency.md`](01-performance-efficiency.md); this doc is the client/end-to-end *feeling* layer. |
| **Companion** | `docs/design/PRODUCT_DESIGN_INNOVATION_VISION.md` §4 Pillar B; `ROADMAP.md` Tier 1 |
| **North star** | *"Feel faster than WhatsApp."* Perceived performance is a feeling — set budgets, measure them, defend them. |

---

## 0. How to read this

A **budget** is a hard ceiling. A flow that exceeds its budget is a defect, not a "nice to fix".
Numbers are targets for a **mid-range Android device** (think Snapdragon 6-series / 4 GB RAM, the
Turkish mass-market phone), on a warm app (process alive), 4G network unless stated.

Each flow lists: the **budget**, **how we measure it**, and **what blocks measurement on this CI
host** (no Android emulator — no KVM —, no Firebase, no device farm). Honesty rule from project
memory: *perf claims are measured, not asserted.* Where we cannot yet measure, the row says so.

---

## 1. The five flows + budgets

| # | Flow | Budget (perceived) | Hard ceiling | Vision ref |
|---|------|--------------------|--------------|------------|
| F1 | **Cold start → usable chat list** | < 1.0 s to *real* content (no spinner) | 1.5 s | §4 B bullet 1 |
| F2 | **Tap chat → messages on screen** | < 150 ms | 300 ms | §4 B bullet 2 |
| F3 | **Keystroke → glyph** (composer) | < 16 ms (1 frame @ 60 Hz) | 33 ms (2 frames) | §4 B bullet 3 |
| F4 | **Send → own bubble appears** | 0 ms (optimistic; synchronous with tap) | 16 ms | §4 B bullet 4 |
| F5 | **Scroll jank** (chat list & history) | 0 dropped frames; 60 fps sustained (120 fps where panel allows) | < 1% frames > 16.6 ms | §4 B "scroll jank" |

### F1 — Cold start → usable chat list (budget < 1.0 s)
- **What "usable" means:** the user sees their *actual* most-recent conversations (from the
  SQLDelight cache), not a skeleton and not an empty state. Network refresh happens *behind* the
  already-rendered list.
- **Measure:**
  - *Lab:* `adb shell am start-activity -W -n com.muhabbet.app/.MainActivity` → `TotalTime` /
    `WaitTime` (cold start, after `pm clear`). Repeat ≥ 10×, report P50/P95.
  - *Frame-accurate:* Macrobenchmark `StartupTimingMetric` (`androidx.benchmark.macro`) with
    `StartupMode.COLD` — gives `timeToInitialDisplay` **and** `timeToFullDisplay`. Mark full display
    with `reportFullyDrawn()` once the cached list is bound.
  - *Field:* Play Console / Android vitals cold-start metric; Firebase Performance `_app_start` trace.
- **CI-host limit:** cannot run — no emulator (no KVM), Macrobenchmark needs a real/virtual device.
  Measure on a physical phone during release prep; record numbers back into this doc.

### F2 — Tap chat → messages on screen (budget < 150 ms)
- **What it means:** from tap on a list row to the first page of messages rendered. Cache-first:
  render cached messages immediately, fetch the live page in the background and reconcile.
- **Measure:**
  - *Lab:* Macrobenchmark `FrameTimingMetric` over a scripted "open chat" interaction +
    `traceEventStart/End` around the navigation→first-bind span.
  - *Manual:* `adb shell dumpsys gfxinfo com.muhabbet.app framestats` before/after the tap.
- **Backend contribution (measurable here):** `GET /conversations/{id}/messages` — server portion has
  an SLO in doc 01 (P95 < 200 ms) and is index-backed (see §3). The *perceived* 150 ms is met by
  rendering the cache first, so server time is off the critical path.
- **CI-host limit:** end-to-end (tap→pixels) needs a device. Backend query latency is measurable via
  the Testcontainers integration tests + k6, but not the UI half.

### F3 — Keystroke → glyph (budget < 16 ms)
- **What it means:** typing in the composer never drops a frame; never block the input thread with
  I/O, mention-resolution, link-preview, or recomposition of the whole message list.
- **Measure:** Macrobenchmark `FrameTimingMetric` while injecting key events
  (`adb shell input text`), assert `frameDurationCpuMs` P99 < 16. Perfetto trace to confirm no
  main-thread I/O during typing.
- **CI-host limit:** device-only. Static guard available: keep composer state hoisted locally
  (`mutableStateOf`) and out of the list's recomposition scope (architectural review, not a metric).

### F4 — Send → own bubble appears (budget 0 ms / optimistic)
- **What it means:** the bubble is inserted **synchronously** with the send tap from a client-built
  `Message` (status = sending), *before* any WS round-trip. The tick upgrades later (clock → single
  → double) off the ServerAck / delivery acks.
- **Measure:** UI test asserting the bubble exists in the list on the same frame as the tap (no
  awaiting network). Manual: airplane-mode send must still show the bubble instantly (then queue).
- **Status:** already implemented (optimistic UI + `PendingMessage` offline queue). The *motion* polish
  (A2 "lift + settle") is a separate Pillar-A slice; this budget covers latency only.
- **CI-host limit:** the optimistic insert is pure client logic; covered by mobile commonMain tests,
  but the visual frame timing is device-only.

### F5 — Scroll jank (budget: 60 fps, 0 dropped frames)
- **What it means:** chat-list and message-history scrolling hold 60 fps (16.6 ms/frame). No
  synchronous decode/format on the scroll thread (images async, timestamps pre-formatted via the
  shared `DateTimeFormatter`).
- **Measure:** Macrobenchmark `FrameTimingMetric` (`frameOverrunMs`, `frameDurationCpuMs` P95/P99)
  over a scripted fling; Perfetto for janky-frame attribution. `dumpsys gfxinfo` "Janky frames %" as
  a quick check.
- **CI-host limit:** device-only.

---

## 2. Current perf assets (what already defends these budgets)

These are real, in-tree, and directly serve the budgets above:

| Asset | Where | Helps |
|---|---|---|
| **SQLDelight offline cache** (cache-first repos: `CachedConversation`, `CachedMessage`) | `mobile/.../data/local/MuhabbetDatabase.sq` + cache-first repositories | F1, F2 (render real content with no network on the critical path) |
| **Optimistic send + offline queue** (`PendingMessage`, drained on WS reconnect) | mobile messaging repo + `WsClient` | F4 (0 ms bubble), resilience |
| **Skeleton loaders** | mobile UI (cold/empty states) | F1 fallback when cache is empty (first run only) |
| **12 DB performance indexes** | `backend/.../db/migration/V14__add_performance_indexes.sql` (+ V1 base indexes) | F2 (history), search, unread, media, inbox |
| **N+1 batch refactors** (inbox 3×N → 3 queries) | `ConversationService.getConversations` + `MessageRepository.getLastMessages/getUnreadCounts` + `ConversationRepository.findMembersByConversationIds` | F1/F2 server side: inbox is O(1) queries, not O(conversations) |
| **Batch delivery-status resolution** | `MessageService.resolveDeliveryStatuses` + `findByMessageIdIn` (one query per page) | F2 (ticks resolved in one round-trip, served by the delivery-status PK) |
| **Trigram search index** | `idx_messages_content_trgm` (GIN, `pg_trgm`) | search latency (no full table scan on `LIKE`) |
| **Covering inbox index** | `idx_messages_conversation_latest` / `idx_messages_conv_latest_covering` | last-message-per-conversation is index-only |
| **Redis Pub/Sub broadcaster** | `RedisMessageBroadcaster` | cross-instance delivery latency (horizontal scale) |
| **Connection pooling** (Ktor client, Redis) + nginx gzip/caching + PG tuning | infra/system-optimization pass | tail latency across the board |

### This-iteration optimization (D3)
- **Dropped redundant `DISTINCT`** from two membership-scoped read queries —
  `MessageRepository.searchGlobal` (message search) and `findMessagesSince` (background-sync feed,
  runs ~every 15 min per device + on silent push). The `conversation_members` PK is
  `(conversation_id, user_id)`, so for a fixed `:userId` the join matches at most one member row per
  conversation and each message has exactly one `conversation_id` → the join is **1:1 and cannot fan
  out**, making `DISTINCT` dead weight (a needless dedup sort/hash). File:
  `backend/.../persistence/repository/SpringDataMessageRepository.kt`. Safety locked by a new
  no-duplicate assertion in `SearchIdorIntegrationTest` (real-Postgres Testcontainers; runs in CI,
  skipped on this Docker-less host). Helps F5-adjacent search latency and the sync feed.

---

## 3. Gaps & prioritized optimization backlog (code-grounded)

The messaging/conversation **read paths are already well-batched and indexed** (V14 + the inbox N+1
refactor covered the obvious wins; this was verified path-by-path during D3). The remaining items are
smaller / higher-risk / measurement gaps — listed so a future slice can pick them up with eyes open.

### P1 — measurement gaps (can't currently prove the budgets)
- **No on-device perf harness.** There is no Macrobenchmark module and no device in CI (no KVM, no
  Firebase). **Action:** add an `androidx.benchmark.macro` module (Startup + FrameTiming) and run it
  on a physical phone during release prep; paste P50/P95 into §1. *Until then every "Current" cell
  for F1–F5 is "unmeasured on this host".*
- **`reportFullyDrawn()` not wired.** F1's "time to *full* display" can't be captured until the app
  calls `reportFullyDrawn()` once the cached conversation list is bound. **Action:** call it after the
  first cache bind. Low risk, client-only.

### P2 — backend read-path micro-optimizations (real but modest)
- **Delivery-status over-fetch in large groups (F2).** `getDeliveryStatuses(messageIds)` →
  `findByMessageIdIn` loads **every recipient row** for every message on a history page. For a 50-msg
  page in a 256-member group that's up to ~50×255 rows transferred, then aggregated in Kotlin
  (`MessageService.resolveDeliveryStatuses`). For the *recipient* perspective only the requester's own
  row is used. **Possible fix:** a projection/aggregate query (e.g. per-message status counts for the
  sender's own messages + the requester's single row for others). **Why not done in D3:** it changes
  the query contract and aggregation semantics — needs a real-DB round-trip test to prove the tick
  aggregation (all-READ→READ, any-DELIVERED→DELIVERED) is byte-identical. Behaviour-sensitive, so
  deferred rather than guessed. 1:1 chats (the common case) are unaffected (≤1 row/msg).
- **`findByMessageIdIn` is not index-only.** It returns the full entity incl. `updated_at`, which the
  resolver never reads, forcing a heap fetch per row. A projection selecting only
  `(message_id, user_id, status)` over a `(message_id, user_id, status)` index would be index-only.
  Coupled to the item above (same query). Low/modest payoff; defer with the projection rewrite.
- **`getStarredMessages` ignores soft-delete + loses order.** `findAllById(messageIds)` returns
  soft-deleted rows and in arbitrary order (the starred-row order from
  `findByUserIdOrderByCreatedAtDesc` is dropped). This is a **correctness** issue first, perf second.
  Out of D3's "safe perf-only" scope; track as a bug. File:
  `backend/.../persistence/StarredMessagePersistenceAdapter.kt`.

### P3 — perceived-flow client work (Pillar B, not pure perf)
- **F2 pre-warm.** Vision suggests pre-warming the last-opened conversation(s) so tap→messages is
  near-instant even on cache miss. Not implemented; needs a heuristic + memory budget. Defer.
- **F1 baseline profiles.** Ship an `androidx.profileinstaller` baseline profile for the startup +
  chat-list path to cut JIT on first frames. Measurable win on cold start; needs the benchmark module
  first (P1) to prove it.
- **One-handed reach layout.** Tracked under Pillar B "one-handed reach" — UX, not latency; out of
  scope here.

---

## 4. Definition of done for Pillar B

Per the Vision: *"the top-5 flows meeting [budget] on a mid-range Android device."* That requires the
P1 measurement harness to exist and a real device run. **This doc establishes the budgets and the
measurement method; the measured "Current" column is filled in once F1–F5 are run on a phone.** The
backend read paths that feed F1/F2 are already index-/batch-optimized and (as of D3) free of the
redundant `DISTINCT` dedup on the search and sync feeds.
