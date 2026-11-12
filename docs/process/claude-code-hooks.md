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
| **Files proposed** | `.claude/settings.json`, `.claude/hooks/design-gate.sh` (launcher), `.claude/hooks/design_gate.py`, `.claude/hooks/design-gate.test.sh`, `.claude/hooks/session-start.sh`; `.claude/active-scope` (gitignored, per working tree — §2.3) |
| **Runtime dependencies** | Design gate: `bash` and a Python ≥ 3.8 interpreter (stdlib only), found as `python3`, `python` or `py -3` — so it runs both in the Linux cloud image and under Git Bash on the owner's Windows dev machine (which ships neither `jq` nor, by default, Python; §6). No `jq`, `awk` or `realpath`. Without Python the launcher's G9 fallback applies. Bootstrap (cloud only): `bash`, `git`, `curl`, `unzip`, `awk`, `sed`, `python3` — all present in the cloud image used on 2026-11-12 |

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
whole tool call. G9 is evaluated first, once per call, because without an interpreter no other row
can be evaluated precisely.

| # | Target (repository-relative after `os.path.realpath`, case-folded on Windows) | Result |
|---|---|---|
| G9 | No Python ≥ 3.8 found (launcher fallback, bash only) | Fail closed **for G4 targets only**: an `Edit`/`Write`/`NotebookEdit` path that is absolute and outside the repository (G1) or under a G2 prefix is allowed, `APPROVAL.md` denied (G3), anything else denied — including relative paths and paths containing `..`. A `Bash` call is denied, because its targets cannot be extracted and an unknown target counts as the repository root (G7). Denial via exit 2 with the reason on stderr |
| G1 | Outside the repository (`/tmp`, scratchpad, `~/.gradle`, `/dev/null`) | allow |
| G3 | `docs/architecture/multi-transport/APPROVAL.md` | **deny** (owner only) — even when a scope is open |
| G2 | `docs/**`, `.github/ISSUE_TEMPLATE/**`, `.github/pull_request_template.md`, `CODEOWNERS`, `.github/CODEOWNERS`, `.claude/**` | allow |
| G5 | anything else, and `.claude/active-scope` missing or not one of `phase-0 0.4 0.5 0.6 0.7 1.0 1.1 1.2 1.3 1.4 process` | **deny** |
| G6 | anything else, and the active scope's block lacks a line exactly `Status: Approved` | **deny** |
| G4 | anything else, and the active scope is open | allow (normal permission flow continues) |

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
| `Bash` | Heuristic over `tool_input.command` (G7), in three passes. **(1) Flatten:** unquoted newlines become ` ; `, so every line is its own command; unquoted here-document bodies (`<<EOF`, `<<'EOF'`, `<<-EOF`) are dropped up to their delimiter line, so an apostrophe in a body does not break parsing. **(2) Tokenise** with `shlex` (`punctuation_chars`), then split merged operator runs (`)>`, `;>`, `&>>`, …) into separate operators by longest match. **(3) Per segment** (split on `&& \|\| ; \| & ( ) ;; \|&`): the word after `>`, `>>`, `>\|`, `&>`, `&>>`, `>&`, `<>` (file descriptors such as `2>&1` ignored; input redirections `<`, `<<`, `<<<`, `<&` skipped); all operands of `rm rmdir touch mkdir truncate shred unlink chmod chown tee`; the last operand of `cp mv install ln rsync`; the files of `sed -i` / `perl -i`; `dd of=`; `.` for `patch`; the path operands of `find … -delete`; for `git` (after skipping global options, `-C <dir>` and `-c <cfg>`; `-C` sets the directory used below): the operands of `git mv`/`git rm`, and the `-C` directory (default `.`) for `apply am checkout restore reset merge pull cherry-pick rebase revert stash clean` — except `git checkout -b`/`git switch -c` and `git stash list\|show`. `cd <dir>` changes the directory against which later segments resolve. A command `shlex` cannot parse counts as a write to `.`. |

Relative paths resolve against the hook input's `cwd` (updated by `cd` within the command).

Known blind spots, accepted (ADR-0033 reviewer note on G7) — the gate does **not** see:

| Blind spot | Example |
|---|---|
| Interpreters and tools writing files | `python -c`, `node -e`, `awk` with a redirection inside its program, `make`, Gradle generating sources |
| Indirection | `bash -c '…'`, `eval`, `sh script.sh`, `xargs` feeding a writer, `$(…)`/backticks building a path, variables (`f=backend/a.kt; echo x > "$f"` — the gate sees the literal word `$f`, not the expanded path) |
| Writers not listed | `find … -exec …`, `git -c core.worktree=…`, `tar -x`, `unzip`, `curl -o`, `wget -O`, `ed`, `ex`, `vim -c` |
| `cd` with options or `pushd`/`popd` | `cd -P x && …`, `pushd backend` |
| Edits to the hook itself | `.claude/**` is G2; caught by CODEOWNERS review (ADR-0032) |

Accepted false positives — **denied** while no scope is open, although harmless in a docs session:

| Command | Why | Workaround |
|---|---|---|
| `git pull`, `git merge`, `git rebase`, `git reset`, `git checkout <branch>`, `git restore`, `git stash` (push/pop/apply) | May rewrite any file; the gate cannot tell which | `git fetch` + `git switch` (allowed); the owner runs the rest |
| A command `shlex` still cannot parse (e.g. unbalanced quotes inside `$(…)` over several lines) | Counted as a write to `.` | Split the command, or use a temp file under `/tmp` |

### 2.3 Active scope

`.claude/active-scope` is a one-line file holding a scope id, e.g. `0.4`. It is **gitignored** (one
line in `.gitignore`, added in the installation PR) and exists per working tree: an agent writes it
at the start of a session at the owner's request (`agent-workflow.md` step 2). Keeping it out of
`main` means no later session inherits an open scope from a merged branch. Absent file ⇒ only
docs/governance paths are editable.

The file is under `.claude/**` (G2), so any agent can write any value. Setting it grants nothing
**unless the named scope is open** in `APPROVAL.md`. The owner rule in ADR-0033 D1a — **at most one
milestone scope (`0.4`–`1.4`) is `Approved` at a time; at milestone exit the owner sets it back to
`Deferred`** with a dated condition "milestone exited", and `phase-0`/`process` likewise once their
work is done — makes the gate equivalent to a global switch: while one scope is open, an agent can
name it and edit any code path, whichever milestone the work belongs to. Scope attribution is then
a matter of review (PR milestone, CODEOWNERS), not of the hook.

> **Reviewer note (open):** residual weakness — the gate distinguishes "some scope open" from "no
> scope open", not 0.4 work from 0.5 work. With two scopes open at once (if the owner does not apply
> the one-open-scope rule), an agent can name the approved one for unapproved work. The rule must
> also be added to `APPROVAL.md` §1, which this author group may not edit.

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

### 3.2 `.claude/hooks/design-gate.sh` (launcher, G9 fallback)

```bash
#!/usr/bin/env bash
# PROPOSED (ADR-0033) — design-gate launcher. Finds a Python 3 interpreter (Linux: python3;
# Windows Git Bash: python / py -3) and runs design_gate.py. No jq, no realpath.
# Spec: docs/process/claude-code-hooks.md
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
for py in "python3" "python" "py -3"; do
  # rejects the Windows Store "python" stub, which exists on PATH but is not an interpreter
  if $py -I -c 'import sys; sys.exit(sys.version_info < (3, 8))' >/dev/null 2>&1; then
    exec $py -I "$HERE/design_gate.py"
  fi
done
# G9 — no interpreter: fail closed for G4 targets only. A Bash command's targets cannot be
# extracted, so it counts as a write to the repository root (G4) and is denied; an Edit/Write path
# is allowed only when it is plainly outside the repository or under a G2 prefix.
input="$(cat)"
deny() { printf 'Design gate (ADR-0033): %s\n' "$1" >&2; exit 2; }
NOPY="no Python 3 interpreter found (python3, python, py -3); install one (Windows: winget install Python.Python.3.12)."
tool="$(sed -nE 's/.*"tool_name"[[:space:]]*:[[:space:]]*"([^"]*)".*/\1/p' <<<"$input" | head -n1)"
path="$(sed -nE 's/.*"(file_path|notebook_path)"[[:space:]]*:[[:space:]]*"([^"]*)".*/\2/p' <<<"$input" | head -n1)"
path="${path//\\\\//}"; root="${CLAUDE_PROJECT_DIR:-$PWD}"; root="${root//\\//}"
case "$tool" in Edit|MultiEdit|Write|NotebookEdit) ;; *) deny "$NOPY" ;; esac
case "$path" in *..*|"") deny "$NOPY" ;; esac
case "$path" in
  "$root"/*)      r="${path#"$root"/}" ;;
  /*|[A-Za-z]:/*) exit 0 ;;                                       # absolute, outside the repo: G1
  *)              deny "$NOPY" ;;                                 # relative: cwd unknown here
esac
case "$r" in docs/architecture/multi-transport/APPROVAL.md) deny "APPROVAL.md is edited by the owner only." ;;
  docs/*|.github/ISSUE_TEMPLATE/*|.github/pull_request_template.md|CODEOWNERS|.github/CODEOWNERS|.claude/*) exit 0 ;;
esac
deny "$NOPY"
```

