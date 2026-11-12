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
| `muhabbet-cx43` (production host, user `deploy` in `docker` group) | Runs every job of every workflow, PRs included | **Deregistered** | Keeps only `push` to `main`, tag and `workflow_dispatch` jobs, after PR jobs are removed; fresh `GRADLE_USER_HOME` per run |
| `muhabbet-mordecai` (unknown machine) | Takes jobs since ≤ 2026-10-26 | Identified and documented, or deregistered | Label changed to one nothing targets (**P0**) |
| GitHub-hosted | Refused: account billing lock (CICD-04) | All PR/push/schedule CI, release build | — |
| Non-prod self-hosted (optional, e.g. device lab, netem) | — | Dedicated VM, `--ephemeral` JIT registration, unique custom label, `push: main`/tag workflows only, no release secrets (CICD-17) | — |

Precondition for the target: clear the billing lock on the account (or move the repository to a free
organisation). The repository is public, so hosted runners are free (CICD-04).

## 2. Which workflow runs where

| Workflow (file on `dev`) | Triggers today | Jobs | Today | Interim | Target |
|---|---|---|---|---|---|
| Backend CI (`backend-ci.yml`) | `workflow_dispatch`, `push` `[main, develop]`, `pull_request` `[main, dev, develop]` | `build-and-test` ("Build & Test"), `docker` | cx43/mordecai | PR: disabled (job `if: github.event_name != 'pull_request'`); push `main`: cx43 | hosted |
| Mobile CI (`mobile-ci.yml`) | same pattern; `ios-framework` on `macos-latest` only if `vars.ENABLE_MACOS_CI` | `android-build` ("Android Build"), `ios-framework` | cx43/mordecai | as Backend CI | hosted (+ macOS) |
| Security & Quality (`security.yml`) | `push`/`pull_request` `[main, develop]` with paths; weekly cron | `dependency-check`, `docker-scan`, `secrets-scan` ("Secret Detection"), `codeql` | cx43/mordecai (only on `main` in practice) | push `main` + schedule on cx43 | hosted; PRs to `main`, no trigger paths filter |
| Mobile Release (`mobile-release.yml`) | tag `v*`; `workflow_dispatch` (tag, play_track) | `release` | cx43 (signing key decoded on the production host) | cx43 only after PR jobs are gone; fresh Gradle home | hosted, environment `release`; split build/promote (ADR-0030) |
| Deploy to Hetzner VPS (`deploy-hetzner.yml`) | `workflow_run` of Backend CI on `main`; `workflow_dispatch` | `deploy` (`environment: production`) | cx43/mordecai | cx43, guard added (§5) | hosted job + forced-command key, or host pull |
| Dependabot auto-merge (`dependabot-auto-merge.yml`) | `pull_request` | `auto-merge` | cx43/mordecai | **disabled** | hosted, only after required checks exist |
| Close linked issues (`close-linked-issues.yml`) | `pull_request` closed into `dev` | `close` | cx43/mordecai | unchanged until trunk | **deleted** (native closing on `main`, ADR-0030) |
| Stale branch cleanup (`stale-branches.yml`) | cron (inert: default-branch only), `workflow_dispatch` | `cleanup` | cx43 when dispatched | disabled until reviewed | hosted |

## 3. Environments

| Environment | Secrets (moved from repository level, then repository copies deleted) | Deployment policy | Reviewers | Used by |
|---|---|---|---|---|
| `release` | `MUHABBET_KEYSTORE_BASE64`, `MUHABBET_KEYSTORE_PASSWORD`, `MUHABBET_KEY_ALIAS`, `MUHABBET_KEY_PASSWORD`, `PLAY_SERVICE_ACCOUNT_JSON` | Selected tags: `v*` | Owner (self-review allowed) | Mobile Release build and promote jobs |
| `production` | `VPS_HOST`, `VPS_USER`, `VPS_SSH_KEY` | Selected branches: `main` | Owner | Deploy |
| (none) | `GOOGLE_SERVICES_JSON`, `DEBUG_KEYSTORE` | — | — | Mobile CI; never exposed to fork PRs; written to `$RUNNER_TEMP`, deleted in `if: always()` (CICD MISS-03) |

