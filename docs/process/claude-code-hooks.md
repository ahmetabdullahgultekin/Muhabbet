# Claude Code hooks — specification (ADR-0033)

> **Status: Proposed — pending owner approval.** Nothing in this document is accepted until the owner
> records approval of scope `process` in
> [`docs/architecture/multi-transport/APPROVAL.md`](../architecture/multi-transport/APPROVAL.md). No
> code may be written — and **none of the files below may be created** — until that approval is
> recorded there. The file contents here are the exact proposal for review; they exist in the
> repository only as text in this document.

| | |
|---|---|
| **Decision record** | [ADR-0033](../adr/0033-claude-code-hooks.md) |
| **Source of truth** | [decision brief §3, ADR-0033](../architecture/multi-transport/decision-brief-2026-11-12.md) |
| **Related** | [ADR-0032](../adr/0032-agent-identity-verification-gate.md) (CODEOWNERS), [`agent-workflow.md`](agent-workflow.md), `APPROVAL.md` §1.1 (block format the hook parses) |
| **Files proposed** | `.claude/settings.json`, `.claude/hooks/design-gate.sh`, `.claude/hooks/design-gate.test.sh`, `.claude/hooks/session-start.sh`, `.claude/active-scope` |
| **Runtime dependencies** | `bash`, `jq`, `python3` (stdlib only), `awk`, `realpath` (GNU coreutils), `git`; bootstrap also `curl`, `unzip` — all present in the cloud image used on 2026-11-12 |

## 1. Overview

| Hook | Event | Matcher | Mode | Purpose |
|---|---|---|---|---|
| `design-gate.sh` | `PreToolUse` | `Edit\|MultiEdit\|Write\|NotebookEdit\|Bash` | synchronous, 15 s timeout | Refuse agent edits outside docs/governance paths unless the active scope is approved in `APPROVAL.md` |
| `session-start.sh` | `SessionStart` | (all sources) | synchronous, 900 s timeout | Make a cloud session Android/Gradle-capable; no-op locally |

Both are executed by the Claude Code harness, not by the model, so they hold regardless of what an
agent remembers. Neither is a security boundary (ADR-0033 "Why").

## 2. Design gate — behaviour

### 2.1 Decision table

Evaluated per target path, in order. The first matching row decides. One denied target denies the
whole tool call.

| # | Target (repository-relative after `realpath -m`) | Result |
|---|---|---|
| G1 | Outside the repository (`/tmp`, scratchpad, `~/.gradle`, `/dev/null`) | allow |
| G3 | `docs/architecture/multi-transport/APPROVAL.md` | **deny** (owner only) — even when a scope is open |
| G2 | `docs/**`, `.github/ISSUE_TEMPLATE/**`, `.github/pull_request_template.md`, `CODEOWNERS`, `.github/CODEOWNERS`, `.claude/**` | allow |
| G5 | anything else, and `.claude/active-scope` missing or not one of `phase-0 0.4 0.5 0.6 0.7 1.0 1.1 1.2 1.3 1.4 process` | **deny** |
| G6 | anything else, and the active scope's block lacks a line exactly `Status: Approved` | **deny** |
| G4 | anything else, and the active scope is open | allow (normal permission flow continues) |
| G9 | `jq` or `python3` missing | **deny** everything (fail closed) |

"Allow" means the hook emits nothing and exits 0; the harness's ordinary permission rules still
apply. "Deny" emits:

```json
{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny",
 "permissionDecisionReason":"Design gate (ADR-0033): scope '0.5' is not approved in docs/architecture/multi-transport/APPROVAL.md, so 'backend/…' may not be changed. Approval is recorded by the owner only."}}
```

### 2.2 Target extraction

| Tool | Target(s) |
|---|---|
| `Edit`, `MultiEdit`, `Write` | `tool_input.file_path` |
| `NotebookEdit` | `tool_input.notebook_path` |
| `Bash` | Heuristic over `tool_input.command`, split on `&& \|\| ; \| & ( )`: the word after `>`, `>>`, `>\|`, `&>`, `&>>`, `>&` (file descriptors such as `2>&1` ignored); all operands of `rm rmdir touch mkdir truncate shred unlink chmod chown tee`; the last operand of `cp mv install ln rsync`; the files of `sed -i` / `perl -i`; `dd of=`; repository root (`.`) for `git apply am checkout restore reset merge pull cherry-pick rebase revert stash mv rm clean` except `git checkout -b` / `git switch -c`. A command `shlex` cannot parse counts as a write to `.`. |

