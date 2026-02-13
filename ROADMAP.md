# Muhabbet — Product Roadmap

> **Last Updated**: February 13, 2026
> **Status**: MVP feature-complete (24 features shipped). Preparing for Play Store internal testing.

---

## Completed Features (24/24 MVP)

| # | Feature | Status |
|---|---------|--------|
| 1 | Auth (OTP + JWT + device management) | DONE |
| 2 | 1:1 messaging (WebSocket real-time) | DONE |
| 3 | Mobile app (CMP Android — auth, chat, settings, dark mode) | DONE |
| 4 | Contacts sync (Android phone hash matching) | DONE |
| 5 | Typing indicators (send, receive, backend broadcast) | DONE |
| 6 | Media sharing (images, thumbnails, full-size viewer) | DONE |
| 7 | Push notifications (FCM, token registration, offline delivery) | DONE |
| 8 | Presence (online/offline via Redis, green dot, last seen) | DONE |
| 9 | Group messaging (create, roles, add/remove, leave) | DONE |
| 10 | Voice messages (record, playback, audio bubbles) | DONE |
| 11 | Production SMS (Netgsm) | DONE |
| 12 | Message delete/edit (soft delete, editedAt, context menu) | DONE |
| 13 | Localization i18n (Turkish + English, runtime switch) | DONE |
| 14 | Reply/quote + forwarding | DONE |
| 15 | Starred messages | DONE |
| 16 | Link previews (Open Graph, cached) | DONE |
| 17 | Message search (full-text, per-conversation) | DONE |
| 18 | File/document sharing (PDF, DOC, etc.) | DONE |
| 19 | Disappearing messages (24h, 7d, 90d) | DONE |
| 20 | Polls (create, vote, real-time results) | DONE |
| 21 | Location sharing (static pin, map preview) | DONE |
| 22 | Status/Stories (24h ephemeral, contacts visibility) | DONE |
| 23 | Channels/Broadcasts (one-to-many, subscriber model) | DONE |
| 24 | UI/UX polish (reactions, swipe-to-reply, typing animation, FAB, filter chips, pinned chats, OLED theme, bubble tails, date pills, empty states, unread styling) | DONE |

### Production Readiness (Completed)

- CORS restricted to specific origins
- Rate limiting on auth endpoints (10 req/min/IP)
- R8/ProGuard configuration for release builds
- Signing config scaffold (env var-based)
- Splash screen (light + dark)
- Privacy policy (Turkish + English, KVKK compliant)
- LICENSE (MIT), CONTRIBUTING.md, SECURITY.md
- Structured logging (replaced 22 println calls)
- allowBackup=false
- Adaptive app icon (speech bubble on green)
- Notification icon (monochrome)
- Redacted sensitive deployment details from docs

---

## Phase 1 — Internal Testing Release (Current)

**Goal**: Get the app on Play Store internal testing track.

| # | Task | Effort | Status |
|---|------|--------|--------|
| 1.1 | Generate keystore + build signed AAB | 1 hour | Manual step |
| 1.2 | Capture Play Store screenshots (8 screens) | 1 day | Manual step |
| 1.3 | Create feature graphic (1024x500) | 1 day | Design |
| 1.4 | Write store description (Turkish + English) | 1 hour | Content |
| 1.5 | Complete IARC content rating questionnaire | 30 min | Manual step |
| 1.6 | Smoke test: fresh install flow on 3+ devices | 1 day | QA |
| 1.7 | Upload AAB + listing to Play Console | 1 hour | Manual step |

---

## Phase 2 — Beta Quality (2-3 weeks)

**Goal**: Architecture cleanup + test coverage + missing social features.

| # | Task | Effort | Why |
|---|------|--------|-----|
| 2.1 | Increase test coverage 13% -> 40% (MessagingService, MediaService, controller integration tests, WebSocket handler tests) | 1 week | Catch regressions |
| 2.2 | Refactor ChatScreen.kt (1,700 lines -> MessageListPane, MessageInputPane, ChatDialogs) | 2 days | SRP compliance |
| 2.3 | Refactor 5 controllers with direct repo access (Status, Channel, Poll, DisappearingMessage, Reaction) to use case pattern | 2-3 days | Hexagonal compliance |
| 2.4 | Split MessagingService into ConversationService + MessageService + GroupService | 1-2 days | SRP compliance |
| 2.5 | Stickers & GIFs (GIPHY/Tenor API, sticker packs, new bubble types) | 1-2 weeks | User expectation |
| 2.6 | Profile viewing (tap avatar to see full profile, phone, about, shared media) | 3-5 days | Basic social feature |
| 2.7 | Contact details screen (phone number, about, mutual groups) | 2-3 days | Basic social feature |

---

## Phase 3 — Public Beta & Voice Calls (4-8 weeks)

**Goal**: Voice/video calls — the make-or-break feature for Turkish market.

