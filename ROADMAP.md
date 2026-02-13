# Muhabbet — Product Roadmap

> **Last Updated**: February 13, 2026
> **Status**: All MVP features complete. iOS platform done. CI/CD active. Security hardened. Call UI built. E2E infra ready. Preparing for beta testing.

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

## Bug Fix Rounds (Post-Phase 2)

**Goal**: Polish and fix issues found during hands-on testing.

| Round | Focus | Status |
|-------|-------|--------|
| Round 1 | Pin, file upload, acks, reactions, forward, starred | DONE |
| Round 2 | Compilation errors from Phase 2-5 merge | DONE |
| Round 3 | Delivery status resolution, shared media, message info, starred redesign, contact name, status upload, forwarded visuals, video thumbnails | DONE |
| Round 4 | Shared media JPQL fix, message info robustness, status text position, starred scroll-to-message, starred back navigation | DONE |

---

## Phase 3 — Voice Calls & Monitoring (UI Complete, WebRTC Pending)

**Goal**: Voice/video calls and operational visibility.

| # | Task | Status |
|---|------|--------|
| 3.1 | Call signaling infrastructure (WebSocket: CallInitiate/Answer/IceCandidate/End, CallSignalingService, call history DB) | DONE |
| 3.2 | Call UI screens (IncomingCallScreen, ActiveCallScreen, CallHistoryScreen, Decompose navigation) | DONE |
| 3.3 | Voice calls (1:1) — WebRTC/LiveKit client SDK integration | Remaining (UI ready, needs media SDK) |
| 3.4 | Video calls (1:1) — Camera toggle, picture-in-picture | Remaining |
| 3.5 | Notification improvements (grouping per conversation, inline reply, channels) | DONE |
| 3.6 | Crash reporting — Sentry mobile SDK (CrashReporter expect/actual, auto-init) | DONE |
| 3.7 | Performance optimization — Database indexes (12), N+1 fixes, connection pooling, nginx tuning, PG tuning | DONE |

---

## Phase 4 — Trust & Security (Infrastructure Complete)

**Goal**: E2E encryption foundation, KVKK legal compliance, security hardening.

| # | Task | Status |
|---|------|--------|
| 4.1 | E2E encryption backend (EncryptionKeyBundle, OneTimePreKeys, key exchange endpoints, EncryptionService, DB migrations) | DONE |
| 4.2 | E2E encryption client infra (E2EKeyManager interface, NoOpKeyManager, EncryptionRepository) | DONE |
| 4.3 | E2E encryption client integration (Signal Protocol: libsignal-client, X3DH, Double Ratchet, key verification UI) | Remaining |
| 4.4 | KVKK compliance (data export endpoint, account soft-deletion, UserDataController) | DONE |
| 4.5 | Security headers (HSTS, CSP, X-Frame-Options, XSS protection, Referrer-Policy, Permissions-Policy) | DONE |
| 4.6 | Input sanitization (InputSanitizer: HTML escaping, control chars, URL validation, 15 tests) | DONE |
| 4.7 | CI security scanning (Trivy, Gitleaks, CodeQL) | DONE |
| 4.8 | Security penetration testing (OWASP ZAP/Burp Suite, fix findings) | Remaining |

---

## Phase 5 — Multi-Platform & Growth (iOS Platform Complete)

**Goal**: iOS release, web client, growth features.

| # | Task | Status |
|---|------|--------|
| 5.1 | iOS platform modules (AudioPlayer, AudioRecorder, ContactsProvider, PushTokenProvider) | DONE |
| 5.2 | iOS platform completion (ImagePicker, FilePicker, ImageCompressor, CrashReporter, LocaleHelper, FirebasePhoneAuth) | DONE |
| 5.3 | iOS APNs delivery (FCM→APNs bridge or direct APNs adapter) | Remaining |
| 5.4 | iOS TestFlight + App Store submission | Remaining |
| 5.5 | Web client / Desktop (React+TS or Kotlin/JS, QR device linking, message sync) | Remaining |
| 5.6 | Group voice/video calls (multi-party LiveKit rooms, participant grid) | Remaining |
| 5.7 | Channel monetization (analytics, subscriber mgmt, tipping) | Remaining |
| 5.8 | CDN for media (CloudFront/CDN for media delivery at scale) | Remaining |

---

## Remaining Work Summary

### High Priority (Beta Release Blockers)
1. **WebRTC client integration** — Connect call signaling to LiveKit SDK. Backend signaling + call UI screens are built; needs media stream SDK integration.

### Medium Priority (Pre-Public Launch)
3. **E2E encryption client** — Key exchange infra + NoOp manager are built; needs `libsignal-client` for actual X3DH + Double Ratchet.
4. **Security penetration testing** — Run OWASP ZAP/Burp Suite before public launch.
5. **iOS APNs delivery** — Needed for iOS push notifications.
6. **Load testing** — k6/Gatling against staging at 1K+ concurrent WebSocket connections.

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
| ~~Test coverage at 13%~~ | ~~High~~ | Improved — ~125 backend tests + 25+ mobile/shared tests |
| ~~No mobile unit tests~~ | ~~Medium~~ | Fixed — FakeTokenStorage, AuthRepository, PhoneNormalization, WsMessage serialization tests |
| ~~iOS ImagePicker stub~~ | ~~Medium~~ | Fixed — PHPickerViewController implementation |
| ~~CrashReporter.ios.kt stub~~ | ~~Low~~ | Fixed — NSLog + Sentry CocoaPod hooks |
| ~~No CI/CD pipeline~~ | ~~Medium~~ | Fixed — GitHub Actions (backend, mobile, security, deploy) |
| ~~No performance optimization~~ | ~~Medium~~ | Fixed — 12 DB indexes, N+1 fixes, connection pooling, nginx/PG tuning |
| Backend enum duplication (intentional) | Low | Monitor |
| Single-server architecture | Low | Phase 6 (adequate for beta) |
| ~~2 active bugs (push, delivery ticks)~~ | ~~Medium~~ | Fixed — prod FCM default + global DELIVERED ack |

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
├── backend/    → Spring Boot 4.0.2 + Kotlin 2.3.10 (modular monolith, hexagonal)
├── shared/     → KMP shared module (models, DTOs, protocol, validation)
├── mobile/     → Compose Multiplatform (Android + iOS)
├── infra/      → Docker Compose, nginx, deploy scripts
└── docs/       → Architecture docs, API contract, privacy policy
```

### Tech Stack
| Component | Technology |
|-----------|-----------|
| Language | Kotlin 2.3.10 (everywhere) |
| Backend | Spring Boot 4.0.2 (Java 25), PostgreSQL 16, Redis 7, MinIO |
| Mobile | CMP, Ktor 3.1.3, Koin 4.1.0, Decompose 3.3.0, Coil 3.1.0, Sentry |
| Shared | KMP, kotlinx.serialization 1.8.1, kotlinx.datetime 0.7.0 |
| Infra | Docker Compose, nginx, GCP (e2-medium), Firebase FCM |
| CI/CD | GitHub Actions (backend, mobile, security, deploy) |
| Security | HSTS, CSP, InputSanitizer, Trivy, Gitleaks, CodeQL |