Relative paths resolve against the hook input's `cwd`. Known blind spots, accepted (ADR-0033
reviewer note): `cd x && echo > y` (resolved against the original `cwd`), interpreters writing files
(`python -c`, `node -e`), build tools generating sources, and edits to the hook itself under
`.claude/**` — the last is caught by CODEOWNERS review (ADR-0032).

### 2.3 Active scope

`.claude/active-scope` is a tracked one-line file holding a scope id, e.g. `0.4`. It is set in the
PR that starts work on a milestone (by the owner, or by an agent at the owner's request — it is under
`.claude/**`, and setting it grants nothing: the scope must still be approved). Branches for
different milestones carry different values. Absent file ⇒ only docs/governance paths are editable.

## 3. Proposed file contents

### 3.1 `.claude/settings.json`

```json
{
  "hooks": {
    "SessionStart": [
      {
        "hooks": [
          {
            "type": "command",
            "command": "\"$CLAUDE_PROJECT_DIR\"/.claude/hooks/session-start.sh",
            "timeout": 900
          }
        ]
      }
    ],
    "PreToolUse": [
      {
        "matcher": "Edit|MultiEdit|Write|NotebookEdit|Bash",
        "hooks": [
          {
            "type": "command",
            "command": "\"$CLAUDE_PROJECT_DIR\"/.claude/hooks/design-gate.sh",
            "timeout": 15
          }
        ]
      }
    ]
  }
}
```

### 3.2 `.claude/hooks/design-gate.sh`

