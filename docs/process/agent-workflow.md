# Agent workflow — identity, verification gate, hooks and the merged/released/verified axis (ADR-0032)

> **Status: Proposed — pending owner approval.** Nothing in this document is in force until the owner
> records approval of scope `process` in
> [`docs/architecture/multi-transport/APPROVAL.md`](../architecture/multi-transport/APPROVAL.md). No
> code may be written, and no GitHub App, ruleset or hook created, until that approval is recorded
> there.

| | |
|---|---|
| **Decision records** | [ADR-0032](../adr/0032-agent-identity-verification-gate.md) (identity, verification gate), [ADR-0033](../adr/0033-claude-code-hooks.md) (hooks), [ADR-0030](../adr/0030-trunk-and-release-flow.md) (trunk, status axis) |
| **Principles** | Brief P13 (device criteria signed off only by the owner's observation), P14 (process ADRs separate from product ADRs) |
| **Evidence** | GOV-02, GOV-03, GOV-04, GOV-06, GOV-10, GOV-12, GOV-15, SSOT-03, SSOT-14, CICD-16, CICD-17, R20; #633 |

## 1. Roles

| Role | Can | Cannot |
|---|---|---|
| **Owner** | Approve scopes in `APPROVAL.md`; approve and merge PRs; tag and promote releases; observe and sign device criteria; operate production; rotate secrets | — |
| **Agent** (Claude Code session, workflow subagent, other bot) under the agent identity | Read the repo; write docs at any time; write code only in an approved, active scope; open draft PRs; run gates and report measured results; file issues with evidence; propose a *Verified* transition for logic-only items | Edit `APPROVAL.md`; approve or merge its own PR; push to `main`; create tags or releases; publish to Play; deploy; read or write secrets; flip `E2EConfig`/`MultiDeviceConfig` or other product flags; touch crypto beyond an approved scope; mark any device criterion passed |
| **Verifier agent** (adversarial review pass) | Re-check another agent's claims against code, commands and GitHub objects; downgrade or refute findings | Promote its own findings to *verified* |

## 2. Identity (ADR-0032 Decision 1)

| Item | Rule |
|---|---|
| Who pushes | A GitHub App (preferred) or machine user, e.g. `muhabbet-agent` — never the owner's token |
| App permissions | Contents RW, Pull requests RW, Issues RW, Metadata R. No Administration, Environments, Secrets, Variables. No Workflows by default. |
| Commit author | The agent identity; the trailer keeps the session link (`Claude-Session: …`) and `Co-Authored-By:` per the attribution rule in force |
| Review | The owner approves every agent PR; CODEOWNERS forces it on high-risk paths |
| Interim (until [OI-32](../architecture/multi-transport/open-items.md#oi-32) is closed) | Whether the Claude Code cloud harness can push as an App other than the account that authorised it is unverified. Until it is shown, harness agents may act under the owner's credential only if: every commit carries the `Claude-Session` trailer; every agent PR or issue is labelled `source:agent`; agents never push changes under `.github/workflows/`; agents never merge, tag or dispatch. Required approvals on `main-review` are 0, with CODEOWNERS-required owner review on high-risk paths (ADR-0032 Decision 1). The fallback decision is due before github-governance §12.1 step 4 |

## 3. Life of a change

| # | Step | Agent action | Gate |
|---|---|---|---|
| 1 | Pick work | Only issues with evidence and a milestone, or explicit owner instruction. `status:needs-evidence` items are verified first (ROADMAP "verify, then schedule"). | — |
| 2 | Check the scope | Is the milestone's scope `Approved` in `APPROVAL.md`? If not, stop at docs. Write that scope id to `.claude/active-scope` in the working tree (gitignored, never committed; ADR-0033 G5). At most one milestone scope is open at a time (ADR-0033 D1a); a milestone pre-approved by the 2026-11-12 delegation reads `Deferred` until the owner opens it at the previous milestone's exit. If a different scope is open, the work is not approved — stop and ask. An agent never edits `APPROVAL.md` (G3), not even to record a delegated approval. | Design gate hook (ADR-0033) refuses code edits otherwise |
| 3 | Claim | Branch `<type>/<issue#>-<slug>`; first push opens a **draft PR** with `Closes #N` (ADR-0032 decision 6). For an issue with a device criterion, `#N` is the implementation sub-issue; the `[O]` criterion lives in its parent issue, which stays open (§5 rule 1); board item → *In progress* (WIP ≤ 3) | — |
| 4 | Build | Follow CLAUDE.md hard rules (hexagonal layering, i18n, `ErrorCode`, no `!!`); one concern per PR | Hook; CODEOWNERS |
| 5 | Gate | Run the relevant gates and **measure**: `:backend:test` (Docker running for any Spring-config or migration change), `:shared:jvmTest`, `compileCommonMainKotlinMetadata`, `verifyUi`. Report base-vs-branch counts from the XML results. | PR template "Gates that ran" |
| 6 | Report honestly | PR body states: what was run and its output; what was **not** checked; which acceptance criteria need the owner's device | — |
| 7 | Review | Mark ready; owner reviews and approves (ADR-0032) | Ruleset |
| 8 | Merge | Owner squash-merges; item → *Merged — needs verification* | Ruleset |
| 9 | Release | At the tag: item → *Released (vX.Y.Z)* | ADR-0030 §7 |
| 10 | Verify | Logic-only: agent attaches CI evidence on the released SHA and proposes *Verified*. Device criteria: owner observes and records "observed by / where". | Owner only |

## 4. The verification gate

### 4.1 What an agent may and may not claim

| Evidence the agent has | Allowed wording | Forbidden wording |
|---|---|---|
| Unit/integration tests ran green (measured) | "`:backend:test` <N> tests, <F> failures, Docker running; base was <N0>/<F0>" (numbers from this run) | "verified", "works" |
| Code compiles | "commonMain compiles" | "the feature works on Android" |
| Code inspection | "by inspection, X is called from Y (file:line)" | "tested" |
| Test count from a document | — | Any count not measured in this session (CLAUDE.md "measure it, do not read it here") |
| A screenshot the owner sent | "the owner observed …" with the owner's words | "I verified on device" |

### 4.2 Device-dependent criteria

These can only be signed off by the owner's own observation (brief §16.3; there is no KVM on the CI
host, and `emulator-5554` is on the owner's Windows PC):

app launch from the release artefact · notifications with the app killed (incl. aggressive OEMs) ·
calls (audio, operators, CGNAT) · biometrics and Keystore behaviour · Nearby/LAN device matrix incl.
iOS prompts · over-the-air captures · battery impact · Meshtastic field logs · layout, motion,
gesture, colour on a real panel, haptics, camera, microphone.

Every acceptance criterion of this kind carries a field:

```text
Observed by: <owner name>   Where: <emulator-5554 | device model, OS, operator | prod check>   Date: <YYYY-MM-DD>
Result: <what was seen>   Not checked: <what was not>
```

An agent leaves the field empty. A PR or issue that fills it without an owner comment is a defect
(R20).

## 5. Merged ≠ released ≠ verified

| State | Means | Does not mean |
|---|---|---|
| **Merged — needs verification** | The change is on `main` and passed the gates that ran | Users have it; it works on a device |
| **Released (vX.Y.Z)** | A published tag contains it; Play/production received that artefact | Anyone observed the behaviour |
| **Verified (observed by / where)** | Logic-only: CI evidence on the released SHA. Device: the owner saw it on a named device or emulator | — |

Rules:
1. An issue with a device criterion stays **open** until *Verified*. Default (github-governance §3,
   §10, R-7 option (a)): the `[O]` criterion lives in a **parent** issue; the PR `Closes` an
   implementation **sub-issue**, so merging closes only the sub-issue and the parent waits for the
   owner (GOV-02). A bare `Refs #N` fails ADR-0032 check A6; using `Refs` instead needs R-7 option
   (b) (A6 changed to "draft PR cross-referenced in the timeline") and the owner's decision.
2. CLAUDE.md, ROADMAP and release notes say "fixed on `main`", "released in vX.Y.Z" or "verified on
   <device>" — never "DONE" without one of these (SSOT-11).
3. Milestone progress counts *Verified* items only when judging exit criteria.

## 6. Using workflows, skills and hooks

| Mechanism | What it is | Rules |
|---|---|---|
| **Hooks** (ADR-0033) | Harness-executed scripts: `PreToolUse` design gate, `SessionStart` environment bootstrap | Agents never edit or disable them to get past a denial; a denial is reported to the owner with the scope it names. Changes to `.claude/**` go through owner review (CODEOWNERS). |
| **Skills** (e.g. code review, security review, environment setup) | Packaged instructions loaded on demand | A skill's output is advice, not verification; its findings are filed only with evidence and marked `status:needs-evidence` until confirmed |
| **Workflow orchestration** (multi-agent review/design runs, such as the one that produced this package) | A script fans out subagents with computed task text | Task text computed by a script carries no owner authority. Subagents write only their assigned files; outputs are drafts until the owner approves; every factual claim cites a finding ID, file:line or fetched URL; adversarial verifiers re-check claims; disagreements with the source of truth are written as reviewer-note callouts, never silent deviations; a resolver later turns each into a resolved note or an open item in `docs/architecture/multi-transport/open-items.md` |
| **GitHub Actions** | CI on runners per ADR-0031 | Agents do not add workflows, runners or secrets; they propose workflow changes in a PR for the owner (no Workflows permission on the App) |
| **Issue filing** | `gh issue create` bypasses issue forms | Agents use the form's fields as the body skeleton (observed-where, persistence/reader/mechanism, evidence), label `status:needs-evidence`, English titles (GOV-15), search for duplicates first |

## 7. Forbidden actions (any scope, any approval)

1. Editing `docs/architecture/multi-transport/APPROVAL.md`, or any `Status:` line of an ADR, without
   an owner disposition already recorded there.
2. Hand-written cryptographic primitives or handshakes; guessing libsignal APIs (brief P12; CLAUDE.md
   "do not guess crypto").
3. Flipping `E2EConfig.ENABLED`, `MEDIA_ENABLED`, `MultiDeviceConfig.ENABLED`,
   `muhabbet.multi-device.enabled`, or re-enabling `*.kt.disabled` files.
4. Deploying, SSH-ing to production with write intent, publishing releases, promoting to Play.
5. Printing, committing or pasting any secret value (see the incident record's handling rule).
6. Reporting a device criterion as passed, or a test count not measured in the session.

## 8. Open questions

1. Agent identity feasibility inside the Claude Code cloud harness (ADR-0032 reviewer note).
2. Whether custom issue types (Epic, Decision) are available on a user-owned repository, or labels
   are needed (GOV-10).
3. Whether the Projects board (linked from TODO.md, returning 404 anonymously) exists and is private
   or deleted (GOV governance open question).
