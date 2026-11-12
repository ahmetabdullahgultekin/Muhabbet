# Branching and release — the canonical process (ADR-0030)

> **Status: Proposed — pending owner approval.** Nothing in this document is in force until the owner
> records approval of scope `process` in
> [`docs/architecture/multi-transport/APPROVAL.md`](../architecture/multi-transport/APPROVAL.md). No
> code may be written, no GitHub setting changed and no branch merged or deleted under this document
> until that approval is recorded there (owner decisions 3 and 4).

| | |
|---|---|
| **Decision record** | [ADR-0030](../adr/0030-trunk-and-release-flow.md) |
| **Depends on** | [ADR-0031](../adr/0031-ci-trust-boundary.md) / [`ci-trust-boundary.md`](ci-trust-boundary.md) (where CI runs), [ADR-0032](../adr/0032-agent-identity-verification-gate.md) / [`agent-workflow.md`](agent-workflow.md) (who approves, who verifies) |
| **Replaces, once approved** | CONTRIBUTING.md branch model; ROADMAP.md "The working method" step 4 and "Release process" steps 5–9 become links here (GOV-11) |
| **Evidence** | GOV-01, GOV-02, GOV-05, GOV-06, GOV-07, GOV-09, GOV MISS-03, GOV MISS-04, CICD-07, CICD-08, CICD-09, CICD-10, CICD-13, CICD-14, SSOT-01, R22 |

## 1. The model in one table

| Topic | Rule |
|---|---|
| Trunk | `main`. No other long-lived branch. |
| Work branches | `<type>/<issue#>-<slug>`, short-lived, deleted on merge |
| Merge method | Squash only; PR title = squash subject; linear history |
| Gate | Rulesets on `main`: `main-checks` (required checks when they can run, linear history; no bypass) + `main-review` (PR + 1 approval; owner bypass in PR mode) (§4.3) |
| Release (mobile) | Tag on `main` → build once → owner launches those bytes on `emulator-5554` → promote the same AAB |
| Release (backend) | Build the image once in CI → deploy that digest/SHA → confirm the running commit |
| Tracker axis | merged (needs verification) → released (vX.Y.Z) → verified (observed by / where) |

## 2. Preconditions for the one-time merge

All must hold; record each with evidence in the merge PR body.