```bash
#!/usr/bin/env bash
# PROPOSED (ADR-0033) — design gate. PreToolUse on Edit|MultiEdit|Write|NotebookEdit|Bash.
# A guard for agents, not a security boundary. Spec: docs/process/claude-code-hooks.md
set -uo pipefail

ROOT="$(realpath -m -- "${CLAUDE_PROJECT_DIR:-$PWD}")"
APPROVAL_REL="docs/architecture/multi-transport/APPROVAL.md"
APPROVAL="$ROOT/$APPROVAL_REL"
SCOPE_FILE="$ROOT/.claude/active-scope"
SCOPES='^(phase-0|0\.4|0\.5|0\.6|0\.7|1\.0|1\.1|1\.2|1\.3|1\.4|process)$'

deny() {
  local msg="Design gate (ADR-0033): $1"
  if command -v jq >/dev/null 2>&1; then
    jq -n --arg r "$msg" '{hookSpecificOutput:{hookEventName:"PreToolUse",
      permissionDecision:"deny", permissionDecisionReason:$r}}'
    exit 0
  fi
  printf '%s\n' "$msg" >&2
  exit 2                                   # exit 2 = block, stderr goes to the agent
}

command -v jq >/dev/null 2>&1 && command -v python3 >/dev/null 2>&1 \
  || deny "jq and python3 are required; the gate fails closed without them."

input="$(cat)"
tool="$(jq -r '.tool_name // empty' <<<"$input")"
cwd="$(jq -r '.cwd // empty' <<<"$input")"
[ -n "$cwd" ] || cwd="$ROOT"

# Repository-relative path of $1, or empty when it lies outside the repository.
rel() {
  local p="$1"
  case "$p" in "~"|"~/"*) p="$HOME${p#\~}" ;; /*) ;; *) p="$cwd/$p" ;; esac
  p="$(realpath -m -- "$p")"
  if [ "$p" = "$ROOT" ]; then printf '.\n'
  elif [ "${p#"$ROOT"/}" != "$p" ]; then printf '%s\n' "${p#"$ROOT"/}"
  else printf '\n'; fi
}

active_scope() {
  [ -f "$SCOPE_FILE" ] || return 1
  local s; s="$(head -n 1 "$SCOPE_FILE" | tr -d '[:space:]')"
  [[ "$s" =~ $SCOPES ]] || return 1
  printf '%s\n' "$s"
}

scope_open() {                              # APPROVAL.md §1.1 parsing rule
  [ -r "$APPROVAL" ] || return 1
  awk -v h="### Scope: $1" '
    { sub(/\r$/, "") }
    $0 == h { inb = 1; next }
    inb && /^### / { exit }
    inb && $0 == "Status: Approved" { found = 1; exit }
    END { exit (found ? 0 : 1) }' "$APPROVAL"
}

check() {
  local r scope
  r="$(rel "$1")"
  [ -z "$r" ] && return 0                                        # G1 outside the repo
  [ "$r" = "$APPROVAL_REL" ] && deny "$APPROVAL_REL is edited by the owner only."   # G3
  case "$r" in                                                   # G2
    docs/*|.github/ISSUE_TEMPLATE/*|.github/pull_request_template.md|CODEOWNERS|.github/CODEOWNERS|.claude/*)
      return 0 ;;
  esac
  scope="$(active_scope)" || deny "'$r' is outside docs/governance paths and no valid scope is set in .claude/active-scope. Ask the owner which scope this work belongs to; it must read 'Status: Approved' in $APPROVAL_REL."
  scope_open "$scope" || deny "scope '$scope' is not approved in $APPROVAL_REL, so '$r' may not be changed. Approval is recorded by the owner only."
  return 0                                                       # G4 open
}

bash_targets() {                            # G7: heuristic, one path per line
  python3 -I - "$1" <<'PY'
import os, shlex, sys
cmd = sys.argv[1]
try:
    lex = shlex.shlex(cmd, posix=True, punctuation_chars=True)
    lex.whitespace_split = True
    toks = list(lex)
except ValueError:
    print("."); sys.exit(0)
SEP = {"&&", "||", ";", "|", "&", "(", ")", ";;", "|&"}
REDIR = {">", ">>", ">|", "&>", "&>>", ">&"}
PREFIX = {"sudo", "env", "command", "time", "nohup", "xargs"}
ALL = {"rm", "rmdir", "touch", "mkdir", "truncate", "shred", "unlink", "chmod", "chown", "tee"}
LAST = {"cp", "mv", "install", "ln", "rsync"}
GIT_RW = {"apply", "am", "checkout", "restore", "reset", "merge", "pull", "cherry-pick",
          "rebase", "revert", "stash", "mv", "rm", "clean"}
out = []
segs, cur = [], []
for t in toks:
    if t in SEP:
        segs.append(cur); cur = []
    else:
        cur.append(t)
segs.append(cur)
for seg in segs:
    words, i = [], 0
    while i < len(seg):
        t = seg[i]
        if t in REDIR:
            if i + 1 < len(seg) and not seg[i + 1].lstrip("&").isdigit() and seg[i + 1] != "-":
                out.append(seg[i + 1])
            i += 2; continue
        words.append(t); i += 1
    while words and ("=" in words[0] and not words[0].startswith("-") or words[0] in PREFIX):
        words = words[1:]
    if not words:
        continue
    verb, args = os.path.basename(words[0]), words[1:]
    scripted = verb in ("sed", "perl") and any(a in ("-e", "-f", "--expression", "--file") for a in args)
    ops, skip = [], False
    for a in args:
        if skip:
            skip = False
        elif scripted and a in ("-e", "-f", "--expression", "--file"):
            skip = True                     # the next word is the sed/perl script, not a file
        elif not a.startswith("-"):
            ops.append(a)
    if verb in ALL:
        out += ops
    elif verb in LAST and ops:
        out.append(ops[-1])
    elif verb in ("sed", "perl") and any(a.startswith("-i") or a == "--in-place" for a in args):
        out += ops if scripted else ops[1:]
    elif verb == "dd":
        out += [a[3:] for a in args if a.startswith("of=")]
    elif verb == "git" and ops:
        sub = ops[0]
        if sub in ("checkout", "switch") and any(a in ("-b", "-B", "-c", "-C") for a in args) \
                and "--" not in args:
            continue
        if sub in GIT_RW:
            out.append(".")
print("\n".join(os.path.expanduser(p) for p in out))
PY
}

case "$tool" in
  Edit|MultiEdit|Write) check "$(jq -r '.tool_input.file_path // empty' <<<"$input")" ;;
  NotebookEdit)         check "$(jq -r '.tool_input.notebook_path // empty' <<<"$input")" ;;
  Bash)
    targets="$(bash_targets "$(jq -r '.tool_input.command // empty' <<<"$input")")" \
      || deny "could not parse the Bash command."
    while IFS= read -r t; do [ -n "$t" ] && check "$t"; done <<<"$targets" ;;
esac
exit 0
```

