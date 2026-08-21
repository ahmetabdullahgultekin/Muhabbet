# Muhabbet — roadmap and how we work

This file answers three questions that kept coming up: **what ships in which version**, **how we
decide that**, and **what "done" means**. The issue tracker is the inventory; this is the method.

Last revised 2026-08-21.

The architecture this schedules is described in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md); the
release history is in [`CHANGELOG.md`](CHANGELOG.md).

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
| **0.4 — the app tells the truth** | Can a person use the app without being lied to by it? |
| **0.5.0 — Honest** | Does every control the app shows either work or not get shown? |
| **0.6.0 — Reachable** | Can an existing user bring a new one in, and can a new user find someone to talk to? |
| **0.7.0 — Live** | Does a call connect, carry audio both ways, and appear in history? |
| **1.0.0 — Encrypted** | Are all of the app's privacy claims true? |

**0.4 is the live one.** Its four conditions are on the milestone itself: search actually searches;
the notification and the tick tell the truth; a community has somewhere to talk; and nothing looks
like it works and does not. Each closes only after being *seen* working on a device — the rule 0.3.7
broke by shipping a launch crash behind a green build.

Two older groupings are still visible in GitHub and are **retired** — do not file against them:

- **`0.4.0 — Installable`** was the earlier framing of the same release. Most of its list is closed
  and what remains belongs to `0.4` or to the backlog. Where the two disagree, `0.4` wins.
- **`Tier 1/2/3`** are a *thematic* grouping inherited from the WhatsApp-parity plan. They were never
  a schedule and have no exit criteria, which is exactly why they were replaced. When a theme and a
  version disagree, the version wins, because it is the one that can be finished.

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

## The order: verify, then schedule, then fix in batches

An issue moves through three stages, and skipping the first is what produced a tracker where a large
share of the entries are not work at all. Do not write its size down here — it moves weekly, and a
count in a document is a claim about a moment. Measure it:
`gh issue list -R ahmetabdullahgultekin/Muhabbet --state open -L 1000 --json number -q length`.

**1. Verify before it is scheduled.** Check the claim against the code and put the evidence — a
`file:line`, a log line, a row count — in the issue. An unverified issue is a rumour, and scheduling
a rumour spends real time on it. Of nineteen claims from the automated review passes that were
actually checked, two named the wrong file, three were badly miscounted, and the two genuinely
exploitable bugs were not in the list at all. Verification is also where an issue gets closed with a
reason, merged into an epic, or reduced from "26 hardcoded radii" to one piece of work.

Verification cuts both ways: it can also *raise* severity, or lower it. #270 was filed as a live
auth bypass; measuring it against production showed Traefik overwrites the header, so it is real in
the code and latent in production — still worth fixing, no longer urgent. That is a different plan
than the title implied.

**2. Then assign it to the version whose exit criteria it blocks.** Not to a theme, not to
"soon". If it blocks nothing, it is backlog, and that is a legitimate destination.

**3. Then fix in version batches, not one issue per branch.** Issues in the same milestone touching
the same area ship together: one branch, one PR, one deploy, one verification pass. Seven separate
PRs for seven token call-sites costs seven reviews and seven CI runs to change the same file.

The batch is also the unit of verification. A backend batch is verified once on CI and once against
production; a mobile batch is driven once on the emulator. That is affordable per batch and is not
per issue — which is precisely why issues were being closed unverified before.

## Definition of done

A change is done when all of these are true. They are here because each was learned by shipping the
mistake.

1. **Three questions for anything user-visible.** Does it *persist*, does anything *read* it, and
   does a *mechanism* exist? Adding persistence alone to App Lock makes the toggle remember its
   position while the app still never locks — strictly worse than leaving it visibly broken, because
   it then looks repaired.
2. **The gate ran.** A check that did not execute is not a check that passed. detekt was dead for
   months while reporting nothing (#279 — fixed; it now prints the file count precisely so this
   cannot recur silently); Mobile CI never built the app at all; a `MessageListenerAdapter`
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
2. The gates ran: `./gradlew :backend:test :shared:jvmTest`. Start Docker first — without it, ten
   `@Testcontainers` classes fail at class-init and are *not executed*, which is how a Spring-config
   regression reached production in #598.
3. `CHANGELOG.md` — move `Unreleased` into the version, including a **Known issues** section.
4. Bump `versionName` / `versionCode` in `mobile/composeApp/build.gradle.kts`, then
   `./gradlew :mobile:composeApp:verifyBuildInfoVersion` so `BuildInfo.kt` cannot drift from them.
   Tag `vX.Y.Z`.
5. Build a **release-signed** artifact. The signer must be `CN=Muhabbet, O=Rollingcat Software`,
   SHA-256 `e848191121876e08cc0968ae2a3c8591810add502e52e5d5dd1c8bb1f2aa3560`. Anything signed with
   the CI debug key installs only over other debug builds and must never be offered as an update.
6. **Install the built artifact on the emulator and launch it.** Not the debug build — download the
   release APK, uninstall the old one, `adb install`, `am start`, check `logcat` for `FATAL`. A green
   CI build proves the app compiles and proves nothing about whether it starts; 0.3.7 shipped a
   launch crash to every user behind one. The full command sequence is in `CLAUDE.md`.
7. Publish the GitHub release **non-draft**. A draft is invisible to everyone without write access —
   which is why users reported the newest release as 0.2.2 while three newer ones existed.
   **This is currently broken again:** as of 2026-08-21, `v0.3.7` through `v0.3.10` are all drafts
   and `gh release list` shows 0.3.5 as `Latest`. Verify with `gh release list` *after* publishing,
   not before.
8. Never attach an artifact that cannot be installed. Naming it `-unsigned-DO-NOT-INSTALL` is not
   enough; it has already been downloaded and failed once.
9. Backend deploys are manual until #419 is fixed: `--env-file .env.prod` with the **repo-root**
   compose file, after tagging the running image `rollback-<date>`.

Play publishing itself is automated —
`gh workflow run "Mobile Release" -f tag=vX.Y.Z -f play_track=internal`. A tag push alone builds and
signs but deliberately does not publish. Store-listing prerequisites are in
[`docs/PLAY_STORE_SETUP.md`](docs/PLAY_STORE_SETUP.md).

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
