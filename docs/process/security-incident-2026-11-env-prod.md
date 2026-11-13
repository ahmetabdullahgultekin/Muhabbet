# Security incident record — production secrets in public history, and PR code on the production host

> **Status: Open — owner action required.**
> This record is part of the multi-transport design package, which is **Proposed — pending owner
> approval**; no code may be written until approval is recorded in
> [`docs/architecture/multi-transport/APPROVAL.md`](../architecture/multi-transport/APPROVAL.md). The
> response actions below are scope `phase-0`; the decision brief (§14) recommends that the owner
> authorise them **now, independently of design approval**, because they are incident response, not
> design.

| | |
|---|---|
| **Incident id** | INC-2026-11-ENV-PROD |
| **Findings** | **CICD-06** (critical) — production secrets committed in `.env.prod`, repository public. **CICD-01** (critical) — `pull_request` workflows run on persistent self-hosted runners, one of which is the production host. Related: CICD-02, CICD-03, CICD-05, CICD MISS-01, CICD MISS-02, CICD MISS-03, GOV MISS-01 |
| **Risks** | R1 (leaked prod secrets still live, H/Critical), R2 (PR code on the prod host, M/Critical) — brief §15 |
| **Decision records** | [ADR-0031](../adr/0031-ci-trust-boundary.md); operational detail [`ci-trust-boundary.md`](ci-trust-boundary.md) |
| **Detected** | 2026-11-12, by the CI/CD supply-chain review and its adversarial verifier |
| **Severity** | Critical. `JWT_SECRET` alone lets anyone mint an access token for any user of the production API. |
| **Owner** | Repository owner (sole operator of the production host) |
| **Handling rule** | **No secret value appears in this file, in an issue, a PR, a commit message, a log or a chat.** Names only. |

## 1. Summary

The production environment file `.env.prod` was committed to the repository and later removed from
tracking in commit `3925f708` (2026-03-28). Removal does not remove it from history. The commit's
diff shows four **non-empty** production values; every other line was an empty placeholder. The
repository is public (GitHub search API: `"visibility": "public"`), so those values are readable by
anyone from the public commit page. The file was added in commit `29874bcb` (2026-03-16; `git log --all -- .env.prod`). On `dev`/`main`
`.gitleaks.toml` still allowlists the **path** `.env.prod`, which keeps the secret scanner permanently
silent about it; on branch `claude/muhabbet-p2p-architecture-wdan5r`, commit `235c63dd` has already
replaced that with a **commit-scoped** allowlist for `29874bcb`, pending merge to `dev`/`main`. No
issue, commit or document records a rotation.

Separately, every CI job — including `pull_request` jobs whose workflow definition comes from the PR
itself — runs on self-hosted runners. **Corrected 2026-11-12 (owner):** production runs on the
owner's own server **mordecai**, and the runner `muhabbet-mordecai` is that machine — the
production host, not an unknown one. Its jobs, Dependabot bumps included, therefore run beside the
live `.env.prod`, the shared Postgres/Redis/MinIO containers and the running backend, with Docker
access. The Hetzner CX43 (runner `muhabbet-cx43`) no longer runs Muhabbet production; it hosts CI
runners for other projects, a stale Muhabbet runner and a stale `.env.prod`. Earlier text in this
record that calls the CX43 "the production host" describes the situation before the move.

## 2. Affected secrets (names only)

