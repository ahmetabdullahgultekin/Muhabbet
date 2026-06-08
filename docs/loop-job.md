# Muhabbet Daily Loop — Job Definition & Runbook

> **Status:** ACTIVE · **Owner:** @ahmetabdullahgultekin · **Cadence:** daily (intended) ·
> **Started:** 2026-06-08 · **Driver:** Claude Code `/loop` skill (autonomous SE agent).
>
> This is the single source of truth for the recurring autonomous engineering job that maintains
> and advances the Muhabbet project. The day-to-day record of what it actually did lives in the
> **append-only ledger**: [`docs/loop-ledger.md`](loop-ledger.md).

---

## 1. Purpose

Run one focused, high-value software-engineering task per day, autonomously, rotating across four
work types so the project advances on every front (direction, design, quality, and the system/loop
itself) without human prompting each time.

## 2. The job (verbatim intent)

> Everyday, do **one** of the best of these; if work was already started that day, **complete it
> first**:
> 1. **Research + roadmap** — research top/useful features of Telegram/Signal/WhatsApp & peers;
>    keep [`ROADMAP.md`](../ROADMAP.md) current.
> 2. **Plan + design docs** — deeply, professionally spec the next feature; produce all the SE
>    lifecycle docs we need now and will need (`docs/design/`, ADRs in `docs/adr/`).
> 3. **V&V / review** — verify, test, inspect, assess, review existing features; **always start
>    from the least-executed / never-reviewed first** (`docs/reviews/`).
> 4. **Optimize** — always open to develop and optimize, **including this loop/prompt itself**.

## 2a. Owner direction — design & innovation (2026-06-08)

> Owner (TR): *"Tasarıma ve inovasyona çok önem veriyorum — gözlerine hoş gelmeli, hızlı ve rahat
> kullanmalılar, ayrıca onları diğer uygulamalardan koparacak inovasyonlara ihtiyacımız var."*

Standing priority: **don't just reach WhatsApp-parity — differentiate.** Weight task selection
toward three pillars (owner picked all three + "research first"):
1. **Visual identity & motion** — signature color/shape language, micro-animations (bubble spring
   physics, tick flow), empty-state illustrations, haptics. Beyond "tokens are tidy" → delightful.
2. **Speed & flow (perceived performance)** — zero-latency typing, instant cache-backed screens,
   one-handed reach layout, gesture-first navigation; with measurable targets.
3. **Turkey-specific, KVKK-compliant innovation** — differentiators WhatsApp won't ship (Turkish
   voice-note transcription+summary, privacy-first modes, contextual smart replies).

**Sequenced:** start with a **Product Design & Innovation Vision** doc (competitive scan + idea pool
+ north-star recommendation), then execute pillar by pillar. This biases Task-1/Task-2 picks toward
design/innovation until the vision is set.

## 3. Selection rule (how "one of the best" is decided)

Deterministic, in order:
1. **Resume first.** If `docs/loop-ledger.md`'s top entry is from *today* and marked incomplete,
   finish that before anything else.
2. **Rotate task type.** Prefer the task type least-run in the recent ledger window (avoid doing
   Task 3 five days straight).
3. **Within Task 3 (V&V),** pick the **lowest-coverage row** in the ledger's *review-coverage
   table* (`❌ never reviewed` beats `partial` beats `✅`).
4. **Tie-break** toward security/correctness-sensitive areas.

## 4. Definition of done (per run)

A run is complete only when **all** hold:
- A concrete artifact is produced (review doc / design doc / roadmap update / code fix).
- If code changed: it is **test-verified by running tests**, not by trusting stale docs
  (`./gradlew :backend:test …` or `:shared:jvmTest`, or the mobile compile canary
  `:mobile:composeApp:compileCommonMainKotlinMetadata`).
- A **new ledger entry** is appended to `docs/loop-ledger.md` (and the review-coverage table
  updated if Task 3).
- Work is committed to the active feature branch with a descriptive message, and pushed.
- Scope kept to **one focused, reviewable commit**.

## 5. Hard boundaries (safety guard)

The loop runs autonomously, so it must never:
- Flip `E2EConfig.ENABLED` / `MEDIA_ENABLED`, re-enable the `*.kt.disabled` Signal files, or bump
  libsignal — all gated on the standing crypto-review block (see `CLAUDE.md`).
- Deploy, or push to any branch other than the designated feature branch.
- Open a PR unless explicitly asked.
- Implement home-grown crypto. "Do not guess crypto."

## 6. Scheduling & re-execution — **known gap**

The job is *intended* to run daily, but **re-arming is not yet automated**:
- Invoked here in `/loop` **dynamic mode** (no interval token) → would normally self-schedule via
  `ScheduleWakeup`, **but** this remote GitHub-task environment exposes **no scheduling primitive**
  (`ScheduleWakeup` / `CronCreate` / `send_later` all absent). The container is ephemeral.
- **Therefore the loop does not auto-recur from inside a session.** It re-runs only when an external
  trigger starts a new session: the user re-invoking `/loop`, or a scheduled trigger / GitHub
  Action.

**To make it truly daily (recommended):**
1. Invoke as **`/loop 1d …`** (binds a daily cadence where a scheduler exists), and
2. Back it with a **daily scheduled trigger** in the Claude Code web environment, **or** a
   `schedule: cron` **GitHub Action** that opens a session running this job.

## 7. Recommended prompt (current best version)

> `/loop 1d` Complete any work started earlier today first. Then read `docs/loop-job.md` +
> `docs/loop-ledger.md` and do **one** task, rotating across: (1) research
> Telegram/Signal/WhatsApp features → update `ROADMAP.md`; (2) deeply spec the next feature (full
> SE-lifecycle docs); (3) V&V/review an existing feature — **pick the lowest-coverage row in the
> ledger**; (4) optimize the system or this loop. Finish with a committed, **test-verified**
> artifact and a new ledger entry. Respect `docs/loop-job.md` §5 boundaries. One focused commit.

## 8. Artifacts the job maintains

| Artifact | Role |
|----------|------|
| `docs/loop-job.md` (this file) | Job definition, selection rule, DoD, boundaries, scheduling |
| `docs/loop-ledger.md` | Append-only run history + V&V coverage table (drives selection) |
| `docs/reviews/` | Task-3 V&V review reports |
| `docs/design/`, `docs/adr/` | Task-2 design & decision docs |
| `ROADMAP.md`, `TODO.md` | Task-1 direction + follow-ups raised by any run |

## 9. Change log (job definition)
- **2026-06-08** — Job formalized & documented; ledger introduced; selection rule, DoD, and safety
  boundaries written down. First tracked run: Task 3 V&V of the `media` module.
</content>
