# Muhabbet — WhatsApp-Parity Roadmap

> **Vision:** *"Muhabbet should become WhatsApp tier by tier, iter by iter."*
> **Last updated:** 2026-06-05 (code-grounded at branch `exec/p0-2026-06-05` / PR #31).
> **Tactical companion:** [`TODO.md`](TODO.md) — P0/P1 are aligned to **Tier 1** below.
> **History:** prior launch-blocker framing is preserved in §"History & Current State" and in
> `CHANGELOG.md` + `docs/engineering-roadmap.md`. The 2026-03-14 feature-by-feature comparison
> lives in `docs/whatsapp-feature-gap-analysis.md` (still accurate; this file sequences it).

---

## How to read this

This roadmap maps Muhabbet onto **WhatsApp's core surface** and sequences the gaps into three
tiers. Each tier is a set of **small, iteration-sized tasks** with a **file path** and a
**verifiable DONE**. Every status claim below is grounded in code (file/class cited).

- **Tier 1 — Core-messaging hardening & trust.** Make the everyday 1:1 + group experience
  correct and trustworthy: land E2E as a canary, get delivery/read receipts provably right,
  make media robust, finish groups. *This is the bar for a credible public launch.*
- **Tier 2 — Real-time & reach.** Calls that actually carry audio/video, presence/typing polish,
  status/stories completeness, multi-device.
- **Tier 3 — Advanced parity.** Communities, disappearing/edit/reactions polish, key verification
  UI, backups at scale, sender-key group E2E.

---

## Feature inventory vs WhatsApp (code-grounded)

Status: **Done** (works end-to-end), **Partial** (present but a real gap remains), **Missing**.

| WhatsApp surface | Status | Evidence (file / class) |
|---|---|---|
| 1:1 messaging (real-time) | **Done** | `MessageService.sendMessage`, WS `WsMessage.SendMessage`/`NewMessage`, `WsClient` |
| Group messaging | **Done (1:1-grade)** | `GroupService.kt`, `WsMessage.GroupMember*`, announcement mode in `MessageService` (`MSG_ANNOUNCEMENT_ONLY`) |
| Delivery / read receipts | **Done** | `MessageService.resolveDeliveryStatuses` (sender-aggregate min, recipient own-row), `updateStatus`, `markConversationRead`; `MessageStatus{SENDING,SENT,DELIVERED,READ}` in `shared/.../Models.kt` |
| Typing indicators | **Done** | `WsMessage.TypingIndicator` / `PresenceUpdate`, presence module (Redis) |
| Presence (online / last seen) | **Done** | Redis TTL presence, `WsMessage.PresenceUpdate.lastSeenAt`, `V2__add_last_seen_at.sql` |
| Media — image / video / docs | **Done** | `MediaService.kt`, MinIO adapter, `ContentType{IMAGE,VIDEO,DOCUMENT}`, `V3__add_media_duration.sql` |
| Voice messages (+ transcription) | **Done** | `ContentType.VOICE`, `SpeechTranscriber` expect/actual, VoiceBubble |
| Reactions | **Done** | `ReactionService.kt`, `WsMessage.MessageReaction`, `V9__add_reactions.sql` |
| Reply / quote | **Done** | `SendMessage.replyToId`, `Message.replyToId` |
| Forwarding | **Done** | `SendMessage.forwardedFrom`, `V13__add_forwarded_from.sql` |
| Edit / delete | **Done** | `MessageService.editMessage` (15-min window), `deleteMessage` (soft), `WsMessage.MessageEdited/Deleted`, `V4__add_edited_at.sql` |
| Search | **Done** | message search endpoint; UI search **Partial** (see Tier 1.5 — `HomeShellScreen.kt` L98 dead button) |
| Starred messages | **Done** | `V5__add_starred_messages.sql` |
| Disappearing messages | **Done** | `DisappearingMessageService.kt`, `MessageService` `expiresAt`, `V6__add_disappearing_messages.sql` |
| View-once | **Done** | `MessageService.markViewOnceViewed`, `SendMessage.viewOnce` |
| Status / stories | **Done** | `StatusService.kt`, `V8__add_statuses.sql` |
| Polls | **Done** | `PollService.kt`, `ContentType.POLL`, `V7__add_polls.sql` |
| Location / contact share | **Done** | `ContentType.LOCATION/CONTACT` |
| Channels / broadcast lists | **Done** | `ChannelService.kt`, `BroadcastListService.kt`, `ChannelAnalyticsService.kt` |
| Communities | **Done (backend)** | `CommunityService.kt`; UI **Partial** (`CommunityDetailScreen.kt` L167 "add group" dead button) |
| Group invite links / join requests | **Done** | `InviteLinkService.kt`, `JoinRequestService.kt`, `GroupInviteLink` model |
| Push notifications | **Done (Android)** / **Missing (iOS)** | FCM `FcmPushNotificationAdapter`; iOS APNs non-functional (`PushTokenProvider.ios.kt` waits on AppDelegate hook, no server APNs path) |
| Offline cache / queue | **Done** | SQLDelight `MuhabbetDatabase.sq`, `WsClient` pending-queue drain + dedup |
| Bots / channel analytics | **Done** | `BotService.kt`, `ChannelAnalyticsService.kt` |
| Moderation (BTK 5651) | **Done** | `ModerationService.kt`, `V15` |
| KVKK export / delete | **Done** | `UserDataService.kt`, `PrivacyDashboardScreen.kt` |
| **E2E encryption (1:1 text)** | **Partial — wired, flag OFF** | `MessageEncryptor.kt` + `E2EConfig.ENABLED=false` + `E2EEnvelope.kt` (PR #31). Android Signal real; **groups/media not encrypted; iOS NoOp** |
| E2E — group (sender keys) | **Missing** | no `SenderKey`/`GroupCipher` outside libsignal store; pairwise only |
| E2E — media encryption | **Missing** | blobs uploaded to MinIO in clear; `MessageEncryptor` skips non-TEXT |
| E2E — safety numbers / key verify UI | **Missing (backend ready)** | `WsMessage.SecurityKeyChanged` + `EncryptionService` keyVersion bump exist; no client UI |
| Voice / video calls | **Partial** | Android **wired to LiveKit** (`CallEngine.android.kt` connect/mute/speaker real); signaling `CallSignalingService.kt`; **iOS stub** (`CallEngine.ios.kt`); no group calls |
| Group voice/video calls | **Missing** | `WsMessage.GroupCallStarted` defined, no multi-party impl |
| Multi-device | **Partial** | `Device` model + `LoginApprovalService` (login approval), per-device keys in `encryption_keys`; **no companion-device message-sync protocol** (envelope carries `senderDeviceId` but server doesn't fan-out per device) |
| Two-step verification (PIN) | **Partial** | `TwoStepVerificationService.kt` exists; confirm wired into OTP login + UI (gap-analysis §1.3) |
| Backups (server-side, at scale) | **Partial** | `BackupService.createBackup` now produces a **real MinIO JSON archive** + presigned URL + counts (PR #31); **no restore/download endpoint, no media blobs** |
| Chat archive / mute UI | **Partial** | `Conversation.isArchived/isMuted` flags exist; mute-duration picker UI gap (gap-analysis §1.4) |
| Web / desktop client | **Missing** | no web surface (browser validation N/A — see §"Browser note") |

---

## Tier 1 — Core-messaging hardening & trust  *(= public-launch bar; P0/P1 in TODO.md)*

Small, sequenced, iteration-sized. Each closes a Tier-1 gap above.

### 1.1 Land E2E (1:1 text) as a canary — **IN FLIGHT (PR #31)**
- **Files:** `mobile/.../crypto/E2EConfig.kt` (flag, default OFF), `MessageEncryptor.kt`,
  `WsClient.kt` (send/receive chokepoints), `shared/.../port/E2EEnvelope.kt`,
  `docs/e2e-rollout-runbook.md` (new).
- **Tasks (iter):**
  1. Keep flag OFF; merge PR #31 as the wired-but-dark canary. *(done in branch)*
  2. Two-device Gate-1 test: confirm **ciphertext at rest in `messages.content`** + decrypt on peer
     + plaintext fallback when no session. Runbook §3 Gate 1.
  3. Wire the flag to a `BuildConfig`/remote toggle so broad rollout has a **no-redeploy kill-switch**
     (runbook §4) before flipping default ON.
- **DONE =** `grep -rn '\.encrypt(' mobile/composeApp/src/commonMain` shows the send path encrypting;
  a manual two-device test confirms the DB stores an `mhbt-e2e-1` envelope, not readable text;
  rollback path documented and exercised.

### 1.2 Delivery/read-receipt correctness (provable)
- **Files:** `MessageService.resolveDeliveryStatuses`/`updateStatus`/`markConversationRead`,
  `DeliveryStatusTest.kt`.
- **Why:** aggregation is subtle (sender sees min across recipients; group read = all-read). Lock it
  with explicit group-scenario tests so receipts never regress under E2E re-wiring.
- **DONE =** `DeliveryStatusTest` covers: 1:1 SENT→DELIVERED→READ; group partial-read stays
  DELIVERED; group all-read → READ; recipient sees own row only. Tests green in CI.

### 1.3 Media robustness
- **Files:** `MediaService.kt`, `MediaUploadHelper.kt`, nginx MinIO proxy.
- **Tasks:** verify compression on every upload path; presigned-URL endpoint rewrite (internal→public)
  holds under load; thumbnail generation for video. (Media **encryption** is Tier 1.4.)
- **DONE =** image/video/voice/doc upload+download round-trip on a real device; presigned URLs resolve
  through nginx; k6 media path has no 5xx (ties to TODO P1 load test).

### 1.4 Media encryption (close the E2E media gap)
- **Files:** `MessageEncryptor.kt` (extend beyond TEXT), `MediaUploadHelper.kt`, new media-key in body.
- **Why:** with 1.1 landed, blobs still upload in clear — partial privacy. Encrypt blob with a random
  key, upload ciphertext, ship the key inside the (already-encrypted) message body.
- **DONE =** an image sent with the flag ON is unreadable in MinIO without the per-message key;
  recipient renders it; flag OFF path unchanged.

### 1.5 Finish group & community surfaces (kill dead buttons)
- **Files (6 dead `onClick = { /* TODO */ }`):** `HomeShellScreen.kt` L98, `MessageBubble.kt` L91,
  `WallpaperPickerScreen.kt` L191, `InviteLinkSheet.kt` L149, `CommunityDetailScreen.kt` L167,
  `BroadcastListScreen.kt` L191.
- **DONE =** each implemented or hidden; no `/* TODO */` remains in those onClick blocks.

### 1.6 Trust hardening (must precede broad E2E)
- Release **keystore + signed AAB** (`mobile/.../build.gradle.kts` signingConfig L132-160).
- **Security pen-test** pass (record in `docs/qa/02-security.md`).
- **Sentry DSN** in prod (`infra/docker-compose.prod.yml` → `.env.prod`) — needed to *see* E2E
  failures during canary.
- **DONE =** signed `.aab` verified with `apksigner`; ZAP/Burp baseline triaged; test exception
  visible in Sentry. (These are TODO P0/P1.)

---

## Tier 2 — Real-time & reach

### 2.1 iOS calls — bridge LiveKit Swift SDK
- `mobile/.../iosMain/.../platform/CallEngine.ios.kt` (stub today). **DONE =** iOS joins a LiveKit
  room and carries audio on a TestFlight build.
### 2.2 Presence / typing polish
- Debounce typing, last-seen privacy honoring `VisibilityLevel`, "online" accuracy under reconnect.
  **DONE =** typing stops within 5s of inactivity; last-seen respects `NOBODY/CONTACTS`.
### 2.3 Status / stories completeness
- Privacy (who-can-see), reply-to-status, view receipts. `StatusService.kt`.
### 2.4 Multi-device sync protocol
- Companion device receives history + new messages; per-device E2E session selection
  (`E2EEnvelope.senderDeviceId` already on the wire; server must fan-out per device).
  **DONE =** a second logged-in device sees new + recent messages.
### 2.5 iOS catch-up: APNs, Firebase-auth decision, libsignal bridge
- `PushTokenProvider.ios.kt`, `FirebasePhoneAuth.ios.kt`, `PlatformModule.ios.kt` (NoOp E2E).
  **DONE =** iOS push delivers; iOS login works via chosen path (decision in `docs/decisions.md`);
  iOS establishes a Signal session.

---

## Tier 3 — Advanced parity

### 3.1 Group E2E (sender keys)
- Sender-key fan-out so group messages are E2E without N pairwise encrypts.
  **DONE =** a group message is ciphertext at rest; all members decrypt.
### 3.2 Key verification UI (safety numbers)
- Surface `WsMessage.SecurityKeyChanged`; show a safety-number / QR compare screen.
### 3.3 Backups at scale + restore
- `BackupService` archives metadata+text today; add **media blobs** + a **restore/download endpoint**;
  chunk large accounts; schedule + encrypt the archive.
  **DONE =** a restore reproduces conversations on a fresh install.
### 3.4 Group voice/video calls
- Multi-party LiveKit rooms + participant grid (`WsMessage.GroupCallStarted` defined).
### 3.5 Web / desktop client
- QR device-linking + message sync (depends on 2.4). Power-user demand; also the only surface that
  would make browser validation meaningful (see below).

---

## Browser note

**Muhabbet has no web/admin client today** (mobile = Compose Multiplatform; backend = headless REST +
WebSocket returning `401` at root by design). **Browser validation is N/A** for this work. A web
client is **Tier 3.5**; when it lands, browser validation becomes the verification path for it.

---

## Suggestions (toward WhatsApp-grade)

**Encryption / privacy**
- Wire the E2E flag to **remote config** before broad rollout (no-redeploy kill-switch).
- **Sender-key group E2E** (Tier 3.1) — the biggest correctness gap vs WhatsApp groups.
- **Media encryption** (Tier 1.4) — privacy-first brand can't ship plaintext blobs at scale.
- **Safety-number verification UI** — backend already emits key-change events; just needs UI.

**Multi-device & protocol**
- A real **multi-device sync protocol** (Tier 2.4): per-device sessions, history sync, dedup across
  devices. `E2EEnvelope.senderDeviceId` is the seam; the server fan-out is the missing half.

**Observability / SLOs**
- Define SLOs: message send→ack p95 < 300ms, WS reconnect < 5s, push delivery < 10s, crash-free
  ≥ 99.5%. Prometheus/Grafana dashboards exist (`infra/monitoring/`); add alerts.
- **Set the Sentry DSN** (currently empty in prod) — you are blind to incidents at launch.

**Load / scale targets**
- Run the existing k6 scripts (`infra/k6/`, `infra/load-tests/`) to a documented ceiling: target
  10k concurrent WS on the single 8GB host; record p95 + WS-concurrency ceiling; the Redis pub/sub
  broadcaster (`RedisMessageBroadcaster`) is the horizontal-scale seam if the ceiling is too low.

**Store readiness / professionalization**
- Release keystore + signed AAB + IARC rating + store listing (TR+EN) — Tier 1.6 / TODO P0.
- Security pen-test before exposing a messaging app publicly.
- Resolve the **Sentry-on-Spring-Boot-4 auto-config exclusion** debt (CLAUDE.md) once a compatible
  Sentry ships.

---

## History & Current State

**Backend: LIVE and healthy** at `https://muhabbet-api.rollingcatsoftware.com` (Hetzner VPS, Docker +
Traefik/nginx + Let's Encrypt; PostgreSQL 16, Redis 7, MinIO). Flyway V1–V17 applied. Root returns
`401` by design. **Do not deploy from this roadmap work** — E2E ships dark via PR #31.

The codebase is broad (24 MVP features + 6 engineering phases shipped). A 2026-06-04 code audit
reframed several prior "DONE" labels — the most important being that **E2E was infrastructure-only
(plaintext on the wire)** until PR #31 wired the path (still OFF). The other launch blockers (signed
AAB, pen-test, Sentry DSN, real backup) are tracked in `TODO.md`; the backup job is now real (PR #31).

**Stack:** Kotlin 2.3.20 / Spring Boot 4.0.5 / Java 21 / PostgreSQL 16 / Redis 7 / MinIO. Mobile:
Compose Multiplatform (Android full; iOS partial — calls/E2E/Firebase-auth stubbed, APNs pending).
CI/CD on self-hosted `hetzner-cx43`.

### Turkish market context
WhatsApp ≈ 60M users in Turkey; the 2021 privacy backlash is the opening. **KVKK + real E2E is the
differentiator** — which is exactly why Tier 1.1 (land E2E for real) is the hinge of this roadmap.
Voice calls are culturally non-negotiable (Android live; iOS = Tier 2.1).

### Architecture reference
```
muhabbet/
├── backend/   → Spring Boot 4.0.5 + Kotlin (modular monolith, hexagonal)
│               modules: auth · messaging · media · moderation · shared(config/security/web)
├── shared/    → KMP (model · dto · protocol/WsMessage · validation · port/EncryptionPort+E2EEnvelope)
├── mobile/    → Compose Multiplatform (androidMain full · iosMain partial: stubs noted)
└── infra/     → docker-compose.prod.yml · nginx · monitoring (Prometheus+Grafana) · k6 · scripts
```
