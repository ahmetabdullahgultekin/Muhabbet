# CI trust boundary — operational detail (ADR-0031)

> **Status: Proposed — pending owner approval.** Nothing in this document is in force until the owner
> records approval in
> [`docs/architecture/multi-transport/APPROVAL.md`](../architecture/multi-transport/APPROVAL.md) —
> scope `phase-0` for the emergency items marked **P0** below (the brief recommends authorising them
> now, independently of design approval), scope `process` for the rest. No code may be written and no
> workflow, runner, environment or secret changed until that approval is recorded there.

| | |
|---|---|
| **Decision record** | [ADR-0031](../adr/0031-ci-trust-boundary.md) |
| **Incident record** | [`security-incident-2026-11-env-prod.md`](security-incident-2026-11-env-prod.md) (CICD-06, CICD-01) |
| **Inputs read** | `.github/workflows/*.yml` and `.github/dependabot.yml` on `dev` @ 2434324; CICD and GOV findings |
| **Evidence** | CICD-01…CICD-17, CICD MISS-01…03, GOV MISS-01, GOV-13, R1, R2, R14, R19 |

## 1. Target runner topology

```text
            pull_request / push / schedule                 tag v* (on main)          main push (CI green)
                        │                                        │                          │
           GitHub-hosted ephemeral runners             GitHub-hosted ephemeral     deploy job, environment
           (ubuntu-latest; macos-* for iOS)            runner, environment         `production` (reviewer),
           no secrets on PRs; contents:read            `release` (reviewer,        hands a digest/SHA to the
                                                       tags v*) → signed APK/AAB   host via a forced-command
                                                                                   SSH key or host-side pull
                                                                                            │
                                                                              Hetzner cx43: runs the backend.
                                                                              NO Actions runner.
```

