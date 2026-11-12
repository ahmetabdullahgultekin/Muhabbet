# ADR 0030 — Trunk-based `main` and a build-once release flow

**Status:** Proposed (process) — pending owner approval; no code may be written, and no GitHub setting changed, against this ADR until approval is recorded in [`docs/architecture/multi-transport/APPROVAL.md`](../architecture/multi-transport/APPROVAL.md) (scope `process`) · **Date:** 2026-11-12 · **Source:** [decision brief §3, ADR-0030](../architecture/multi-transport/decision-brief-2026-11-12.md) · **Operational detail:** [`docs/process/branching-and-release.md`](../process/branching-and-release.md) · Supersedes the branch model in `CONTRIBUTING.md` (`main`/`develop`/`feature/*`) and the de-facto `dev` integration flow. Amends ROADMAP.md "The working method" step 4 (branch names) and "Release process" steps 5–9.

## Context

ROADMAP.md already claims the project is "incremental and trunk-based" with squash-merged PRs. The
repository does something else:

| Fact (evidence) | Consequence |
|---|---|
| `main` last moved 2026-08-18 (`418eb54`, v0.3.10); `dev` is ≥ 100 commits ahead (120 at `2434324` in this checkout) and unreleased for ~12 weeks (GOV-01, CICD-08, SSOT-01) | Production and Play run August code; "fixed" in CLAUDE.md means "fixed on `dev`" |
| GitHub reads issue templates, `schedule:` triggers, closing keywords and Dependabot config from the **default** branch only (GOV-01; [events that trigger workflows](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows), [issue templates](https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/configuring-issue-templates-for-your-repository)) | Every August governance repair on `dev` is inert: issue forms, Dependabot redesign, stale-branch cron, CONTRIBUTING fixes, detekt gate (R22) |
| 69 issues closed "completed" after the last release, 0 PR-linked (GOV-02) | The tracker counts unreleased, unverified work as done |
| Three documents prescribe three branch models; 52 of the last 100 `dev` commits are merge commits; 205 branches (GOV-07, GOV-11, SSOT-10) | Agents receive contradictory instructions |
| Mobile Release builds once on tag push and **again** on the publishing `workflow_dispatch`, which `--clobber`s the assets; the AAB sent to Play was never launched (CICD-10) | The 0.3.7 failure class (#633, launch crash behind a green build) is structurally possible on every release |
| Deploy runs `git pull origin main` and rebuilds on the production host; the tested SHA is not what ships; 12% deploy success over 50 runs, none since 2026-08-17 (CICD-07, CICD-08) | No artefact provenance; deploys race with merges |
| Release bodies are one-line CI text and are fed to Play as the tr-TR note; v0.3.7 is a lingering draft (GOV-09) | Users and Play see no known-issues text |

## Decision

1. **Trunk is `main`.** `dev` is merged into `main` once, open PRs are retargeted to `main`, and `dev`
   is deleted. There is no long-lived integration branch afterwards. The exact procedure, its
   preconditions and its rollback are in
   [`branching-and-release.md` §3](../process/branching-and-release.md#3-one-time-dev--main-merge).
2. **Squash-only, linear history.** Repository settings allow squash merges only; the PR title is the
   squash subject and follows ROADMAP's user-side sentence rule. "Allow merge commits" and "Allow
   rebase merging" are off; "Automatically delete head branches" is on. The one-time `dev` merge in (1)
   is the single, recorded exception and happens before the ruleset is created.
3. **Branch names** `<type>/<issue#>-<slug>` with `type ∈ {fix, feat, docs, ci, chore, refactor, spike}`.
4. **Two rulesets on `main`** (repository rulesets, not classic protection). A bypass actor
   bypasses *every* rule of a ruleset, required checks included, so checks and review are split:
   - **`main-checks`**: required status checks `Build & Test`, `Android Build`, `Secret Detection`
     (Gitleaks, once it runs on every PR, ADR-0031); require linear history; block force-push and
     deletion. **Empty bypass list** — nobody, the owner included, bypasses it.
   - **`main-review`**: require a PR; 1 approval (satisfiable once agents have their own identity,
     ADR-0032); code-owner review; conversation resolution. Bypass = owner, in pull-request mode.

   The owner's bypass therefore skips review only, never the checks. Tags `v*` are protected by two
   further rulesets, for the same reason: **`release-tags-create`** (restrict creations; bypass =
   repository admin role) and **`release-tags-immutable`** (restrict updates and deletions; empty
   bypass list).

   > **Reviewer note (open):** this tag-ruleset split amends the single "third ruleset" of the
   > first draft (github-governance §9.2, R-8a). One ruleset cannot both let the owner create tags
   > and forbid everyone, the owner included, from moving them.
5. **Release = build once, verify, promote the same artefact.** Release tags exist on `main` only;
   there are no `release/*` branches, hotfixes included — a hotfix is a patch tag on `main`, and
   unreleased work already on `main` must be flag-gated (see Why). The build job fails closed unless
   the tag commit is an ancestor of `origin/main`.
   `tag vX.Y.Z on main` → one build job produces the signed APK, AAB and `SHA256SUMS` → the owner
   installs **those bytes** on `emulator-5554` and launches them (ROADMAP release step 6) → a promote
   job uploads the **same AAB**, located by release-asset ID and checked against `SHA256SUMS`, to the
   chosen Play track. The promote job has no Gradle step and never uses `--clobber`. GitHub releases
   are published non-draft after the launch check.
6. **Backend deploy by artefact.** CI builds the image once and records its digest; deploy runs that
   digest (or, until a registry exists, that exact SHA), never `git pull` on the host. Success means
   the running build reports the expected commit, not only `/actuator/health` (CICD-07).
7. **Status axis.** The tracker distinguishes **merged (not verified)** → **released (in vX.Y.Z)** →
   **verified (observed by / where)**. An issue with a device-dependent criterion closes only at
   *verified*, and only the owner sets that (ADR-0032, P13) (GOV-02).
8. **Sequencing.** Applied only after Phase 0 exit and owner approval of scope `process` (owner
   decision 3). A one-time tracker cleanup (GOV-03) precedes filing any multi-transport epic.

## Why

- **One branch, one truth.** Every inert mechanism in GOV-01 becomes live by the merge alone, with no
  further work. A solo engineer with flag-gated agent work (`E2EConfig`, `MultiDeviceConfig`) gains
  nothing from a second long-lived branch, and the batch-release model is what stopped CD (CICD-08).
- **Configuration over prose.** GOV-07 shows prose policies were ignored by humans and agents alike;
  a ruleset and merge-method settings cannot be ignored.
- **The binary that ships must be the binary that was launched.** AGP/R8 builds are not guaranteed
  bit-reproducible (CICD-10), so "same tag" is not "same binary". Promotion of a hashed artefact is
  the only way the emulator step proves anything about what Play distributes.
- **Merged ≠ released ≠ verified.** The tracker is ROADMAP's "memory"; it must not overstate.

## Consequences

- One-time work, owner-driven: the merge, settings, rulesets, workflow trigger edits (`develop`/`dev`
  removed, `close-linked-issues.yml` deleted because native closing works on the default branch,
  Dependabot `target-branch` → `main`), retargeting, tracker cleanup. Listed in
  [`branching-and-release.md` §4](../process/branching-and-release.md#4-post-merge-configuration).
- **Merging `dev` into `main` would trigger a production deploy of ~12 weeks of unreleased code**
  through `deploy-hetzner.yml` (`workflow_run` on Backend CI for `main`). The deploy workflow is
  disabled before the merge and the first deploy afterwards is a deliberate, artefact-based release.
- `stale-branches.yml` (schedule) and `dependabot-auto-merge.yml` become live on merge; both are
  disabled before the merge and re-enabled only after review and after ADR-0031's runner change.
- detekt becomes a blocking gate on `main` (R22, fixed on `dev` in `db9e59f`).
- Mobile Release is split into a tag-triggered build job and a dispatch-triggered promote job; the
  ROADMAP release process (steps 5–9) is rewritten to match.
- CONTRIBUTING.md, ROADMAP.md and CLAUDE.md link to the process doc instead of restating it (GOV-11),
  after approval.

> **Reviewer note (open):** the required checks in Decision 4 can only report on PRs if PR CI runs
> somewhere. ADR-0031 forbids PR jobs on the production host and, until GitHub-hosted runners are
> unblocked (CICD-04) or a non-production runner exists, disables them. Creating the ruleset with
> required checks before that point blocks **every** merge. The brief orders ADR-0030/0031 together
> but does not state this dependency. Proposed order: (a) ADR-0031 runner change → (b) PR CI green on
> the new runners → (c) ruleset with required checks. Until (c), `main-checks` holds only linear
> history and the force-push/deletion blocks (empty bypass list); its required checks are added at
> (c). Meanwhile `main-review` requires PR and approval, and the PR template's "gates that ran" section carries locally measured evidence.

> **Reviewer note (open):** the brief says "install that exact signed APK/AAB on emulator-5554". An
> APK and an AAB are different binaries; Play serves APKs generated from the AAB. Installing the
> attached APK verifies the APK, not the AAB that is promoted. The process doc therefore requires the
> owner to also install a universal APK generated with `bundletool` **from the promoted AAB**. This
> is a refinement, not a deviation; the owner should confirm it.

## Alternatives considered

| Alternative | Rejected because |
|---|---|
| Keep `dev` as a long-lived integration branch (with a scheduled release-train PR) | Keeps the default-branch drift that made GOV-01's fixes inert; batches stopped CD for 12 weeks (CICD-08). Brief: rejected. |
| Make `dev` the default branch | Visitors land on unreleased code; releases would still be cut from a second branch; `close-linked-issues.yml`'s own header records this was rejected once already |
| Continue rebuilding on publish (`--clobber`) | Ships a never-launched binary (CICD-10, #633). Brief: rejected. |
| Merge commits or rebase merges on trunk | Breaks generated release notes and auto-closing; PR history unusable as a changelog (GOV MISS-04) |
| A single aggregate `ci-gate` required check (CICD proposal) | Sound, and a natural later step; the brief fixes the named checks now because they already exist and report today. Revisit when workflows are restructured. |

## Evidence

GOV-01, GOV-02, GOV-03, GOV-05, GOV-06, GOV-07, GOV-09, GOV-11, GOV MISS-04, CICD-07, CICD-08,
CICD-10, CICD-14, SSOT-01, SSOT-10, R22 — [findings register](../reviews/2026-11-12-findings-register.md).
External: [GitHub: events that trigger workflows](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows),
[configuring issue templates](https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/configuring-issue-templates-for-your-repository),
[linking a PR to an issue](https://docs.github.com/en/issues/tracking-your-work-with-issues/using-issues/linking-a-pull-request-to-an-issue),
[generated release notes](https://docs.github.com/en/repositories/releasing-projects-on-github/automatically-generated-release-notes).

## Verification

| # | Check | Method | Evidence |
|---|---|---|---|
| V1 | `dev` no longer exists; `main` contains every `dev` commit | `git ls-remote origin refs/heads/dev` empty; `git merge-base --is-ancestor pre-trunk/dev-<date> origin/main` exit 0 | Command output in the merge PR |
| V2 | Issue forms render on the web; scheduled workflows are listed | Open "New issue" on github.com; `gh workflow list` shows `Stale branch cleanup` active from `main` | Screenshot / output |
| V3 | Merge methods and ruleset | `gh api repos/{owner}/{repo}` → `allow_squash_merge: true`, `allow_merge_commit: false`, `allow_rebase_merge: false`, `delete_branch_on_merge: true`; `gh api repos/{owner}/{repo}/rulesets` lists `main-checks`, `main-review`, `release-tags-create` and `release-tags-immutable`; `main-checks` and `release-tags-immutable` have an empty `bypass_actors` | API output |
| V4 | Bypass does not skip checks | A test PR with a failing required check cannot be merged by the owner without the check passing (owner bypass of `main-review` offered, merge still blocked by `main-checks`) | Owner observation |
| V5 | Same artefact promoted | Promote job log shows asset ID and SHA-256 equal to `SHA256SUMS` from the build job; no Gradle task in the log | Run log |
| V6 | Launch check on the promoted bytes | Owner installs the release APK and the `bundletool` universal APK from the AAB on `emulator-5554`; `logcat` has no `FATAL`; screenshot | Owner note in the release issue (observed by / where) |
| V7 | Deploy by artefact | Deploy log shows the digest/SHA; the running backend reports the same commit | Run log + endpoint output |
| V8 | Status axis exists | A merged PR moves its issue to "Merged (needs verification)", not closed | Tracker |
