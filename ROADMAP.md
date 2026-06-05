# Muhabbet — Product Roadmap

> **Last updated**: 2026-06-04 (refreshed from deep code analysis at HEAD `97626f8`).
> **Tactical companion**: see [`TODO.md`](TODO.md) for the executable P0–P3 checklist.

---

## Current State

**Backend: LIVE and healthy.** `https://muhabbet-api.rollingcatsoftware.com/actuator/health`
returns `UP` for db (PostgreSQL), redis (7.4.8), ssl, liveness and readiness. Deployed on a
Hetzner VPS via Docker + Traefik/nginx + Let's Encrypt. Flyway migrations V1–V17 applied.
Root path returns `401` by design (Spring Security).

**Development is PAUSED** — last commit `97626f8` on 2026-03-31. The codebase is broad and
mature on paper (24 MVP features + 6 phases marked DONE in prior docs), but a code-level audit
at HEAD surfaces gaps that the older roadmap framed as "complete":

| Area | Prior doc said | Verified at HEAD |
|------|----------------|------------------|
| E2E encryption | "Signal Protocol client DONE" | Keys register on login and Signal sessions can be set up, but **no send/receive path calls `.encrypt()`/`.decrypt()`** — messages are sent in **plaintext**. The only `EncryptionPort` use outside DI is its registration in `AppModule.kt`. **This is the #1 launch blocker.** |
| Android release | signing scaffold present | Env-var-gated signing config, **no keystore**, CI builds **debug APK only**. No signed AAB exists. |
| iOS push (APNs) | "Remaining" | Correct — non-functional; token path depends on an AppDelegate hook, no server-side APNs. |
| iOS Firebase auth | "fallback stub" | `isAvailable()` hard-`false`; `verifyCode` throws. iOS can only use backend OTP. |
| iOS E2E | "NoOp until bridged" | Correct — `NoOpKeyManager`/`NoOpEncryption`; needs a libsignal Kotlin/Native bridge. |
| iOS calls | "stub" | Correct — `CallEngine.ios.kt` connect/mute/speaker are no-ops. |
| Message backup | "DONE" | `BackupService.createBackup` is a **placeholder** — marks `COMPLETED` with null URL / zero counts; no archive is produced. |
| Pen test | "Remaining" | Correct — never run. |
| Sentry (prod) | "code ready" | DSN unset in prod compose → no backend error capture in production. |
| Tests | "332/333 pass" (Feb 2026) | 34 backend test files + ArchUnit present; **count unverified at HEAD** — re-run before trusting. |

**Is it launch-ready? No.** The backend is production-grade and live, but the Android client
ships plaintext messages under an E2E banner, has no signed release artifact, and has never
been pen-tested. iOS is further behind (no push, no Firebase auth, NoOp E2E, stub calls).
A defensible path is **Android-first public launch** after closing the E2E + signing + pen-test
blockers, with iOS following once APNs and the libsignal bridge land.

## Next Up (sequenced)

1. **Wire E2E into the message path** (or remove the E2E claim) — TODO P0.
2. **Generate keystore + signed AAB** — TODO P0.
3. **Security pen-test pass** on staging — TODO P0.
4. **Implement the backup job + set Sentry DSN** — TODO P1, low-effort credibility wins.
5. **Merge/close Dependabot #30 & #21, unblock CI** — TODO P1.
6. **Run k6 load tests** against prod-like env — TODO P1.
7. **Android internal-testing → public** (Phase 1 manual steps below).
8. **iOS catch-up**: APNs, Firebase-auth decision, libsignal bridge, then TestFlight — TODO P1/P3.

---

## Deployment Status

**Deployed at:** https://muhabbet-api.rollingcatsoftware.com (Hetzner VPS, Docker + Traefik/nginx + Let's Encrypt)
**Stack:** Kotlin 2.3.20 / Spring Boot 4.0.5 / Java 21 runtime / PostgreSQL 16 / Redis 7 / MinIO
**Health:** UP (db, redis, ssl, liveness, readiness)

