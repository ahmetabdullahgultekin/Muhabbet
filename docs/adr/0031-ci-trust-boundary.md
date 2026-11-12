# ADR 0031 — CI trust boundary: no untrusted code on the production host, no long-lived keys before it holds

**Status:** Proposed (process) — pending owner approval; no code may be written, and no GitHub setting changed, against this ADR until approval is recorded in [`docs/architecture/multi-transport/APPROVAL.md`](../architecture/multi-transport/APPROVAL.md) (scope `process`; the security emergencies of brief §14 items 1–5 are scope `phase-0` and the brief recommends authorising them now, independently of design approval) · **Date:** 2026-11-12 · **Source:** [decision brief §3, ADR-0031; §14 items 1–5](../architecture/multi-transport/decision-brief-2026-11-12.md) · **Operational detail:** [`docs/process/ci-trust-boundary.md`](../process/ci-trust-boundary.md) · **Incident record:** [`docs/process/security-incident-2026-11-env-prod.md`](../process/security-incident-2026-11-env-prod.md) · Supersedes the "deliberate trade" recorded in the header of `.github/workflows/mobile-release.yml` and ROADMAP.md "Where the keys live" (signing on the production runner).

## Context

The repository is **public**. Its CI trust boundary does not exist:

| Fact (evidence) | Why it matters |
|---|---|
| Every job in all 8 workflows on `dev` runs on `[self-hosted, linux, x64]`; `backend-ci.yml`, `mobile-ci.yml`, `security.yml` and `dependabot-auto-merge.yml` trigger on `pull_request`; no fork guard (CICD-01) | For `pull_request`, the workflow definition comes from the PR's merge ref, so PR code chooses what runs |
| One runner is the production host `muhabbet-cx43`, runner user `deploy`, member of the `docker` group (CICD-01; CLAUDE.md) | Docker-group membership is root-equivalent: `.env.prod`, the shared Postgres/Redis/MinIO of five projects and the running backend are reachable from any job |
| A second runner `muhabbet-mordecai` (machine `mordecai`) with identical labels, documented nowhere, took jobs from 2026-10-26; registered between 2026-09-02 and 2026-10-26 (CICD-03) | Release-signing and deploy jobs can land on a machine nobody has described |
| Release keystore and Play service-account key are **repository-level** secrets; `mobile-release.yml` has workflow-level `permissions: contents: write` and no `environment:` (CICD-02) | Any same-repo branch PR (agent, Copilot, Dependabot-bumped plugin) can read them via an edited workflow; a shared persistent `GRADLE_USER_HOME` lets a PR job plant an init script that runs inside the next release build |
| 44 `uses:` references on `dev`, 0 SHA-pinned; `aquasecurity/trivy-action@master` twice; 12 Security runs fell in the March 2026 Trivy compromise window, logs gone (CICD-05) | Mutable refs execute third-party code on the production host |
| `.env.prod` with four non-empty production values (`JWT_SECRET`, `POSTGRES_PASSWORD`, `REDIS_PASSWORD`, `MINIO_ROOT_PASSWORD`) is in public history; removed from tracking in commit `3925f708` (2026-03-28); `.gitleaks.toml` allowlists the **path**; no rotation tracked (CICD-06) | `JWT_SECRET` alone mints a valid token for any user |
| Gitleaks/Trivy/CodeQL run only on `main`/`develop` (`develop` does not exist) and Gitleaks is path-filtered away from `mobile/` (GOV MISS-01, CICD-12) | Secrets and SAST are never checked where work lands |
| `deploy-hetzner.yml` fires on any successful Backend CI `workflow_run` for branch `main`, not only `push` events (CICD MISS-01); `environment: production` exists but its protection and secret scoping are unknown (CICD MISS-02) | A PR-triggered run can start a production rebuild in the base-repo context |
| Root cause: GitHub-hosted runners are refused by an account billing lock; on a public repo they are free (CICD-04) | Every isolation fix depends on clearing it or provisioning a separate box |

Later ADRs create long-lived secrets that are worth far more than today's: the device-certificate
intermediate (ADR-0018, 1.2) and the uplink unseal key (ADR-0025, 1.3). They must not be born into
this boundary.

## Decision

1. **No `pull_request` job — fork or same-repo branch — ever runs on the production host.**
2. **Interim, until CI moves off `muhabbet-cx43`:** PR-triggered jobs run only on a non-production
   runner, or are disabled. Push jobs on `main` (owner-merged code) and tag jobs may continue on
   `cx43` only after (1) holds and only with a fresh `GRADLE_USER_HOME` per run.
