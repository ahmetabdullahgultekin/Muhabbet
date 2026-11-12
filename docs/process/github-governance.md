# GitHub governance — target state and migration plan

> **Status: Proposed — pending owner approval.** Nothing in this document is in force until the owner
> records approval of scope `process` in
> [`docs/architecture/multi-transport/APPROVAL.md`](../architecture/multi-transport/APPROVAL.md). No
> code may be written, and no label, milestone, ruleset, environment, project, template, setting or
> issue changed on GitHub under this document, until that approval is recorded there (owner
> decisions 3 and 4; brief Q-O9), except the §12.1 step 0 items, which require scope `phase-0`
> instead. Milestones 1.1–1.4 additionally wait for their own scopes.

| | |
|---|---|
| **Source of truth** | [Decision brief 2026-11-12](../architecture/multi-transport/decision-brief-2026-11-12.md) — §3 ADR-0030/0031/0032, §13, §14 item 22, Q-O9 |
| **Decision records** | [ADR-0030](../adr/0030-trunk-and-release-flow.md) (trunk, release, status axis) · [ADR-0031](../adr/0031-ci-trust-boundary.md) (CI trust boundary) · [ADR-0032](../adr/0032-agent-identity-verification-gate.md) (agent identity, verification gate) · [ADR-0033](../adr/0033-claude-code-hooks.md) (hooks) |
| **Operational siblings** | [`branching-and-release.md`](branching-and-release.md) (merge, release, rollback mechanics) · [`agent-workflow.md`](agent-workflow.md) (roles, claim, verification) · [`ci-trust-boundary.md`](ci-trust-boundary.md) (runners, environments, secrets) |
| **This document owns** | Issue types and forms, labels, milestones, Projects v2, PR template, CODEOWNERS, **rulesets and code-security settings** (§9.2, §9.4), tracker automation, the one-time tracker cleanup, rollout order |
| **Audience** | Owner (sole human maintainer) and every agent acting on the repository |
| **Evidence** | GOV-01…GOV-15, GOV MISS-01…04, CICD-01…05, CICD-12…14, SSOT-01, SSOT-02, SSOT-07, SSOT-08, SSOT-11, SSOT-13 — [findings register](../reviews/2026-11-12-findings-register.md) |
| **Measured** | Read-only GitHub API reads on 2026-11-12 (issues, labels, milestones, branch protection, repo settings). Re-measure before acting: every count below is a snapshot (CLAUDE.md "measure it, do not read it here"). |

Where this document and a sibling overlap, the sibling named in the table above wins for its topic;
this document links instead of restating. Rulesets are decided by ADR-0030 D4 (and verified by its
V3); §9.2 below is the click-level implementation of that decision and defers to it.

---

## 1. Current state (2026-11-12)

### 1.1 Numbers