- [x] Firebase Cloud Messaging (FCM) — `FCM_ENABLED=true`, credentials mounted
- [x] Netgsm SMS OTP — `NetgsmOtpSender` active via `@ConditionalOnProperty`
- [x] Android app production API URLs configured
- [x] Automated database backups (shared Hetzner pg_dump cron)
- [ ] **Sentry DSN configured** — code ready, env var empty in `.env.prod` (TODO P1)
- [ ] **iOS APNs delivery** — APNs key + server path + TestFlight (TODO P1)
- [ ] **iOS LiveKit / Signal Protocol bridge** (Kotlin/Native) (TODO P1/P3)
- [ ] **Security penetration testing** (OWASP ZAP / Burp) (TODO P0)
- [ ] **App Store / Google Play submission** (TODO P0 keystore prerequisite)

### Known behaviors
- API root returns `401` due to Spring Security — use `/actuator/health` to verify.
- `favicon.ico` requests return 401/403 — APIs don't serve favicons.

---

## Phase 0 — Pre-Launch Hardening (CURRENT — blocks public launch)

| # | Task | Status | Ref |
|---|------|--------|-----|
| 0.1 | Encrypt/decrypt messages in the send/receive path (Signal) | **OPEN — P0** | TODO P0 |
| 0.2 | Release keystore + signed Android AAB | **OPEN — P0** | TODO P0 |
| 0.3 | Security penetration test (staging) | **OPEN — P0** | TODO P0 |
| 0.4 | Real message-backup job (MinIO archive) | **OPEN — P1** | TODO P1 |
| 0.5 | Sentry DSN in production | **OPEN — P1** | TODO P1 |
| 0.6 | Merge/close Dependabot #30 + #21, unblock CI | **OPEN — P1** | TODO P1 |
| 0.7 | k6 load test against prod-like env | **OPEN — P1** | TODO P1 |

## Phase 1 — Android Internal → Public Release (manual + Phase 0 gated)

| # | Task | Status |
|---|------|--------|
| 1.1 | Build signed AAB (depends on 0.2) | Blocked on 0.2 |
| 1.2 | Play Store screenshots (8 screens) | Manual |
| 1.3 | Feature graphic (1024×500) | Design |
| 1.4 | Store description (TR + EN) | Content |
| 1.5 | IARC content rating questionnaire | Manual |
| 1.6 | Fresh-install smoke test on 3+ devices | QA |
| 1.7 | Upload AAB + listing to Play Console (internal → production) | Manual |

## Phase 2 — iOS Catch-Up & Submission

| # | Task | Status |
|---|------|--------|
| 2.1 | iOS APNs delivery (token registration + server push) | Remaining (TODO P1) |
| 2.2 | iOS auth: bridge Firebase Phone Auth OR commit to backend OTP | Remaining (TODO P1) |
| 2.3 | iOS E2E: libsignal-client Kotlin/Native bridge (after 0.1) | Remaining (TODO P1) |
| 2.4 | iOS Crash reporting (Sentry CocoaPod, replace NSLog stub) | Remaining (TODO P3) |
| 2.5 | iOS LiveKit voice bridge (replace CallEngine stub) | Remaining (TODO P3) |
| 2.6 | TestFlight + App Store submission | Remaining |

## Phase 3 — Growth (post-launch)

| # | Task | Status |
|---|------|--------|
| 3.1 | Web / Desktop client (Kotlin/JS or React+TS, QR device-linking, sync) | Remaining |
| 3.2 | Group voice/video calls (multi-party LiveKit) | Remaining |
| 3.3 | CDN for media at scale | Remaining |
| 3.4 | Channel monetization (analytics + bot platform already shipped) | Remaining |

---

## Completed Features (verified present in code)

24 MVP features + 6 engineering phases are implemented in the modular-monolith backend and the
Compose Multiplatform Android client. Headline set (full historical breakdown retained in
`CHANGELOG.md` and `docs/qa/engineering-roadmap.md`):

