# ADR-001: Modular Monolith Over Microservices

**Date:** 2026-02-11
**Status:** Accepted
**Decision Makers:** Solo engineer

## Context
We need an architecture for a messaging platform MVP built by a single engineer.
Options: microservices, modular monolith, serverless.

## Decision
Build as a **modular monolith** with strict hexagonal architecture boundaries per module.

## Rationale
- Solo engineer: microservices add 3-5x operational overhead with zero team-autonomy benefit
- Module boundaries (ports/adapters) are identical to future microservice boundaries
- One deployment to monitor, debug, and operate
- Extraction to microservices is a mechanical refactoring, not a rewrite
- WhatsApp, Discord, Shopify all started as monoliths

## Consequences
- Faster development velocity (3-5x vs microservices)
- Must discipline module boundaries via package structure and ArchUnit tests
- Cannot scale modules independently until extraction
- One module's resource issue affects the whole application