| Item | Value | Finding |
|---|---|---|
| Default branch | `main`, last commit `418eb54` at 2026-08-17 22:30 UTC (2026-08-18 local), v0.3.10. `dev` @ `2434324`, 2026-09-02. Nothing has reached `main` since. | GOV-01, SSOT-01, CICD-08 |
| Issues | **284 open / 187 closed** (471 total) | — |
| Assignees | **0** issues ever assigned | GOV-12 |
| Issue types | **0** issues have a type | GOV-10 |
| Sub-issues | none found | GOV-10 |
| Open issues without a milestone | **103** of 284 | GOV-05 |
| Open issues without an `area:*` label | **141** (57 `ui-ux-review`, 41 `oop-review`, 36 with no label at all, 7 other) | GOV-04 |
| Open issues without a `priority:*` label | 52 | GOV-04 |
| Open issues not updated in 60 days | 283 of 284 | GOV-03 |
| Unverified automated-review findings | 97 per GOV-03 (34%); label count today: 42 `oop-review` + 60 `ui-ux-review` = 102 open | GOV-03 |
| Closed "completed" since the last release (≥ 2026-08-22) | 69, of which 0 linked to a PR; none is in a release | GOV-02 |
| Merged PRs | **231; 227 have no milestone**; **0 approved reviews** (231 `review:none`) | GOV-05, GOV-06 |
| Open PRs | 5: #690, #718, #736, #738 (base `dev`); #743 Dependabot (base `main`, old config) | GOV-13 |
| Branches | 206 | GOV-07 |
| Labels | 41 as enumerated in §5.2 (rows 1–11, 13–16, 18–43); re-measure with `gh label list --limit 200 \| wc -l` before TC-0 and reconcile | GOV-04 |
| Milestones | open: `0.4 — the app tells the truth` (6 open / 34 closed), `0.5.0 — Honest` (50/36), `0.6.0 — Reachable` (96/48, of which 56 open `ui-ux-review`), `0.7.0 — Live` (21/4), `1.0.0 — Encrypted` (8/0). Closed: `Tier 1/2/3` (0/0 each), `0.4.0 — Installable` (0/24) | GOV-05, SSOT-07 |
| Open P0 | 6 (#67–#72), all `from:md-migration`, at least #70 provably stale (asks to merge PR #49, merged 2026-06-07) | GOV-03 |

### 1.2 Settings as read through the API

| Setting | Value | Note |
|---|---|---|
| Visibility / owner type / licence | public / **User** / MIT | User-owned matters for Projects access by a GitHub App (§7, R-3) |
| Merge methods | merge commit **on**, rebase **on**, squash **on** | ADR-0030 wants squash only |
| Delete head branch on merge | off | |
| Rulesets | none | |
| Classic protection `main` and `dev` | required checks `Build & Test`, `Android Build`; enforcement `non_admins` (admins bypass) | PR #698 described `main` as having no required checks; the API now shows two. Both run PR jobs on `muhabbet-cx43` (CICD-01). |
| Secret scanning / push protection | **enabled / enabled**; non-provider patterns and validity checks disabled | See reviewer note R-1 |
| Dependabot security updates | enabled | |
| Private vulnerability reporting | **disabled** | GOV-08 |
| Discussions | disabled | |
| Community profile | 71%; no code of conduct, no PR template, no issue templates on `main` | GOV-14 |
| Issue forms | exist only on `dev` (`bug_report.yml`, `feature_or_work.yml`, `config.yml`), so they never render | GOV-01 |
| Projects board | `users/ahmetabdullahgultekin/projects/5` linked from `TODO.md:7`; 404 to an anonymous client, state unknown | GOV-10 |

### 1.3 Diagnosis (one line each)

1. Every governance repair since August sits on a non-default branch and is inert (GOV-01).
2. "Closed as completed" means "merged into `dev`", not shipped and not verified (GOV-02).
3. P0/P1 are polluted by stale, duplicate and unverified items; real risks (#422, #289, #273) sit at
   the same weight as style findings (GOV-03).
4. Labels encode one dimension twice (severity mirrors priority with zero conflicts: every open
   `critical`/`high` is P1, `medium` is P2, `low` is P3) and carry synonyms (GOV-04).
5. Milestones are buckets; 0.6.0 is a dumping ground (GOV-05).
6. Review is impossible: one identity writes and merges; approval is bypassed (GOV-06).
7. Native PM features (types, sub-issues, Projects, Discussions) are unused (GOV-10).

---

## 2. Principles

| # | Principle | Mechanism |
|---|---|---|
| G1 | **Configuration over prose.** A rule that matters is enforced by a GitHub setting, a ruleset, a form or a hook, not only written down. | §8–§9 |
| G2 | **One axis per label family.** No family encodes what another already encodes. | §5 |
| G3 | **Closed ≠ merged ≠ released ≠ verified.** The tracker carries all four states. Device criteria are verified only by the owner's observation (brief P13). | §5.1 `status:*`, §7 Verification field, ADR-0032 |
| G4 | **Verify, then schedule** (ROADMAP). An unverified claim never enters a milestone. Agent-filed items start as `status:needs-evidence`. | §4.4, §11 |
| G5 | **A milestone is a version with exit criteria.** An issue enters only if it blocks an exit criterion. | §6 |
| G6 | **Machine writes, human approves.** Agents act under their own identity; the owner approves. | §9, ADR-0032 |
| G7 | **Runner-free automation first.** Actions run on the production host until ADR-0031 is in force, so prefer built-in Projects workflows, issue forms, native closing and generated release notes. | §10, CICD-01, CICD-04 |
| G8 | **Agent-queryable state lives where agents can write it.** `gh search` and the REST API see labels and milestones; agents may not be able to write user-owned Projects (R-3). Labels are the source of truth; project fields mirror them. | §5, §7 |
| G9 | **English for engineering artefacts** (titles, labels, PRs, ADRs). Turkish for user-facing release summaries and quoted user reports. | GOV-15 |
| G10 | **Every change here is reversible or snapshotted first.** | §11, §12 |

---

## 3. Branching and merge

Defined by [ADR-0030](../adr/0030-trunk-and-release-flow.md); mechanics in
[`branching-and-release.md`](branching-and-release.md) §1–§5 and §9. Summary only:

| Topic | Rule |
|---|---|
| Trunk | `main`; `dev` merged once and deleted (branching-and-release §3) |
| Branch names | `<type>/<issue#>-<slug>`, `type ∈ {fix, feat, docs, ci, chore, refactor, spike}` (harness-named `claude/*` branches: open point in branching-and-release §5) |
| Merge | Squash only, PR title = squash subject written from the user's side, linear history |
| Claim | Draft PR with `Closes #N` on first push (ADR-0032 decision 6, GOV-12). **Default for device-criterion work:** the `[O]` criterion lives in a parent issue and the PR `Closes` an implementation sub-issue, so the claim keeps a native Development link and the device criterion stays open. A bare `Refs #N` creates no Development link and fails ADR-0032 check A6 (reviewer note R-7). Whoever merges a PR that `Refs #N` an issue sets that issue's Status to *Needs verification* and adds `status:needs-verification` by hand (§10.1). |
| Closing | Native closing keywords on the default branch; `close-linked-issues.yml` deleted after the trunk merge (GOV-02) |
| Milestones on PRs | Not required. The issue carries the milestone; the PR inherits it through the link. The 227 historical unmilestoned PRs are not back-filled. |

---

## 4. Issue types, forms and epics

### 4.1 Types

| Type | Use for | Required body sections | Closes when |
|---|---|---|---|
| **Bug** | Observed behaviour differs from intended | Observed where, repro, expected, persistence/reader/mechanism, version | Fix verified (logic: CI on released SHA; device: owner observation) |
| **Feature** | A user-visible capability that does not exist | User problem, checkable done-means, persistence/reader/mechanism, verification method | Same |
| **Task** | Non-user-visible work: chore, refactor, test, docs, spike, decision | Goal, done-means, evidence to attach | Done-means met |
| **Epic** | A body of work with sub-issues: one per milestone, plus cross-milestone programmes (Calls, Phase 0, tracker cleanup) | Question it answers, exit criteria (copied from the milestone), sub-issue list, links to ADRs | All sub-issues closed **and** exit criteria signed |

Rules:

1. **Native issue types (Bug, Feature, Task; Epic created as a custom type). `type:*` labels only on
   PRs, for release notes** (§10.3). Bug, Feature and Task exist by default for user accounts
   (GOV-10; reviewer note R-2, resolved). If the custom Epic type cannot be created, epics carry
   `type:epic` as a label.
2. **Sub-issues** model every epic. First epics: *Phase 0 emergencies* (brief §13: one epic, not a
   milestone), *Calls* (#715 as parent; #367–#373 and #606 as sub-issues), one epic per milestone
   0.4 → 1.0.0, and *Tracker cleanup 2026-11* (§11). 1.1–1.4 epics are created with their scopes.
3. **Decisions** do not become Feature issues. A design question opens as a Discussion in
   *Design RFC* (§9.6); an accepted RFC produces an ADR PR and, if it schedules work, an Epic.
   Waiting on the owner is `status:needs-decision` on the Task that needs the answer.
4. **Research** items (`research:transport`, brief §13 "Research") are Tasks without a milestone.
   Each exits only through an ADR that opens a milestone or closes the topic.

### 4.2 Forms that exist on `dev` (read 2026-11-12)

| File | What it does well | Keep / change |
|---|---|---|
| `bug_report.yml` | Required duplicate check; required "observed where" incl. "only read the code"; required persistence/reader/mechanism; required version + environment; logcat/`docker logs` hints; reporter priority estimate | Keep. Changes in §4.3. |
| `feature_or_work.yml` | Problem-before-solution; checkable done-means; persistence/reader/mechanism; verification-method dropdown; "split if multi-layer" checkbox | Keep. Changes in §4.3. |
| `config.yml` | Blank issues enabled on purpose (agents use `gh issue create`, which bypasses forms); search link; security link to `security/policy`; HANDOFF link | Keep blank issues enabled. Changes in §4.3. |

### 4.3 Changes to existing forms

| File | Change | Why |
|---|---|---|
| `bug_report.yml` | `labels: ["type:bug"]` → `type: Bug`; add `projects: ["ahmetabdullahgultekin/<board#>"]` | §4.1; auto-add is the backstop when the reporter lacks project write access |
| both forms | Area dropdown options = the eight `area:*` values of §5.1 (adds `designsystem`, keeps one `mobile` entry for commonMain/Android) | Labels and form agree; triage maps 1:1 |
| both forms | Field labels bilingual (`Ne oluyor / What happens`); the title placeholder asks for an **English** title | G9; dedup search matches one language (GOV-15) |
| both forms | Optional input `Finding / evidence IDs` (e.g. `MOB-03`, `#633`) | Traceability to the register and V&V plan |
| `feature_or_work.yml` | Add `Observed by / where` guidance under done-means: device criteria are marked `[O]` and signed only by the owner | ADR-0032 decision 4 |
| `feature_or_work.yml` | Add `Task` to the type choice (dropdown "Feature / Task") or split into `feature.yml` and `task.yml` | §4.1 |
| `config.yml` | Security link → `https://github.com/ahmetabdullahgultekin/Muhabbet/security/advisories/new` after private vulnerability reporting is enabled | GOV-08 |
| `config.yml` | Replace the HANDOFF link (points at `blob/dev/…`, which breaks when `dev` is deleted; HANDOFF is to be demoted, SSOT-03) with `docs/process/agent-workflow.md` on `main` | GOV-01, SSOT-03 |
| `config.yml` | Add contact link "Design question / RFC" → `https://github.com/ahmetabdullahgultekin/Muhabbet/discussions/categories/design-rfc` | §4.1 rule 3 |
| `config.yml` | Update the comment's "389 open issues" text, or remove the count (SSOT-13) | Counts in prose rot |

### 4.4 New forms

| File | Kind | Fields |
|---|---|---|
| `.github/DISCUSSION_TEMPLATE/design-rfc.yml` | Discussion category form for *Design RFC* | Context (with finding IDs) · Question to decide · Options (≥ 2, each with evidence) · Recommendation · Who decides and by when · ADR number to be written · Gates touched (legal, DPIA, crypto review) · Checkbox "No code is written until approval is recorded in `APPROVAL.md`" |
| Security | **No form.** Contact link only, to private vulnerability reporting. Public security issues are not accepted. | GOV-08 |
| Agent-filed issues | No form (forms do not apply to `gh issue create`). Agents copy the form's headings into the body and add `status:needs-evidence` and `source:agent-review` (ADR-0032 decision 7; agent-workflow §6) | — |

---

## 5. Label taxonomy

### 5.1 Target (30 labels, managed as a table in this document, applied by hand or `gh label`)

| Family | Labels | Cardinality | Definition |
|---|---|---|---|
| `type:` | `type:bug`, `type:feature`, `type:task`, `type:epic` | exactly 1 on PRs; on issues only if the Epic type cannot be created (then `type:epic` on epics) | §4.1 |
| `area:` | `area:backend`, `area:mobile` (commonMain + androidMain + new `:mobile:*` modules), `area:ios` (iosMain, Xcode, iOS CI), `area:designsystem`, `area:shared`, `area:infra`, `area:ci`, `area:docs` | exactly 1 (the area where the fix lands) | Code location, not topic |
| `prio:` | `prio:P0`, `prio:P1`, `prio:P2`, `prio:P3` | exactly 1 after triage | **P0** drop everything: data loss, security exposure in production, or the release is blocked; **at most 3 open**. **P1** blocks the current milestone's exit. **P2** needed for a scheduled milestone. **P3** backlog. |
| `status:` | `status:needs-evidence`, `status:needs-decision`, `status:blocked`, `status:needs-verification`, `status:verified` | 0 or 1 | needs-evidence = the claim itself is not yet confirmed (default for agent-filed). needs-decision = waiting on the owner. blocked = waiting on something external (link it). needs-verification = the fix is merged, not yet verified. verified = **the fix** was verified (logic: CI on the released SHA; device: owner observed) — never used for "the finding was confirmed" (that is simply removing `needs-evidence`). |
| `source:` | `source:agent-review`, `source:md-migration` | 0 or 1 | Provenance. agent-review = filed from an automated review pass; md-migration = migrated from the old TODO markdown |
| Flags | `security`, `a11y`, `ux`, `research:transport`, `legal-gate`, `dependencies`, `skip-changelog` | any | security = confidentiality/integrity/abuse impact (combine with an area). a11y = accessibility. ux = visual/interaction quality without functional failure. research:transport = brief §13 research, no milestone. legal-gate = cannot start or ship until a written counsel opinion or DPIA exists (ADR-0029). dependencies = Dependabot PRs. skip-changelog = PR excluded from release notes. |

Colours (for the create commands): `type:` `#1d76db` · `area:` `#0e8a16` · `prio:P0` `#b60205`,
`P1` `#d93f0b`, `P2` `#fbca04`, `P3` `#c5def5` · `status:` `#5319e7` · `source:` `#bfdadc` · flags
`#d4c5f9` (`security` `#ee0701`, `legal-gate` `#000000`).

Not labels, on purpose:

| Concept | Where it lives instead |
|---|---|
| Severity | Removed. It mirrored priority exactly (§1.3 item 4). A bug's user impact goes in its body. |
| Duplicate / invalid / won't fix / question | Close reasons (`duplicate`, `not planned`) and Discussions |
| Milestone / target version | The milestone |
| In progress / in review | Draft vs ready PR, and the Project `Status` field |
| Released (vX.Y.Z) | The Project `Verification` field plus the closed milestone |

### 5.2 Mapping from the current 41 labels

Counts are open / closed issues carrying the label on 2026-11-12 (PR counts in brackets).

| # | Current label | Open / closed | Action | Target |
|---|---|---|---|---|
| 1 | `priority:P0` | 6 / 1 | **rename** | `prio:P0` (then the P0 review in §11 TC-4) |
| 2 | `priority:P1` | 104 / 36 | rename | `prio:P1` |
| 3 | `priority:P2` | 88 / 44 | rename | `prio:P2` |
| 4 | `priority:P3` | 35 / 5 | rename | `prio:P3` |
| 5 | `area:backend` | 53 / 31 | keep | — |
| 6 | `area:mobile` | 69 / 42 | keep | — |
| 7 | `area:ios` | 6 / 0 | keep | — |
| 8 | `area:ci` | 13 / 4 | keep | — |
| 9 | `area:infra` | 18 / 6 | keep | — |
| 10 | `area:security` | 3 / 0 | **merge** | add `security`; area from code location (#628 → `area:backend`, #605 → `area:mobile`, #72 → `area:infra`); delete |
| 11 | `area:web` | 1 / 0 | merge | #85 "Web / Desktop client" → `prio:P3`, no milestone, `status:needs-decision` (owner decides whether a web client is in the product), **no area** until decided — or close `not planned` with an owner comment. Brief §1.3: not covered by this design. Delete the label |
| 12 | — | — | **create** | `area:designsystem`, `area:shared`, `area:docs` |
| 13 | `type:bug` | 7 / 28 | keep (or migrate to native type, then keep for PRs) | `type:bug` |
| 14 | `type:feature` | 41 / 4 | keep | `type:feature` |
| 15 | `type:chore` | 15 / 1 | **rename** | `type:task` |
| 16 | `type:test` | 4 / 0 | merge | `type:task`; delete |
| 17 | — | — | create | `type:epic` |
| 18 | `bug` | 6 / 44 | merge | `type:bug`; delete |
| 19 | `enhancement` | 0 / 1 | merge | `type:feature`; delete |
| 20 | `documentation` | 0 / 0 | merge | `area:docs`; delete |
| 21 | `critical` | 18 / 10 | **delete after review** | all 18 open are already P1; review each for P0 (cap 3) in §11 TC-4 |
| 22 | `high` | 78 / 30 | delete | all already `priority:P1` (verified crosstab) |
| 23 | `medium` | 61 / 29 | delete | all already `priority:P2` |
| 24 | `low` | 19 / 6 | delete | all already `priority:P3` |
| 25 | `security` | 9 / 7 | keep | flag |
| 26 | `a11y` | 2 / 1 | keep | flag |
| 27 | `accessibility` | 5 / 1 | merge | `a11y`; delete |
| 28 | `wcag` | 1 / 1 | merge | `a11y`; delete |
| 29 | `color-contrast` | 1 / 3 | merge | `a11y`; delete |
| 30 | `design-consistency` | 11 / 3 | merge | `ux`; delete |
| 31 | `visual-hierarchy` | 5 / 2 | merge | `ux`; delete |
| 32 | `responsive-design` | 0 / 1 | merge | `ux`; delete |
| 33 | `oop-review` | 42 / 0 | **rename** | `source:agent-review` (+ `status:needs-evidence` on open ones until re-verified, §11 TC-5) |
| 34 | `ui-ux-review` | 60 / 45 | merge | into `source:agent-review` (created by the row 33 rename) + `ux` (+ `status:needs-evidence` on open ones); delete |
| 35 | `from:md-migration` | 29 / 0 | rename | `source:md-migration`; update the `TODO.md:5` query link to `label%3Asource%3Amd-migration` |
| 36 | `dependencies` | 0 / 0 [38 PRs] | keep | Dependabot default label |
| 37 | `java` | 0 / 0 [38 PRs] | delete | Dependabot language label. `dev`'s `dependabot.yml` already sets `labels` per ecosystem (so it is not recreated); on landing, change its `type:chore` → `type:task` (row 15 rename) |
| 38 | `duplicate` | 0 / 0 | delete | close reason `duplicate` |
| 39 | `invalid` | 0 / 0 | delete | close reason `not planned` |
| 40 | `wontfix` | 0 / 0 | delete | close reason `not planned` |
| 41 | `question` | 0 / 0 | delete | Discussions |
| 42 | `good first issue` | 0 / 0 | delete | unused; recreate if outside contributors appear |
| 43 | `help wanted` | 0 / 0 | delete | unused |
| 44 | `ui-ux-review` / `oop-review` title prefixes `[UI-HIGH]`, `[OOP-MEDIUM]` … (94 open titles) | — | retitle during TC-5 | Severity-free English title stating the user-side defect |
| 45 | — | — | create | `status:needs-evidence`, `status:needs-decision`, `status:blocked`, `status:needs-verification`, `status:verified`, `ux`, `research:transport`, `legal-gate`, `skip-changelog` |

Mechanics and reversibility:

1. **Rename** (`gh label edit "priority:P1" --name "prio:P1"`) keeps every association and is
   reversed by renaming back.
2. **Merge** = add the target label to every issue carrying the source label
   (`gh issue list --label <src> --state all --json number` → `gh issue edit <n> --add-label <dst>`),
   check counts match, then delete the source. Deletion drops associations irreversibly; the
   snapshot of §11 TC-0 is the restore path.
3. Deletions run **last** (§11 TC-9), after one full triage week on the new labels.

---

## 6. Milestones

### 6.1 Policy (written into ROADMAP "Milestones are versions" at the merge step, roadmap §18)

| # | Rule |
|---|---|
| MP-1 | A milestone is a version: title `X.Y[.Z] — Name`; it produces tag `vX.Y.Z` (`0.4` → `v0.4.0`). Existing titles are kept (brief naming note). |
| MP-2 | Exactly one milestone is *current*; the next one may receive issues; later ones receive only Epics. |
| MP-3 | The description is the exit-criteria checklist (each criterion tagged `[CI]`, `[I]` inspection, `[A]` analysis or `[O]` owner-observed) plus a link to the milestone Epic. Device criteria are ticked only by the owner (P13). |
| MP-4 | **Admission:** an issue enters only if it blocks an exit criterion and is not `status:needs-evidence`. Soft cap ~25 open issues per milestone (GOV-05). |
| MP-5 | Patch releases (`vX.Y.Z`, Z > 0) have no milestone. |
| MP-6 | At release: move leftovers deliberately (comment why), close the milestone the day the tag is published, link it in the release notes. |
| MP-7 | A dropped milestone (1.1, 1.3, 1.4 kill criteria) is closed with a dated ADR amendment, not left open (roadmap rule 7). |
| MP-8 | New milestones are created only when their `APPROVAL.md` scope reads `Approved` (brief §13 preamble). |

### 6.2 Disposition

| Milestone | State today | Action | When |
|---|---|---|---|
| `0.4 — the app tells the truth` | open, 6/34 | Keep; current. Append brief §13 exit criteria (§6.3) | After `process` + `0.4` scopes |
| `0.5.0 — Honest` | open, 50/36 | Keep; append §6.3; re-admit issues by MP-4 | After `0.5` scope |
| `0.6.0 — Reachable` | open, 96/48 | Keep; **remove the 56 `ui-ux-review` items** (§11 TC-6); append §6.3 | Cleanup |
| `0.7.0 — Live` | open, 21/4 | Keep; append §6.3; Calls epic #715 | After `0.7` scope |
| `1.0.0 — Encrypted` | open, 8/0 | Keep; append §6.3 | After `1.0` scope |
| `Tier 1 / Tier 2 / Tier 3` | closed, 0 issues each | **Delete** (empty; no history lost) | Cleanup |
| `0.4.0 — Installable` | closed, 24 closed issues | Keep closed for history; prefix description with "Retired 2026-11 — superseded by `0.4 — the app tells the truth` (ROADMAP)" | Cleanup |
| `1.1 — Direct`, `1.2 — Nearby`, `1.3 — Relay`, `1.4 — Off-grid hardware` | do not exist | Create with §6.4 text | Each only when its own scope is `Approved` (MP-8) |

### 6.3 Text to append to existing milestones (from brief §13; ROADMAP's existing criteria stay above it)

**0.4 — the app tells the truth**

```markdown
### Added by the 2026-11-12 design (brief §13; approval scope `0.4`)
Entry: Phase 0 exit (secrets rotated, no PR job on the production host, ADRs dispositioned).
- [ ] [O] Kill mid-send: no loss and no duplicate after relaunch (emulator, two test numbers)
- [ ] [O] Double tick only after the recipient DB holds the row
- [ ] [O] Logout, then login as another number: no prior data visible
- [ ] [O] A revoked device's socket closes within 5 s
- [ ] [CI] Registration below the configured minimum age (currently 18) is refused
- [ ] [CI] Testcontainers: 50 concurrent senders get gapless unique seq; concurrent prekey claims never return the same key
- [ ] [CI] Backend suite measured with Docker running, base vs branch
- [ ] [O] Release APK installed and launched on emulator-5554
Epic: #<epic>
```

**0.5.0 — Honest**

```markdown
### Added by the 2026-11-12 design (brief §13; approval scope `0.5`)
Entry: 0.4 shipped; ADR-0011/12/13/14/16/19/20/22/28 accepted; Docker available for Spring-config changes.
- [ ] [CI] SimulatedLink property suite, 10k seeded runs (loss, duplication, reorder, partition, process death, 24 h offline, token expiry): every message exactly once in sender and receiver stores, in seq order
- [ ] [CI] `:mobile:messaging` has no Compose, Koin-annotation or WsMessage dependency (build-enforced)
- [ ] [I] Captured FCM payload contains no message content
- [ ] [I] Forensic check after logout: no readable DB
- [ ] [O] Airplane-mode toggling script recovers with no manual refresh (emulator)
- [ ] [O] Notification arrives with the app killed
- [ ] [O] Release APK installed and launched on emulator-5554
Epic: #<epic>
```

**0.6.0 — Reachable**

```markdown
### Added by the 2026-11-12 design (brief §13; approval scope `0.6`)
Entry: 0.5 exit.
- [ ] [O] A message received while the app is killed is in the DB before the notification is tapped (emulator)
- [ ] [CI] Contact-discovery enumeration throttling integration test
- [ ] [O] iOS launches on a simulator — OR — the ADR scoping iOS out of 1.0 is accepted (BLOCKING gate, KMP-01)
- [ ] [CI] An identity-key replacement attempt for an existing device returns an error
- [ ] [O] Release APK installed and launched on emulator-5554
Epic: #<epic>
```

**0.7.0 — Live**

```markdown
### Added by the 2026-11-12 design (brief §13; approval scope `0.7`)
Entry: ADR 0009 accepted; LiveKit host provisioned (separate from cx43); 0.6 exit.
- [ ] [O] A call connects, carries audio both ways and appears in history on two physical devices on two operators, one of them TT Mobil
- [ ] [I] A non-contact call uses only relay candidates (ICE stats)
- [ ] [A] One week of relayed share and TURN egress measured
- [ ] [O] Release APK installed and launched on emulator-5554
Epic: #715 (Calls)
```

**1.0.0 — Encrypted**

```markdown
### Added by the 2026-11-12 design (brief §13; approval scope `1.0`)
Entry: 0.7 shipped or explicitly deferred; ADR-0017 preconditions (licence approved for mobile/, contributor audit, counsel opinion on Play and App Store, iOS bridge spike or Android-only decision); crypto reviewer engaged; two-device Android round trip on real builds.
- [ ] [I] Claims register: every privacy claim maps to a test or a device observation
- [ ] [CI] SQL assertion: no plaintext content for E2E conversations
- [ ] [CI] Injected seal failure ends in PENDING_SEAL or FAILED, never plaintext
- [ ] [CI] No SealedLink receives a non-SealedFrame; crypto-testkit absent from the release classpath
- [ ] [I] Erasure leaves no readable data
- [ ] [I] External crypto review findings closed
- [ ] [O] Two-device round trip on physical Android (and Android↔iOS if iOS is in scope)
- [ ] [O] Release APK installed and launched on emulator-5554
Epic: #<epic>
```

### 6.4 New milestones (create only under MP-8)

Description texts, ready to paste. Details and RQ IDs:
[`roadmap.md`](../architecture/multi-transport/roadmap.md) §11–§14.

**1.1 — Direct** (gated, droppable)

```markdown
**Question:** do P2P 1:1 calls buy end-to-end media and better latency than the SFU, at an acceptable TURN cost and without IP exposure?
Status: Proposed (gated). Approval scope `1.1` in docs/architecture/multi-transport/APPROVAL.md. ADR-0023.
Entry (all): 1.0 exit · 0.7 telemetry shows benefit (latency or SFU egress) · TURN egress budgeted (Q-O8) · iOS WebRTC bridge spiked if iOS is in scope · counsel opinion relevant to 1.1 (ADR-0029).
Exit:
- [ ] [A] Per-ASN setup success and latency A/B vs SFU
- [ ] [I] No IP exposure to non-contacts (candidate inspection)
- [ ] [O] Two-operator physical-device P2P call
- [ ] [O] Release APK installed and launched on emulator-5554
Kill: if the SFU satisfies the need and the E2E-1:1-calls claim is not wanted, drop 1.1 (threshold: Q-O5); close with an ADR amendment.
Epic: #<epic>
```

**1.2 — Nearby** (gated)

```markdown
**Question:** can two verified contacts exchange E2E text and photos with no internet, and does every claim about it hold on a measured device matrix?
Status: Proposed (gated). Approval scope `1.2`. ADR-0015, 0018, 0024, 0029.
Entry gate G-PROXIMITY (all): 1.0 exit · 0.6 iOS decision honoured · 2-week device-matrix spike published (Pixel, Samsung A, Xiaomi, iPhone 12, iPhone 15+; A↔A, i↔i, A↔i) · handshake library chosen by spike · proximity DPIA signed · counsel opinion covering E2E over BLE/Wi-Fi · ADR-0031 in force · HMS share in Turkey measured.
Exit:
- [ ] [O] Two Android phones in airplane mode exchange E2E text and a photo via LanLink on shared Wi-Fi, and via NearbyLink with Wi-Fi off
- [ ] [CI][O] On reconnect each message appears once, in seq order, on both devices and a linked companion
- [ ] [CI][O] Over-the-air capture shows no stable identifiers
- [ ] [CI][O] A revoked device is refused after certificate expiry or a CRL update
- [ ] [I] Certificate signing key absent from every PR-job host
- [ ] [I] UI claims nothing beyond the published device matrix
- [ ] [O] Release APK installed and launched on emulator-5554
Kill/fallback: if the spike fails on iOS, ship Android plus LAN-only cross-platform and say so. Negative BLE/Wi-Fi opinion: 1.2 does not start in TR.
Epic: #<epic>
```

**1.3 — Relay** (gated, droppable)

```markdown
**Question:** is INDIRECT delivery through the user's own devices, then one verified contact's online phone, used often enough and safe enough to keep?
Status: Proposed (gated). Approval scope `1.3`. ADR-0025.
Entry (all): 1.2 shipped · telemetry shows Nearby sessions with one side offline above the owner's threshold (Q-O5) · external crypto review of the uplink sealing · written KVKK opinion on contacts transiently holding ciphertext (Q-L5) · DPIA · ADR-0031 in force.
Exit:
- [ ] [O] Phone offline, tablet online: a message reaches the phone through the tablet over LAN and is E2E-acked
- [ ] [O][CI] A phone with no connectivity delivers through a contact's online phone; the server attributes it to the true sender device
- [ ] [CI] The mule store holds only opaque blobs
- [ ] [CI] Quota abuse stays bounded
- [ ] [I] Uplink key absent from every PR-job host
- [ ] [O] Release APK installed and launched on emulator-5554
Kill: telemetry below threshold, or any negative opinion or review. Own-device custody may ship alone.
Epic: #<epic>
```

**1.4 — Off-grid hardware** (gated, droppable, TR-excluded until cleared)

```markdown
**Question:** can certified Meshtastic nodes carry Muhabbet text and receipts lawfully and within duty cycle?
Status: Proposed (gated). Approval scope `1.4`. ADR-0026. Label: legal-gate.
Entry (all): written opinion on Law 5809 Art. 39/63(6), the 2010 coded/encrypted communications regulation and KET conformity · G-PROXIMITY in place. No adapter code before the opinion exists.
Exit:
- [ ] [CI] AirtimeLedger tests (time-on-air golden vectors)
- [ ] [O] One-hour field log within the EU868 duty cycle
- [ ] [O] Text and receipts delivered between two certified nodes
- [ ] [CI] Module absent from every TR release classpath
- [ ] [I] TR flavour includes the module only after counsel signs off
Kill: negative opinion. This may never ship in Turkey.
Epic: #<epic>
```

---

## 7. Projects v2

### 7.1 Board

One board, **Muhabbet**, linked to the repository. First check whether
`users/ahmetabdullahgultekin/projects/5` exists (owner, signed in). If it does, reconfigure it to
this section; if not, create a new board and fix the link at `TODO.md:7`. The `TODO.md:5` issue
query is fixed with the row 35 rename (§5.2).

### 7.2 Fields

| Field | Type | Values | Written by | Source of truth |
|---|---|---|---|---|
| **Status** | single select (built-in) | Inbox · Needs evidence · Ready · In progress · In review · Needs verification · Blocked · Done | Built-in workflows (§10.1), owner; agents if they can write the board (R-3) | This field |
| **Priority** | single select | P0 · P1 · P2 · P3 | Mirror of `prio:*` | Label |
| **Size** | single select | XS (≤ 1 h) · S (≤ ½ day) · M (≤ 2 days) · L (≤ 1 week) · XL (split it) | Triage | This field |
| **Target version** | built-in **Milestone** field (no custom field) | milestones | Triage | Milestone |
| **Area** | single select | the eight `area:*` values | Mirror of `area:*` | Label |
| **Verification** | single select | — · Merged · Released · Verified (CI) · Verified (on device) | Agent proposes Merged/Released/Verified (CI) with evidence; **only the owner** sets Verified (on device) | This field, mirrored by `status:needs-verification` / `status:verified` |
| **Observed by** | text | `<owner> · <emulator-5554 | model, OS, operator | prod check> · <YYYY-MM-DD>` | **Owner only** (ADR-0032 decision 4) | This field |

Mirror rule: when a label and its mirror field disagree, the label wins. The mirror is refreshed in the
weekly triage (§10.4) until it can be automated on a hosted runner.

### 7.3 Views

| View | Layout | Filter | Group / sort | Purpose |
|---|---|---|---|---|
| **Roadmap by milestone** | table (or roadmap, if milestones get due dates) | `is:open -label:research:transport` | group Milestone; sort Priority | What each version still needs |
| **Triage** | table | `status:Inbox` | sort created ↑ | Weekly triage queue |
| **Missing priority** | table | `is:open no:priority` | sort created ↑ | View filters AND across fields, so each missing field gets its own view (or the §10.4 `gh` queries) |
| **Missing area** | table | `is:open no:area` | sort created ↑ | same |
| **In flight** | board | `status:"In progress","In review"` | column limit 3 on In progress (GOV-12) | WIP |
| **Verification queue** | table | `is:issue status:"Needs verification"` | group Verification; sort Milestone | The owner's device-session list (issues only; merged PRs go to Done, §10.1) |
| **Security** | table | `label:security` | sort Priority | Security and privacy items |
| **Gated / research** | table | `label:legal-gate,research:transport` | group Milestone | Items that wait on counsel, DPIA or an ADR |

---

## 8. PR template and CODEOWNERS

### 8.1 `.github/pull_request_template.md` (ready to paste)

```markdown
<!-- Title = squash subject: what the USER experienced, before → after. English. -->

## Issues
<!-- One line per issue. Closes = the issue this PR implements (for device work: the implementation
     sub-issue; the [O] criterion stays in its open parent). Refs = cross-reference only, no
     Development link. Whoever MERGES a PR with "Refs #N" sets #N's Status to "Needs verification"
     and adds label status:needs-verification by hand. -->
Closes #
Refs #

## What the user experienced before, and after

## Scope check
- [ ] The milestone's scope is `Approved` in `docs/architecture/multi-transport/APPROVAL.md` (or this PR touches only docs / templates)
- [ ] One concern; no unrelated changes
- [ ] Flyway: next free `V` number reserved in the title; expand/contract (no destructive change in the same release as the code that stops using it)

## Persistence / reader / mechanism (anything user-visible)
- Persistence — where it is written:
- Reader — what reads it:
- Mechanism — what applies it:
<!-- "not user-visible" is a valid answer. "don't know" is a valid answer; a guess is not. -->

## Gates that ran (measured in this session, not copied from a document)
| Gate | Command | Base | Branch |
|---|---|---|---|
| Backend | `./gradlew :backend:test` (Docker running: yes/no) | tests / failures | tests / failures |
| Shared | `./gradlew :shared:jvmTest` | | |
| Mobile compile | `./gradlew :mobile:composeApp:compileCommonMainKotlinMetadata` | | |
| UI guardrails | `./gradlew verifyUi` | | |
<!-- Spring config or migration changed? The @Testcontainers classes (count them in the run) must have RUN, not failed on Docker. -->
<!-- A red CI check is compared with the same check on main before the branch is blamed. -->

## Verified where
- [ ] CI only
- [ ] Code inspection only (file:line)
- [ ] Production read-only check (which)
- [ ] emulator-5554 — **owner only**
- [ ] Physical device — **owner only**
Not checked:

## Device criteria
Observed by:            Where:            Date:
<!-- Agents leave these empty. Only the owner fills them. -->

## CHANGELOG
- [ ] `[Unreleased]` entry written from the user's side, including what is still broken — or label `skip-changelog`

## Risk and rollback
<!-- What breaks if this is wrong; does a migration block rolling back the image? -->

## Agent
<!-- If an agent wrote this: session link, and which claims are inspection-only. -->
```

### 8.2 `CODEOWNERS` (repository root; ready to paste)

The path list is ADR-0032 decision 3 plus four additions marked `+`.

```text
# Single human maintainer. With one human, CODEOWNERS does not add a second reviewer; it
# (1) requests the owner's review automatically, and (2) keeps the high-risk list explicit for the
# ADR-0032 fallback (required reviews = 0 everywhere except these paths).
/.github/                                   @ahmetabdullahgultekin
/.claude/                                   @ahmetabdullahgultekin
/CODEOWNERS                                 @ahmetabdullahgultekin
/docs/architecture/multi-transport/APPROVAL.md @ahmetabdullahgultekin
/docs/adr/                                  @ahmetabdullahgultekin
/backend/src/main/resources/db/migration/   @ahmetabdullahgultekin
/mobile/**/crypto/                          @ahmetabdullahgultekin
/shared/**/port/                            @ahmetabdullahgultekin
/mobile/composeApp/build.gradle.kts         @ahmetabdullahgultekin
/gradle/                                    @ahmetabdullahgultekin
/settings.gradle.kts                        @ahmetabdullahgultekin
# + additions (not in ADR-0032; owner to confirm)
/SECURITY.md                                @ahmetabdullahgultekin
/LICENSE*                                   @ahmetabdullahgultekin
/mobile/LICENSE*                            @ahmetabdullahgultekin
/docs/legal/                                @ahmetabdullahgultekin
```

`/mobile/**/crypto/` also covers the future `:mobile:crypto` module directory (`/mobile/crypto/`).
SECURITY.md (GOV-08), the licence files (ADR-0017 relicence) and `docs/legal/` (ADR-0029) are added
because each carries a public claim or a legal position.

### 8.3 Code of conduct

Add `CODE_OF_CONDUCT.md` (Contributor Covenant, current version, unmodified) with the owner's private
contact; the repository is public (GOV-14).

---

## 9. Protection, environments and security settings

All values below take effect only after the trunk merge (branching-and-release §3), except where the
"When" column says Phase 0.

### 9.1 Repository settings

| Setting | Target | When | Verify |
|---|---|---|---|
| Merge commits / rebase merges | off / off | After trunk merge (the merge itself is the one merge commit) | `gh api repos/{o}/{r} --jq '.allow_merge_commit,.allow_rebase_merge'` → `false false` |
| Squash merging | on; commit title = PR title | same | `.squash_merge_commit_title` = `PR_TITLE` |
| Automatically delete head branches | on | same | `.delete_branch_on_merge` = `true` |
| Discussions | on, with category *Design RFC* (format: open-ended discussion) and nothing else | Any time after `process` approval | Discussions tab |
| Wiki | off (docs live in `docs/`) | same | `.has_wiki` = `false` |
| Auto-merge | on only after required checks exist (branching-and-release §4) | Later | `.allow_auto_merge` |

### 9.2 Rulesets on `main` (replace classic protection)

ADR-0030 requires that the owner's bypass never skips checks. A ruleset bypass actor bypasses every
rule **of that ruleset**, so the rules are split in two:

| Ruleset | Target | Rules | Bypass |
|---|---|---|---|
| **`main-review`** | default branch | Require a pull request · 1 approval · dismiss stale approvals on push · require approval of the most recent reviewable push · require review from code owners · require conversation resolution | Repository admin role (the owner is the only admin), mode **For pull requests only** (audited; used for the owner's own PRs) |
| **`main-checks`** | default branch | Required status checks (below) · require branches up to date (a sub-option of required checks, so it arrives with them in §12.1 step 10) · require linear history · block force pushes · block deletion | **None** |
| **`release-tags-create`** | tags `v*` | Restrict creations | Repository admin role |
| **`release-tags-immutable`** | tags `v*` | Restrict updates · restrict deletions | **None** — a published `v*` tag never moves (build once, promote the same artefact: ADR-0030, CICD-07). Also enable immutable releases where offered (branching-and-release §4.3) |

A bypass list cannot name an individual user; eligible actors are repository roles, teams, GitHub
Apps and Dependabot. Any future admin collaborator therefore inherits the `main-review` and
`release-tags-create` bypass.

Required status checks (job names on `dev` @ 2434324):

| Check context | Workflow / job | Required when |
|---|---|---|
| `Build & Test` | `backend-ci.yml` / `build-and-test` | PR CI runs off the production host (ADR-0031) |
| `Android Build` | `mobile-ci.yml` / `android-build` | same |
| `Secret Detection` | `security.yml` / `secrets-scan` (Gitleaks) | same, **and** `security.yml`'s trigger `paths:` filter is removed, otherwise the check never reports on PRs that miss the filter and blocks them (GOV MISS-01, CICD-12) |
| later: one aggregate `ci-gate` | — | Optional simplification (CICD-14) once the job set is stable |

Not required: `Docker Build`, `Dependency Scan`, `Docker Image Scan`, `CodeQL Analysis`,
`iOS Framework Build` (macOS hosted runner; blocked by the billing lock, CICD-04).

**Transition (Phase 0 interaction).** Classic protection on `main` and `dev` already requires
`Build & Test` and `Android Build` (§1.2). Stopping PR jobs on `muhabbet-cx43` (CICD-01, Phase 0)
means those checks stop reporting and every PR is blocked. Therefore, in the same change that
disables PR jobs, the owner removes the two required checks from classic protection and records
that in the incident log; `main-checks` re-adds them once PR CI runs on GitHub-hosted (or
non-production) runners. Until then the PR template's measured "Gates that ran" is the gate
(branching-and-release §4.3). See reviewer note R-4.

Branch-name enforcement by ruleset is **not** enabled until the `claude/*` question in
branching-and-release §5 is decided.

### 9.3 Environments

Detail and secret lists: [`ci-trust-boundary.md`](ci-trust-boundary.md) §2–§3.

| Environment | Deployment refs | Reviewers | Secrets | Used by |
|---|---|---|---|---|
| `production` (exists; protection unverified, CICD MISS-02) | branch `main` only | owner (self-review allowed) | `VPS_HOST`, `VPS_USER`, `VPS_SSH_KEY` — environment-scoped, repo-level copies deleted | `deploy-hetzner.yml` |
| `release` (new) | tags `v*` | owner | `MUHABBET_KEYSTORE_BASE64`, `MUHABBET_KEYSTORE_PASSWORD`, `MUHABBET_KEY_ALIAS`, `MUHABBET_KEY_PASSWORD`, `PLAY_SERVICE_ACCOUNT_JSON` — moved from repo level | `mobile-release.yml` build and promote jobs (CICD-02) |

### 9.4 Code security

| Feature | Today | Target | When |
|---|---|---|---|
| Secret scanning | enabled | keep; enable validity checks and non-provider patterns if offered for this repository | Phase 0 |
| Push protection | enabled | keep; bypasses require a reason and are reviewed in the weekly triage | Phase 0 |
| Gitleaks allowlist for `.env.prod` | path allowlist | commit-scoped allowlist recording the rotation (CICD-06) | Phase 0 (incident record) |
| Private vulnerability reporting | **disabled** | **enabled**; `SECURITY.md` and `config.yml` point to it | §12.1 step 2 (scope `process`; not in brief §14 items 1–6, so not in `phase-0`) (GOV-08) |
| `SECURITY.md` | false phone-hash claim on `main` and `dev`; false soft-deletion = KVKK erasure wording on `dev` | Phase 0: correct on `main` (docs-only PR, owner merge) **and** on `dev`: remove the phone-hash claim (GOV-08) and the soft-deletion = KVKK erasure wording (GOV MISS-03, SSOT MISS-03); supported versions = latest 0.x. Re-check before the trunk merge that `dev` is clean | Phase 0 (brief §14 item 6) |
| Dependabot alerts / security updates | security updates enabled; alerts not readable via this API | both on | verify |
| Dependabot version updates | `main` (old config) sends grouped PRs that fail (#743) | `dev`'s config lands on `main`: remove `target-branch`; add the `docker` ecosystem (`github-actions` already present on `dev`) (CICD-13); change label `type:chore` → `type:task` | Trunk merge + workflow PR |
| Dependabot auto-merge workflow | on `dev` only | stays **disabled** until `main-checks` has required checks (its own header: `--auto` without required checks merges immediately) | Later |
| CodeQL | `security.yml`, `main` only in practice | runs on PRs to `main` (CICD-12) | With hosted runners |

### 9.5 Actions settings (pointer)

Fork-PR approval, runner groups, SHA pinning and per-job permissions are owned by
[`ci-trust-boundary.md`](ci-trust-boundary.md) (CICD-01, CICD-03, CICD-05).

### 9.6 Discussions

One category: **Design RFC** (open-ended), with the form of §4.4. Rules: one RFC per decision; the
RFC links the finding IDs; when decided, the owner posts a closing comment with the outcome and the
ADR number, closes the discussion as *Resolved*, and locks it (open-ended categories have no
"mark as answer"). The multi-transport design enters the tracker as **one**
RFC → ADR set → per-milestone Epics, never as dozens of feature issues (GOV-10).

---

## 10. Automation

Ordered by runner dependency: §10.1–§10.3 need no runner; §10.4–§10.6 need one and wait for
ADR-0031 (CICD-01, CICD-04, GOV-13).

### 10.1 Projects built-in workflows (runner-free)

| Workflow | Setting |
|---|---|
| Auto-add to project | repository `Muhabbet`, filter `is:issue,pr is:open`. One auto-add workflow on the Free plan. Catches only new or updated items: existing items are bulk-added in §12.1 step 7 |
| Item added to project | Status → Inbox |
| Item reopened | Status → Inbox |
| Pull request merged | Status → Done (acts on the **PR item** only, never on linked issues) |
| Item closed | Status → Needs verification for issues closed by `Closes` (triage moves `not planned` / `duplicate` closures to Done; the owner moves verified items to Done) |

Issues referenced with `Refs #N` are moved by no workflow: whoever merges the PR sets the issue's
Status to *Needs verification* and adds `status:needs-verification` by hand, until the §10.5
"Merge → `status:needs-verification`" automation exists (same rule in the §8.1 template).
| Auto-archive | closed items with reason `not planned` not updated for 30 days. Completed items are archived by hand from the Done column, so nothing leaves the verification queue unseen. |

### 10.2 Native closing (runner-free)

After the trunk merge, `Closes #N` in a PR merged into `main` closes the issue natively and records
the Development link (GOV-02). `close-linked-issues.yml` is deleted (branching-and-release §4.2).
Device-criterion work follows §3: the PR `Closes` an implementation sub-issue and the `[O]` parent
stays open. Where `Refs #N` is used anyway, it gives only a timeline cross-reference (no Development
link, R-7) and the merger moves #N to *Needs verification* by hand (§10.1).

### 10.3 Release notes (runner-free generation)

`.github/release.yml` (ready to paste):

```yaml
changelog:
  exclude:
    labels: [skip-changelog, dependencies]
  categories:
    - title: Security
      labels: [security]
    - title: Fixed
      labels: [type:bug]
    - title: Added
      labels: [type:feature]
    - title: Internal
      labels: [type:task, type:epic]
    - title: Other
      labels: ["*"]
```

The release body = generated notes + a hand-written Turkish summary of at most 500 characters on
top (what Play shows) + the CHANGELOG "Known issues" (GOV-09). The `mobile-release.yml` change that
uses `--generate-notes` is a workflow edit owned by branching-and-release §4.2/§7. Agents add the
matching `type:*` label to each PR (copied from its issue) so categorisation works.

### 10.4 Weekly triage (agent session, owner decides; no runner)

| Step | Query (example; date = today − N) |
|---|---|
| Untriaged | `gh issue list --search "is:open no:milestone -label:prio:P0 -label:prio:P1 -label:prio:P2 -label:prio:P3"` |
| Stale | `gh search issues --repo ahmetabdullahgultekin/Muhabbet --state open --updated "<YYYY-MM-DD"` (60 days) |
| P0 cap | `gh issue list --label prio:P0 --state open` → more than 3 is a triage defect |
| Verification queue | `gh issue list --label status:needs-verification --state all` |
| Mirror drift | Priority/Area field vs label (§7.2) |
| Push-protection bypasses | Security tab |

**Stale policy:** nothing is auto-closed. An item untouched for 60 days gets an explicit decision in
triage — schedule, backlog (`prio:P3`, no milestone) or close `not planned` with a reason. If
`actions/stale` is ever adopted, it only labels, never closes, and exempts `prio:P0`, `prio:P1`,
`security`, `legal-gate` and `status:needs-decision`.

### 10.5 After hosted runners exist (ADR-0031 in force)

| Automation | Purpose |
|---|---|
| CHANGELOG check on PRs | Fails when `CHANGELOG.md` `[Unreleased]` is unchanged and the PR lacks `skip-changelog` |
| Label → field sync | Writes Priority/Area mirrors (§7.2) |
| Merge → `status:needs-verification` | Adds the label to issues closed by a merged PR |
| Duplicate Flyway V-number check across open PRs | GOV-12 |
| Re-enable `stale-branches.yml` | After reviewing its defaults (branching-and-release §4.2) |

### 10.6 Not adopted

Release Drafter (generated notes cover it without a runner); merge queue (solo, low concurrency);
Copilot assignment (billing lock, GOV-12); auto-closing bots.

---

## 11. One-time tracker cleanup

**Preconditions:** `process` scope approved; trunk merge done (otherwise issues are re-verified
against a stale `main`); labels of §5.1 created. Run by an agent doing the evidence work, owner
deciding. Each step is logged as a sub-issue of the *Tracker cleanup 2026-11* epic.

| # | Step | Items (measured 2026-11-12) | Reversible by |
|---|---|---|---|
| TC-0 | **Snapshot.** `gh issue list --state all --limit 1000 --json number,title,state,stateReason,labels,milestone,body` and `gh pr list --state all --limit 1000 --json number,labels,milestone` (PR label associations, e.g. the 38 on `java`/`dependencies`) → commit nothing; store the files with the epic as an attachment; also export milestones and labels | 471 issues, 41 labels (re-measure), 9 milestones | — (this is the restore source) |
| TC-1 | **Create** new labels (§5.1) and **rename** `priority:*`→`prio:*`, `type:chore`→`type:task`, `from:md-migration`→`source:md-migration` (and update the `TODO.md:5` query link), `oop-review`→`source:agent-review` | 13 creates (§5.2 rows 12, 17, 45), 7 renames | Rename back; delete creates |
| TC-2 | **Merge** synonym labels (rows 10, 11, 16, 18, 19, 20, 27–32, 34 of §5.2) by adding targets, and add `status:needs-evidence` to open `source:agent-review` items (row 33); do **not** delete sources yet | ~240 issue edits (state all) | Remove added labels (from TC-0) |
| TC-3 | **Duplicates** — re-read both, keep the richer one, close the other as `duplicate` with "Duplicate of #N" and carry over any unique detail: 140/150, 123/607, 415/587, 413/591, 581/671 | 5 closures | Reopen |
| TC-4 | **Stale and mis-prioritised P0/critical.** Re-verify, then: #70 close (PR #49 merged 2026-06-07); #69, #71 close only with evidence that login/notifications and the signed AAB work in production today; #67 → owner `[O]` item under 1.0.0; #68 → superseded by the ADR-0017 1.0 epic (close as `not planned` with a link, or make it a sub-issue); #72 → `prio:P2`, 1.0.0. #158 close (fixed: "Yeni mesaj" appears only in comments). **#327 stays open** (media erasure unimplemented, GOV MISS-02), retitled to the remaining scope and linked to #426. Re-verify #420 and #545. Review the 18 `critical` items: promote at most 3 to P0 (candidates named by GOV-03: #422, #289, #273) | 6 P0, 18 critical, 4 named | Reopen; relabel from TC-0 |
| TC-5 | **Unverified automated findings.** For each open `source:agent-review` issue: re-check the file:line on `main` HEAD. Confirmed and blocking an exit criterion → keep, retitle in English without the `[OOP-…]`/`[UI-…]` prefix, set area and prio, milestone per MP-4. Confirmed, not blocking → sub-issue of a per-area *Code-quality backlog* epic, `prio:P3`, no milestone. Not reproducible or already fixed → close `not planned` (or `completed` if fixed on `main`) with the evidence. | 102 open by label (97 per GOV-03); outcome split is measured, not estimated | Reopen; relabel from TC-0 |
| TC-6 | **Re-milestone.** Remove the 56 `ui-ux-review` items from 0.6.0 (they go through TC-5). For the 103 unmilestoned: admit by MP-4 or leave in backlog with a prio. Delete `Tier 1/2/3`; banner `0.4.0 — Installable` | 56 + 103 issues, 4 milestones | Re-add milestone from TC-0 (deleted milestones held 0 issues) |
| TC-7 | **Merged, not released.** The 69 issues closed "completed" since 2026-08-22: add `status:needs-verification` and Verification = Merged; when the first trunk release ships, move each to Released; owner verifies device items | 69 | Remove label |
| TC-8 | **Fill gaps.** Area on the 141 without one; prio on the 52 without one; English titles on the 8 with Turkish titles (#671, #673, #676, #717, #719, #722, #732, #734) — body text may stay Turkish | 141 / 52 / 8 | From TC-0 |
| TC-9 | **Delete** source labels of TC-2 and the dead labels (rows 21–24 after TC-4, 37–43) after one triage week | ~25 labels | Recreate + reapply from TC-0 |
| TC-10 | **Epics.** Create Phase 0, per-milestone (0.4 → 1.0.0) and Calls (#715 + sub-issues) epics; link each in its milestone description | ~7 epics | Delete epic issues |
| TC-11 | **Open PRs.** Retarget #690, #718, #736, #738 to `main` (branching-and-release §3.1 step 8); close #743 | 5 | Retarget back / reopen |

**Exit of the cleanup** (owner checks): every open P0/P1 has evidence and an area; at most 3 open
P0; no open item lacks prio or area; no milestone holds an item that blocks none of its exit
criteria; the 5 duplicate pairs are resolved; every `source:agent-review` item is either re-verified
or closed. GOV-03 proposes "fewer than 120 open issues" as a target; treat it as an expectation to
check, not a quota to close towards.

**Order constraint (amended 2026-11-12, ADR-0030 D8 option (b)):** no multi-transport Epic or issue
is filed until TC-11 is done, **except** 0.4 implementation issues labelled `status:needs-evidence`,
with milestone 0.4, linked from a single Phase-0/0.4 epic. No other multi-transport epics before
TC-11 (roadmap §17 precondition). Recorded as a `process` Condition in `APPROVAL.md`.

---

## 12. Rollout order and rollback

### 12.1 Order

| Step | What | Depends on | Section |
|---|---|---|---|
| 0 | Phase 0 emergencies: secrets rotated, PR jobs off cx43 **with the two required checks removed from classic protection in the same change**, `mordecai` resolved, environments, SHA pins, SECURITY.md corrected on `main` (docs-only PR, owner merge) **and** on `dev` (phone-hash claim, soft-deletion = KVKK erasure wording; supported versions = latest 0.x) | `phase-0` scope (brief recommends authorising now) | §9.2, §9.4, ci-trust-boundary |
| 1 | Owner records `process` approval and answers Q-O9 | Brief Phase 0 exit | APPROVAL.md |
| 2 | Additive, zero-risk settings: Discussions + *Design RFC*, wiki off, private vulnerability reporting on, new labels created (TC-1 creates only) | 1 | §9.1, §9.4, §5 |
| 3 | Trunk merge `dev` → `main`, retarget PRs, delete `dev` | 1, Phase 0 | branching-and-release §3 |
| 4 | Repository merge settings; rulesets `main-review` (+ admin-role bypass), `release-tags-create` and `release-tags-immutable`; classic protection removed. `main-checks` **without** required checks: linear history, block force pushes, block deletion ("up to date" arrives with the checks in step 10) | 3 | §9.1, §9.2 |
| 5 | One PR into `main`: PR template, CODEOWNERS, CODE_OF_CONDUCT, form edits (§4.3), discussion form, `release.yml`, workflow edits (branching-and-release §4.2), Dependabot retarget | 4 | §4, §8, §10.3 |
| 6 | Agent identity (ADR-0032) installed; first agent PR approved by the owner end to end | 4, 5 | agent-workflow §2 |
| 7 | Projects board: fields, views, built-in workflows; **bulk-add existing open issues and the 69 TC-7 issues** (Projects "Add item" with a `repo:ahmetabdullahgultekin/Muhabbet is:issue is:open` search, or a `gh project item-add` loop) — auto-add only catches new or updated items | 2 | §7, §10.1 |
| 8 | Tracker cleanup TC-0–TC-11, run under the agent identity (ADR-0032 decision 1) | 3, 5, 6, 7 | §11 |
| 9 | Milestone text appended (§6.3) per approved scope | 8 | §6 |
| 10 | Required checks added to `main-checks`; Dependabot auto-merge re-enabled | Hosted (or non-production) PR runners — ADR-0031 | §9.2, §9.4 |
| 11 | 1.1–1.4 milestones and epics | each scope `Approved` (MP-8) | §6.4 |

### 12.2 Rollback

| Step | Rollback | Cost |
|---|---|---|
| 0 | Not rolled back (security). Required checks return in step 10. | — |
| 2 | Disable Discussions (content kept); delete unused new labels | none |
| 3 | branching-and-release §3.2 (`pre-trunk/*` tags). A force-restore of `main` first requires disabling `main-checks` (an audited settings change); there is no bypass | high after `dev` deletion; tags make it recoverable |
| 4 | Delete rulesets; re-create classic protection from the before-values recorded in the step-4 PR body; restore merge settings | low |
| 5 | Revert the squash commit | low |
| 6 | Uninstall the App; agents fall back to the owner token (ADR-0032 status quo) | low; review boundary lost again |
| 7 | Disable built-in workflows; board deletion is not needed | none |
| 8 | Per-step "Reversible by" column of §11, from the TC-0 snapshot | medium (label deletions need re-application) |
| 9–11 | Edit/close milestones; a dropped gated milestone is closed with an ADR amendment (MP-7) | low |

### 12.3 Verification of the rollout

| Check | Evidence |
|---|---|
| Issue chooser on `github.com/…/issues/new/choose` shows the forms and the security/RFC links | Owner screenshot |
| `gh api repos/{o}/{r}/rulesets` lists `main-checks`, `main-review`, `release-tags-create` and `release-tags-immutable` (ADR-0030 V3); an agent PR cannot merge without the owner's approval; an owner PR via bypass still shows required checks once step 10 is done | Test PR |
| A merged PR with `Closes #N` closes #N natively with a Development link | Test issue |
| A merged PR with `Refs #N` leaves #N open; the merger has moved it to *Needs verification*; the PR item is in Done | Test issue |
| A merged PR that `Closes` an implementation sub-issue shows the Development link; the `[O]` parent stays open | Test issue |
| Moving or deleting an existing `v*` tag (e.g. `git push --force` of `v0.3.10` to another commit) is refused, also for the admin | Refused push output (no scratch `v*` tag: it could never be deleted) |
| `gh label list` returns exactly the §5.1 set after TC-9 | Command output |
| Private vulnerability reporting accepts a draft advisory | Owner, Security tab |
| Dependabot opens PRs against `main` with the new grouping | Next weekly run |

---

## 13. Evidence

GOV-01 (default-branch drift) · GOV-02 (closed ≠ shipped) · GOV-03 (tracker content) · GOV-04
(labels) · GOV-05 (milestones) · GOV-06 (zero review) · GOV-07 (branch policy) · GOV-08
(SECURITY.md) · GOV-09 (release hygiene) · GOV-10 (native PM features) · GOV-11 (process-doc
contradictions) · GOV-12 (claim signal) · GOV-13 (bot noise on prod runner) · GOV-14 (community
health) · GOV-15 (language) · GOV MISS-01…04 · CICD-01, 02, 03, 04, 05, 12, 13, 14 · CICD MISS-02 ·
SSOT-01, 02, 03, 07, 08, 11, 13 — [findings register](../reviews/2026-11-12-findings-register.md).

External references (from the governance digest): GitHub docs on
[events that trigger workflows](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows),
[configuring issue templates](https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/configuring-issue-templates-for-your-repository),
[syntax for issue forms](https://docs.github.com/en/communities/using-templates-to-encourage-useful-issues-and-pull-requests/syntax-for-issue-forms),
[linking a PR to an issue](https://docs.github.com/en/issues/tracking-your-work-with-issues/using-issues/linking-a-pull-request-to-an-issue),
[automatically generated release notes](https://docs.github.com/en/repositories/releasing-projects-on-github/automatically-generated-release-notes).

## 14. Reviewer notes

> **Resolved (2026-11-12):** **R-1:** brief erratum E-32: "Confirm and keep secret scanning and push
> protection (already enabled 2026-11-12); enable validity checks if offered. The burned `.env.prod`
> values in history are unaffected."

> **Reviewer note (resolved) R-2:** native issue types are available. GitHub docs: issue types can be
> used "across your organization and user-owned repositories", and default types Task, Bug and
> Feature "are included in every organization and user account"
> ([managing issue types](https://docs.github.com/en/issues/tracking-your-work-with-issues/configuring-issues/managing-issue-types-in-an-organization));
> the GOV-10 digest records that Bug/Feature/Task exist but no issue uses them. The only open point
> is creating a custom `Epic` type in the user profile's issue-type settings; if that fails, epics
> carry `type:epic` as a label (§4.1, §5.1).

> **Note:** **R-3:** settled by the process approval. Baseline (c) is adopted: agents
> write labels only, the board follows by built-in workflows and owner triage, and the draft PR is the
> authoritative claim. Moving to an organisation is a later owner option.

> **Resolved (2026-11-12):** **R-4:** same sequencing as ADR-0030 Decision 4 (now normative) and brief
> erratum E-33: remove the two required checks from classic protection together with the runner
> change; re-add them in `main-checks` once PR CI is off-host. The two-ruleset split is what makes
> "admin bypass does not skip checks" true.

> **Note:** **R-5:** the four milestones are already closed (Tier 1–3 empty); §6.2 deletes
> the empty Tier milestones and banners `0.4.0` instead.

> **Note:** **R-6:** settled by the process approval: `area:mobile` plus `area:ios` is
> accepted (most mobile code is CMP `commonMain`).

> **Resolved (2026-11-12):** **R-7:** ADR-0032 decision 6 states option (a) as the default: the `[O]`
> owner-observation criterion lives in a parent issue; the agent's draft PR `Closes` an implementation
> sub-issue; A6 checks for a linked draft PR on that sub-issue.

> **Reviewer note (closed) R-8:** superseded. `branching-and-release.md` §4.3 now carries the same
> `main-checks` / `main-review` split as §9.2, and its §4.1 private vulnerability reporting row
> points to §12.1 step 2.

> **Note:** **R-8a:** settled by the process approval: the `release-tags-create` /
> `release-tags-immutable` split is accepted as part of ADR-0030 D4.

> **Note:** **R-9:** settled by the process scope approval: private vulnerability
> reporting stays scheduled under §12.1 step 2 (scope `process`) and can be done as soon as that scope
> is recorded as approved.
