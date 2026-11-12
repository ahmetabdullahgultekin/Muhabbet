# ADR 0031 — CI trust boundary

**Status:** Accepted (2026-11-12, delegated; process, with the amendments listed in the record) — by the delegated owner review recorded in [`docs/reviews/2026-11-12-design-approval-review.md`](../reviews/2026-11-12-design-approval-review.md); no GitHub setting is changed, no hook file created and no code written against this ADR until scope `process` reads `Status: Approved` in [`docs/architecture/multi-transport/APPROVAL.md`](../architecture/multi-transport/APPROVAL.md), which only the owner edits (scope `process`; the security emergencies of brief §14 items 1–5 are scope `phase-0` and the brief recommends authorising them now, independently of design approval) · **Date:** 2026-11-12 · **Source:** [decision brief §3, ADR-0031; §14 items 1–5](../architecture/multi-transport/decision-brief-2026-11-12.md) · **Operational detail:** [`docs/process/ci-trust-boundary.md`](../process/ci-trust-boundary.md) · **Incident record:** [`docs/process/security-incident-2026-11-env-prod.md`](../process/security-incident-2026-11-env-prod.md) · Supersedes the "deliberate trade" recorded in the header of `.github/workflows/mobile-release.yml` and ROADMAP.md "Where the keys live" (signing on the production runner).

## Context

In one line: no untrusted code on the production host, and no long-lived keys before that holds.

The repository is **public**. Its CI trust boundary does not exist:

| Fact (evidence) | Why it matters |
|---|---|
| Every job that runs in the 8 workflows on `dev` runs on `[self-hosted, linux, x64]`, except the variable-gated `ios-framework` job of `mobile-ci.yml` (`macos-latest`, currently off via `vars.ENABLE_MACOS_CI`); `backend-ci.yml`, `mobile-ci.yml`, `security.yml` and `dependabot-auto-merge.yml` trigger on `pull_request`; no fork guard (CICD-01) | For `pull_request`, the workflow definition comes from the PR's merge ref, so PR code chooses what runs |
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
2. **Interim, until CI moves off `muhabbet-cx43`: the runner service on `cx43` is stopped and
   deregistered.** The repository then has no CI on that host; the owner runs the gates locally
   (ADR-0030 reviewer note) until hosted runners or a separate non-production box exist. Rationale:
   a runner cannot be restricted to "push to `main`" jobs. Workflow files come from the PR or branch
   being run, so a job-level `if: github.event_name != 'pull_request'` guard (or deleting
   `pull_request` triggers) stops only well-behaved PRs: (a) a same-repo PR deletes the `if:`;
   (b) a PR adds a new workflow with `on: pull_request` and `runs-on: [self-hosted, linux, x64]`;
   (c) any write-access identity — including agents using the owner's token today — pushes a branch
   carrying a workflow with `on: push` and no branch filter. Each runs as `deploy` (docker group) on
   `cx43`. Workflow-restricted runner groups, the GitHub control that would bind a runner to named
   workflows, exist for organisations and enterprises only, not for a user-owned repository.
   **Accepted-risk variant (owner decision, recorded in `APPROVAL.md` with an expiry):** if the owner
   keeps `cx43` registered for `main`/tag jobs anyway, that is an explicit accepted risk, permitted
   only with all of: no write-access identity other than the owner; the agent GitHub App without the
   `workflows` permission (ADR-0032 D1); "Require approval for all external contributors"; the
   `if:` guard on every self-hosted job (hygiene, not a boundary); a fresh `GRADLE_USER_HOME` per
   run; the adversarial test of F1; **no agent session holding an owner credential with the
   `workflow` scope or contents-write** (while ADR-0032 D1 is unproven for the harness — agent sessions
   push with the owner's credential today, and commit 235c63dd, made by an agent, changed the
   backend-ci, mobile-ci and security workflows — the variant is **not available**); and the
   **`release-tags-create` ruleset created in Phase 0** (restrict creation of `v*`, bypass = admin
   role), before the variant takes effect, because any contents-write identity could otherwise push a
   `v*` tag and run `mobile-release.yml` on `cx43` with the signing secrets against whatever Gradle
   scripts the tagged commit carries. The residual risk is the owner's own credential and every
   workflow the owner merges; no long-lived key of Decision 10 may ever be created or stored on
   `cx43` under this variant.
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
     reviewer = owner; repository-level copies deleted. Both the build job (tag push) and the promote
     job run in it; the promote job is therefore dispatched **on the tag ref**
     (`gh workflow run "Mobile Release" --ref vX.Y.Z -f track=<track>`), because a dispatch on the
     default branch has ref `refs/heads/main` and the environment rejects it. The workflow definition
     is then taken from the tag.
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
8. **Secret hygiene.** GitHub secret scanning and push protection confirmed and kept (already enabled
   on 2026-11-12); validity checks enabled if offered. Gitleaks runs on every PR
   and every push to `main`, **without a paths filter**, and is a required check (with ADR-0030).
