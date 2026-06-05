# Design: <Feature> (<Project> Tier <N>)

> **How we build features (the process this template enforces).**
> 1. **Design-doc first** — no non-trivial feature starts as code. This doc is reviewed before implementation.
> 2. **ADR for each significant decision** — `docs/adr/NNNN-title.md`, immutable, numbered (Context / Decision / Consequences).
> 3. **C4 + sequence diagrams** — at least a container/component view and the key flow, in `docs/diagrams/*.mmd` (mermaid) so they render in the docs site.
> 4. **Contract-first** — DB schema (Flyway/Alembic), API/protocol, and types are defined here *before* implementation; tests assert the contract.
> 5. **Vertical-slice agile** — break the feature into thin end-to-end slices (S1…Sn), each independently shippable **behind a feature flag** (default OFF). Not horizontal layers.
> 6. **Reversible rollout** — flag default-OFF = byte-identical to today; dark → staging → canary-one-tenant → broad; kill-switch by flag, never a redeploy. (Per the project's reversible-change posture.)
> 7. **TDD + green CI gate** — tests written against the contract; CI (build + test + lint + typecheck) must be green; no silent failures.
> 8. **Verify in the real product** — a slice is "done" only when demonstrated end-to-end in the running app, not when unit tests pass.

| | |
|---|---|
| **Status** | Draft / In review / Approved / Shipped |
| **Author / Reviewers** | |
| **Feature flag** | `<name>` (default OFF) |
| **ADRs** | links |
| **Tracking** | ROADMAP T<N>.x → slices S1…Sn |

## 1. Context & problem
*Why now, what's broken/missing, who it's for.*

## 2. Goals / Non-goals
*Crisp. Non-goals are as important as goals — they bound the tier.*

## 3. Current state
*What exists, what we reuse, what we extend vs replace.*

## 4. Proposed design
*Prose + a diagram (ASCII here, mermaid in `docs/diagrams/`). The key flows.*

## 5. Data model
*Migrations (Flyway `V##__*.sql` / Alembic), new tables/columns, invariants.*

## 6. API / protocol / contract
*New endpoints, message types, DTOs — additive & backwards-compatible.*

## 7. Files to add / change
*An explicit tree, layered (hexagonal: domain → port → adapter → ui). `(+)` add, `(~)` change.*

## 8. Rollout & flags
*Flag default-OFF semantics; dark→canary→broad; backwards compat; rollback.*

## 9. Agile iteration plan
*S1…Sn vertical slices, each with a one-line "Done =" acceptance criterion.*

## 10. Test plan
*Unit / integration / contract / security — what proves each slice.*

## 11. Risks & open questions
*The hard parts, mitigations, what's deferred to a later tier.*

## 12. Rollback
*How to revert with zero data loss (additive migrations + flag OFF).*