"Restricted to tags on `main`" (brief) is implemented as: the `v*` tag ruleset (ADR-0030) limits who
creates release tags, and the build job's first step fails unless
`git merge-base --is-ancestor "$TAG" origin/main` (ADR-0031 reviewer note).

`production` already exists on the deploy job; whether its secrets are environment-scoped and
whether it has reviewers/branch policy is **unknown** (CICD MISS-02). Verify with
`gh api repos/{owner}/{repo}/environments/production` and `gh secret list` (repository level) before
assuming anything.

## 4. SHA pinning

| Item | Rule |
|---|---|
| Scope | All 44 `uses:` references on `dev` (0 pinned today). Count with `grep -ho 'uses: [^ ]*' .github/workflows/*.yml \| wc -l`. |
| Form | `uses: owner/repo@<40-hex-sha> # vX.Y.Z` |
| Trivy | Remove `aquasecurity/trivy-action@master` (`security.yml`, two steps); pin a release ≥ v0.35.0 per Aqua; `exit-code: '1'`, `severity: CRITICAL`, `ignore-unfixed: true` (CICD-05, CICD-11) |
| Bumps | Dependabot `github-actions` ecosystem on `main` (CICD-13) |
| Lint | A job running actionlint and zizmor; fails on unpinned refs, `${{ }}` template injection in `run:`, and missing/over-broad `permissions` |
| Seed cache | `infra/scripts/seed-runner-action-cache.sh` pins actions invisibly to Dependabot; delete it once CI is off self-hosted (CICD-05) |

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
| `dependabot-auto-merge.yml` | workflow: `contents: write`, `pull-requests: write` | same, job-scoped | Disabled until required checks exist (its header: `--auto` without required checks merges immediately) |
| `close-linked-issues.yml` | workflow: `issues: write`, `pull-requests: read` | — | Deleted after trunk (ADR-0030) |
| `stale-branches.yml` | workflow: `contents: write`, `pull-requests: read` | same, job-scoped | Review deletion defaults before the cron goes live on `main` |

## 6. Interim controls (P0, until hosted runners or a second box)

| # | Control | How | Verify |
|---|---|---|---|
| I1 (**P0**) | Fork PR approval | Settings → Actions → General → "Require approval for all external contributors" | Setting screenshot |
| I2 (**P0**) | No PR job on cx43 | Every self-hosted job: `if: github.event_name != 'pull_request'` (or remove `pull_request` triggers). Fork guard `github.event.pull_request.head.repo.full_name == github.repository` is **not** sufficient — same-repo branches are in scope. | Test PR from a branch: no job scheduled on any self-hosted runner |
| I3 (**P0**) | `mordecai` | Change its labels to one nothing targets, or remove it, until identified | Runners page |
| I4 (**P0**) | Disable `dependabot-auto-merge.yml` | `gh workflow disable "Dependabot auto-merge"` | `gh workflow list --all` |
| I5 | Fresh Gradle home on remaining cx43 jobs | `GRADLE_USER_HOME: ${{ runner.temp }}/gradle-home` per job (CICD-02 path 2: init-script planting) | Job log |
| I6 | Release builds only after I2 | Signing on cx43 is acceptable only once no untrusted code shares the host | — |

Consequence: PRs carry no CI during the interim. The owner runs the gates locally and records counts
in the PR (ADR-0030 reviewer note on required checks).

## 7. Definition of "in force"

ADR-0031 is in force when F1–F8 of its Verification table are recorded as passed. 1.2 (device
certificates) and 1.3 (uplink key) cannot start before that (brief §13; ADR-0018; ADR-0025), and no
long-lived signing, device-CA, uplink or crypto key is generated before it.

## 8. Open questions (owner; not readable from this session)

1. Current value of the fork-PR approval setting (CICD-01).
2. What `mordecai` is, who controls it, when it was registered (between 2026-09-02 and 2026-10-26) (CICD-03).
3. `production` environment protection and secret scoping (CICD MISS-02).
4. Whether secret scanning, push protection and Dependabot alerts are enabled.
5. The cause of the account billing lock and whether it can be lifted (CICD-04).
6. Whether `/opt/projects/infra/deploy.sh` (called by `deploy-hetzner.yml`) is version-controlled anywhere, and who can modify it.
7. Whether Play App Signing is enabled (is `MUHABBET_KEYSTORE` the upload key or the app-signing key?) and whether an offline backup exists (#422).
