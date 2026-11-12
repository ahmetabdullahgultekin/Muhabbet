# ADR 0032 — Agent identity and the owner-observed verification gate

**Status:** Proposed (process) — pending owner approval; no code may be written, and no GitHub setting changed, against this ADR until approval is recorded in [`docs/architecture/multi-transport/APPROVAL.md`](../architecture/multi-transport/APPROVAL.md) (scope `process`) · **Date:** 2026-11-12 · **Source:** [decision brief §3, ADR-0032; principle P13](../architecture/multi-transport/decision-brief-2026-11-12.md) · **Operational detail:** [`docs/process/agent-workflow.md`](../process/agent-workflow.md) · Supersedes nothing; makes ROADMAP.md "Definition of done" item 3 enforceable.

## Context

| Fact (evidence) | Consequence |
|---|---|
| 231 merged PRs, 0 approved reviews (`review:none` = 231) (GOV-06) | Agent-written auth, migration and crypto-adjacent code reached the release branch with no second look |
| `main` requires 1 approval, which a solo author cannot give; merges go through `--admin`, which also skips required checks (GOV-06, CICD-14; PR #698) | The only rule is unmeetable, so it is bypassed, and the bypass removes the CI gate too |
| Agents push, open PRs and file issues with the owner's token (GOV-06) | "Who wrote this" is invisible; "machine writes, human approves" cannot be expressed |
| No emulator or KVM on the CI host; the only emulator is `emulator-5554` on the owner's Windows PC (CLAUDE.md; brief §16.3) | Agents cannot observe launch, notifications, calls, radios or layout |
| 0.3.7 shipped a launch crash behind a green build while "no emulator" was repeated in-session (#633) | Agent claims about device behaviour have already been wrong in production |
| 69 issues closed "completed" with nothing released or verified (GOV-02) | Closure is not evidence |
| Parallel agents collided: duplicate fixes, a stranded branch, a migration-number collision; no claim signal (GOV-12) | Work is lost or done twice |

## Decision

1. **Agents act under their own GitHub identity** — a GitHub App (preferred) or a machine user — for
   every branch push, PR, issue and comment. Never the owner's token.
   - Permissions: contents write, pull requests write, issues write, metadata read. **No**
     administration, environments, secrets or actions-variables access. No `workflows` permission by
     default: workflow changes are pushed by the owner (CODEOWNERS on `/.github/` enforces review in
     any case).
   - Commits carry the agent identity as author; the session link stays in the commit trailer.
2. **The owner approves agent PRs.** This makes ADR-0030's 1-approval rule satisfiable without
   bypass. The owner's own PRs use the audited ruleset bypass (owner only), which never skips required
   checks.
3. **CODEOWNERS** assigns the owner to: `/.github/`, `/.claude/`, `CODEOWNERS`,
   `/docs/architecture/multi-transport/APPROVAL.md`, `/docs/adr/`,
   `/backend/src/main/resources/db/migration/`, `/mobile/**/crypto/`, `/shared/**/port/`,
   `/mobile/composeApp/build.gradle.kts`, `/gradle/`, `/settings.gradle.kts`. Code-owner review is
   required by the `main` ruleset.
4. **Device-dependent acceptance criteria record "observed by / where"**, and only the owner ticks
   them. "Where" is one of: `emulator-5554`, a named physical device (model, OS, operator), production
   via a named check. An agent may run CI, tests and code inspection and must report them as such; it
   may never mark a device criterion passed, and never writes "verified" for something it did not
   observe (P13; ROADMAP DoD 3).
5. **Status axis** (with ADR-0030): *merged (needs verification)* → *released (vX.Y.Z)* → *verified
   (observed by / where)*. Only the owner moves an item with a device criterion to *verified*.
6. **Claim protocol** (GOV-12): an agent starting on issue #N creates `<type>/<N>-<slug>`, opens a
   **draft PR** containing `Closes #N` with its first push, and moves the board item to *In progress*.
   The draft PR is the claim. Work-in-progress limit: 3 items *In progress*.
7. **Agent-filed issues** carry `status:needs-evidence` until the owner or a verifier confirms the
   evidence (GOV-03, GOV-04; ROADMAP "verify, then schedule").

## Why

- Review is only possible across an identity boundary. A distinct agent identity is the cheapest way
  to turn "zero reviews" into "every agent change reviewed by the human", which is the only review
  model a solo project can satisfy (GOV-06).
- Agents cannot verify devices, calls or radios, and the multi-transport roadmap (1.1–1.4) is almost
  entirely device-verified (brief §16.3). Without this gate R20 ("agents report device criteria as
  passed", M/H) is the most likely failure of the whole programme.

## Consequences

- Every milestone in brief §13 and every device row in the V&V plan uses the "observed by / where"
  field (brief: "Every milestone in §13 uses this field").
- The owner's review load rises; mitigated by small squash PRs (ADR-0030) and CODEOWNERS focusing
  mandatory review on high-risk paths.
- Audit trail: GitHub records who applied a label or moved a board item; a periodic check flags any
  *verified* transition not made by the owner.
- Agent sessions need the App's installation token configured in the agent environment; how the
  Claude Code cloud harness authenticates pushes must be checked against this (open question below).

## Alternatives considered

| Alternative | Rejected because |
|---|---|
| Status quo (owner token, `--admin` merges) | Zero review, checks skipped on bypass, authorship invisible (GOV-06). Brief: rejected. |
| Required reviews set to 0 (CICD-14 option) | Honest about a solo repo but forfeits "machine writes, human approves" once agents have their own identity |
| Agents self-certify device criteria from logs or screenshots they generated | Agents have no device; R20; #633 precedent |
| Assignees as the claim signal | All agents act as one identity today; assignee says nothing about in-flight work (GOV-12) |

## Evidence

GOV-02, GOV-03, GOV-04, GOV-06, GOV-12, GOV-14, CICD-14, R20; CLAUDE.md "This host is TWO machines"
and #633; ROADMAP.md "Definition of done" — [findings register](../reviews/2026-11-12-findings-register.md).

> **Reviewer note (open):** the brief does not say whether the Claude Code cloud harness can push
> under a GitHub App identity other than the account that authorised it. If it cannot, Decision 1
> holds only for agents run outside that harness, and the approval rule stays unmeetable for harness
> PRs. Feasibility must be checked before this ADR is accepted; the fallback is required reviews = 0
> plus CODEOWNERS-only review on the high-risk paths in Decision 3.

## Verification

| # | Check | Method |
|---|---|---|
| A1 | Agent identity in use | The last 10 agent PRs show the App/machine user as author; none show the owner as author with an agent session trailer |
| A2 | Approvals | `is:pr is:merged review:approved` rises with every agent PR; `review:none` merged agent PRs after the cut-over date = 0 |
| A3 | CODEOWNERS | A test PR touching `/backend/src/main/resources/db/migration/` requests the owner's review automatically |
| A4 | Observed-by gate | Every closed issue with a device criterion has an owner comment naming device/emulator and date; spot-check 5 per release |
| A5 | No agent "verified" | Board history: every transition to *Verified* was made by the owner |
| A6 | Claim protocol | Every *In progress* item has a linked draft PR |
