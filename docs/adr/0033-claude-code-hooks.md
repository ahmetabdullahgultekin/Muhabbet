# ADR 0033 — Claude Code hooks: a design gate and a reproducible cloud environment

**Status:** Proposed (process) — pending owner approval; no hook file may be created and no code written against this ADR until approval is recorded in [`docs/architecture/multi-transport/APPROVAL.md`](../architecture/multi-transport/APPROVAL.md) (scope `process`) · **Date:** 2026-11-12 · **Source:** [decision brief §3, ADR-0033 (owner decision 4, "both")](../architecture/multi-transport/decision-brief-2026-11-12.md) · **Specification:** [`docs/process/claude-code-hooks.md`](../process/claude-code-hooks.md) (exact proposed file contents, behaviour, test plan) · Supersedes nothing.

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
  `process`, each a block whose line `Status: Approved` opens it; later milestones re-arm the gate.

## Decision

Two hooks, registered in `.claude/settings.json`, added only after scope `process` is approved:

### 1. Design gate — `PreToolUse` on `Edit|MultiEdit|Write|NotebookEdit|Bash`

| Rule | Behaviour |
|---|---|
| G1 | A target path outside the repository is always allowed (scratchpad, `/tmp`, `~/.gradle`). |
| G2 | Inside the repository, these are always allowed: `docs/**`, `.github/ISSUE_TEMPLATE/**`, `.github/pull_request_template.md`, `CODEOWNERS` (root or `.github/`), `.claude/**`. |
| G3 | `docs/architecture/multi-transport/APPROVAL.md` is **never** editable by an agent (owner only). |
| G4 | Any other repository path is allowed only if the **active scope** is open in `APPROVAL.md`. |
| G5 | Active scope = the first line of `.claude/active-scope`, one of the `APPROVAL.md` scope ids. Missing or invalid ⇒ denied. |
| G6 | A scope is open iff its `### Scope: <id>` block contains a line exactly `Status: Approved` (CR stripped; the owner edits on Windows). |
| G7 | `Bash` targets are extracted heuristically (redirections, `tee`, `cp`/`mv`/`install`/`ln` destinations, `rm`/`touch`/`mkdir`/…, `sed -i`/`perl -i`, `dd of=`, patch-applying `git` subcommands). Unparseable commands are treated as writing to the repository root. |
| G8 | Denial is returned as `permissionDecision: "deny"` with a reason naming `APPROVAL.md`, the scope and the path. |
| G9 | Fail closed: if `jq` or `python3` is missing, or `APPROVAL.md` is unreadable, edits under G4 are denied. |

The gate is **a guard for agents, not a security boundary.** Bypasses (interpreters writing files,
`cd` inside a compound command, editing the hook itself under `.claude/**`) are out of the threat
model; the backstop is CODEOWNERS review of `.claude/**` and of the PR diff (ADR-0032).

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

- `.claude/settings.json`, `.claude/hooks/design-gate.sh`, `.claude/hooks/session-start.sh`,
  `.claude/hooks/design-gate.test.sh` and `.claude/active-scope` become reviewed files under
  CODEOWNERS (ADR-0032).
- Starting work on a milestone becomes an explicit act: set `.claude/active-scope`, and the owner
  approves the scope in `APPROVAL.md`.
- An owner who wants an agent to change `APPROVAL.md` must make that edit personally (G3).
- The hook ships with its test: an attempted edit to `backend/**` is refused while `APPROVAL.md` is
  unsigned (brief consequence), plus the cases in the specification.
- The bootstrap pins one cmdline-tools archive (`commandlinetools-linux-13114758_latest.zip`, the one
  used in this session); updating it is a reviewed change.

## Alternatives considered

| Alternative | Rejected because |
|---|---|
| CLAUDE.md rule only | Already broken by agents in the past (HANDOFF). Brief: rejected. |
| Git pre-commit hooks | Guard commits, not the edits agents make before committing; not installed in fresh clones. Brief: rejected. |
| Cloud environment setup script only | Also proposed, but lives outside the repository; the hook keeps the knowledge versioned with the code. Brief: rejected as the sole mechanism. |
| Infer scope from the path or branch name | Paths do not map to milestones (0.4 and 0.5 both touch `backend/**`); branch names `<type>/<issue#>-<slug>` carry no milestone (see reviewer note) |
| Deny all Bash while the gate is armed | Makes documentation work impossible (git, grep, builds of docs) |

> **Reviewer note (open):** the brief does not define how the hook knows which scope an edit belongs
> to, and it lists `docs/**` as always-allowed, which would let an agent edit `APPROVAL.md` and open
> its own gate. This ADR adopts the remedy proposed in `APPROVAL.md`'s own reviewer note: the active
> scope is declared in `.claude/active-scope` (G5), and `APPROVAL.md` is excluded from the
> always-allowed set (G3). Both refine the brief and need the owner's explicit acceptance.

> **Reviewer note (open):** the brief scopes the Bash rule to "commands that write files". That set
> is not decidable from a command string; G7 is a heuristic. Accepting the gate means accepting that
> it catches ordinary agent edits, not determined circumvention.

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
| H5 | Bash heuristics | `echo x > backend/a.kt` deny; `echo x > /tmp/a` allow; `sed -i s/a/b/ docs/a.md` allow; `git apply p.diff` deny while unsigned |
| H6 | Bootstrap idempotent | Run `session-start.sh` twice with `CLAUDE_CODE_REMOTE=true`; second run installs nothing and finishes in seconds |
| H7 | Bootstrap leaves the tree clean | `git status --porcelain` identical before/after |
| H8 | Bootstrap enables the build | In a fresh cloud session: `./gradlew :mobile:composeApp:compileCommonMainKotlinMetadata` resolves dependencies without HTTP 429 (owner or agent; not a device criterion) |
| H9 | Local sessions untouched | Run with `CLAUDE_CODE_REMOTE` unset ⇒ exits 0, no changes |
