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