### 3.3 `.claude/hooks/design-gate.test.sh`

```bash
#!/usr/bin/env bash
# PROPOSED (ADR-0033) — tests for design-gate.sh. Runs against a throwaway fixture repository,
# never against the real APPROVAL.md. Usage: .claude/hooks/design-gate.test.sh
set -euo pipefail
HOOK="$(cd "$(dirname "$0")" && pwd)/design-gate.sh"
FIX="$(mktemp -d)"; trap 'rm -rf "$FIX"' EXIT
mkdir -p "$FIX/docs/architecture/multi-transport" "$FIX/.claude" "$FIX/backend"

approval() {   # $1 = scope to approve ("" = none); CRLF line endings, as the owner's editor may write
  { for s in phase-0 0.4 0.5 process; do
      printf '### Scope: %s\r\n' "$s"
      if [ "$s" = "$1" ]; then printf 'Status: Approved\r\n'; else printf 'Status: Pending\r\n'; fi
      printf 'Approved-by:\r\nDate:\r\nConditions:\r\n\r\n'
    done; } > "$FIX/docs/architecture/multi-transport/APPROVAL.md"
}
scope() { if [ -n "$1" ]; then printf '%s\n' "$1" > "$FIX/.claude/active-scope"; else rm -f "$FIX/.claude/active-scope"; fi; }

pass=0; fail=0
run() {        # $1 expected allow|deny  $2 name  $3 tool  $4 json tool_input
  local out verdict
  out="$(jq -n --arg t "$3" --argjson i "$4" --arg c "$FIX" \
          '{tool_name:$t, tool_input:$i, cwd:$c, hook_event_name:"PreToolUse"}' \
        | CLAUDE_PROJECT_DIR="$FIX" "$HOOK" 2>&1)" && rc=0 || rc=$?
  if [ $rc -eq 2 ] || grep -q '"permissionDecision": *"deny"' <<<"$out"; then verdict=deny; else verdict=allow; fi
  if [ "$verdict" = "$1" ]; then pass=$((pass+1)); printf 'ok    %s\n' "$2"
  else fail=$((fail+1)); printf 'FAIL  %s (expected %s, got %s)\n%s\n' "$2" "$1" "$verdict" "$out"; fi
}
W() { jq -n --arg p "$1" '{file_path:$p, content:"x"}'; }
B() { jq -n --arg c "$1" '{command:$c}'; }

approval ""; scope ""
run deny  "T01 backend edit refused while APPROVAL.md is unsigned"   Write "$(W "$FIX/backend/A.kt")"
run allow "T02 docs edit allowed while unsigned"                     Write "$(W "$FIX/docs/process/x.md")"
run allow "T03 relative docs path allowed"                           Edit  "$(W docs/adr/0099-x.md)"
run deny  "T04 APPROVAL.md never editable by an agent"               Edit  "$(W "$FIX/docs/architecture/multi-transport/APPROVAL.md")"
run allow "T05 .claude/** allowed"                                   Write "$(W "$FIX/.claude/active-scope")"
run allow "T06 issue templates allowed"                              Write "$(W "$FIX/.github/ISSUE_TEMPLATE/bug.yml")"
run deny  "T07 workflows not on the allow-list"                      Write "$(W "$FIX/.github/workflows/ci.yml")"
run allow "T08 path outside the repository allowed"                  Write "$(W /tmp/scratch.txt)"
run deny  "T09 ../ traversal back into code refused"                 Write "$(W "$FIX/docs/../backend/B.kt")"
run deny  "T10 notebook outside docs refused"                        NotebookEdit "$(jq -n --arg p "$FIX/backend/n.ipynb" '{notebook_path:$p}')"
run deny  "T11 Bash redirect into code refused"                      Bash  "$(B 'echo x > backend/A.kt')"
run allow "T12 Bash redirect to /tmp allowed"                        Bash  "$(B 'echo x > /tmp/a && cat /tmp/a')"
run allow "T13 Bash sed -i on docs allowed"                          Bash  "$(B "sed -i -e 's/a/b/' docs/a.md")"
run deny  "T14 Bash sed -i on code refused"                          Bash  "$(B "sed -i 's/a/b/' backend/A.kt")"
run deny  "T15 git apply refused while unsigned"                     Bash  "$(B 'git apply fix.patch')"
run allow "T16 read-only Bash allowed"                               Bash  "$(B 'grep -rn foo backend | head -5 2>&1')"
run allow "T17 git checkout -b allowed"                              Bash  "$(B 'git checkout -b docs/1-x')"
run deny  "T18 cp into code refused"                                 Bash  "$(B 'cp /tmp/a backend/A.kt')"
run deny  "T19 unparseable command treated as a repository write"    Bash  "$(B "echo 'unterminated")"
run deny  "T26 rm -f into code refused"                              Bash  "$(B 'rm -f backend/A.kt')"
run deny  "T27 tee into code refused"                                Bash  "$(B 'printf x | tee -a backend/A.kt')"
run allow "T20 2>&1 is not a file target"                            Bash  "$(B 'ls docs 2>&1 >/dev/null')"

scope "0.4"
run deny  "T21 scope set but still Pending"                          Write "$(W "$FIX/backend/A.kt")"
approval "0.4"
run allow "T22 scope 0.4 approved and active"                        Write "$(W "$FIX/backend/A.kt")"
scope "0.5"
run deny  "T23 a different active scope re-arms the gate"            Write "$(W "$FIX/backend/A.kt")"
scope "1.9"
run deny  "T24 invalid scope id refused"                             Write "$(W "$FIX/backend/A.kt")"
scope "0.4"
run deny  "T25 APPROVAL.md stays owner-only even when a scope is open" Edit "$(W "$FIX/docs/architecture/multi-transport/APPROVAL.md")"

printf '\n%d passed, %d failed\n' "$pass" "$fail"
[ "$fail" -eq 0 ]
```