| # | Precondition | Why | Check |
|---|---|---|---|
| P-1 | Scope `process` is `Approved` in `APPROVAL.md`; Q-O9 answered | Owner decision 3 | `APPROVAL.md` |
| P-2 | Phase-0 emergencies done or explicitly scheduled: no runner on `cx43`, or the owner-recorded accepted-risk variant (ADR-0031 D2; CICD-01), `.env.prod` rotated (CICD-06) | The merge activates more PR-triggered workflows from `dev` (e.g. `dependabot-auto-merge.yml`) on the default branch | [incident record](security-incident-2026-11-env-prod.md) status |
| P-3 | SECURITY.md false phone-hash claim fixed **on `dev`** | The `dev` rewrite kept it; the merge would re-publish it (GOV MISS-03, GOV-08) | `grep -n "SHA-256 hashes" SECURITY.md` on `dev` returns nothing |
| P-4 | `main` has no commits that `dev` lacks | Otherwise the merge is not a pure promotion | `git rev-list --count origin/dev..origin/main` = 0 (it was 0 on 2026-11-12 in this checkout, 120 the other way — re-measure) |
| P-5 | Deploy, stale-branch and Dependabot auto-merge workflows disabled, or (stale-branch only) without a `schedule:` trigger | Merging into `main` runs Backend CI on `main` → `deploy-hetzner.yml` (`workflow_run`, branch `main`) deploys **12 weeks of unreleased code**; `stale-branches.yml`'s cron (Mondays 04:00 UTC) and `dependabot-auto-merge.yml` go live from the default branch. `stale-branches.yml` exists only on `dev`, so `gh workflow disable` by name can fail before the merge (its own header: "not found on the default branch") | `gh workflow list --all` shows Deploy and Dependabot auto-merge as `disabled_manually`; `git show origin/dev:.github/workflows/stale-branches.yml` has no `schedule:` (or, if step 7a was used, it is `disabled_manually` immediately after step 7) |
| P-6 | Backend gate measured with Docker running on `dev` HEAD | `dev` carries Spring-config and migration changes since August; only the ten Testcontainers classes catch that class of failure (CLAUDE.md, #598) | `./gradlew :backend:test` with Docker; counts from `backend/build/test-results/test/*.xml` |
| P-7 | Open PRs into `dev` listed | Retarget list | `gh pr list --base dev --state open --json number,title,headRefName` (at review time: #690, #718, #736, #738 — re-list) |

## 3. One-time `dev` → `main` merge

### 3.1 Procedure

Run from a clean full clone (this session's clone is shallow; do not use it).

| Step | Who | Command / action | Verify |
|---|---|---|---|
| 1 | Owner | `git fetch origin --prune --tags` | — |
| 2 | Owner | `git merge-base --is-ancestor origin/main origin/dev && echo ancestor` | prints `ancestor` (else stop: P-4 fails) |
| 3 | Owner | Snapshot both tips: `D=$(date +%F); git tag -a "pre-trunk/main-$D" origin/main -m "ADR-0030 snapshot"; git tag -a "pre-trunk/dev-$D" origin/dev -m "ADR-0030 snapshot"; git push origin "pre-trunk/main-$D" "pre-trunk/dev-$D"` | `git ls-remote --tags origin 'pre-trunk/*'` shows both |
| 4 | Owner | Disable workflows (P-5): `gh workflow disable "Deploy to Hetzner VPS"`, `gh workflow disable "Dependabot auto-merge"`. For `stale-branches.yml` (present only on `dev`): **preferred** — an owner PR into `dev` that removes (comments out) its `schedule:` block, merged before step 5; it is re-added after review (§4.2) | `gh workflow list --all`; `schedule:` absent on `dev` |
| 5 | Owner | `gh pr create --base main --head dev --title "Promote dev to main (ADR-0030 one-time merge)" --body-file <body with P-1…P-7 evidence>` | PR checks report |
| 6 | Owner | Read the PR's check results; compare any red check with the same check on `dev` before blaming the merge (ROADMAP DoD 4) | Every red check explained in the PR |
| 7a | Owner | Only if the `schedule:` was **not** removed in step 4: schedule steps 5–7 away from Sunday/Monday morning UTC, and immediately after step 7 run `gh workflow disable stale-branches.yml` (by file name/id, now resolvable on the default branch) | `gh workflow list --all` shows it `disabled_manually` before the next Monday 04:00 UTC |
| 7 | Owner | Merge with a **merge commit** — the single permitted exception: `gh pr merge <PR> --merge`. Classic protection on `main` still requires an approval the author cannot give, so this needs `--admin`; record that in the PR. (A fast-forward of `main` to `dev` is equivalent if P-4 holds and the owner prefers it.) | `git fetch && git diff --stat "pre-trunk/dev-$D" origin/main` is empty |
| 8 | Owner | Retarget every PR from P-7: `gh pr edit <n> --base main` | `gh pr list --base dev --state open` is empty |
| 9 | Owner | Close Dependabot's grouped PR against the old config (#743 at review time) | — |
| 10 | Owner | Apply §4 (settings, rulesets, workflow edits). The workflow edits go in **one** follow-up PR into `main` | §4 checks |
| 11 | Owner | Delete `dev`: `git push origin --delete dev` | `git ls-remote origin refs/heads/dev` empty |
| 12 | Owner | First deliberate release from `main` under §6–§7 (GOV-09 suggests shipping the stalled v0.3.11 as the first release under the new flow — owner's call); then `gh workflow enable "Deploy to Hetzner VPS"` only after its trigger guard (§4.2) is merged | Release issue with observed-by note |
| 13 | Owner / agent | One-time tracker cleanup (GOV-03) before any multi-transport epic is filed; move the 69 post-v0.3.10 "completed" closures to *merged (needs verification)* (GOV-02) | Tracker |

### 3.2 Rollback

| Situation | Action |
|---|---|
| Before step 7 | Close the PR; re-enable the workflows from step 4; nothing else changed. |
| After step 7, before step 11 | Open a PR that reverts the merge: `git revert -m 1 <merge-sha>`. Note that a reverted merge cannot simply be re-merged later; to retry, revert the revert. Alternatively restore `main` to `pre-trunk/main-<D>` with an owner bypass push — this rewrites the public default branch and is the last resort. |
| After step 11 | Restore `dev`: `git push origin "pre-trunk/dev-<D>^{commit}:refs/heads/dev"`; retarget PRs back with `gh pr edit <n> --base dev`. |
| Settings (§4) | Revert each setting by hand; record the before-values in the PR body at step 10 so they can be restored. |

Do not delete the `pre-trunk/*` tags for at least one release cycle.

## 4. Post-merge configuration

### 4.1 Repository settings

| Setting | Value | Verify |
|---|---|---|
| Allow merge commits | off | `gh api repos/{owner}/{repo} --jq .allow_merge_commit` → `false` |
| Allow rebase merging | off | `.allow_rebase_merge` → `false` |
| Allow squash merging | on; default message = PR title (and description) | `.allow_squash_merge` → `true`; `.squash_merge_commit_title` → `PR_TITLE` |
| Automatically delete head branches | on | `.delete_branch_on_merge` → `true` |
| Private vulnerability reporting | on (GOV-08); enabled earlier, in github-governance §12.1 step 2, not with these merge settings | Security tab |

### 4.2 Workflow edits (one PR into `main`)

| File | Change | Finding |
|---|---|---|
| `backend-ci.yml`, `mobile-ci.yml` | `push.branches: [main]`; `pull_request.branches: [main]`; drop `dev`/`develop` | GOV-07, SSOT-10 |
| `mobile-ci.yml` | Remove the `github.base_ref != 'dev'` conditions (now dead); decide whether APK assembly runs on every PR or only on `main` push (runner cost, ADR-0031) | — |
| `security.yml` | `push`/`pull_request` on `main`; remove the trigger `paths:` filter; path filtering moves into jobs; Gitleaks unfiltered | GOV MISS-01, CICD-12 |
| `close-linked-issues.yml` | Delete; native closing keywords work on the default branch | GOV-02 |
| `.github/dependabot.yml` | `target-branch: "main"` (or remove the key); add `github-actions` and `docker` ecosystems | CICD-13 |
| `deploy-hetzner.yml` | Guard and artefact changes from [`ci-trust-boundary.md` §5](ci-trust-boundary.md#5-per-workflow-least-privilege-on-dev) | CICD-07, CICD MISS-01 |
| `stale-branches.yml` | Review defaults before re-enabling (it deletes branches by age on a schedule) | GOV-01 |
| `mobile-release.yml` | Split into build-on-tag and promote-on-dispatch (§6) | CICD-10 |

### 4.3 Rulesets

**Rules on the default branch** (summary; split into two rulesets below):

| Rule | Value |
|---|---|
| Require a pull request | yes; 1 approval; dismiss stale approvals on push; require conversation resolution |
| Require review from code owners | yes (ADR-0032 CODEOWNERS) |
| Required status checks | `Build & Test`, `Android Build`, `Secret Detection` — **only once PR CI runs off the production host** (ADR-0031). Until then: no required checks, and the PR template's "gates that ran" section carries locally measured evidence (ADR-0030 reviewer note). |
| Require linear history | yes |
| Block force pushes, block deletion | yes |

A bypass actor bypasses every rule in a ruleset, required checks included; no per-rule setting
changes that. Rules are therefore split across two rulesets on the default branch:

| Ruleset | Rules | Bypass list |
|---|---|---|
| `main-checks` | Require linear history; block force pushes and deletion (from day one); required status checks `Build & Test`, `Android Build`, `Secret Detection` **added** only once PR CI runs off the production host (see above) | **Empty** — nobody bypasses it, the owner included |
| `main-review` | Require a PR; 1 approval; dismiss stale approvals; code-owner review; conversation resolution | Owner, in pull-request mode |

The owner's bypass skips review only; the checks live in a ruleset nobody can bypass (ADR-0030 D4,
ADR-0032 D2).

**Tag rulesets** (target: tags `v*`), split for the same bypass reason (ADR-0030 D4,
github-governance §9.2): **`release-tags-create`** restricts creation (bypass: repository admin role,
i.e. the owner; later the release environment) and **`release-tags-immutable`** blocks update and
deletion (empty bypass list). GitHub releases are made immutable once published
where the account supports it.

## 5. Branches and commits

| Item | Rule |
|---|---|
| Name | `<type>/<issue#>-<slug>`, lower-case, slug ≤ 5 words. `type ∈ {fix, feat, docs, ci, chore, refactor, spike}`. Example: `fix/673-outbox-delete-on-ack`. |
| No issue | Only `docs/` and `chore/` may omit the number (`docs/process-typos`). Anything else is filed first ("the tracker is the memory"). |
| Never pushed | `worktree-*`, `tmp*`, `wip/*` (GOV-12) |
| PR | Draft PR opened with the first push, body contains `Closes #N` (the claim, ADR-0032) |
| PR title = squash subject | What the **user** experienced, not what the code does (ROADMAP: `Deliver the messages that Redis was silently dropping`) |
| Language | English for engineering artefacts; Turkish allowed in user-facing release summaries (GOV-15) |
| Migrations | Reserve the Flyway `V` number in the PR title; expand/contract — a destructive change ships one release after the code stops using the column (CICD-09) |
| Size | One concern per PR; a PR that needs a heading per subsystem is two PRs |

> **Reviewer note (open):** Claude Code cloud sessions create branches named `claude/<slug>` (this
> design package was authored on `claude/muhabbet-p2p-architecture-wdan5r`), which does not match
> `<type>/<issue#>-<slug>`. The brief does not address harness-assigned names. Options: (a) accept
> `claude/<issue#>-<slug>` as a sanctioned agent form, or (b) agents rename the branch before opening
> the PR. Do not enforce branch names with a ruleset until this is decided, or harness pushes fail.

## 6. Versioning (from ROADMAP.md, unchanged)

| Rule | Detail |
|---|---|
| SemVer | `MAJOR.MINOR.PATCH`; tags `vX.Y.Z` on `main` only — hotfixes included (§9) |
| Pre-1.0 | The minor number carries breaking changes (`0.3.x → 0.4.0` may break clients) |
| Patch | Defects only |
| 1.0.0 | Reserved for the first release with end-to-end encryption **on** (not negotiable by convenience) |
| Milestones | Title = the version it produces, optionally with its name: `0.4` "the app tells the truth", `0.5.0` Honest, `0.6.0` Reachable, `0.7.0` Live, `1.0.0` Encrypted; post-1.0 (brief): `1.1` Direct, `1.2` Nearby, `1.3` Relay, `1.4` Off-grid hardware. `0.4.0 — Installable` and Tier 1/2/3 are retired (GOV-05). |
| Source of the number | `versionName`/`versionCode` in `mobile/composeApp/build.gradle.kts`; `:mobile:composeApp:verifyBuildInfoVersion` checks `BuildInfo.kt`; the build job **must** assert `tag == "v$versionName"` (new, CICD-10 — `mobile-release.yml` does not compare today) |
| `versionCode` | Strictly increasing per Play upload |

## 7. Mobile release: build once, verify, promote

### 7.1 Steps

| # | Step | Who | Artefact / evidence |
|---|---|---|---|
| R1 | Milestone exit criteria met; device criteria have owner "observed by / where" notes | Owner | Milestone |
| R2 | Gates: `./gradlew :backend:test :shared:jvmTest` with Docker running; base vs branch counts | Agent or owner | Numbers in the release PR |
| R3 | `CHANGELOG.md`: `Unreleased` → version, with **Known issues** written from the user's side | Agent drafts, owner edits | CHANGELOG |
| R4 | Bump `versionName`/`versionCode`, run `verifyBuildInfoVersion`, squash-merge, tag `vX.Y.Z` on the merge commit, push the tag | Owner | Tag |
| R5 | **Build job** (tag push, `release` environment, ADR-0031): checkout with `fetch-depth: 0` (the default depth-1 tag checkout has no `origin/main`); assert `tag == v$versionName`; assert the tag commit is on `main`, **failing closed**: `git rev-parse --verify origin/main` must succeed (else fail), then `git merge-base --is-ancestor "$TAG" origin/main` must exit 0; build the signed APK and AAB **once**; assert the signer SHA-256 (`EXPECTED_SIGNER_SHA256`, existing check); write `SHA256SUMS`; create a **draft** release with the three files; record asset IDs in the job summary | CI | Draft release, `SHA256SUMS`, job summary |
| R6 | **Launch check on those bytes** on `emulator-5554` (Windows host): `gh release download vX.Y.Z -p "*.apk" -p "*.aab" -p SHA256SUMS`; `sha256sum -c SHA256SUMS`; uninstall the old build; `adb install muhabbet-X.Y.Z.apk`; `adb shell am start -n com.muhabbet.app/.MainActivity`; `adb logcat -d \| grep FATAL` must be empty; screenshot. Then the AAB: `bundletool build-apks --bundle=muhabbet-X.Y.Z.aab --output=u.apks --mode=universal` (**no `--ks`**: bundletool signs with the local debug keystore, so the release key never reaches the workstation; this verifies the AAB's contents, not its signature — R5's signer assertion covers the signature), uninstall, `bundletool install-apks --apks=u.apks`, launch, `FATAL` grep, screenshot | **Owner only** | Release issue comment: observed by, where (`emulator-5554`), date, both hashes, screenshots |
| R7 | Publish the GitHub release **non-draft** with generated notes plus a hand-written Turkish summary ≤ 500 characters (Play shows the first 500) (GOV-09) | Owner | `gh release list` shows it as Latest |
| R8 | **Promote job** (`workflow_dispatch` on the tag ref, input `track`; environment `release`): dispatch with `gh workflow run "Mobile Release" --ref vX.Y.Z -f track=<track>`. `--ref` is mandatory: the `release` environment admits only tag refs `v*`, and a dispatch without it runs on `refs/heads/main` and is rejected. The workflow definition is then taken from the tag, so the promote job that runs is the one released with that version. The tag is `github.ref_name`, not an input. download the AAB by **release-asset ID**, verify against `SHA256SUMS`, upload to Play. No checkout of source, no Gradle, no `--clobber`. Production track uses a staged rollout (`status: inProgress`, small `userFraction`), completed by a separate dispatch (CICD-10) | Owner dispatches | Run log showing asset ID and matching hash |
| R9 | Status axis: issues fixed in the release move *merged* → *released (vX.Y.Z)* | Agent | Tracker |

The emulator cannot sign off camera, microphone, haptics or colour on a real panel; R6 says so in
its note (CLAUDE.md).

### 7.2 Mobile rollback

| Situation | Action |
|---|---|
| R6 fails | Do not publish. Delete the draft release and the tag; fix on `main`; new patch number. Never reuse a version number. |
| Problem after R8 on a staged rollout | Halt the rollout in Play Console (or the halt dispatch); ship a patch release through R1–R8 |
| Problem after 100% | Patch release; Play cannot roll back to a lower `versionCode` |

## 8. Backend release: deploy by artefact

| # | Step | Detail |
|---|---|---|
| D1 | CI builds the image once on `main` push | Today `backend-ci.yml`'s Docker job builds with `push: false`; target pushes to a registry by digest with provenance and SBOM (CICD-07). Until a registry exists, the artefact identity is the commit SHA that passed CI. |
| D2 | Deploy job receives the digest (or `workflow_run.head_sha`) | Never `git pull origin main` on the host (`deploy-hetzner.yml` does today) |
| D3 | Before switching | Tag the running image `rollback-<date>` (CLAUDE.md); `pg_dump` before any migration (CICD-09) |
| D4 | Start with `--env-file .env.prod` and the **repo-root** compose file | CLAUDE.md "Deployment & Infrastructure" |
| D5 | Success = running commit equals the deployed SHA | Requires exposing the build's commit id (e.g. via `/actuator/info`), which the backend does **not** do today (no git/build properties configured) — a 0.4-era chore; until then, compare the image label/creation time and `/actuator/health` and say which was checked |
| D6 | Rollback | Re-pin the previous digest/`rollback-<date>` image. Safe only if every migration since then is additive (expand/contract, CICD-09; #295). |

## 9. Hotfix

There is no hotfix branch and no `release/*` branch (ADR-0030 D5). A fix is `fix/<issue#>-<slug>` →
PR into `main` → patch tag `vX.Y.(Z+1)` on `main` → §7/§8. Unreleased work already on `main` must be
behind a default-off flag (as `E2EConfig` / `MultiDeviceConfig` are), so a patch tag on `main` ships
it dark. If work on `main` cannot be flag-gated and must not ship, revert it on `main` first, tag, then
re-land it. Tagging off-`main` commits would fail R5's ancestry check by design.

## 10. Tracker status axis (with ADR-0032)

| Status | Entered when | Set by |
|---|---|---|
| In progress | Draft PR with `Closes #N` exists | Agent |
| In review | PR ready for review | Agent |
| Merged — needs verification | PR squash-merged into `main` (Projects built-in "Pull request merged" workflow) | Automation |
| Released (vX.Y.Z) | The tag containing the merge is published | Agent at R9 |
| Verified (observed by / where) | Logic-only items: CI evidence on the released SHA. Items with a device criterion: owner observation | Owner for device items; agent may propose with evidence for logic-only items |
| Done / closed | After *Verified* | Owner |

GitHub closes an issue when a PR with `Closes #N` merges into the default branch. For work with a
device criterion, the `[O]` criterion lives in a parent issue and the PR `Closes` an implementation
sub-issue, so the parent stays open until the owner verifies it (GOV-02; github-governance §3, §10,
R-7 option (a)). A bare `Refs #N` fails ADR-0032 check A6; it is allowed only if the owner adopts
R-7 option (b).