| Group | Features |
|-------|----------|
| Core | OTP+JWT auth, device mgmt, 1:1 messaging (WebSocket), delivery status, presence (Redis), typing indicators |
| Rich messaging | media/image sharing, voice messages + transcription, reply/quote, forward, starred, reactions, edit/delete, search, link previews, stickers/GIFs (GIPHY), file/document sharing |
| Advanced | groups (roles), channels/broadcasts, communities, polls, disappearing messages, status/stories, location sharing, view-once, scheduled messages |
| Platform | push (FCM), i18n (TR default + EN, runtime switch), SQLDelight offline cache + pending-message queue, WS resilience (backoff+jitter+dedup), KVKK privacy dashboard (export/delete) |
| Backend infra | content moderation (BTK 5651), bot platform (API tokens), channel analytics, call signaling + LiveKit room adapter, Redis pub/sub WS broadcaster, security headers, InputSanitizer, rate limiting |

**Caveats from audit** (see Current State table): E2E send-path is inert, message backup is a
placeholder, and several iOS platform bindings are stubs. Treat the prior "DONE" labels as
"present/compiles" rather than "end-to-end verified" until re-tested.

---

## Architecture Reference

```
muhabbet/
├── backend/   → Spring Boot 4.0.5 + Kotlin 2.3.20 (modular monolith, hexagonal)
│               modules: auth · messaging · media · moderation · shared(config/security/web)
│               presence/notification live inside messaging/shared wiring
├── shared/    → KMP module (model, dto, protocol/WsMessage, validation, port/EncryptionPort)
├── mobile/    → Compose Multiplatform (androidMain full client · iosMain partial: stubs noted)
│               Ktor · Koin · Decompose · SQLDelight · Coil · libsignal (Android)
├── infra/     → docker-compose.prod.yml · nginx · monitoring (Prometheus+Grafana) · load-tests (k6) · scripts
└── docs/      → api-contract.md · decisions.md · qa/ (9 ISO 25010 docs + UI audit) · adr/
```

| Component | Technology |
|-----------|-----------|
| Language | Kotlin 2.3.20 (backend, shared, mobile) |
| Backend | Spring Boot 4.0.5, Java 21 runtime, PostgreSQL 16, Redis 7, MinIO |
| Mobile | CMP, Ktor 3.x, Koin, Decompose, SQLDelight, Coil; libsignal-android (E2E); LiveKit (Android calls) |
| Auth | OTP (Netgsm prod / mock dev) + JWT HS256 (`iss: muhabbet`) |
| Push | FCM (Android live; iOS APNs pending) |
| Observability | SLF4J/Logback JSON + Spring Actuator + Sentry (DSN unset in prod) + Prometheus/Grafana |
| CI/CD | GitHub Actions on self-hosted `hetzner-cx43`: backend CI, mobile CI (Android debug APK + iOS framework), security (Trivy/Gitleaks/CodeQL), deploy-on-push-to-main |

---

## Key Decision Points (open)

1. **E2E scope for launch** — full Signal both platforms vs Android-first (iOS NoOp gated). Drives launch timeline.
2. **iOS auth** — integrate Firebase iOS SDK vs make backend OTP the official iOS path (remove dead Firebase branch).
3. **Dependabot #30** — accept the 38-update group as one bump (watch Twilio 11→12 major, kotlinx-datetime 0.7→0.8) vs split.
4. **Revenue model** — ad-free premium / freemium / channel monetization — decide before Phase 3 growth.

## Turkish Market Context

- WhatsApp ≈ 60M users in Turkey; 2021 privacy backlash drove (temporary) exodus to BiP/Signal/Telegram.
- **Privacy + KVKK compliance is the core differentiator** — which makes shipping plaintext under
  an E2E banner (Phase 0.1) an existential credibility issue, not just a feature gap.
- Voice calls are culturally non-negotiable (Android live; iOS pending).