| Name | Where it was exposed | What it grants if still valid | Reachability for an outsider |
|---|---|---|---|
| `JWT_SECRET` | Public history (commit diff of `3925f708`) | Mint a valid HS256 access token (`iss: muhabbet`) for any user id → read/send messages, profile, contacts as that user | Directly, over the public API `muhabbet-api.rollingcatsoftware.com` |
| `POSTGRES_PASSWORD` | Public history | Database access as that role on `shared-postgres` | Only if the database port is reachable from outside the Docker network, or from code running on the host (CICD-01) |
| `REDIS_PASSWORD` | Public history | Read/write of `shared-redis` (WS fan-out channels, presence, rate-limit state) | As above |
| `MINIO_ROOT_PASSWORD` | Public history | Root of `shared-minio`: every media object, all buckets | Through the public media host route to `shared-minio:9000` (S3 API) if root credentials authenticate there; otherwise as above |
| `JWT_SECRET` (second value) | Public history: hardcoded in `infra/scripts/test_bot.py`, added `e7bbb827`, removed `be5d80e0` | As `JWT_SECRET` above, if production ever used it or uses it now | As above. Not compared by `check-env-prod-exposure.sh`; `harden-host.sh` compares it |
| `JWT_SECRET` (third value), `POSTGRES_PASSWORD`, `REDIS_PASSWORD` | In the current tree until 2026-11-12: `docs/PROD_READINESS_AND_PLAN_2026-06-06.md` quoted the Postgres and Redis values and a JWT value one character longer than the `.env.prod` one. Redacted on this branch; still on `main`/`dev` until merged, and in history for good | As above | As above. `harden-host.sh` carries the digest of the third value |
| Every other value in the **current** production `.env.prod`, and the Firebase admin key | Not in history; but readable by any job that ran on the production host's runner (CICD-01) | Depends on the value | Only through a PR job or a compromised action on the runner |

The three infrastructure services are **shared** with other projects on the host (CLAUDE.md
"Containers"); if the leaked passwords are instance-wide credentials rather than Muhabbet-scoped
roles, the exposure extends to those projects' data.

**Second exposure source.** Rotation must cover both sets: compare all three values from
`794418b4` with the live `.env.prod` the same way as the `29874bcb` values (hashes, never values),
and rotate any that match. The weekly full-history secret scan flags `794418b4` and stays red until,
after rotation, that commit is added to the commit allowlist in `.gitleaks.toml` next to `29874bcb`.

## 3. Timeline