| Runner | Today (2026-11-12) | Target | Interim (billing lock not cleared, no second box) |
|---|---|---|---|
| `muhabbet-cx43` (was the production host until the move to mordecai; now stale for Muhabbet, user `deploy` in `docker` group) | Runs every job of every workflow, PRs included | **Deregistered** | **Runner service stopped and deregistered (P0)**; no CI until hosted runners or a separate box. Accepted-risk variant only (owner decision, ADR-0031 D2): kept for `main`/tag jobs with the mitigations of §6 I2 |
| `muhabbet-mordecai` (the owner's machine and **the current production host** — identified 2026-11-12) | Takes jobs since ≤ 2026-10-26, Dependabot PRs included, with Docker access | **Deregistered** | Runner service stopped and deregistered, or labels changed to one nothing targets (**P0**); the same reasoning as for cx43 applies, and applies first |
| GitHub-hosted | Refused: account billing lock (CICD-04) | All PR/push/schedule CI, release build | — |
| Non-prod self-hosted (optional, e.g. device lab, netem) | — | Dedicated VM, `--ephemeral` JIT registration, unique custom label, `push: main`/tag workflows only, no release secrets (CICD-17) | — |

Precondition for the target: clear the billing lock on the account (or move the repository to a free
organisation). The repository is public, so hosted runners are free (CICD-04).

## 2. Which workflow runs where

| Workflow (file on `dev`) | Triggers today | Jobs | Today | Interim | Target |
|---|---|---|---|---|---|
| Backend CI (`backend-ci.yml`) | `workflow_dispatch`, `push` `[main, develop]`, `pull_request` `[main, dev, develop]` | `build-and-test` ("Build & Test"), `docker` | cx43/mordecai | none (no runner on cx43); owner runs gates locally | hosted |
| Mobile CI (`mobile-ci.yml`) | same pattern; `ios-framework` on `macos-latest` only if `vars.ENABLE_MACOS_CI` | `android-build` ("Android Build"), `ios-framework` | cx43/mordecai | as Backend CI | hosted (+ macOS) |
| Security & Quality (`security.yml`) | `push`/`pull_request` `[main, develop]` with paths; weekly cron | `dependency-check`, `docker-scan`, `codeql` | cx43/mordecai (only on `main` in practice) | none (no runner on cx43) | hosted; PRs to `main`, no trigger paths filter |
| Secret Scan (`secret-scan.yml`, 2026-11-12, F25) | `push` every branch and tag; `pull_request` every base; weekly cron (full history); `workflow_dispatch` | `gitleaks` ("Secret Detection") | cx43/mordecai | none (no runner) — run `gitleaks git .` locally | hosted |
| Mobile Release (`mobile-release.yml`) | tag `v*`; `workflow_dispatch` (tag, play_track) | `release` | cx43 (signing key decoded on the production host) | none; release builds wait for hosted runners or the accepted-risk variant | hosted, environment `release`; split build/promote (ADR-0030) |
| Deploy to Hetzner VPS (`deploy-hetzner.yml`) | `workflow_dispatch` only, on `main`, with a required `expected_hostname` (the `workflow_run` trigger was removed 2026-11-12, F15/F33/F57) | `deploy` (`environment: production`) | cx43/mordecai | disabled (ADR-0030 P-5); owner deploys by hand per CLAUDE.md | hosted job + forced-command key, or host pull |
| ~~Dependabot auto-merge (`dependabot-auto-merge.yml`)~~ | — | — | — | **deleted 2026-11-12** (F16/F24): every Dependabot PR is merged by a person | not reintroduced while any CI runs on a persistent runner |
| Close linked issues (`close-linked-issues.yml`) | `pull_request` closed into `dev` | `close` | cx43/mordecai | no runner (jobs queue) until trunk | **deleted** (native closing on `main`, ADR-0030) |
| Stale branch cleanup (`stale-branches.yml`) | cron (inert: default-branch only), `workflow_dispatch` | `cleanup` | cx43 when dispatched | disabled until reviewed | hosted |

## 3. Environments

| Environment | Secrets (moved from repository level, then repository copies deleted) | Deployment policy | Reviewers | Used by |
|---|---|---|---|---|
| `release` | `MUHABBET_KEYSTORE_BASE64`, `MUHABBET_KEYSTORE_PASSWORD`, `MUHABBET_KEY_ALIAS`, `MUHABBET_KEY_PASSWORD`, `PLAY_SERVICE_ACCOUNT_JSON` | Selected tags: `v*` | Owner (self-review allowed) | Mobile Release build job (tag push) and promote job — the latter dispatched on the tag ref, `gh workflow run "Mobile Release" --ref vX.Y.Z -f track=<track>`; a dispatch on `main` is rejected by this policy (branching-and-release §7.1 R8) |
| `production` | `VPS_HOST`, `VPS_USER`, `VPS_SSH_KEY` | Selected branches: `main` | Owner | Deploy |
| (none) | `GOOGLE_SERVICES_JSON`, `DEBUG_KEYSTORE` | — | — | Mobile CI; never exposed to fork PRs; written to `$RUNNER_TEMP`, deleted in `if: always()` (CICD MISS-03) |

"Restricted to tags on `main`" (brief) is implemented as: the `v*` tag ruleset (ADR-0030) limits who
creates release tags, and the build job (checkout `fetch-depth: 0`) fails closed unless
`origin/main` resolves and `git merge-base --is-ancestor "$TAG" origin/main` exits 0 (ADR-0031
reviewer note). There are no `release/*` branches (ADR-0030 D5).

`production` already exists on the deploy job; whether its secrets are environment-scoped and
whether it has reviewers/branch policy is **unknown** (CICD MISS-02). Verify with
`gh api repos/{owner}/{repo}/environments/production` and `gh secret list` (repository level) before
assuming anything.

## 4. SHA pinning

| Item | Rule |
|---|---|
| Scope | Every `uses:` reference. **Done on this branch 2026-11-12 (F35):** all pinned, none left on a tag or branch. Check with `grep -h 'uses:' .github/workflows/*.yml \| grep -vE '@[0-9a-f]{40} # v'` (must print nothing). Pins take effect on `main` only when merged there — the weekly schedule reads `main` (F14). |
| Form | `uses: owner/repo@<40-hex-sha> # vX.Y.Z` |
| Trivy | Remove `aquasecurity/trivy-action@master` (`security.yml`, two steps); pin a release ≥ v0.35.0 per Aqua; `exit-code: '1'`, `severity: CRITICAL`, `ignore-unfixed: true` (CICD-05, CICD-11) |
| Bumps | Dependabot `github-actions` ecosystem on `main` (CICD-13) |
| Lint | A job running actionlint and zizmor; fails on unpinned refs, `${{ }}` template injection in `run:`, and missing/over-broad `permissions` |
| Seed cache | `infra/scripts/seed-runner-action-cache.sh` now reads the SHAs from the workflows and seeds by SHA, so it can no longer hold a ref Dependabot cannot see; delete it once CI is off self-hosted (CICD-05) |

Resolve a tag to a SHA with `gh api repos/<owner>/<repo>/commits/<tag> --jq .sha` and record both.

## 5. Per-workflow least privilege (on `dev`)

Current state read from the files; target applies to every workflow: `permissions: {}` at the top,
grants per job, `persist-credentials: false` on every checkout, secrets only through `env:`,
`timeout-minutes` on every job (CICD-15).

| Workflow | `permissions` today | Target per job | Other required changes |
|---|---|---|---|
| `backend-ci.yml` | none declared (repository default; read-only per PR logs) | `build-and-test`: `contents: read`; a separate same-repo-only reporting job with `checks: write`, `pull-requests: write` — or delete the two reporters that 403 today (`mikepenz/action-junit-report`, `madrapps/jacoco-report`) (CICD-11); `docker`: `contents: read` | Remove `continue-on-error` on JaCoCo verification and make it a ratchet (CICD-11); timeout 30 |
| `mobile-ci.yml` | none declared | `android-build`, `ios-framework`: `contents: read` | `DEBUG_KEYSTORE` and `GOOGLE_SERVICES_JSON` are interpolated as `${{ secrets.* }}` inside `run:` — move to `env:`; write the keystore under `$RUNNER_TEMP`, delete in `if: always()`; remove the `find /` step (CICD-15, CICD MISS-03); timeout 40 |
| `security.yml` | workflow: `contents: read`, `security-events: write` | `secrets-scan`: `contents: read`; scanners uploading SARIF: `contents: read`, `security-events: write` | Gitleaks on every PR and push to `main`, no trigger paths filter (GOV MISS-01); CodeQL also covering `:mobile:composeApp` (CICD-12); image scan on schedule; timeout 30 |
| `mobile-release.yml` | workflow: `contents: write` | build job: `contents: write` (release assets) + `id-token: write`, `attestations: write` only if provenance attestation is added; promote job: `contents: read` | `environment: release`; tag-on-`main` check; no `--clobber`; split build/promote; timeout 45 |
| `deploy-hetzner.yml` | none declared | `deploy`: `contents: read` (or `{}`) | `if:` gains `github.event.workflow_run.event == 'push' && github.event.workflow_run.head_repository.full_name == github.repository`; deploy `workflow_run.head_sha`, not `git pull origin main` (CICD MISS-01, CICD-07); `appleboy/ssh-action` pinned; job timeout 20 |
| ~~`dependabot-auto-merge.yml`~~ | — | — | Deleted 2026-11-12 (F16/F24) |
| `secret-scan.yml` | workflow: `contents: read`, `pull-requests: read` | same | PR comments off (a public comment would repeat the finding); full history on schedule |
| `close-linked-issues.yml` | workflow: `issues: write`, `pull-requests: read` | — | Deleted after trunk (ADR-0030) |
| `stale-branches.yml` | workflow: `contents: write`, `pull-requests: read` | same, job-scoped | Review deletion defaults before the cron goes live on `main` |

## 6. Interim controls (P0, until hosted runners or a second box)

| # | Control | How | Verify |
|---|---|---|---|
| I1 (**P0**) | Fork PR approval | Settings → Actions → General → "Require approval for all external contributors" | Setting screenshot |
| I2 (**P0**) | No PR- or branch-supplied code on cx43 | **Stop and deregister the runner service on `cx43`** (`./svc.sh stop && ./svc.sh uninstall`, then `./config.sh remove --token <removal token>` on the host). No workflow-level guard suffices: workflow files come from the PR/branch being run, so `if: github.event_name != 'pull_request'` or removing `pull_request` triggers stops only well-behaved PRs — a same-repo PR deletes the `if:`, a PR adds a new `on: pull_request` workflow targeting `[self-hosted, linux, x64]`, or any write-access identity pushes a branch with an unfiltered `on: push` workflow. Workflow-restricted runner groups exist only for organisations/enterprises, not a user-owned repository. The fork guard `head.repo.full_name == github.repository` is likewise insufficient. **Accepted-risk variant** (owner decision with expiry in `APPROVAL.md`): keep `cx43` for `main`/tag jobs only with all of — no write-access identity but the owner; agent GitHub App without `workflows` permission (ADR-0032 D1); **no agent session holding an owner credential with the `workflow` scope or contents-write** (until ADR-0032 D1 is proven for the harness the variant is not available: agent sessions push with the owner's credential today, and commit 235c63dd, made by an agent, changed three workflows); the **`release-tags-create` ruleset** created in Phase 0 before the variant takes effect (otherwise any contents-write identity can push a `v*` tag that runs `mobile-release.yml` on `cx43` with the signing secrets); I1; the `if:` guard as hygiene; I5. After deregistration, `deploy-hetzner`, the weekly `security.yml` cron and Backend CI on `main` pushes queue and silently expire after 24 h; manual deploys (needed for rotation) follow CLAUDE.md with `--env-file .env.prod` and a `rollback-<date>` tag | Settings → Actions → Runners lists no runner on `cx43`; `systemctl list-units 'actions.runner.*'` on the host is empty. Variant: collaborators = owner only; App has no `workflows` permission; a throwaway branch pushed by the agent identity adding an `on: push` workflow on the self-hosted labels is refused and no job appears on `cx43`; a `v*` tag pushed by the agent identity is refused |
| I3 (**P0**) | `mordecai` is production (F24) — treat it exactly as I2, and first | Stop and deregister its runner service; remove `deploy` from the `docker` group; inspect `~/.gradle/init.d`, `~/.m2`, `~/.android` and `_work/_tool` for planted files; rotate anything Docker could read there **after** the runner is gone | Runners page; `systemctl list-units 'actions.runner.*'` empty on mordecai |
| I4 (**P0**) | Dependabot auto-merge | File deleted on this branch; on `main` until merged: `gh workflow disable "Dependabot auto-merge"` | `gh workflow list --all` |
| I5 | Fresh Gradle home on any remaining self-hosted job (accepted-risk variant only) | `GRADLE_USER_HOME: ${{ runner.temp }}/gradle-home` per job (CICD-02 path 2: init-script planting) | Job log |
| I6 | Release builds only after I2 | Signing on cx43 only under the accepted-risk variant, never as a boundary; no long-lived key of ADR-0031 D10 is ever created on cx43 | — |

Consequence: there is no CI during the interim (accepted-risk variant: `main`/tag only). The owner runs the gates locally and records counts
in the PR (ADR-0030 reviewer note on required checks).

## 7. Definition of "in force"

ADR-0031 is in force when F1–F8 of its Verification table are recorded as passed. 1.2 (device
certificates) and 1.3 (uplink key) cannot start before that (brief §13; ADR-0018; ADR-0025), and no
long-lived signing, device-CA, uplink or crypto key is generated before it.

## 8. Open questions (owner; not readable from this session)

1. Current value of the fork-PR approval setting (CICD-01).
2. ~~What `mordecai` is~~ — answered 2026-11-12: the owner's own machine, now the production host (CICD-03).
3. `production` environment protection and secret scoping (CICD MISS-02).
4. Whether secret scanning, push protection and Dependabot alerts are enabled.
5. The cause of the account billing lock and whether it can be lifted (CICD-04).
6. Whether `/opt/projects/infra/deploy.sh` (called by `deploy-hetzner.yml`) is version-controlled anywhere, and who can modify it.
7. Whether Play App Signing is enabled (is `MUHABBET_KEYSTORE` the upload key or the app-signing key?) and whether an offline backup exists (#422).

## 9. Owner steps that no workflow file can do (F17, F18, F24 — 2026-11-12)

The YAML on this branch closes what YAML can close: SHA pins, no automatic deploy, no automatic
Dependabot merge, secrets only through `env:`, the debug keystore under `$RUNNER_TEMP`, no `find /`,
least-privilege `permissions:` everywhere, and a secret scan on every branch. **None of that is a
trust boundary while jobs run on a persistent runner on a production machine**: a job runs as the
runner's user, can write that user's home, the runner's own `bin/`, the shared Gradle home and the
Docker image store, and a later job — a release build, a deploy — inherits all of it. Workflow files
come from the branch being run, so a guard written in YAML can be deleted by the branch it guards.

Do these in order. Each is a setting or a host change, not a commit.

| # | Step | Where | Verify |
|---|---|---|---|
| O1 | **No runner on any production host.** Stop and deregister `muhabbet-mordecai` (mordecai **is** production) and `actions-runner-muhabbet` on cx43: `sudo ./svc.sh stop && sudo ./svc.sh uninstall && ./config.sh remove --token <removal token>` in each runner directory | the two hosts; Settings → Actions → Runners | Runners page lists neither; `systemctl list-units 'actions.runner.*'` empty on both |
| O2 | **Run CI on GitHub-hosted runners** (clear the account billing lock — the repository is public, so hosted minutes are free), **or** on ephemeral self-hosted runners: a dedicated VM that is not a production host, registered with `--ephemeral` (one job, then the runner and its workspace are destroyed), a unique label, and no Docker socket shared with anything else. Then change `runs-on:` from `[self-hosted, linux, x64]` in one PR | Billing settings / a separate VM | A run's "Set up job" log shows a hosted image or an ephemeral runner name that never repeats |
| O3 | **`release` environment with a required reviewer.** Create `release` (the workflow already names it), add yourself as required reviewer, deployment policy "Selected tags: `v*`", then move `MUHABBET_KEYSTORE_BASE64`, `MUHABBET_KEYSTORE_PASSWORD`, `MUHABBET_KEY_ALIAS`, `MUHABBET_KEY_PASSWORD`, `PLAY_SERVICE_ACCOUNT_JSON` into it and delete the repository-level copies | Settings → Environments; Settings → Secrets | A tag push waits for approval; `gh secret list` no longer lists the five |
| O4 | **`production` environment**: required reviewer, branch policy `main` only, and the `VPS_*` secrets moved into it. Read the current `VPS_HOST` — the deploy workflow now refuses unless the target reports the `expected_hostname` typed at dispatch, but the secret should not point at a retired machine either. Rotate `VPS_SSH_KEY` and remove its public key from `authorized_keys` on **both** hosts (F15) | Settings → Environments; both hosts | `gh api repos/{owner}/{repo}/environments/production` shows reviewers and a branch policy |
| O5 | **Old host clean-up (F18).** On cx43: delete the stale `.env.prod`, any `firebase-adminsdk.json` and the `shared-*` data volumes Muhabbet no longer uses; rotate the other projects' runner registrations there if any Muhabbet PR job ever ran beside them | cx43 | `ls /opt/projects/Muhabbet/.env.prod` fails |
| O6 | **Rotate the debug keystore** if its SHA-1 is registered in Firebase: it lived in `~/.android` on a persistent runner, readable by every later job | Firebase console; `DEBUG_KEYSTORE` secret | New fingerprint in the next Mobile CI log |
| O7 | **Secret scanning settings (F49).** Settings → Code security: turn on "Scan for non-provider patterns" and "validity checks"; review the alerts that appear for existing history. The weekly `secret-scan.yml` run scans full history and **will be red** until the historical findings it reports (listed, redacted, in the run's own output) are removed from the current tree and, once each value is confirmed rotated, their commits are added to the `commits` allowlist in `.gitleaks.toml` the way `29874bcb` is | Settings → Code security; Actions → Secret Scan | Scheduled run green after rotation is recorded |
| O8 | **Merge the pins to `main`** quickly. Schedules run from the default branch, so `main`'s unpinned `trivy-action@master` keeps running every Monday until then (F14); in the meantime `gh workflow disable "Security & Quality"` stops it | — | `gh workflow list --all` |
| O9 | **Re-seed the runner action cache.** `infra/scripts/seed-runner-action-cache.sh` now derives `<owner>/<repo>@<sha>` from the workflows and fetches by SHA (it seeded `@vN` refs before, which the runner stopped using once the pins landed). Run it once on each self-hosted runner after this merges, and after each action bump. Delete the script once O1 is done (CICD-05) | Each runner host | `ls <runner>/_work/_actions/actions/checkout/` lists the SHA from the workflows, with a `.completed` file |
| O10 | **Rotate the second exposed set.** `docs/PROD_READINESS_AND_PLAN_2026-06-06.md` carried literal `POSTGRES_PASSWORD`, `REDIS_PASSWORD` and `JWT_SECRET` values from commit `794418b4` until 2026-11-12; they differ from the `29874bcb` set. Compare them with the live `.env.prod` by hash and rotate any that match, then add `794418b4` to the `.gitleaks.toml` commit allowlist so the weekly full-history scan goes green. The same full-history scan also reports a literal `JWT_SECRET` default in `infra/scripts/test_bot.py` at commit `e7bbb827` (the file now reads it from the environment); compare it with the live secret the same way, rotate if it matches, then allowlist `e7bbb827` too | Production host; `.gitleaks.toml` | Weekly Secret Scan run on `main` passes |
