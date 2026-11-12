# ADR 0033 — Claude Code hooks: design gate and reproducible cloud environment

**Status:** Accepted (2026-11-12, delegated; process, with the amendments listed in the record) — by the delegated owner review recorded in [`docs/reviews/2026-11-12-design-approval-review.md`](../reviews/2026-11-12-design-approval-review.md); no GitHub setting is changed, no hook file created and no code written against this ADR until scope `process` reads `Status: Approved` in [`docs/architecture/multi-transport/APPROVAL.md`](../architecture/multi-transport/APPROVAL.md), which only the owner edits (scope `process`) · **Date:** 2026-11-12 · **Source:** [decision brief §3, ADR-0033 (owner decision 4, "both")](../architecture/multi-transport/decision-brief-2026-11-12.md) · **Specification:** [`docs/process/claude-code-hooks.md`](../process/claude-code-hooks.md) (exact proposed file contents, behaviour, test plan) · Supersedes nothing.

## Context

- **Owner decision 4:** nothing in the multi-transport design is accepted until the owner approves it,
  and no code is written before that approval — and the owner wants this as a *mechanism*, not a
  convention ("both": design gate and environment bootstrap).
- **Conventions have already failed.** CLAUDE.md rules were broken by agents in the past (the
  HANDOFF records several; SSOT-03 shows the entry point itself carried false facts). A rule an agent
  must remember is only as strong as the agent's context window.
- **Hooks are executed by the harness**, not the model. A `PreToolUse` hook sees every `Edit`,
  `Write`, `NotebookEdit` and `Bash` call before it runs and can deny it; a `SessionStart` hook runs
  before the first prompt.
- **Environment rediscovery is expensive.** In this session the cloud environment took about 20
  minutes to make Android-capable: no SDK, Maven Central (`repo.maven.apache.org`) answering HTTP 429
  through the session proxy under Gradle's parallel fetches, and no `google-services.json` (gitignored;
  CI writes a dummy from a heredoc in `.github/workflows/mobile-ci.yml`).
- **Approval is per scope.** `APPROVAL.md` defines scopes `phase-0`, `0.4`–`0.7`, `1.0`–`1.4`,
  `process`, each a block whose line `Status: Approved` opens it; later milestones re-arm the gate
  only if the earlier scope is closed again at milestone exit (D1a).

## Decision

Two hooks, registered in `.claude/settings.json`, added only after scope `process` is approved:

### 1. Design gate — `PreToolUse` on `Edit|MultiEdit|Write|NotebookEdit|Bash`

