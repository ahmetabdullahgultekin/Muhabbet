> **Superseded (2026-11-12, delegated owner review — [record](../reviews/2026-11-12-design-approval-review.md)):** superseded by [ADR 0016 — E2E fail-closed type gate](0016-e2e-fail-closed-type-gate.md); related: [ADR 0017 — E2E library and licence](0017-e2e-library-and-licence.md). Kept for history; the text below is no longer the decision.

# ADR-003: Defer E2E Encryption to Phase 2

**Date:** 2026-02-11
**Status:** Superseded by ADR 0016 (ADR 0017 related) — 2026-11-12; originally Accepted 2026-02-11

## Context
Signal Protocol (X3DH + Double Ratchet) is the industry standard for E2E messaging encryption.
Implementation by one engineer takes 4-8 weeks. Incorrect implementation is a security liability.

## Decision
MVP uses **TLS 1.3 transport encryption only**. Signal Protocol implementation deferred to Phase 2.
Architecture is prepared via `EncryptionPort` interface with `NoOpEncryption` implementation.

## Rationale
- Incorrect E2E is worse than no E2E — false security + PR risk if audited
- libsignal has JVM bindings but no Kotlin/Native (iOS) binding — KMP gap
- MVP goal is proving the messaging flow, not cryptographic security
- TLS 1.3 still encrypts in transit — not plaintext
- EncryptionPort provides a clean seam: Signal Protocol plugs in with zero domain changes

## Consequences
- Server CAN read message content during MVP
- Cannot market as "uçtan uca şifreli" until Phase 2
- Must be transparent with beta testers about encryption status
- Phase 2 priority #1: implement Signal Protocol properly with security audit
