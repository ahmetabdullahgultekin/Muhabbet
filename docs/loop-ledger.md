# Muhabbet Daily Loop — Ledger

> Append-only record of the autonomous daily loop (`/loop`). Each entry = one day's pick.
> **Purpose:** make "do one of the best of these" and "start from the **least executed / never
> reviewed**" *deterministic* — the loop reads this file first to choose, instead of re-deriving
> state from scratch every run. Newest entries on top.
>
> **Job definition / runbook:** [`docs/loop-job.md`](loop-job.md) (selection rule, definition of
> done, safety boundaries, scheduling status).

## Task rotation (the 4 loop tasks)
1. **Research + roadmap** — survey Telegram/Signal/WhatsApp features, update `ROADMAP.md`.
2. **Plan + design docs** — deeply spec the next feature (SE lifecycle docs).
3. **V&V / review** — verify/test/inspect existing features, **least-reviewed first**.
4. **Optimize** — improve the system *and this loop/prompt itself*.

## Module/feature review coverage (Task 3 — keep current; pick the lowest count next)
| Area | Dedicated review? | Last reviewed | Notes |
|------|-------------------|---------------|-------|
| backend `media` | ✅ | 2026-06-08 | `docs/reviews/2026-06-08-media-module-vv.md` (fixed Finding A) |
| backend `moderation` | partial | 2026-06-07 | IDOR Q in `docs/qa/02-security.md`; no full module review |
| backend `messaging` | partial | 2026-06-07 | IDOR fixes PR #55; 24 test files; no holistic review |
| backend `auth` | partial | 2026-06-07 | JWT boot-guard PR #55; no full module review |
| backend `presence` (adapter) | ❌ | — | `RedisPresenceAdapter` inside messaging; never reviewed |
| backend `notification` (adapter) | ❌ | — | `FcmPushNotificationAdapter`; never reviewed |
| mobile (CMP) | UI-audit only | 2026-06-07 | `docs/qa/mobile-ui-audit.md` (87 issues); no logic V&V |
| shared (KMP) | ❌ | — | model/dto/protocol/validation; never reviewed |

**Next V&V pick (least reviewed):** `presence` or `notification` adapter, or `shared` module.

---

## Entries

### 2026-06-08 (run 6) — Design impl D2: motion language foundation + press feedback
- **Trigger:** owner "durmak yok, profesyonel ve önerilen şekilde ilerle" → continued with the
  recommended D2 (motion).
- **Did:** new `MuhabbetMotion.kt` — canonical motion tokens (3 durations, 3 easing curves, 3 spring
  specs) + reusable `Modifier.pressBounce` (scroll-safe tactile press) and `Modifier.bubbleEntrance`
  (signature lift+settle, opt-in for new messages). Wired `pressBounce` into the chat `MessageBubble`
  (shared `MutableInteractionSource` with `combinedClickable`). Spec: `docs/design/D2-motion-spec.md`.