| Rule | Behaviour |
|---|---|
| G1 | A target path outside the repository is always allowed (scratchpad, `/tmp`, `~/.gradle`). |
| G2 | Inside the repository, these are always allowed: `docs/**`, `.github/ISSUE_TEMPLATE/**`, `.github/pull_request_template.md`, `CODEOWNERS` (root or `.github/`), and only the governance files `.claude/settings.json`, `.claude/hooks/**`, `.claude/active-scope` (case-folded on Windows). **Amended 2026-11-12:** a path under `.claude/worktrees/<wt>/` (git worktrees created by Claude Code and workflow harnesses) is **not** allowed blanket; the remainder is evaluated against that worktree's own `APPROVAL.md` and `.claude/active-scope` (G2w), so parallel agents cannot bypass the gate. Tests T42 (code in a worktree, unsigned ⇒ deny) and T43 (docs in a worktree ⇒ allow). |
| G3 | `docs/architecture/multi-transport/APPROVAL.md` is **never** editable by an agent (owner only). |
| G4 | Any other repository path is allowed only if the **active scope** is open in `APPROVAL.md`. |
| G5 | Active scope = the first line of `.claude/active-scope`, one of the `APPROVAL.md` scope ids. Missing or invalid ⇒ denied. The file is **gitignored** and per working tree, so it never reaches `main` and no later session inherits it. |
| G6 | A scope is open iff its `### Scope: <id>` block contains a line exactly `Status: Approved` (CR stripped; the owner edits on Windows). |
| G7 | `Bash` targets are extracted heuristically: each unquoted line is a separate command and here-document bodies are skipped; merged operators (`)>`) are split; targets are redirections, `tee`, `cp`/`mv`/`install`/`ln` destinations, `rm`/`touch`/`mkdir`/…, `sed -i`/`perl -i`, `dd of=`, `patch`, `find … -delete`, `git mv`/`git rm` operands and worktree-changing `git` subcommands (honouring `git -C`; `git stash list\|show` read-only); `cd` updates the base directory. Unparseable commands are treated as writing to the repository root. |
| G8 | Denial is returned as `permissionDecision: "deny"` with a reason naming `APPROVAL.md`, the scope and the path. |
| G9 | Evaluated first. The gate needs only `bash` and a Python ≥ 3.8 interpreter (`python3`, `python` or `py -3`, so it also runs under Git Bash on the owner's Windows machine). If none is found, it fails closed **for G4 targets only**: `Edit`/`Write`/`NotebookEdit` paths outside the repository or under G2 are allowed, `APPROVAL.md` and everything else denied; a `Bash` call is denied because its targets are unknown and count as the repository root. An unreadable `APPROVAL.md` closes every scope. |

**D1a — one open scope (owner rule).** At most one milestone scope (`0.4`–`1.4`) is `Approved` at a
time. At milestone exit the owner sets it to `Deferred` with the condition "milestone exited
YYYY-MM-DD"; `phase-0` and `process` likewise once their work is done. Milestones approved in
advance (as the 2026-11-12 delegation approved 0.5–0.7 and 1.0 in principle) are recorded as
`Deferred` with the condition "pre-approved; set to Approved by the owner at exit of the previous
milestone", never as a second `Approved` block (check H11). `.claude/active-scope` is
agent-writable (G2), so with one open scope the gate is in effect a global switch ("is any code work
approved now?"), and attribution of work to a milestone is a review matter. This rule is also to be
recorded in `APPROVAL.md` §1.

The gate is **a guard for agents, not a security boundary.** Bypasses (interpreters writing files,
indirection through `bash -c`/variables, unlisted writers, editing the hook itself under `.claude/hooks/**`,
GitHub MCP/API writes to remote branches, switching to a branch whose `APPROVAL.md` reads `Approved`)
are out of the threat model; the backstop is CODEOWNERS review of `.claude/settings.json`,
`.claude/hooks/**` and of the PR diff (ADR-0032).

### 2. Environment bootstrap — `SessionStart`, cloud sessions only

Runs only when `CLAUDE_CODE_REMOTE=true`. Idempotent. Never modifies a tracked file, and checks that
it did not (`git status --porcelain --untracked-files=no` before = after).

| Step | Action when missing |
|---|---|
| B1 | Android SDK at `/opt/android-sdk` (or `$ANDROID_HOME`): `cmdline-tools;latest`, then `platforms;android-36`, `platforms;android-35`, `build-tools;36.0.0` via `sdkmanager` (licences accepted non-interactively) |
| B2 | User-level Gradle init script `~/.gradle/init.d/muhabbet-maven-mirror.gradle` adding `https://maven-central.storage-download.googleapis.com/maven2/` to `pluginManagement` and `dependencyResolutionManagement` repositories in `beforeSettings` |
| B3 | `mobile/composeApp/google-services.json` written from the dummy heredoc in `mobile-ci.yml` (single source), only if absent **and** gitignored **and** untracked |
| B4 | `export ANDROID_HOME` / `ANDROID_SDK_ROOT` appended to `$CLAUDE_ENV_FILE` (once) |

Synchronous, with a 900 s hook timeout (the first run downloads the SDK; later runs are no-ops).

## Why

- The owner asked for the no-code rule as a mechanism; hooks hold regardless of agent memory.
- A path-plus-scope rule is simple enough to test exhaustively and to explain in one denial message.
- Versioning the bootstrap with the code keeps environment knowledge next to the build files it
  serves, instead of in a chat transcript or an out-of-repo setting.

## Consequences

- `.claude/settings.json`, `.claude/hooks/design-gate.sh`, `.claude/hooks/design_gate.py`,
  `.claude/hooks/session-start.sh` and `.claude/hooks/design-gate.test.sh` become reviewed files under
  CODEOWNERS (ADR-0032). Because `.gitignore:62` ignores the whole `.claude/` directory today (a child
  of an excluded directory cannot be re-included), the installation PR replaces that line with
  `/.claude/*`, `!/.claude/settings.json`, `!/.claude/hooks/`, `/.claude/active-scope`,
  `/.claude/settings.local.json`, `/.claude/worktrees/` and verifies that `git check-ignore
  .claude/settings.json` prints nothing and `git ls-files .claude` lists the five files
  (claude-code-hooks §3.5). `.claude/active-scope` stays gitignored.
- Starting work on a milestone becomes an explicit act: the owner approves the scope in
  `APPROVAL.md` (closing the previous one, D1a), and the session sets `.claude/active-scope`.
  Because the file is agent-writable, the gate cannot stop an agent from naming the open scope for
  work that belongs to an unapproved milestone; D1a bounds that to "only while some code work is
  approved".
- The hook runs in the owner's local sessions too (project `.claude/settings.json`). On the Windows
  dev machine Python must be installed (`winget install Python.Python.3.12`); otherwise the G9
  fallback allows docs edits but denies all `Bash`.
- An owner who wants an agent to change `APPROVAL.md` must make that edit personally (G3).
- The hook ships with its test: an attempted edit to `backend/**` is refused while `APPROVAL.md` is
  unsigned (brief consequence), plus the cases in the specification.
- The bootstrap pins one cmdline-tools archive (`commandlinetools-linux-13114758_latest.zip`, the one
  used in this session) **and its SHA-256**, recorded by the owner at the first verified download; a
  download or checksum failure is logged as a warning and never fails session start. Updating either
  is a reviewed change.

## Alternatives considered

| Alternative | Rejected because |
|---|---|
| CLAUDE.md rule only | Already broken by agents in the past (HANDOFF). Brief: rejected. |
| Git pre-commit hooks | Guard commits, not the edits agents make before committing; not installed in fresh clones. Brief: rejected. |
| Cloud environment setup script only | Also proposed, but lives outside the repository; the hook keeps the knowledge versioned with the code. Brief: rejected as the sole mechanism. |
| Infer scope from the path or branch name | Paths do not map to milestones (0.4 and 0.5 both touch `backend/**`); branch names `<type>/<issue#>-<slug>` carry no milestone (see reviewer note) |
| Deny all Bash while the gate is armed | Makes documentation work impossible (git, grep, builds of docs). The chosen heuristic still denies some git hygiene while unsigned (`git pull`/`merge`/`rebase`/`reset`/`checkout <branch>`/`stash` push/pop) because those rewrite arbitrary files; `git fetch`, `git switch`, `git commit`, `git push`, `git mv` within `docs/` remain allowed |

> **Note:** amendments G3 (`APPROVAL.md` never agent-editable) and G5
> (`.claude/active-scope`) accepted by owner delegation 2026-11-12 (D6; brief erratum E-44).

> **Note:** **accepted limitations** (process approval): the Bash rule is a heuristic, "a
> guard, not a boundary". It catches ordinary agent edits, not determined circumvention. Accepted
> blind spots (specification §2.2): interpreters and build tools writing files; `bash -c`/`eval`/
> scripts, variables and command substitution in paths; unlisted writers (`find -exec`, `tar -x`,
> `curl -o`, editors); `pushd`/`cd` with options; GitHub MCP/API writes to remote branches.
> Accepted false positives: worktree-rewriting git subcommands, and any command `shlex` cannot parse,
> are denied while unsigned. Multi-line commands, `)>`, `find -delete`, `patch`, `git -C` and heredocs
> containing an apostrophe are handled and covered by tests T28–T37.

> **Resolved (2026-11-12):** D6 plus the scope approvals, with D1a kept intact: `APPROVAL.md` opens
> `phase-0`, `process` and `0.4` only (`Status: Approved`). 0.5, 0.6, 0.7 and 1.0 are recorded as
> `Status: Deferred` with Conditions "design approved 2026-11-12 by owner delegation; opened by the
> owner when the previous milestone exits; 0.7 excludes ADR 0009 hosting/budget (Q-O8); 1.0 gated on
> counsel, external crypto review and the licence step (D1)". The owner (G3) writes these lines and
> the D1a rule into `APPROVAL.md` §1; agents do not.

## Evidence

Owner decisions 3 and 4 (brief header); SSOT-03 (HANDOFF entry point carried false facts); SSOT-14
(CLAUDE.md does four jobs); this session's environment setup; `.github/workflows/mobile-ci.yml`
"Inject google-services.json" step — [findings register](../reviews/2026-11-12-findings-register.md).

## Verification

| # | Check | Method |
|---|---|---|
| H1 | Gate refuses code while unsigned | `design-gate.test.sh` case: `Write backend/src/.../X.kt` with every scope `Pending` ⇒ deny naming `APPROVAL.md` |
| H2 | Gate allows docs | `Write docs/process/x.md` ⇒ allow |
| H3 | Gate protects `APPROVAL.md` | `Edit docs/architecture/multi-transport/APPROVAL.md` ⇒ deny |
| H4 | Gate opens per scope | Fixture with `0.4` Approved, `.claude/active-scope` = `0.4` ⇒ allow `backend/**`; active scope `0.5` ⇒ deny |
| H5 | Bash heuristics | `echo x > backend/a.kt` deny; `echo x > /tmp/a` allow; `sed -i s/a/b/ docs/a.md` allow; `git apply p.diff` deny while unsigned; multi-line `echo hi⏎rm backend/build.gradle.kts` deny; `(echo x)>backend/a.kt` deny; `find backend -name '*.kt' -delete` deny; `git -C backend apply p.diff` deny; `git stash list` allow; heredoc with an apostrophe written to `/tmp` allow (spec TP1, T28–T37) |
| H6 | Bootstrap idempotent | Run `session-start.sh` twice with `CLAUDE_CODE_REMOTE=true`; second run installs nothing and finishes in seconds |
| H7 | Bootstrap leaves the tree clean | `git status --porcelain` identical before/after |
| H8 | Bootstrap enables the build | In a fresh cloud session: `./gradlew :mobile:composeApp:compileCommonMainKotlinMetadata` resolves dependencies without HTTP 429 (owner or agent; not a device criterion) |
| H9 | Local sessions untouched | Run with `CLAUDE_CODE_REMOTE` unset ⇒ exits 0, no changes |
| H10 | Gate on Windows / without Python | Spec TP2a on the owner's Windows machine (Git Bash + Python): docs edit and `git status` allowed, `backend/**` denied; and T38–T41 (no interpreter on `PATH`): docs/`/tmp` writes allowed, `backend/**` and all `Bash` denied |
| H11 | One open scope | `grep -c '^Status: Approved' APPROVAL.md` counts at most one milestone scope (`0.4`–`1.4`) at any time; `.claude/active-scope` is listed in `.gitignore` and `git ls-files .claude/active-scope` is empty |