| # | Task | Effort | Why |
|---|------|--------|-----|
| 3.1 | Voice calls (1:1) — WebRTC via LiveKit SFU, call signaling, incoming call screen, in-call UI, call history | 3-4 weeks | Non-negotiable for WhatsApp replacement |
| 3.2 | Video calls (1:1) — Camera toggle, picture-in-picture | 1-2 weeks | Natural extension |
| 3.3 | Notification improvements — Grouping per conversation, inline reply, sound customization | 3-5 days | Beta feedback demand |
| 3.4 | Performance optimization — Lazy loading (1000+ messages), image caching audit, WS resilience | 1 week | Scale preparation |
| 3.5 | Crash reporting — Wire Sentry mobile SDK, basic analytics events | 2-3 days | Visibility for beta |

---

## Phase 4 — Trust & Security (6-12 weeks)

**Goal**: E2E encryption and legal compliance.

| # | Task | Effort | Why |
|---|------|--------|-----|
| 4.1 | E2E encryption (Signal Protocol) — Key generation, X3DH, Double Ratchet, key verification. Start with opt-in "Secret Chat" | 6-10 weeks | Core differentiator |
| 4.2 | KVKK compliance audit — Data export endpoint, hard account deletion, retention policies, consent flows | 1-2 weeks | Legal requirement |
| 4.3 | Security penetration testing — OWASP ZAP/Burp Suite, fix findings | 2-4 weeks | Before public launch |

---

## Phase 5 — Multi-Platform & Growth (8-16 weeks)

**Goal**: iOS, web client, and growth features.

| # | Task | Effort | Why |
|---|------|--------|-----|
| 5.1 | iOS release — Platform module, APNs, contact sync, audio, image picker, TestFlight | 4-6 weeks | 40% of Turkish smartphones |
| 5.2 | Web client / Desktop — React+TS or Kotlin/JS, QR device linking, message sync | 4-8 weeks | Power users |
| 5.3 | Group voice/video calls — Multi-party LiveKit rooms, participant grid | 2-3 weeks | Feature parity |
| 5.4 | Channel monetization — Analytics, subscriber mgmt, tipping | 2-3 weeks | Revenue |
| 5.5 | CDN for media — CloudFront/CDN for media delivery at scale | 1-2 weeks | Scale preparation |

---

## Key Decision Points

1. **Before Phase 3**: Choose WebRTC provider — LiveKit Cloud (managed, free dev tier) vs self-hosted LiveKit on GCP
2. **Before Phase 4**: Choose E2E library — `libsignal-client` (battle-tested, used by Signal/WhatsApp) vs custom
3. **Before Phase 5 (iOS)**: CMP iOS target vs native SwiftUI for better platform feel
4. **Revenue model**: Ad-free premium? Freemium? Channel monetization? Decide before Phase 5

---

## Technical Debt Tracker

| Debt | Severity | Phase to Fix |
|------|----------|-------------|
| ChatScreen.kt ~1,700 lines | High | Phase 2 |
| 5 controllers bypass use case layer | Medium | Phase 2 |
| MessagingService implements 7 use cases | Medium | Phase 2 |
| Test coverage at 13% | High | Phase 2 |
| Backend enum duplication (intentional) | Low | Monitor |
| No mobile unit tests | Medium | Phase 3 |
| Single-server architecture | Low | Phase 5+ |

---

## Critical Path

```
Phase 1 (now)        → Internal testing on Play Store
Phase 2 (2-3 weeks)  → Beta quality, architecture cleanup
Phase 3 (4-8 weeks)  → VOICE CALLS ← make-or-break for Turkish market
Phase 4 (6-12 weeks) → E2E encryption, KVKK compliance
Phase 5 (8-16 weeks) → iOS, web, growth
```

---

## Turkish Market Context

- WhatsApp: 60M users in Turkey (88.6% of internet users)
- 2021 privacy controversy drove exodus to BiP/Signal/Telegram — users returned due to missing features
- **Privacy + KVKK compliance** is Muhabbet's key differentiator
- **Voice calls are non-negotiable** — Turkish culture is voice-heavy
- Telegram channels used by 85% of its users — channel ecosystem creates network effects

---

## Architecture Reference

### Project Structure
```
muhabbet/
├── backend/    → Spring Boot 3.4 + Kotlin (modular monolith, hexagonal)
├── shared/     → KMP shared module (models, DTOs, protocol, validation)
├── mobile/     → Compose Multiplatform (Android + iOS stubs)
├── infra/      → Docker Compose, nginx, deploy scripts
└── docs/       → Architecture docs, API contract, privacy policy
```

### Tech Stack
| Component | Technology |
|-----------|-----------|
| Language | Kotlin (everywhere) |
| Backend | Spring Boot 3.4, PostgreSQL 16, Redis 7, MinIO |
| Mobile | CMP, Ktor, Koin, Decompose, Coil3 |
| Shared | KMP, kotlinx.serialization, kotlinx.datetime |
| Infra | Docker Compose, nginx, GCP (e2-medium), Firebase FCM |
| CI/CD | GitHub Actions |