### 3.3 `.claude/hooks/design_gate.py` (G1–G8)

```python
# PROPOSED (ADR-0033) — design gate, PreToolUse on Edit|MultiEdit|Write|NotebookEdit|Bash.
# A guard for agents, not a security boundary. Stdlib only; run with `python -I`.
# Spec: docs/process/claude-code-hooks.md
import json, os, re, shlex, sys

APPROVAL_REL = "docs/architecture/multi-transport/APPROVAL.md"
SCOPES = re.compile(r"^(phase-0|0\.4|0\.5|0\.6|0\.7|1\.0|1\.1|1\.2|1\.3|1\.4|process)$")
ALLOW = ("docs/", ".github/ISSUE_TEMPLATE/", ".claude/")
ALLOW_FILES = (".github/pull_request_template.md", "CODEOWNERS", ".github/CODEOWNERS")


def deny(msg):
    print(json.dumps({"hookSpecificOutput": {"hookEventName": "PreToolUse",
          "permissionDecision": "deny", "permissionDecisionReason": "Design gate (ADR-0033): " + msg}}))
    sys.exit(0)


def norm(p):
    return os.path.normcase(os.path.realpath(p))


ROOT = norm(os.environ.get("CLAUDE_PROJECT_DIR") or os.getcwd())


def rel(path, cwd):                       # repository-relative '/'-path, or None outside the repo
    p = norm(os.path.join(cwd, os.path.expanduser(path)))
    if p == ROOT:
        return "."
    if p.startswith(ROOT + os.sep):
        return p[len(ROOT) + 1:].replace(os.sep, "/")
    return None


def scope_open(scope):                    # APPROVAL.md §1.1 parsing rule (G6)
    try:
        lines = open(os.path.join(ROOT, APPROVAL_REL), encoding="utf-8").read().split("\n")
    except OSError:
        return False
    inb = False
    for ln in (l.rstrip("\r") for l in lines):
        if ln == "### Scope: " + scope:
            inb = True
        elif inb and ln.startswith("### "):
            return False
        elif inb and ln == "Status: Approved":
            return True
    return False


def check(path, cwd):
    r = rel(path, cwd)
    if r is None:
        return                                                    # G1
    if r.lower() == APPROVAL_REL.lower():
        deny(APPROVAL_REL + " is edited by the owner only.")      # G3
    if r.startswith(ALLOW) or r in ALLOW_FILES:
        return                                                    # G2
    try:
        scope = open(os.path.join(ROOT, ".claude", "active-scope"), encoding="utf-8").readline().strip()
    except OSError:
        scope = ""
    if not SCOPES.match(scope):                                   # G5
        deny("'%s' is outside docs/governance paths and no valid scope is set in .claude/active-scope. "
             "It must name a scope that reads 'Status: Approved' in %s." % (r, APPROVAL_REL))
    if not scope_open(scope):                                     # G6
        deny("scope '%s' is not approved in %s, so '%s' may not be changed. Approval is recorded by "
             "the owner only." % (scope, APPROVAL_REL, r))
    # G4: open scope, normal permission flow continues


# --- G7: Bash target extraction (heuristic) ---------------------------------------------------
def flatten(cmd):
    """Unquoted newlines -> ' ; '; unquoted here-document bodies dropped."""
    out, i, q, pending = [], 0, None, []
    while i < len(cmd):
        c = cmd[i]
        if q == "'":
            q = None if c == "'" else q
        elif c == "\\" and i + 1 < len(cmd):
            out.append(cmd[i:i + 2]); i += 2; continue
        elif q == '"':
            q = None if c == '"' else q
        elif c in "'\"":
            q = c
        elif cmd.startswith("<<", i) and not cmd.startswith("<<<", i):
            m = re.match(r"<<(-?)\s*(['\"]?)([A-Za-z0-9_]+)\2", cmd[i:])
            if m:
                pending.append((m.group(3), m.group(1) == "-"))
                out.append(cmd[i:i + m.end()]); i += m.end(); continue
        elif c == "\n":
            out.append(" ; "); i += 1
            for word, strip_tabs in pending:          # skip each pending here-document body
                while i <= len(cmd):
                    j = cmd.find("\n", i); j = len(cmd) if j < 0 else j
                    line = cmd[i:j]; i = j + 1
                    if (line.lstrip("\t") if strip_tabs else line) == word:
                        break
            pending = []
            continue
        out.append(c); i += 1
    return "".join(out)


OPS = ["&>>", "<<<", "<<-", "&&", "||", ";;", "|&", ">>", ">|", "&>", ">&", "<<", "<&", "<>",
       ">", "<", "|", "&", ";", "(", ")"]
SEP = {"&&", "||", ";", "|", "&", "(", ")", ";;", "|&"}
REDIR = {">", ">>", ">|", "&>", "&>>", ">&", "<>"}
INREDIR = {"<", "<<", "<<-", "<<<", "<&"}
PREFIX = {"sudo", "env", "command", "time", "nohup", "xargs", "exec"}
ALL = {"rm", "rmdir", "touch", "mkdir", "truncate", "shred", "unlink", "chmod", "chown", "tee"}
LAST = {"cp", "mv", "install", "ln", "rsync"}
GIT_RW = {"apply", "am", "checkout", "restore", "reset", "merge", "pull", "cherry-pick",
          "rebase", "revert", "stash", "clean"}


def split_ops(tok):
    if not tok or any(ch not in "();<>|&" for ch in tok):
        return [tok]
    res = []
    while tok:
        op = next(o for o in OPS if tok.startswith(o))
        res.append(op); tok = tok[len(op):]
    return res


def bash_targets(cmd, cwd):
    """Yield (path, cwd) pairs; a command that cannot be parsed yields the repository root."""
    try:
        lex = shlex.shlex(flatten(cmd), posix=True, punctuation_chars=True)
        lex.whitespace_split = True
        toks = [p for t in lex for p in split_ops(t)]
    except ValueError:
        yield ".", cwd; return
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
                    yield seg[i + 1], cwd
                i += 2; continue
            if t in INREDIR:
                i += 2; continue
            words.append(t); i += 1
        while words and (("=" in words[0] and not words[0].startswith("-")) or words[0] in PREFIX):
            words = words[1:]
        if not words:
            continue
        verb, args = os.path.basename(words[0]), words[1:]
        ops = [a for a in args if not a.startswith("-")]
        if verb == "cd" and len(ops) == 1:                 # later segments resolve against it
            cwd = os.path.join(cwd, os.path.expanduser(ops[0])); continue
        if verb in ("sed", "perl"):
            scripted = any(a in ("-e", "-f", "--expression", "--file") for a in args)
            if not any(a.startswith("-i") or a == "--in-place" for a in args):
                continue
            files, skip = [], False
            for a in args:
                if skip: skip = False
                elif a in ("-e", "-f", "--expression", "--file"): skip = True
                elif not a.startswith("-"): files.append(a)
            for f in (files if scripted else files[1:]):
                yield f, cwd
        elif verb in ALL:
            for a in ops: yield a, cwd
        elif verb in LAST and ops:
            yield ops[-1], cwd
        elif verb == "dd":
            for a in args:
                if a.startswith("of="): yield a[3:], cwd
        elif verb == "patch":
            yield ".", cwd
        elif verb == "find" and "-delete" in args:
            roots = []
            for a in args:
                if a.startswith(("-", "(", "!")): break
                roots.append(a)
            for a in roots or ["."]: yield a, cwd
        elif verb == "git":
            gdir, rest = ".", list(args)
            while rest and rest[0].startswith("-"):
                o = rest.pop(0)
                if o in ("-C", "-c") and rest:
                    v = rest.pop(0)
                    if o == "-C": gdir = os.path.join(gdir, v)
            if not rest:
                continue
            sub, sargs = rest[0], rest[1:]
            sops = [a for a in sargs if not a.startswith("-")]
            if sub in ("checkout", "switch") and any(a in ("-b", "-B", "-c", "-C") for a in sargs) \
                    and "--" not in sargs:
                continue
            if sub == "stash" and sops[:1] in (["list"], ["show"]):
                continue
            if sub in ("mv", "rm"):
                for a in sops: yield os.path.join(gdir, a), cwd
            elif sub in GIT_RW:
                yield gdir, cwd


def main():
    try:
        data = json.load(sys.stdin)
    except ValueError:
        deny("could not parse the hook input.")
    cwd = data.get("cwd") or ROOT
    tool, ti = data.get("tool_name", ""), data.get("tool_input") or {}
    if tool in ("Edit", "MultiEdit", "Write"):
        check(ti.get("file_path", ""), cwd)
    elif tool == "NotebookEdit":
        check(ti.get("notebook_path", ""), cwd)
    elif tool == "Bash":
        for path, c in bash_targets(ti.get("command", ""), cwd):
            check(path, c)


main()
```

