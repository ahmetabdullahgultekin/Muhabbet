# Muhabbet — roadmap and how we work

This file answers three questions that kept coming up: **what ships in which version**, **how we
decide that**, and **what "done" means**. The issue tracker is the inventory; this is the method.

Last revised 2026-08-16.

---

## How we version

Semantic versioning, with one project-specific rule.

- **Pre-1.0, the minor number carries breaking changes.** `0.3.x → 0.4.0` may break clients.
- **Patch releases fix defects only.** `0.3.1` and `0.3.2` were both "what a real device found in the
  release before it" — that is the correct use.
- **1.0.0 is reserved for the first release that ships end-to-end encryption on.** This is stated in
  `CHANGELOG.md` and is not negotiable by convenience. A privacy-first messenger that is not
  encrypted has not reached 1.0, whatever else it does.

`versionName` / `versionCode` live in `mobile/composeApp/build.gradle.kts` and are checked against
`BuildInfo.kt` by a Gradle task — they cannot drift.

## Milestones are versions, not themes

Every milestone in GitHub is a version with **exit criteria written into its description**. An issue
belongs to the version whose exit criteria it blocks. If it blocks none of them, it stays
unmilestoned — that is the backlog, and it is allowed to be large.

| Milestone | The question it answers |
|---|---|
| **0.4.0 — Installable** | Can someone who is not us install it, update it, and use it for a day without hitting a crash or a dead control? |
| **0.5.0 — Honest** | Does every control the app shows either work or not get shown? |
| **0.6.0 — Reachable** | Can an existing user bring a new one in, and can a new user find someone to talk to? |
| **0.7.0 — Live** | Does a call connect, carry audio both ways, and appear in history? |
| **1.0.0 — Encrypted** | Are all of the app's privacy claims true? |

The earlier `Tier 1/2/3` milestones are a **thematic** grouping from the WhatsApp-parity plan and
still exist. They are not a schedule. When the two disagree, the version milestone wins, because it
is the one with exit criteria.

A version ships when its exit criteria are met — **not** when its issue list is empty. Anything
unfinished moves to the next milestone at release time, deliberately and visibly.

## The working method

**Incremental and trunk-based. Not Scrum.** There are no sprints, no story points, no ceremonies —
they would be theatre for a solo engineer working with agents. What is borrowed from agile is the
part that actually pays: small increments, working software as the measure of progress, and
re-planning whenever reality disagrees with the plan.

The loop:

1. **Find** — from a real device, a user report, an audit, or a failing gate. Never from a document.
2. **File** — one issue per problem, with the evidence that proves it. An issue with no `file:line`,
   log line or row count is a rumour.
3. **Milestone** — which version does this block? If none, it is backlog.
4. **Branch** — `fix/`, `feat/`, `docs/`, `ci/`, `chore/` + a short slug.
5. **PR** — squash-merged. The commit message says what the *user* experienced, not what the code
   does. `Deliver the messages that Redis was silently dropping`, not `fix(redis): channel parsing`.
6. **Release** — tag `vX.Y.Z`, CHANGELOG entry, GitHub release with the signed artifact.

Rule: **the tracker is the memory.** Anything discussed and not filed is lost. This has already
happened often enough to be a rule rather than advice.

## Definition of done

A change is done when all of these are true. They are here because each was learned by shipping the
mistake.

1. **Three questions for anything user-visible.** Does it *persist*, does anything *read* it, and
   does a *mechanism* exist? Adding persistence alone to App Lock makes the toggle remember its
   position while the app still never locks — strictly worse than leaving it visibly broken, because
   it then looks repaired.
2. **The gate ran.** A check that did not execute is not a check that passed. detekt has been dead
   for months while reporting nothing; Mobile CI never built the app at all; a `MessageListenerAdapter`
   published happily and delivered nothing. Confirm the thing ran, not that it failed to complain.
3. **Verified where the property lives.** Logic → tests. Layout, motion, gesture, colour → a screen.
   The build host has no emulator; the Windows dev host does (AVD `openscale_tr`). If it was not
   observed, say what was checked and what was not — never call it "verified".
4. **CI is compared against `main` before a branch is blamed.** Most red checks here are
   environmental (#419).
5. **The CHANGELOG entry is written from the user's side**, including what is still broken. Listing
   known defects openly is a feature of this project's release notes; keep it.

## Release process

1. Exit criteria for the milestone are met.
2. `CHANGELOG.md` — move `Unreleased` into the version, including a **Known issues** section.
3. Bump `versionName` / `versionCode`, tag `vX.Y.Z`.
4. Build a **release-signed** artifact. The signer must be `CN=Muhabbet, O=Rollingcat Software`,
   SHA-256 `e848191121876e08cc0968ae2a3c8591810add502e52e5d5dd1c8bb1f2aa3560`. Anything signed with
   the CI debug key installs only over other debug builds and must never be offered as an update.
5. Publish the GitHub release **non-draft**. A draft is invisible to everyone without write access —
   which is why users reported the newest release as 0.2.2 while three newer ones existed.
6. Never attach an artifact that cannot be installed. Naming it `-unsigned-DO-NOT-INSTALL` is not
   enough; it has already been downloaded and failed once.
7. Backend deploys are manual until #419 is fixed: `--env-file .env.prod` with the **repo-root**
   compose file, after tagging the running image `rollback-<date>`.

## Where the keys live

- **Release keystore:** the owner's Windows machine, `~/.keystores/muhabbet-release.jks`, with its
  credentials file and the Play publisher service account beside it. **It is not backed up anywhere,
  and losing it means the app can never be updated again** — a new key is a new app on Play. See
  #422 for what the storage arrangement should be.
- **Server secrets:** `/opt/projects/Muhabbet/.env.prod` on the Hetzner host. There is no `.env`;
  every compose command needs `--env-file .env.prod` or every secret resolves empty.
- The self-hosted runner **is** the production server, so putting signing secrets in GitHub Actions
  puts the signing key on the production box during every release build (#419). Decide that
  consciously.