3. **Target:** all `pull_request`, `push` and `schedule` CI on GitHub-hosted ephemeral runners once
   the billing lock is cleared (public repo, so free), or on a separate non-production box with
   ephemeral (JIT) registration and custom labels. Zero runners on the production host. Deploy is a
   job that cannot be triggered by PR code (environment-gated; forced-command SSH key or host-side
   pull of a CI-built digest).
4. **`muhabbet-mordecai`** is identified (owner, purpose, security) or deregistered. Until then it is
   given a label no workflow targets. No workflow targets bare `self-hosted` again.
5. **Protected environments.**
   - `release`: holds `MUHABBET_KEYSTORE_BASE64`, `MUHABBET_KEYSTORE_PASSWORD`, `MUHABBET_KEY_ALIAS`,
     `MUHABBET_KEY_PASSWORD`, `PLAY_SERVICE_ACCOUNT_JSON`; deployment policy = tags `v*`; required
     reviewer = owner; repository-level copies deleted.
   - `production`: holds `VPS_HOST`, `VPS_USER`, `VPS_SSH_KEY`; deployment policy = branch `main`;
     required reviewer = owner. Its current configuration is **verified**, not assumed.
6. **Actions are SHA-pinned** (40-character SHA plus `# vX.Y.Z` comment; `trivy-action` ≥ v0.35.0
   per Aqua, `@master` removed); Dependabot's `github-actions` ecosystem bumps them; a lint job
   (actionlint + zizmor) fails on unpinned refs, template injection and over-broad permissions.
7. **Least privilege.** `permissions: {}` at workflow level, minimal grants per job;
   `persist-credentials: false` on checkout; secrets only through `env:`, never interpolated into
   `run:` text; `timeout-minutes` on every job; deploy's `if:` requires
   `workflow_run.event == 'push'` and the head repository to be this repository, and deploys
   `workflow_run.head_sha`.
8. **Secret hygiene.** GitHub secret scanning and push protection enabled. Gitleaks runs on every PR
   and every push to `main`, **without a paths filter**, and is a required check (with ADR-0030).
9. **CICD-06 response.** Every value ever committed in `.env.prod` (history up to commit
   `3925f708`) is treated as burned and rotated. The `.gitleaks.toml` path allowlist is replaced by a
   commit-scoped allowlist whose comment records the rotation date and the incident record.
10. **Key-creation gate.** No long-lived signing, device-CA, uplink or crypto key is created until
    this ADR is **in force** (definition below), and none ever lives on a host that runs PR code.