9. **CICD-06 response.** Every value ever committed in `.env.prod` (history up to commit
   `3925f708`) is treated as burned and rotated. The `.gitleaks.toml` path allowlist is replaced by a
   commit-scoped allowlist whose comment records the rotation date and the incident record.
10. **Key-creation gate.** No long-lived signing, device-CA, uplink or crypto key is created until
    this ADR is **in force** (definition below), and none ever lives on a host that runs PR code.
11. **Dependency integrity (amended 2026-11-12, threat model RN-5, R24).** Before libsignal is
    restored (1.0 entry): Gradle dependency verification (`verification-metadata.xml` with
    checksums); `gradle-wrapper` `distributionSha256Sum` plus wrapper validation in CI; JitPack removed
    from global resolution; `exclusiveContent` for `build-artifacts.signal.org` (the only source for
    libsignal versions above 0.86.5). Implementation is a later code task.
12. **Rotation order.** Rotation of `.env.prod` values (Decision 9) follows the runner change
    (Decision 2), or is repeated after it, because while PR jobs still run on `cx43` a rotated value
    is again readable by PR code. Rotating `JWT_SECRET` invalidates access tokens only; clients
    silently refresh (whether to also revoke all refresh tokens is incident action A4,
    [OI-09](../architecture/multi-transport/open-items.md#oi-09)).

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

- CI capacity changes. Until the billing lock is cleared or a second box exists, there is no CI at
  all (Decision 2) and the owner runs gates locally (see ADR-0030 reviewer note on required checks).
  Required status checks (ADR-0030) cannot pass without a runner, so the trunk switch waits for one.
- Rotating `JWT_SECRET` invalidates every outstanding **access** token (see the reviewer note below
  on why this does not log users out); the four rotations touch **shared**
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

> **Note:** accepted under the process approval: the `v*` tag ruleset limits who creates
> release tags, and the build's first step fails unless `git merge-base --is-ancestor "$TAG"
> origin/main` (checkout with `fetch-depth: 0`, failing closed if `origin/main` is absent).

> **Resolved (2026-11-12):** brief erratum E-32: rotating `JWT_SECRET` invalidates access tokens only
> (refresh tokens are 32 random bytes independent of it, `JwtProvider.generateRefreshToken`); clients
> silently refresh.
> **Open item [OI-09](../architecture/multi-transport/open-items.md#oi-09):** owner decision in incident action A4: keep sessions, or revoke all refresh tokens. Reviewer recommendation: revoke (fail closed; it is unknown whether a forged-access-token holder could have obtained a refresh token; cost is one re-login per user).

> **Note:** accepted under the Phase 0 and process approvals: "runner off cx43" is the
> implementation, because no workflow-level guard keeps PR-supplied workflow files off a
> repository-level self-hosted runner. The accepted-risk variant is not taken.

> **Resolved (2026-11-12):** brief erratum E-32 (§14 item 1): rotate after the runner is off cx43
> (item 2), or repeat the rotation after it. The incident record orders the actions this way.

## Verification

| # | Check ("in force" item) | Method |
|---|---|---|
| F1 | No PR- or branch-supplied code can run on `cx43` | Settings → Actions → Runners lists no runner on `cx43`, and on the host `systemctl list-units 'actions.runner.*'` shows none active. **Accepted-risk variant only** (Decision 2), replacing the runner check: (i) Settings → Collaborators lists no write-access identity but the owner; (ii) the agent App's installation shows no `workflows` permission; (iii) adversarial test — using the agent identity, push a throwaway branch that adds a workflow with `on: push` and `runs-on: [self-hosted, linux, x64]`: the push is refused and no job appears on `cx43`; and push a `v*` tag from the agent identity: it is refused by `release-tags-create`. (iv) No agent session holds an owner credential with the `workflow` scope or contents-write. A test PR that merely omits the `if:` guard is not evidence. |
| F2 | `muhabbet-mordecai` resolved | Settings → Actions → Runners lists only documented runners; ownership recorded |
| F3 | Environments | `gh api repos/{owner}/{repo}/environments/release` and `/production` show deployment policies and reviewer; `gh secret list` (repository level) no longer lists the five release or three VPS secrets |
| F4 | Pinning | `grep -hoE 'uses: [^ ]+' .github/workflows/*.yml` — every ref matches `@[0-9a-f]{40}`; lint job green |
| F5 | Least privilege | Every workflow has top-level `permissions: {}` or an explicit minimal block; no `${{ secrets.* }}` inside `run:` |
| F6 | Secret scanning | Repository security settings show secret scanning and push protection enabled; a test push of a fake credential pattern is blocked (owner, on a throwaway branch) |
| F7 | `.env.prod` rotated | Incident record §6 rotation log complete with verification evidence for all four values |
| F8 | Deploy trigger | `deploy-hetzner.yml` `if:` contains the `push`/head-repository guard and deploys `head_sha` |