### 3.4 `.claude/hooks/session-start.sh`

```bash
#!/usr/bin/env bash
# PROPOSED (ADR-0033) — cloud-session environment bootstrap. SessionStart, synchronous.
# Cloud sessions only; idempotent; never modifies a tracked file. Spec: docs/process/claude-code-hooks.md
set -euo pipefail
cat >/dev/null 2>&1 || true                          # hook input on stdin is not needed
[ "${CLAUDE_CODE_REMOTE:-}" = "true" ] || exit 0

ROOT="${CLAUDE_PROJECT_DIR:-$PWD}"
SDK="${ANDROID_HOME:-/opt/android-sdk}"
CMDLINE_ZIP_URL="https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip"
MIRROR="https://maven-central.storage-download.googleapis.com/maven2/"
PACKAGES=("platforms;android-36" "platforms;android-35" "build-tools;36.0.0")
log() { printf '[session-start] %s\n' "$*" >&2; }

tracked_before="$(git -C "$ROOT" status --porcelain --untracked-files=no 2>/dev/null || true)"

# B1 — Android SDK ----------------------------------------------------------------------------
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$SDKMANAGER" ]; then
  log "installing Android cmdline-tools into $SDK"
  tmp="$(mktemp -d)"
  curl -fsSL --retry 3 -o "$tmp/cmdline-tools.zip" "$CMDLINE_ZIP_URL"
  unzip -q "$tmp/cmdline-tools.zip" -d "$tmp"
  mkdir -p "$SDK/cmdline-tools"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$tmp"
fi
missing=()
for p in "${PACKAGES[@]}"; do
  [ -d "$SDK/${p//;//}" ] || missing+=("$p")
done
if [ "${#missing[@]}" -gt 0 ]; then
  log "installing ${missing[*]}"
  yes 2>/dev/null | "$SDKMANAGER" --sdk_root="$SDK" --licenses >/dev/null 2>&1 || true
  "$SDKMANAGER" --sdk_root="$SDK" "${missing[@]}" >/dev/null
fi

# B2 — Gradle init script: Maven Central through Google's mirror -------------------------------
INIT="$HOME/.gradle/init.d/muhabbet-maven-mirror.gradle"
want="$(cat <<GRADLE
// Written by .claude/hooks/session-start.sh (ADR-0033). Cloud sessions only; not in the repository.
// repo.maven.apache.org answers HTTP 429 through the session proxy under Gradle's parallel fetches.
def MIRROR = '$MIRROR'
beforeSettings { settings ->
    settings.pluginManagement.repositories {
        maven { url MIRROR }
    }
    settings.dependencyResolutionManagement.repositories {
        maven { url MIRROR }
    }
}
GRADLE
)"
if [ ! -f "$INIT" ] || [ "$(cat "$INIT")" != "$want" ]; then
  log "writing $INIT"
  mkdir -p "$(dirname "$INIT")"
  printf '%s\n' "$want" > "$INIT"
fi

# B3 — CI dummy google-services.json (single source: the heredoc in mobile-ci.yml) -------------
GS_REL="mobile/composeApp/google-services.json"
GS="$ROOT/$GS_REL"
WF="$ROOT/.github/workflows/mobile-ci.yml"
if [ ! -e "$GS" ] && [ -f "$WF" ] \
   && git -C "$ROOT" check-ignore -q "$GS_REL" \
   && ! git -C "$ROOT" ls-files --error-unmatch "$GS_REL" >/dev/null 2>&1; then
  json="$(awk '/<< .GSEOF./ { f = 1; next } /^[[:space:]]*GSEOF[[:space:]]*$/ { f = 0 } f' "$WF" \
          | sed -E 's/^ {10}//')"
  if [ -n "$json" ] && printf '%s' "$json" | python3 -I -m json.tool >/dev/null 2>&1; then
    log "writing CI dummy $GS_REL (gitignored)"
    printf '%s\n' "$json" > "$GS"
  else
    log "WARNING: could not extract the dummy google-services.json from mobile-ci.yml; skipped"
  fi
fi

# B4 — ANDROID_HOME for the session -----------------------------------------------------------
if [ -n "${CLAUDE_ENV_FILE:-}" ] && ! grep -qs "^export ANDROID_HOME=" "$CLAUDE_ENV_FILE"; then
  printf 'export ANDROID_HOME="%s"\nexport ANDROID_SDK_ROOT="%s"\n' "$SDK" "$SDK" >> "$CLAUDE_ENV_FILE"
fi

# Self-check: no tracked file changed -----------------------------------------------------------
tracked_after="$(git -C "$ROOT" status --porcelain --untracked-files=no 2>/dev/null || true)"
if [ "$tracked_before" != "$tracked_after" ]; then
  log "ERROR: tracked files changed during bootstrap; this is a bug in the hook"
  exit 1
fi
exit 0
```