**"In force"** (used by brief §13 G-PROXIMITY and ADR-0018/0025) means all of F1–F8 in
[Verification](#verification) are recorded as passed in the incident record or the `process` scope
conditions of `APPROVAL.md`.

## Why

- Docker-group membership on the production host is root on production (CICD-01). Fork-approval
  settings are a policy toggle whose current value cannot even be read from this session; they are
  not a boundary, and same-repo branches (agents, Dependabot) bypass them entirely.
- Environment-scoped secrets with a deployment policy are the GitHub mechanism that makes a secret
  unreadable from a PR job ([security hardening for GitHub Actions](https://docs.github.com/en/actions/security-for-github-actions/security-guides/security-hardening-for-github-actions)).
- Mutable action tags were the attack vector in March 2026 ([Aqua](https://www.aquasec.com/blog/trivy-supply-chain-attack-what-you-need-to-know/),
  [Snyk](https://snyk.io/articles/trivy-github-actions-supply-chain-compromise/)).
- Leaked values in a public repository are compromised regardless of later deletion; rotating only
  `JWT_SECRET` leaves three working credentials (brief: rejected).

## Consequences

- CI capacity changes. Until the billing lock is cleared or a second box exists, PRs carry no CI and
  the owner runs gates locally (see ADR-0030 reviewer note on required checks).
- Rotating `JWT_SECRET` forces every user to log in again; the four rotations touch **shared**
  instances (`shared-postgres`, `shared-redis`, `shared-minio`) used by other projects on the host and
  need coordination (incident record §5).
- Release signing moves to an ephemeral runner with the `release` environment; ROADMAP "Where the
  keys live" is rewritten. The owner's offline copy of the keystore remains the backup question of
  #422, which this ADR does not solve.
- Dependabot PRs no longer run on production; `dependabot-auto-merge.yml` stays disabled until PR CI
  exists elsewhere and required checks exist (its own header warns `--auto` without required checks
  merges immediately).
- 1.2 and 1.3 are blocked until this ADR is in force (brief §13, ADR-0018, ADR-0025).

## Alternatives considered

| Alternative | Rejected because |
|---|---|
| Rely on "Require approval for all external contributors" alone | Does not cover same-repo branches (agents, Dependabot); setting not readable/auditable here. Brief: rejected. Kept as a backstop, not the control. |
| `if: head.repo.full_name == github.repository` fork guard alone | Same-repo PR code still runs as `deploy` on production |
| Rotate only `JWT_SECRET` | Three further production credentials stay live. Brief: rejected. |
| Rewrite history to remove `.env.prod` | Does not un-publish values already public (clones, caches); rotation is the remedy. History rewrite on a public default branch adds risk without benefit. |
| Keep signing on the production runner ("deliberate trade", `mobile-release.yml` header) | Was accepted before PR jobs and a second unknown runner shared that host; the premise no longer holds |

## Evidence

CICD-01, CICD-02, CICD-03, CICD-04, CICD-05, CICD-06, CICD-07, CICD-11, CICD-12, CICD-13, CICD-15,
CICD MISS-01, CICD MISS-02, CICD MISS-03, GOV MISS-01, GOV-13, R1, R2, R14, R19 —
[findings register](../reviews/2026-11-12-findings-register.md);
target topology derived from the CI/CD review's proposals: [`ci-trust-boundary.md` §1](../process/ci-trust-boundary.md#1-target-runner-topology).
External: [GitHub security hardening for Actions](https://docs.github.com/en/actions/security-for-github-actions/security-guides/security-hardening-for-github-actions),
[GitHub changelog 2021-04-22 (first-time contributor approval)](https://github.blog/changelog/2021-04-22-github-actions-maintainers-must-approve-first-time-contributer-workflow-runs/),
[Aqua: Trivy supply-chain attack](https://www.aquasec.com/blog/trivy-supply-chain-attack-what-you-need-to-know/),
[Socket](https://socket.dev/blog/trivy-under-attack-again-github-actions-compromise),
[oss-sec 2026/q1/371](https://seclists.org/oss-sec/2026/q1/371),
[trivy-action](https://github.com/aquasecurity/trivy-action).

> **Reviewer note (open):** the brief says the `release` environment is "restricted to tags on
> `main`". GitHub environment deployment policies can restrict to tag *patterns* (`v*`), not to
> "tags whose commit is on `main`". The process doc closes the gap with (a) the `v*` tag ruleset of
> ADR-0030 limiting who can create release tags and (b) a first build step that fails unless
> `git merge-base --is-ancestor "$TAG" origin/main`. Confirm this is an acceptable reading.

> **Reviewer note (open):** rotation order matters and the brief does not state it. While PR jobs
> still run on `cx43`, any rotated value written to `/opt/projects/Muhabbet/.env.prod` is again
> readable by PR code (CICD-01). Rotation should therefore follow Decision 1/2 (PR jobs off `cx43`),
> or be repeated after it. The incident record orders the actions this way.

## Verification

| # | Check ("in force" item) | Method |
|---|---|---|
| F1 | No PR job runs on `cx43` | Inspect every workflow's `on:`/`runs-on:`/`if:`; open a test PR from a branch and confirm no job is scheduled on `muhabbet-cx43` (Actions run → job → "Runner name") |
| F2 | `muhabbet-mordecai` resolved | Settings → Actions → Runners lists only documented runners; ownership recorded |
| F3 | Environments | `gh api repos/{owner}/{repo}/environments/release` and `/production` show deployment policies and reviewer; `gh secret list` (repository level) no longer lists the five release or three VPS secrets |
| F4 | Pinning | `grep -hoE 'uses: [^ ]+' .github/workflows/*.yml` — every ref matches `@[0-9a-f]{40}`; lint job green |
| F5 | Least privilege | Every workflow has top-level `permissions: {}` or an explicit minimal block; no `${{ secrets.* }}` inside `run:` |
| F6 | Secret scanning | Repository security settings show secret scanning and push protection enabled; a test push of a fake credential pattern is blocked (owner, on a throwaway branch) |
| F7 | `.env.prod` rotated | Incident record §6 rotation log complete with verification evidence for all four values |
| F8 | Deploy trigger | `deploy-hetzner.yml` `if:` contains the `push`/head-repository guard and deploys `head_sha` |
