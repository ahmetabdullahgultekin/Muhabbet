# ADR-002: Kotlin Everywhere Strategy

**Date:** 2026-02-11
**Status:** Accepted

## Context
Need to choose languages for backend and mobile. Options included Java+Flutter, Kotlin+Flutter, Kotlin+KMP/CMP, Node.js+React Native.

## Decision
**Kotlin everywhere**: Spring Boot 3 + Kotlin (backend), KMP + CMP (mobile), shared KMP module.

## Rationale
- Single language across entire stack reduces context switching
- Shared KMP module: domain models, validation, WebSocket protocol, DTOs written once
- Null safety eliminates NPE category of bugs
- Coroutines for elegant async (WebSocket handling, DB calls)
- kotlinx.serialization shared between backend and mobile — zero protocol drift
- JetBrains backs both Kotlin and IntelliJ — first-class tooling

## Trade-offs Accepted
- CMP is younger than Flutter (2 years vs 7 years) — smaller ecosystem
- 2-3 week learning curve for CMP/Decompose
- Kotlin hiring pool in Turkey smaller than Java (but growing)
- Some Spring Boot examples are Java-first

## Consequences
- One `WsMessage` sealed class defines WebSocket protocol for all platforms
- Shared validation prevents client/server rule drift
- EncryptionPort interface in shared module enables future Signal Protocol on both sides