### 3.5 `.claude/active-scope`

Not created by the approval of `process` itself. Created with the first approved milestone, e.g.:

```text
0.4
```

## 4. Environment bootstrap — behaviour

| Step | Condition to act | Action | Idempotence check |
|---|---|---|---|
| guard | `CLAUDE_CODE_REMOTE != "true"` | exit 0 immediately (local and Windows sessions untouched) | — |
| B1a | `$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager` not executable | download `commandlinetools-linux-13114758_latest.zip` from `dl.google.com` (the archive installed in this session), unzip to `cmdline-tools/latest` | file exists |
| B1b | any of `platforms/android-36`, `platforms/android-35`, `build-tools/36.0.0` missing | accept licences, `sdkmanager --sdk_root=… <missing…>` | directory exists |
| B2 | `~/.gradle/init.d/muhabbet-maven-mirror.gradle` absent or different | write the init script (mirror first in `pluginManagement` and `dependencyResolutionManagement` repositories via `beforeSettings`) | content compare |
| B3 | `mobile/composeApp/google-services.json` absent **and** gitignored **and** untracked | extract the `GSEOF` heredoc from `.github/workflows/mobile-ci.yml`, strip the 10-space YAML indent, validate as JSON, write it | file exists |
| B4 | `$CLAUDE_ENV_FILE` set and has no `export ANDROID_HOME=` | append `export ANDROID_HOME` / `ANDROID_SDK_ROOT` | grep |
| self-check | always | compare `git status --porcelain --untracked-files=no` before/after; exit 1 if different | — |

