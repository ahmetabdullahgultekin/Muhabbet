# Local continuation prompt (2026-11-13)

Paste everything below the line into Claude Code on the owner's own machine (the one with `ssh mordecai`,
`ssh hetzner`, `gh` logged in as the repo owner, and the Android emulator). It contains no secrets.

---

You are continuing the Muhabbet security incident #745 and the 0.4 batch from a cloud session. Work in
the Muhabbet repo (`ahmetabdullahgultekin/Muhabbet`). Rules: fail fast (`set -euo pipefail`), never
print, log or paste any secret value, verify every step and show me the evidence, stop and ask before
anything irreversible that is not listed here. Reply to me in Turkish.

## 0. Sync
`git fetch origin claude/muhabbet-p2p-architecture-wdan5r` and read, from that branch (use `git show
origin/claude/muhabbet-p2p-architecture-wdan5r:<path>`, do not check it out over local work):
`docs/HANDOFF_2026-11-12.md` (§3–§4), `docs/process/security-incident-2026-11-env-prod.md`,
`docs/process/ci-trust-boundary.md`. They are the source of truth for everything below.

## 1. Old Hetzner host (CX43) — retire Muhabbet there
`ssh hetzner`, then run the host sweep with apply + retire (it stops the Muhabbet runner there and
shreds the stale `.env.prod`; it refuses if a Muhabbet backend runs there):
```bash
curl -fsS https://raw.githubusercontent.com/ahmetabdullahgultekin/Muhabbet/954e5c2810cbe17ba5755dc36d40370a56fdca19/infra/scripts/harden-host.sh -o /tmp/hh.sh \
 && echo "c76ff2eaa082c4b2dde3150115974fa754bbf939861979647d47839678eb1217  /tmp/hh.sh" | sha256sum -c --quiet \
 && sudo bash /tmp/hh.sh --apply --retire; rm -f /tmp/hh.sh
```
Expect: Muhabbet runner `inactive`, `shredded /opt/projects/Muhabbet/.env.prod`. Show me the output.

## 2. GitHub (use `gh`, show before/after)
1. Runners: `gh api repos/ahmetabdullahgultekin/Muhabbet/actions/runners` → delete `muhabbet-mordecai`
   and the CX43 Muhabbet runner (`gh api -X DELETE .../actions/runners/<id>`). Then on mordecai:
   `sudo /home/deploy/actions-runner-muhabbet/svc.sh uninstall` (service is already stopped).
2. Disable on the default branch: `gh workflow disable "Deploy to Hetzner"` and
   `gh workflow disable "Dependabot auto-merge"` (names may differ — list with `gh workflow list --all`).
3. `gh secret delete VPS_SSH_KEY -R ahmetabdullahgultekin/Muhabbet`. List remaining secrets (names only).
4. Secret scanning: enable push protection, non-provider patterns and validity checks via
   `gh api -X PATCH repos/ahmetabdullahgultekin/Muhabbet -F 'security_and_analysis[...]'` (if a field is
   not available on this plan, tell me which). Then list open alerts
   (`gh api repos/.../secret-scanning/alerts?state=open`) and close each as `revoked` with the comment
   "rotated/not in use — verified 2026-11-13 by harden-host.sh (#745)". Show the list first.
5. Settings → Actions → General: fork PR workflows from outside collaborators = require approval for
   all (UI if no API; tell me if I must click it).
6. Milestones: create `1.1 Direct`, `1.2 Nearby`, `1.3 Relay`, `1.4 Off-grid hardware` (descriptions
   = entry gates from ROADMAP.md on the branch) and attach epics #754, #755, #756, #757 respectively.

## 3. Production host (mordecai) — prepare the env for the next deploy (do NOT deploy)
The branch's `docker-compose.prod.yml` requires `MINIO_SECRET_KEY` and `MINIO_PUBLIC_ENDPOINT`; the
running (old) compose still reads the scoped key from `MINIO_ROOT_PASSWORD`. So ADD, do not rename yet:
- `MINIO_SECRET_KEY=` with the same value as the current `MINIO_ROOT_PASSWORD` line (copy without
  printing, e.g. with awk into the file).
- `MINIO_PUBLIC_ENDPOINT=` = the https origin Traefik routes to `shared-minio` for Muhabbet media —
  find it in the Traefik dynamic config (`/opt/projects/infra/traefik/config/dynamic.yml`) and the
  running backend's env (`docker inspect muhabbet-backend` → value of MINIO_PUBLIC_ENDPOINT); show me
  the hostname before writing.
Back up `.env.prod` first (mode 600), keep owner `deploy`. Verify both compose files parse:
`docker compose --env-file .env.prod -f docker-compose.prod.yml config >/dev/null` (current file) and
the same with the branch's compose fetched to /tmp. Remove the old `MINIO_ROOT_PASSWORD` line only
after the branch is deployed.

## 4. Other projects' runners on the production host (ask me before changing)
mordecai runs 6 runners for private repos (Sarnic ×2, fahrieren, dus-kocu ×2, dus-data-mining-research);
three run as `deploy`, which owns `/opt/projects/Muhabbet/.env.prod`. Propose a plan to run them as a
dedicated user without the docker group (or move them off), then wait for my OK.

## 5. Emulator check of the branch build
Build `:mobile:composeApp:assembleDebug` from the branch (in a separate worktree), install on
`emulator-5554`, launch `com.muhabbet.app/.MainActivity`, check `adb logcat -d | grep FATAL`, take a
screenshot. Sign in as test number `+905000000001` (OTP is in the prod backend log on mordecai:
`docker logs --since 30s muhabbet-backend 2>&1 | grep -oE "code is [0-9]{6}" | tail -1`). The prod
backend is still the OLD build, so only check that the app starts, logs in, opens a chat, sends a
message and shows an image. Report what you saw and what you could not see.

## 6. Owner-only, do not do it for me — just remind me
- GitGuardian: mark the 6 incidents on `Rollingcat-Software/fivucsas-papers` (commits 2b49a4a, 9aa5c0b,
  f2b5af1) as "False positive" (verified: a generator prefix, a 5-char test fixture, UUIDs/hashes).
- `APPROVAL.md`: I paste `docs/architecture/multi-transport/open-items.md` §2 (OI-46) myself.

## 7. Questions to put to me, then act on the answers
1. Is the domain `muhabbet.app` mine? (The branch moved invite links to `muhabbet.rollingcatsoftware.com`
   because a third party owns it; after the next deploy all old invite links must be revoked.)
2. Minimum age: 15 (current code), 16 with parental consent, or 18? Update code
   (`ValidationRules.MIN_AGE_YEARS`), Terms and privacy pages consistently.
3. Open a PR from `claude/muhabbet-p2p-architecture-wdan5r` into `dev`? (Never merge `dev` → `main`
   without me: it auto-deploys and starts branch deletion.)

Finish with a short Turkish summary: done / verified / still open.