| Date (UTC) | Event | Source |
|---|---|---|
| 2026-03-16 | Commit `29874bcb` adds `.env.prod` with four non-empty production values. | `git log --all -- .env.prod` (full history, 2026-11-12) |
| 2026-03-19/20 | Trivy supply-chain compromise window; 12 Security runs fell inside it (GitHub-hosted, `contents: read` + `security-events: write`); logs now 410 Gone, so whether compromised code ran cannot be checked. Not known to involve `.env.prod`. | CICD-05 |
| 2026-03-28 | Commit `3925f708` removes `.env.prod` from tracking. Values remain in history. The `.gitleaks.toml` path allowlist cites this commit ("Historical .env.prod — removed from tracking in commit 3925f70"); the date the allowlist was added is not established. | `.gitleaks.toml`; CICD-06 |
| Not established | Repository made public (or already public at `3925f708`). | Open question |
| Not established → 2026-11-12 | `pull_request` jobs execute on `muhabbet-cx43`. At the latest since PR CI on `dev` was enabled (#487). | CICD-01; workflow comments |
| 2026-09-02 → 2026-10-26 | `muhabbet-mordecai` registered (PR #730 ran on cx43 on 2026-09-02; a job ran on mordecai on 2026-10-26). | CICD-03 |
| 2026-11-12 | Review confirms: repository public, `forks_count: 0`; the diff of `3925f708` holds four non-empty values; no rotation tracked; PR workflows run on self-hosted runners with no fork guard. | CICD-01, CICD-06 verifiers |
| 2026-06-06 | Commit `794418b4` adds `docs/PROD_READINESS_AND_PLAN_2026-06-06.md`, whose P0-12 evidence line quotes three literal values (`POSTGRES_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET`) — the Postgres and Redis values equal those in `29874bcb` (same SHA-256), the JWT value differs (a third JWT value). | Review of the secret-scanner change, 2026-11-12 (values compared, not printed) |
| 2026-11-12 | The three values in that document are replaced with `<redacted>`. They remain in history at `794418b4`. | This change |
| 2026-11-12 | This record opened. | — |
| 2026-11-12 | Owner states that production runs on mordecai and that `muhabbet-mordecai` is the owner's machine; the CX43 no longer runs Muhabbet production. A second JWT value (old `test_bot.py`) and a third (docs file) are found; the docs copy is redacted from the current tree. | owner; security review |
| 2026-11-13 | MinIO root password rotated; Muhabbet moved to the bucket-scoped user `muhabbet-app`. | §6 |

`forks_count: 0` does not bound exposure: clones, caches and scrapers of public repositories are not
counted.

## 4. Impact assessment (to complete)

| Question | How to answer | Answer |
|---|---|---|
| Are any of the leaked values still in use? | Run `sudo bash infra/scripts/harden-host.sh` (report only) **on mordecai and on the CX43**: it compares SHA-256 digests (never values) of every value that has been public — the four `.env.prod` values, the second JWT value from `test_bot.py` and the third from the docs file — against every env file and every running container's environment. `check-env-prod-exposure.sh` compares only the four `.env.prod` keys, so its earlier `DIFFERENT` for `JWT_SECRET` does not cover the other two. Neither script is on `main` yet, so copy it to the host first | Four `.env.prod` keys: see §6. Second and third JWT values: **open** |
| Were the infrastructure ports ever reachable from the internet? | `docker ps --format '{{.Names}} {{.Ports}}'`, `ss -ltnp`, the host's firewall rules | |
| Is `MINIO_ROOT_PASSWORD` accepted through the public media route? | Owner checks MinIO's admin/auth config; do not test with the leaked value against production from any shared machine | |
| Any sign of forged tokens? | Backend logs for requests authenticated for users who were not active at that time, unusual `deviceId`s, or bursts from unfamiliar IPs; HS256 tokens are stateless, so absence of evidence is weak | |
| Any sign of database or object-store misuse? | Postgres connection logs from unexpected addresses; MinIO audit/trace if enabled | |
| Did any PR job on `cx43`/`mordecai` come from a non-owner? | Actions run history filtered by `pull_request` and actor; fork PRs (`forks_count: 0` suggests none) | |
| Personal data breach notification duty (KVKK) | Counsel, if impact evidence is found | |

## 5. Required actions

Order matters: rotated values written to `.env.prod` while any PR- or branch-supplied workflow can
still run on the production host are immediately re-exposed (ADR-0031 reviewer note). Do A1–A3 first.
The production host is **mordecai** (A2), so A1 means no runner serving this repository is left on
mordecai — and the stale one on the CX43 is removed too; a job-level `if:` guard does not count
(ADR-0031 D2).

| # | Action | Steps | Verification | Status |
|---|---|---|---|---|
| A0 | Record the containment changes made before approval | Commit `235c63dd` (fork guards on PR jobs, trivy pin, commit-scoped gitleaks allowlist, `check-env-prod-exposure.sh`) was made by an agent before phase-0 approval | Ratified by the delegated `phase-0` approval (2026-11-12) | Done (pending merge to `dev`/`main`) |
| A1 | Stop PR- and branch-supplied code on the production host (CICD-01) | `ci-trust-boundary.md` §6 I1–I4: fork approval for all external contributors; **stop and deregister the runner service on the production host (mordecai, runner `muhabbet-mordecai`), and the stale `muhabbet-cx43` runner on the CX43** (the `if:` guard or removing PR triggers stops only well-behaved PRs: a PR can delete the guard or add a new workflow, and any write-access identity can push a branch with an unfiltered `on: push` workflow); disable `Dependabot auto-merge`. If the owner instead takes ADR-0031 D2's accepted-risk variant, record it with an expiry in `APPROVAL.md` and apply all its mitigations (owner-only write access, agent App without `workflows` permission, I1, I5). Two further mandatory conditions (amended 2026-11-12): **no agent session holds an owner credential with the `workflow` scope or contents-write** — until ADR-0032 D1 is proven for the harness the variant is not available, because agent sessions push with the owner's credential today and commit 235c63dd, made by an agent, changed three workflows — and the **`release-tags-create` ruleset** (restrict creation of `v*`, bypass = admin role) is created in Phase 0 before the variant takes effect, so no contents-write identity can push a `v*` tag that runs `mobile-release.yml` on `cx43` with the signing secrets. Once the runner is deregistered, `deploy-hetzner`, the weekly `security.yml` cron and Backend CI on `main` pushes queue and silently expire after 24 h; manual deploys (needed for A4–A7) follow CLAUDE.md with `--env-file .env.prod` and a `rollback-<date>` image tag | Settings → Actions → Runners lists no runner on mordecai or the CX43 serving this repository; `systemctl list-units 'actions.runner.*'` on the host is empty. Variant: collaborators = owner only; App lacks `workflows`; a throwaway branch pushed by the agent identity that adds an `on: push` workflow on the self-hosted labels is refused and no job runs on `cx43`; a `v*` tag pushed by the agent identity is refused. A test PR that merely omits the guard is **not** evidence | Open |
| A2 | Resolve `muhabbet-mordecai` (CICD-03) | **Identified 2026-11-12:** the owner's own machine, mordecai, which is the production host. So A1 applies to it **first**: deregister it, or move every `pull_request` and Dependabot job to GitHub-hosted runners before it takes another job. Then deregister the stale `muhabbet-cx43` runner on the CX43 | Runners page lists no runner on mordecai, and none on the CX43 that serves this repository | Identification done; removal open |
| A3 | Protect release and deploy secrets (CICD-02, CICD MISS-02) | Create `release` (tags `v*`, reviewer) and verify `production` (branch `main`, reviewer); move the 5 + 3 secrets; delete repository-level copies | `gh secret list` (repo level) no longer lists them; environment API shows policies | Open |
| A4 | Rotate `JWT_SECRET` | Generate a new random value of at least the length the backend boot guard requires, on the host; write it to `.env.prod`; redeploy with `--env-file .env.prod` | Fact: refresh tokens are 32 random bytes stored as a SHA-256 hash in `refresh_tokens` (`JwtProvider.kt:99-103`; `AuthService.kt:367-394`); they do not depend on `JWT_SECRET` and survive its rotation. **Owner decision (record in §6):** (i) keep sessions — clients get 401 on the old access token and silently refresh; or (ii) precaution — revoke all refresh tokens server-side (`UPDATE refresh_tokens SET revoked_at = now() WHERE revoked_at IS NULL`) to force re-login, because whether a forged-token holder could have obtained a refresh token is open. Verification: (a) a pre-rotation access token gets **401 on a direct API call**; (b) a fresh login works; (c) under (ii) only, a pre-rotation refresh token gets `AUTH_TOKEN_REVOKED`. Any test token minted with the **old** value is minted by the owner only, locally, never in CI or by an agent. | Open |
| A5 | Rotate `POSTGRES_PASSWORD` | Coordinate with the other projects on `shared-postgres` if the role is shared; `ALTER ROLE … PASSWORD …` inside the container; update `.env.prod`; redeploy | `psql` with the old password fails; backend `/actuator/health` UP with the new one | Open |
| A6 | Rotate `REDIS_PASSWORD` | Same coordination for `shared-redis`; change `requirepass`/ACL user; update `.env.prod`; redeploy | `redis-cli -a <old>` → `WRONGPASS`/`NOAUTH`; WS fan-out works (send a message between the two test numbers) | Open |
| A7 | Rotate `MINIO_ROOT_PASSWORD` | Same coordination for `shared-minio`; change root credentials; move the backend to a scoped access key in the same step; update `.env.prod`; redeploy | Old credentials rejected (`mc alias set` fails); media upload and download through the public media host work | **Done 2026-11-13** (§6) |
| A7a | Rename the Muhabbet MinIO secret in mordecai's `.env.prod` | The `muhabbet-app` secret was written under the old name `MINIO_ROOT_PASSWORD`, because the compose file of the time read it from there. `docker-compose.prod.yml` now reads `MINIO_SECRET_KEY`, `MINIO_ACCESS_KEY` and `MINIO_PUBLIC_ENDPOINT` with `${VAR:?}` and has no `minioadmin` or hostname default, so **rename that line to `MINIO_SECRET_KEY` before deploying this compose**. A missed rename makes Compose refuse to start; it cannot run with an empty or root credential | `docker compose --env-file .env.prod -f docker-compose.prod.yml config >/dev/null` succeeds; after deploy, upload and download work | Open |
| A7b | No anonymous read on `muhabbet-media` | `minio-init` used to grant anonymous download on `thumbnails/` at every deploy. Thumbnails are already served through presigned URLs (`MediaService.getPresignedUrl`), so the grant only added a permanent unsigned route. The line is removed from both compose files; remove the live rule with `harden-host.sh --apply` or `mc anonymous set none <alias>/muhabbet-media/thumbnails` | `mc anonymous get` reports `none` for the bucket and the prefix | Repo done; host open |
| A7c | Check the second and third JWT values | `harden-host.sh` on mordecai and on the CX43 (§4) | No `LEAKED VALUE IN USE` line for `JWT_SECRET` | Open |
| A8 | Review values never committed but on the host (owner decision) | Decide whether values reachable only via CICD-01 (Twilio, FCM/Firebase admin, any API keys) are rotated; record the decision | Decision recorded in §6 | Open |
| A9 | Replace the gitleaks path allowlist (CICD-06) | Determine the commit(s) that contained `.env.prod` in a full clone: `git log --all --full-history --format=%H -- .env.prod`; replace `paths = ['''\.env\.prod''']` with `commits = [<those SHAs>]` and a comment "rotated <date>, see docs/process/security-incident-2026-11-env-prod.md" | `gitleaks detect` over full history reports nothing for those commits; and, **in a local clone, never pushed**, committing a fake `.env.prod` makes `gitleaks detect` report it (pushing even a fake credential file to a public repository publishes it) | Drafted in `235c63dd` (branch; commit `29874bcb` allowlisted), pending merge to `dev`/`main` and the rotation date in the comment |
| A10 | Confirm and keep GitHub secret scanning and push protection (already enabled on 2026-11-12); enable validity checks if offered | Repository → Settings → Code security | A test push of a fake credential pattern on a throwaway branch is blocked | Open (confirm) |
| A11 | Run Gitleaks on every PR without a paths filter (GOV MISS-01) | `security.yml` change (ADR-0031 §8) | A PR touching only `mobile/` runs Secret Detection | Open |
| A12 | SHA-pin actions; remove `trivy-action@master` (CICD-05) | `ci-trust-boundary.md` §4 | 44/44 refs pinned | Partial — trivy pinned to v0.36.0 on the branch; 42 of 44 `uses:` refs remaining |
| A13 | Do not rewrite history | Recorded decision: rotation is the remedy; rewriting the public default branch does not un-publish values (ADR-0031) | — | Proposed |

Phase-0 exit (brief §13): every `.env.prod` value rotated, verified by the old JWT failing; no PR job on
the production host (no runner serving this repository on mordecai or the CX43, per A1 — or the
accepted-risk variant's adversarial test).

User impact of A4: under decision (i), none visible — the client's 401 → refresh path issues a new
access token signed with the new secret. Under decision (ii), every user is logged out and must sign
in again with an OTP. The test numbers `+905000000001..5` keep working either way (their OTP is in the
backend log, CLAUDE.md).

## 6. Rotation log (names, dates and evidence only)

| Secret name | Rotated on | By | Verification performed | Evidence location |
|---|---|---|---|---|
| `JWT_SECRET` | before 2026-11-12 (live value differs from the leaked one) | owner | `check-env-prod-exposure.sh` on the host, 2026-11-12: `DIFFERENT` | this table |
| `POSTGRES_PASSWORD` | before 2026-11-12 | owner | same run: `DIFFERENT` | this table |
| `REDIS_PASSWORD` | before 2026-11-12 | owner | same run: `DIFFERENT` | this table |
| `MINIO_ROOT_PASSWORD` | 2026-11-13 | owner, with `infra/scripts/rotate-minio-root.sh` | Exposure check on the real production host (mordecai) showed `SAME` on 2026-11-12. The script gave Muhabbet and Mandalean their own bucket-scoped MinIO users, moved both apps to them, replaced the root password and verified the old one is rejected and both app users still work. Before rotation: no IAM users or access keys other than root existed (no persistence found). | script output (owner terminal); this table |
| `NETGSM_*`, `SENTRY_DSN` | n/a | | same run: `MISSING` (no longer in the live file; revoke at the provider if the accounts still exist) | |
| A8 decision | | | | |

## 7. Closure criteria

The incident is closed when: A1–A7c and A9–A11 are done with evidence in §6 or linked; §4 is answered;
ADR-0031 F1, F2, F3, F6, F7 are recorded as passed; and the owner signs below.

Closed by: · Date:

## 8. Lessons recorded

1. Removing a file from tracking is not remediation; the remedy for a leaked secret is rotation, and
   the record of rotation belongs next to the allowlist that silences the scanner.
2. A CI runner on the production host makes every PR author — human, agent or Dependabot-bumped
   plugin — an operator of production.
3. A billing problem in one place (CICD-04) silently moved all CI onto production; such trades must
   be recorded as decisions with an owner and an expiry.
