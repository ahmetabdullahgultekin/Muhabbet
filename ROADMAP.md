# Muhabbet — Product Roadmap

> **Last Updated**: February 13, 2026
> **Status**: Phases 2-5 architecture & features implemented. Preparing for beta testing.

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

## Phase 1 — Internal Testing Release (Manual Steps)

**Goal**: Get the app on Play Store internal testing track.

| # | Task | Status |
|---|------|--------|
| 1.1 | Generate keystore + build signed AAB | Manual step |
| 1.2 | Capture Play Store screenshots (8 screens) | Manual step |
| 1.3 | Create feature graphic (1024x500) | Design |
| 1.4 | Write store description (Turkish + English) | Content |
| 1.5 | Complete IARC content rating questionnaire | Manual step |
| 1.6 | Smoke test: fresh install flow on 3+ devices | QA |
| 1.7 | Upload AAB + listing to Play Console | Manual step |

---

## Phase 2 — Beta Quality (Completed)

**Goal**: Architecture cleanup + test coverage + social features.

| # | Task | Status |
|---|------|--------|
| 2.1 | Backend test coverage (MediaService, ConversationService, GroupService, WebSocket, RateLimit — ~125 tests) | DONE |
| 2.2 | Refactor ChatScreen.kt (1,771→405 lines → MessageBubble, MessageInputPane, ChatDialogs) | DONE |
| 2.3 | Refactor 5 controllers to use case pattern (Status, Channel, Poll, DisappearingMessage, Reaction) | DONE |
| 2.4 | Split MessagingService → ConversationService + MessageService + GroupService | DONE |
| 2.5 | Stickers & GIFs (GIPHY integration, GifStickerPicker, STICKER/GIF content types) | DONE |
| 2.6 | Profile viewing (tap avatar → full profile, phone, about, shared media, mutual groups, action buttons) | DONE |

---

## Phase 3 — Voice Calls & Monitoring (Partially Complete)

**Goal**: Voice/video calls and operational visibility.

| # | Task | Status |
|---|------|--------|
| 3.1 | Call signaling infrastructure (WebSocket: CallInitiate/Answer/IceCandidate/End, CallSignalingService, call history DB) | DONE |
| 3.2 | Voice calls (1:1) — WebRTC integration, incoming call screen, in-call UI | Remaining (needs WebRTC/LiveKit client) |
| 3.3 | Video calls (1:1) — Camera toggle, picture-in-picture | Remaining |
| 3.4 | Notification improvements (grouping per conversation, inline reply, channels) | DONE |
| 3.5 | Crash reporting — Sentry mobile SDK (CrashReporter expect/actual, auto-init) | DONE |
| 3.6 | Performance optimization — Lazy loading, image caching audit, WS resilience | Remaining |

---

## Phase 4 — Trust & Security (Architecture Complete)

**Goal**: E2E encryption foundation and KVKK legal compliance.

| # | Task | Status |
|---|------|--------|
| 4.1 | E2E encryption architecture (EncryptionKeyBundle, OneTimePreKeys, key exchange endpoints, EncryptionService, DB migrations) | DONE (architecture) |
| 4.2 | E2E encryption client integration (Signal Protocol: X3DH, Double Ratchet, key verification UI) | Remaining |
| 4.3 | KVKK compliance (data export endpoint, account soft-deletion, UserDataController) | DONE |
| 4.4 | Security penetration testing (OWASP ZAP/Burp Suite, fix findings) | Remaining |

---

## Phase 5 — Multi-Platform & Growth (iOS Foundation Done)

**Goal**: iOS release, web client, growth features.

| # | Task | Status |
|---|------|--------|
| 5.1 | iOS platform modules (AudioPlayer, AudioRecorder, ContactsProvider, PushTokenProvider — real implementations) | DONE |
| 5.2 | iOS remaining (ImagePicker, APNs delivery, TestFlight, App Store listing) | Remaining |
| 5.3 | Web client / Desktop (React+TS or Kotlin/JS, QR device linking, message sync) | Remaining |
| 5.4 | Group voice/video calls (multi-party LiveKit rooms, participant grid) | Remaining |
| 5.5 | Channel monetization (analytics, subscriber mgmt, tipping) | Remaining |
| 5.6 | CDN for media (CloudFront/CDN for media delivery at scale) | Remaining |

---

## Remaining Work Summary

### High Priority (Beta Release Blockers)
1. **WebRTC client integration** — Connect call signaling to actual WebRTC (LiveKit SFU recommended). Backend signaling is done; needs mobile call UI + media streams.
2. **iOS ImagePicker** — Currently stubbed; needed for iOS photo sharing.

### Medium Priority (Pre-Public Launch)
4. **E2E encryption client** — Server-side key exchange is built; needs Signal Protocol client library (libsignal-client) for actual encryption/decryption.
5. **Security penetration testing** — Run OWASP ZAP before public launch.
6. **Performance optimization** — Test with 1000+ messages, audit image caching.

### Low Priority (Growth Phase)
7. **Web/Desktop client** — Power user demand.
8. **Group calls** — After 1:1 calls are stable.
9. **Channel monetization** — Revenue feature.
10. **CDN** — When media traffic justifies it.

---

## Technical Debt Tracker

| Debt | Severity | Status |
|------|----------|--------|
| ~~ChatScreen.kt ~1,700 lines~~ | ~~High~~ | Fixed (Phase 2) — split to 405 lines |
| ~~5 controllers bypass use case layer~~ | ~~Medium~~ | Fixed (Phase 2) — all use use cases |
| ~~MessagingService implements 7 use cases~~ | ~~Medium~~ | Fixed (Phase 2) — split into 3 services |
| ~~Test coverage at 13%~~ | ~~High~~ | Improved (Phase 2) — ~125 backend tests |
| Backend enum duplication (intentional) | Low | Monitor |
| No mobile unit tests | Medium | Phase 3+ |
| Single-server architecture | Low | Phase 5+ |
| iOS ImagePicker stub | Medium | Phase 5 |
| CrashReporter.ios.kt stub (needs Sentry iOS pod) | Low | Phase 5 |

---

## Key Decision Points

1. **WebRTC provider**: LiveKit Cloud (managed, free dev tier) vs self-hosted LiveKit on GCP — decide before building call UI
2. **E2E library**: `libsignal-client` (battle-tested) vs custom — architecture supports either
3. **iOS release strategy**: CMP iOS target (current) vs native SwiftUI for better platform feel
4. **Revenue model**: Ad-free premium? Freemium? Channel monetization? Decide before Phase 5 growth

---

## Critical Path

```
Phase 1 (manual)     → Internal testing on Play Store
Phase 2 (DONE)       → Architecture cleanup, tests, GIFs
Phase 3 (signaling done) → WebRTC client integration ← make-or-break for Turkish market
Phase 4 (arch done)  → Signal Protocol client, pen testing
Phase 5 (iOS foundation) → iOS release, web, growth
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
├── mobile/     → Compose Multiplatform (Android + iOS)
├── infra/      → Docker Compose, nginx, deploy scripts
└── docs/       → Architecture docs, API contract, privacy policy
```

### Tech Stack
| Component | Technology |
|-----------|-----------|
| Language | Kotlin (everywhere) |
| Backend | Spring Boot 3.4, PostgreSQL 16, Redis 7, MinIO |
| Mobile | CMP, Ktor, Koin, Decompose, Coil3, Sentry |
| Shared | KMP, kotlinx.serialization, kotlinx.datetime |
| Infra | Docker Compose, nginx, GCP (e2-medium), Firebase FCM |
| CI/CD | GitHub Actions |