- **Verification:** `:mobile:composeApp:compileCommonMainKotlinMetadata` green. Device-visual
  verification pending a real APK (full Android app doesn't assemble on this host).
- **Boundaries:** UI motion only; no crypto, no deploy.
- **Commit:** branch `claude/relaxed-goldberg-P1IKj`.

### 2026-06-08 (run 5) — Design impl D1: own brand identity (İznik çini palette)
- **Trigger:** owner chose "Türk turkuazı / çini" for D1 (highest-leverage gap from run-4 vision:
  we shipped WhatsApp's exact palette).
- **Did:** replaced the WhatsApp-cloned palette in `MuhabbetTheme.kt` with an İznik-çini identity —
  firuze (turquoise) primary `#0E94A8`/`#26B3C7`, kobalt (cobalt) read-ticks/links, mercan (coral)
  warm accent; warm-ivory light wallpaper, pale-firuze own-bubble, teal-shifted dark surfaces. All
  `WhatsApp*` constants renamed `Cini*`; updated 3 semantic-color sets + 3 M3 schemes (light/dark/
  OLED). No call sites touched (pure token swap). Spec: `docs/design/D1-brand-color-identity.md`.
- **Verification:** `:mobile:composeApp:compileCommonMainKotlinMetadata` green; grep confirms no
  leftover WhatsApp color refs / hardcoded old greens in mobile.
- **Boundaries:** UI tokens only; no crypto, no deploy.
- **Commit:** branch `claude/relaxed-goldberg-P1IKj`.

### 2026-06-08 (run 4) — Task 1/2 (Research + vision): Product Design & Innovation Vision
- **Trigger:** owner directive — design & innovation must *differentiate*, not just reach parity;
  picked all 3 pillars + "research first" (`docs/loop-job.md` §2a).
- **Did:** `docs/design/PRODUCT_DESIGN_INNOVATION_VISION.md` — competitive scan (2026 web research:
  WhatsApp/Telegram/Signal/Session), the differentiation gap (**we ship WhatsApp's exact palette** —
  `MuhabbetTheme.kt` `0xFF00A884`), Muhabbet's open lane (sovereign + privacy-first + warm + Turkish),
  3 pillars (visual identity & motion / speed & flow / TR innovation), scored innovation idea pool,
  **north-star** (own identity + flagship pair: **Mahrem Mod** + **Turkish voice→text+summary**), and
  D1–D5 flagged slices wired into the loop.
- **Verification:** docs-only; research cited (6 sources); grounded in real code (`MuhabbetTheme.kt`).
- **Boundaries:** none of D1–D5 touch libsignal/E2E flags; no deploy.
- **Commit:** branch `claude/relaxed-goldberg-P1IKj`.

### 2026-06-08 (run 3) — Implementation: @mentions slice S1 (contract + migration + flag)
- **Context:** user said "skip loop, start working" → implemented the first vertical slice of the
  @mentions design from run 2 (the obvious, low-regret next step: additive + default-OFF).
- **Did (S1, behavior-neutral):** `V19__add_message_mentions.sql` (additive table + `mentions_everyone`
  column); `MentionRef` + optional `mentions`/`mentionsEveryone` on shared `Message`,
  `WsMessage.SendMessage`, `WsMessage.NewMessage`; backend `Mention` value object + `Message` fields;
  `MentionProperties` flag (`muhabbet.mentions.enabled`, default OFF) wired in `AppConfig` +
  `application.yml`. `MessageJpaEntity` left unmapped for the new column on purpose (persistence is S2).
- **Tests:** added old-client-compat + round-trip cases to `WsMessageSerializationTest`.
- **Verification:** `:shared:jvmTest` green, `:backend:compileKotlin` green, mobile commonMain
  metadata compile green. CHANGELOG + design-doc status + this ledger updated.
- **Boundaries:** no crypto flags flipped, no disabled Signal files touched, no deploy.
- **Commit:** branch `claude/relaxed-goldberg-P1IKj`.

### 2026-06-08 (run 2) — Task 2 (Plan + design docs): @mentions in group chats
- **Picked because:** rotation — Tasks 3 & 4 already done today; Task 2 never run by the loop.
  Chose @mentions as the next feature (non-blocked, no crypto/deploy, high group-usability value).
- **Did:** full SE-lifecycle design — `docs/design/T2-group-mentions.md` (context→goals→data model
  `V19__add_message_mentions.sql`→additive WS/DTO contract→hexagonal file tree→default-OFF flag
  `muhabbet.mentions.enabled`→S1…S5 vertical slices→test plan→risks→rollback), ADR-0008
  (`docs/adr/0008-mentions-storage-and-notification.md`), sequence diagram
  (`docs/diagrams/mentions-sequence.mmd`), and wired it into `ROADMAP.md`.
- **Key decisions:** structured client-resolved/server-validated mentions (no server-side `@name`
  regex); mentions pierce conversation-mute but respect global-mute/block; `@everyone` admin-gated +
  size-capped; explicit E2E boundary (must not weaken E2E; notify-set moves client-side post-libsignal).
- **Verification:** design-only run (no code) — verified the doc against real code: migration head is
  V18 (→V19), real `SendMessage`/`NewMessage` shapes, `MediaAccessQueryRepository` soft-FK pattern
  reused for membership decoupling.
- **Commit:** branch `claude/relaxed-goldberg-P1IKj`.

### 2026-06-08 — Task 3 (V&V): `media` module review
- **Picked because:** thinnest-tested backend module (2 test files), never had a dedicated review.
- **Did:** full static review of all 17 media source files; wrote
  `docs/reviews/2026-06-08-media-module-vv.md` (5 findings).
- **Fixed:** Finding A — `uploadDocument` derived the MinIO object-key extension from the
  unsanitized client filename → path-segment (`/`, `..`) injection. Added `safeExtension()`
  (alphanumeric, length-capped, `bin` fallback) + 6 `uploadDocument` unit tests (method had 0).
- **Deferred (added to `TODO.md`):** Finding B (force `attachment` disposition on presigned URLs —
  owner decision), Finding C (storage-usage doc bucketing).
- **Verification:** `:backend:test --tests MediaServiceTest` green.
- **Also (Task 4 — optimize the loop):** formalized & documented the job itself —
  `docs/loop-job.md` (definition, selection rule, definition-of-done, safety boundaries,
  scheduling gap) + this ledger. Surfaced the scheduling gap: this remote env has no
  `ScheduleWakeup`/cron primitive, so the loop can't self-re-arm — needs an external daily trigger.
- **Commit:** see branch `claude/relaxed-goldberg-P1IKj`.
</content>