### 3.3a `.claude/hooks/design-gate.test.sh`

```bash
#!/usr/bin/env bash
# PROPOSED (ADR-0033) — tests for design-gate.sh / design_gate.py. Runs against a throwaway fixture
# repository, never against the real APPROVAL.md. Needs python3 (it builds the JSON inputs).
# Usage: .claude/hooks/design-gate.test.sh
set -euo pipefail
HOOK="$(cd "$(dirname "$0")" && pwd)/design-gate.sh"
FIX="$(mktemp -d)"; trap 'rm -rf "$FIX"' EXIT
FIX="$(cd "$FIX" && pwd -P)"
mkdir -p "$FIX/docs/architecture/multi-transport" "$FIX/.claude" "$FIX/backend"

approval() {   # $1 = scope to approve ("" = none); CRLF line endings, as the owner's editor may write
  { for s in phase-0 0.4 0.5 process; do
      printf '### Scope: %s\r\n' "$s"
      if [ "$s" = "$1" ]; then printf 'Status: Approved\r\n'; else printf 'Status: Pending\r\n'; fi
      printf 'Approved-by:\r\nDate:\r\nConditions:\r\n\r\n'
    done; } > "$FIX/docs/architecture/multi-transport/APPROVAL.md"
}
scope() { if [ -n "$1" ]; then printf '%s\n' "$1" > "$FIX/.claude/active-scope"; else rm -f "$FIX/.claude/active-scope"; fi; }
j() { python3 -I -c 'import json,sys; print(json.dumps({sys.argv[1]: sys.argv[2]}))' "$@"; }

pass=0; fail=0
run() {        # $1 expected allow|deny  $2 name  $3 tool  $4 json tool_input
  local out rc verdict
  out="$(python3 -I -c 'import json,sys; print(json.dumps({"tool_name":sys.argv[1],"tool_input":json.loads(sys.argv[2]),"cwd":sys.argv[3],"hook_event_name":"PreToolUse"}))' "$3" "$4" "$FIX" \
        | CLAUDE_PROJECT_DIR="$FIX" "$HOOK" 2>&1)" && rc=0 || rc=$?
  if [ $rc -eq 2 ] || grep -q '"permissionDecision": *"deny"' <<<"$out"; then verdict=deny; else verdict=allow; fi
  if [ "$verdict" = "$1" ]; then pass=$((pass+1)); printf 'ok    %s\n' "$2"
  else fail=$((fail+1)); printf 'FAIL  %s (expected %s, got %s)\n%s\n' "$2" "$1" "$verdict" "$out"; fi
}
W() { j file_path "$1"; }
B() { j command "$1"; }

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
run deny  "T10 notebook outside docs refused"                        NotebookEdit "$(j notebook_path "$FIX/backend/n.ipynb")"
run deny  "T11 Bash redirect into code refused"                      Bash  "$(B 'echo x > backend/A.kt')"
run allow "T12 Bash redirect to /tmp allowed"                        Bash  "$(B 'echo x > /tmp/a && cat /tmp/a')"
run allow "T13 Bash sed -i on docs allowed"                          Bash  "$(B "sed -i -e 's/a/b/' docs/a.md")"
run deny  "T14 Bash sed -i on code refused"                          Bash  "$(B "sed -i 's/a/b/' backend/A.kt")"
run deny  "T15 git apply refused while unsigned"                     Bash  "$(B 'git apply fix.patch')"
run allow "T16 read-only Bash allowed"                               Bash  "$(B 'grep -rn foo backend | head -5 2>&1')"
run allow "T17 git checkout -b allowed"                              Bash  "$(B 'git checkout -b docs/1-x')"
run deny  "T18 cp into code refused"                                 Bash  "$(B 'cp /tmp/a backend/A.kt')"
run deny  "T19 unparseable command treated as a repository write"    Bash  "$(B "echo 'unterminated")"
run allow "T20 2>&1 is not a file target"                            Bash  "$(B 'ls docs 2>&1 >/dev/null')"
run deny  "T26 rm -f into code refused"                              Bash  "$(B 'rm -f backend/A.kt')"
run deny  "T27 tee into code refused"                                Bash  "$(B 'printf x | tee -a backend/A.kt')"
run deny  "T28 multi-line: write on a later line refused"            Bash  "$(B $'echo hi\nrm backend/build.gradle.kts')"
run deny  "T28b multi-line: cd then cp into code refused"            Bash  "$(B $'cd /tmp\ncp x '"$FIX"'/backend/A.kt')"
run deny  "T29 merged punctuation )> refused"                        Bash  "$(B '(echo x)>backend/a.kt')"
run deny  "T30 find -delete under code refused"                      Bash  "$(B "find backend -name '*.kt' -delete")"
run deny  "T30b patch refused while unsigned"                        Bash  "$(B 'patch -p1 < fix.diff')"
run deny  "T31 git -C dir apply refused"                             Bash  "$(B 'git -C backend apply p.diff')"
run allow "T31b git -C outside the repo allowed"                     Bash  "$(B 'git -C /tmp/other apply p.diff')"
run allow "T32 git stash list is read-only"                          Bash  "$(B 'git stash list')"
run allow "T33 git mv inside docs allowed"                           Bash  "$(B 'git mv docs/a.md docs/b.md')"
run allow "T34 heredoc with an apostrophe to /tmp allowed"           Bash  "$(B $'cat > /tmp/n <<\'EOF\'\ndon\'t\nEOF\necho done')"
run deny  "T35 write after a heredoc still seen"                     Bash  "$(B $'cat > /tmp/n <<EOF\nx\nEOF\necho x > backend/A.kt')"
run allow "T36 multi-line commit message allowed"                    Bash  "$(B $'git commit -m "first line\n\nit\'s fine"')"
run allow "T37 cd into /tmp then relative write allowed"             Bash  "$(B 'cd /tmp && echo x > a.kt')"

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

# G9 — no interpreter: PATH without python; launcher fallback in bash
approval ""; scope ""
G9BIN="$(mktemp -d)"; for t in bash sed head cat dirname; do ln -s "$(command -v $t)" "$G9BIN/$t"; done
g9() { local out rc v; out="$(printf '%s' "$4" | PATH="$G9BIN" CLAUDE_PROJECT_DIR="$FIX" "$HOOK" 2>&1)" && rc=0 || rc=$?
  [ $rc -eq 2 ] && v=deny || v=allow
  if [ "$v" = "$1" ]; then pass=$((pass+1)); printf 'ok    %s\n' "$2"; else fail=$((fail+1)); printf 'FAIL  %s (expected %s, got %s)\n%s\n' "$2" "$1" "$v" "$out"; fi; }
g9 allow "T38 no Python: docs Write allowed"        - "{\"tool_name\":\"Write\",\"tool_input\":{\"file_path\":\"$FIX/docs/x.md\"}}"
g9 allow "T39 no Python: /tmp Write allowed"        - '{"tool_name":"Write","tool_input":{"file_path":"/tmp/x"}}'
g9 deny  "T40 no Python: backend Write denied"      - "{\"tool_name\":\"Write\",\"tool_input\":{\"file_path\":\"$FIX/backend/A.kt\"}}"
g9 deny  "T41 no Python: Bash denied (target unknown = repository root)" - '{"tool_name":"Bash","tool_input":{"command":"ls"}}'
rm -rf "$G9BIN"

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

### 3.5 `.claude/active-scope` (gitignored)

Not created by the approval of `process` itself, and never committed. `.gitignore` gains the line
`/.claude/active-scope` in the installation PR. Written per working tree when a scope is open, e.g.:

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
| TP1 | Gate decision table, including multi-line, `)>`, `find -delete`, `patch`, `git -C`, `git stash list`, heredoc-with-apostrophe and the G9 no-Python fallback (T01–T41) | `.claude/hooks/design-gate.test.sh` (fixture repository in `mktemp -d`, CRLF `APPROVAL.md`) | 44 passed, 0 failed | **Run** against the proposed text from a scratch directory (Linux, Python 3): 44/44 |
| TP2 | Gate against the real `APPROVAL.md` (all scopes Pending) | `echo '{"tool_name":"Write","tool_input":{"file_path":"backend/x.kt"},"cwd":"'"$PWD"'"}' \| CLAUDE_PROJECT_DIR=$PWD .claude/hooks/design-gate.sh` | deny JSON naming `APPROVAL.md` | **Run**: deny |
| TP2a | Gate on the owner's Windows dev machine (Git Bash) | After installing Python (§6): `git status`, an `Edit` of a `docs/` file and an `Edit` of `backend/…` in a local Claude Code session | first two allowed, third denied with the G5/G6 reason; then, with Python removed from `PATH`, the docs `Edit` still allowed and `Bash` denied with the G9 message naming `winget install Python.Python.3.12` | Not run (no Windows host in this session) |
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
2. **Windows prerequisite (owner's dev machine).** The hook is registered in the project
   `.claude/settings.json`, so it also runs in the owner's local sessions under Git Bash, which ships
   no Python by default. Install it first: `winget install Python.Python.3.12` (then confirm
   `python -c "import sys; print(sys.version)"` from Git Bash prints 3.12 and not the Microsoft Store
   stub message). Without it the G9 fallback allows `docs/**` edits but denies every `Bash` call.
3. An agent (or the owner) creates the five tracked files with exactly the content of §3 in one PR
   and adds `/.claude/active-scope` to `.gitignore` (the only edit outside `.claude/**`; allowed
   because scope `process` is open — set `.claude/active-scope` to `process` first). The hook paths are under `.claude/**`, so the
   gate never blocks its own installation.
4. `chmod +x .claude/hooks/*.sh` (git records the executable bit).
5. The PR runs TP1 and TP2 and pastes the output; CODEOWNERS requests the owner's review (ADR-0032).
   The owner runs TP2a on the Windows machine before merging.
6. After merge, the next cloud session runs TP7; the first agent attempt to touch code runs TP3.

## 7. How the owner signs `APPROVAL.md`

1. Open the **raw** file `docs/architecture/multi-transport/APPROVAL.md` in an editor (not the
   rendered view; Markdown joins the four lines). CRLF line endings are fine.
2. In the block for the scope, change exactly one line from `Status: Pending` to `Status: Approved`,
   with no list marker and no trailing spaces. Fill `Approved-by:`, `Date:` (`YYYY-MM-DD`) and any
   binding `Conditions:`.
3. Record any ADR dispositions in §3 of the same file and add a change-log row.
4. Commit under the owner's own identity and merge through a PR (CODEOWNERS makes the owner the
   reviewer of this file; agents cannot edit it under G3).
5. Check: TP2 with the scope set in `.claude/active-scope` ⇒ allow.
6. To revoke: set the line back to `Status: Pending` (or `Deferred`) with a dated condition. The gate
   re-arms on the next tool call.
7. **At milestone exit** (and when `phase-0`/`process` work is complete): set the scope to
   `Deferred` with the condition "milestone exited YYYY-MM-DD", so at most one milestone scope is
   open at any time (§2.3, ADR-0033 D1a).