Logs go to stderr only; `SessionStart` stdout would enter the model's context, and nothing here needs
to. The hook is synchronous so that the first Gradle command cannot race the SDK install; after the
first run it completes in about a second. Switch to `{"async": true}` only if the owner prefers faster
start-up over that guarantee.

Two `beforeSettings` mirrors are harmless: a session that already has its own init script (this
session has `~/.gradle/init.d/central-mirror.gradle`) simply lists the mirror twice.

> **Reviewer note (open):** the cmdline-tools archive is fetched over HTTPS without a pinned SHA-256,
> because no checksum for it appears in any source used here. Recording the hash at first install and
> pinning it in the script is a cheap hardening step for the owner to take when the file is created.

## 5. Test plan

| # | What | How | Expected | Status 2026-11-12 |
|---|---|---|---|---|
| TP1 | Gate decision table | `.claude/hooks/design-gate.test.sh` (fixture repository in `mktemp -d`, CRLF `APPROVAL.md`) | 27 passed, 0 failed | **Run** against the proposed text from a scratch directory: 27/27 |
| TP2 | Gate against the real `APPROVAL.md` (all scopes Pending) | `jq -n '{tool_name:"Write",tool_input:{file_path:"backend/x.kt"},cwd:"'"$PWD"'"}' \| CLAUDE_PROJECT_DIR=$PWD .claude/hooks/design-gate.sh` | deny JSON naming `APPROVAL.md` | **Run**: deny |
| TP3 | Gate in a live session | After installing: ask an agent to edit `backend/build.gradle.kts` | Tool call refused with the G5/G6 reason; agent reports it | Not run (files not created) |
| TP4 | Bootstrap no-op locally | `env -u CLAUDE_CODE_REMOTE .claude/hooks/session-start.sh </dev/null` | exit 0, no files written | **Run**: exit 0, nothing written |
| TP5 | Bootstrap B2–B4 in a fixture | `CLAUDE_CODE_REMOTE=true CLAUDE_PROJECT_DIR=<fixture repo> HOME=<tmp> CLAUDE_ENV_FILE=<tmp file>` | init script, valid dummy JSON, two env lines; tree clean | **Run**: as expected |
| TP6 | Bootstrap idempotence | run TP5 twice | second run writes nothing; env file still 2 lines | **Run**: as expected |
| TP7 | Bootstrap B1 on a fresh container | new cloud session with no `/opt/android-sdk` | SDK installed; `:mobile:composeApp:compileCommonMainKotlinMetadata` resolves without HTTP 429 | **Not run** — this container already had the SDK |
| TP8 | CI | add `bash .claude/hooks/design-gate.test.sh` to the backend or a lint job (runner per ADR-0031) | green | Not run |

TP3 and TP7 must be observed after the files exist; neither is a device criterion, so an agent may
report them, stating exactly what it ran.

## 6. Installation (after approval of scope `process`)

1. Owner records `Status: Approved` for scope `process` in `APPROVAL.md` (§7).
2. An agent (or the owner) creates the five files with exactly the content of §3 in one PR. The paths
   are under `.claude/**`, so the gate itself never blocks its own installation.
3. `chmod +x .claude/hooks/*.sh` (git records the executable bit).
4. The PR runs TP1 and TP2 and pastes the output; CODEOWNERS requests the owner's review (ADR-0032).
5. After merge, the next cloud session runs TP7; the first agent attempt to touch code runs TP3.

## 7. How the owner signs `APPROVAL.md`

1. Open the **raw** file `docs/architecture/multi-transport/APPROVAL.md` in an editor (not the
   rendered view; Markdown joins the four lines). CRLF line endings are fine.
2. In the block for the scope, change exactly one line from `Status: Pending` to `Status: Approved`,
   with no list marker and no trailing spaces. Fill `Approved-by:`, `Date:` (`YYYY-MM-DD`) and any
   binding `Conditions:`.
3. Record any ADR dispositions in §3 of the same file and add a change-log row.
4. Commit under the owner's own identity and merge through a PR (CODEOWNERS makes the owner the
   reviewer of this file; agents cannot edit it under G3).
5. Check: `awk` rule from §3.2, or TP2 with the scope set in `.claude/active-scope` ⇒ allow.
6. To revoke: set the line back to `Status: Pending` (or `Deferred`) with a dated condition. The gate
   re-arms on the next tool call.
