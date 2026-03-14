# WhatsApp Feature Gap Analysis — Launch Readiness

> **Date:** 2026-03-14
> **Goal:** Identify what Muhabbet needs to be a credible WhatsApp alternative for Turkey's 85M users
> **Scope:** Feature-by-feature comparison against WhatsApp 2026, prioritized by launch readiness

## Context
Muhabbet has shipped 33 features across a modular monolith backend (Spring Boot 4.0.2), Compose Multiplatform mobile app, and shared KMP module. The core messaging experience is solid. However, several critical gaps remain before Turkish users would consider switching from WhatsApp.

---

## TIER 1: LAUNCH BLOCKERS

These features are **non-negotiable** — users will uninstall without them.

### 1.1 Voice & Video Calls — Actually Working

| Aspect | WhatsApp | Muhabbet |
|--------|----------|---------|
| 1:1 voice calls | Full WebRTC, earpiece/speaker/BT | **Infrastructure only** — signaling, UI screens, LiveKit room adapter exist, but LiveKit SDK not wired to `CallEngine` |
| 1:1 video calls | Full WebRTC with camera toggle | Same — no actual media stream |
| Group voice calls | Up to 32 participants | Not implemented |
| Group video calls | Up to 32 participants with grid | Not implemented |
| Call quality | Adaptive bitrate, OPUS codec | N/A |
| Audio routing | Earpiece/speaker/Bluetooth/wired | Not implemented |
| Call UI | In-call controls, minimize to PiP | Screens exist but non-functional |
| Screen sharing | In video calls | Not implemented |

**What exists:** `CallSignalingService`, `CallSession` domain model, `call.initiate`/`call.answer`/`call.ice`/`call.end` WS messages, `IncomingCallScreen`, `ActiveCallScreen`, `CallHistoryScreen`, LiveKit `RoomAdapter` with `@ConditionalOnProperty`

**What's missing:** Wire LiveKit Android SDK → `CallEngine` actual impl, audio routing (earpiece/speaker/BT), PiP mode, group call support, call quality adaptation

**Complexity:** XL | **Layers:** Backend (M), Mobile (XL)

> **This is the #1 blocker.** Voice calls are the most-used WhatsApp feature in Turkey. Users will uninstall instantly without them.

---

### 1.2 End-to-End Encryption — Active on Messages

| Aspect | WhatsApp | Muhabbet |
|--------|----------|---------|
| 1:1 message encryption | Signal Protocol (always on) | **Infrastructure only** — key exchange endpoints, `PersistentSignalProtocolStore`, `SignalKeyManager` exist, but messages flow in **plaintext** |
| Group encryption | Sender Keys protocol | Not implemented |
| Media encryption | Encrypted before upload | Not implemented |
| Key verification | QR code + safety number | Not implemented |
| Encryption indicator | Lock icon on every message | Not implemented |

**What exists:** `EncryptionController` (PUT/POST/GET key endpoints), `encryption_keys` + `one_time_pre_keys` tables, `EncryptionPort` interface, `SignalKeyManager` (libsignal-android), `PersistentSignalProtocolStore` (EncryptedSharedPreferences), `E2ESetupService` for key registration on login

**What's missing:** Activate encryption in message send/receive path (currently `NoOpKeyManager` for MVP), encrypt media before upload, group Sender Keys, verification UI (QR + safety numbers), lock icon indicator

**Complexity:** XL | **Layers:** Backend (L), Mobile (XL), Shared (L)

> **Core brand promise.** You cannot market as "privacy-first" while messages transit in plaintext. This undermines the entire value proposition.

---

### 1.3 Two-Step Verification (2FA)

| Aspect | WhatsApp | Muhabbet |
|--------|----------|---------|
| PIN-based 2FA | 6-digit PIN on re-registration | **Not implemented** |
| Email recovery | Email backup for PIN reset | Not implemented |
| Periodic PIN prompt | Asks periodically to prevent forgetting | Not implemented |

**What's missing:** `two_step_pin` column (hashed) on `users` table, PIN verification during OTP login, email recovery flow, periodic prompt logic

**Complexity:** M | **Layers:** Backend (M), Mobile (S)

---

### 1.4 Chat Archive & Mute UI

| Aspect | WhatsApp | Muhabbet |
|--------|----------|---------|
| Archive chats | Hide from main list, stays archived | **Not implemented** — no `archived` flag |
| Mute conversations | 8 hours / 1 week / Always | **Partial** — `mutedUntil` field exists on `conversation_members` but **no UI** to set it |
| Custom notifications | Per-chat notification tone | Not implemented |

**What exists:** `mutedUntil: Instant?` on `ConversationMember` domain model

**What's missing:** `archived: Boolean` flag, archive/unarchive endpoints, mute duration picker UI, "Archived Chats" section in conversation list

**Complexity:** S | **Layers:** Backend (S), Mobile (M)

---

### 1.5 Chat Wallpapers

| Aspect | WhatsApp | Muhabbet |
|--------|----------|---------|
| Default wallpapers | Built-in gallery | **Not implemented** |
| Custom wallpaper | Set photo as background | Not implemented |
| Per-chat wallpaper | Different per conversation | Not implemented |
| Dark mode wallpapers | Separate set for dark mode | Not implemented |

**What's missing:** Wallpaper assets, selection UI, per-chat wallpaper preference (local storage), wallpaper rendering behind chat bubbles

**Complexity:** M | **Layers:** Mobile only

---

### 1.6 Video Messages (Instant Video)

| Aspect | WhatsApp | Muhabbet |
|--------|----------|---------|
| Instant video | Record short circular video messages | **Not implemented** — voice messages exist but not video |
| Video compression | Auto-compress before send | Not for video capture |

**What exists:** Voice message recording + playback, `CameraPicker` expect/actual, `ContentType.VIDEO` in domain model, media upload endpoint

**What's missing:** Video capture UI (hold-to-record circular), video compression pipeline (H.264 720p), circular video bubble component, inline playback

**Complexity:** M | **Layers:** Mobile (M), Backend (S — reuse audio upload)

---

## TIER 2: HIGH PRIORITY (Expected within first weeks)

### 2.1 Multi-Device Support

| Aspect | WhatsApp | Muhabbet |
|--------|----------|---------|
| Linked devices | Up to 4 simultaneously | **Not implemented** — `devices` table tracks push tokens only, no message sync |
| WhatsApp Web | Browser client with QR login | Not implemented |
| Desktop app | Native Windows/macOS | Not implemented |
| Primary device independence | Works without phone | Not implemented |

**What exists:** `devices` table with `userId`, `platform`, `pushToken`, `isPrimary`

**What's missing:** Multi-device message fan-out (deliver to ALL linked devices), QR code device linking protocol, web client (React), desktop client (Electron/Compose Desktop), device-to-device E2E key sync

**Complexity:** XXL | **Layers:** All (Backend, Mobile, Web, Desktop)

---

### 2.2 Advanced Group Features

| Aspect | WhatsApp | Muhabbet |
|--------|----------|---------|
| Group size | Up to 1,024 members | 256 members |
| Admin approval | Join requests need approval | Not implemented |
| Group invite links | Shareable join link | Not implemented |
| QR code join | Scan to join | Not implemented |
| Group events | Schedule events | Not implemented |
| Announcement mode | Only admins send | Not implemented |
| Previous participants | See who left | Not implemented |

**What exists:** Full group CRUD, roles (OWNER/ADMIN/MEMBER), add/remove members, `GroupController`, `GroupInfoScreen`

**What's missing:** Invite link generation + validation, join request queue, announcement mode flag, events data model, participant history

**Complexity:** L | **Layers:** Backend (L), Mobile (M)

---

### 2.3 Communities (Meta-Groups)

| Aspect | WhatsApp | Muhabbet |
|--------|----------|---------|
| Community structure | Parent group with sub-groups | **Not implemented** |
| Announcement group | Broadcast to all sub-groups | Not implemented |
| Community admin tools | Manage multiple groups | Not implemented |
| Community directory | Browse sub-groups | Not implemented |

**What's missing:** Entire data model (`communities` table, `community_groups` join table), navigation structure, community admin UI, announcement broadcasting

**Complexity:** XL | **Layers:** Backend (L), Mobile (XL)

---

### 2.4 Video Compression & HD Photo Toggle

| Aspect | WhatsApp | Muhabbet |
|--------|----------|---------|
| Video compression | Auto-compress before send | **Not implemented for video** |
| HD photo option | Send in original quality | Not implemented — always compresses to 1280px/80% |
| Photo quality setting | Standard/HD preference | Not implemented |

**What exists:** `ImageCompressor` (1280px/80%), `MediaUploadHelper` compression pipeline for images

**What's missing:** Video compression expect/actual (H.264 720p), HD toggle in attach menu, quality preference in settings

**Complexity:** L | **Layers:** Mobile (L), Backend (S)

---

### 2.5 Rich Notifications

| Aspect | WhatsApp | Muhabbet |
|--------|----------|---------|
| Notification grouping | By conversation | Unknown (basic FCM) |
| Reply from notification | Inline reply action | Not implemented |
| Mark as read | Action button | Not implemented |
| React from notification | Quick reaction | Not implemented |
| Media preview | Show thumbnail | Not implemented |

**What exists:** `FcmPushNotificationAdapter`, push token registration, basic notification delivery

**What's missing:** `NotificationCompat.MessagingStyle`, `RemoteInput` for inline reply, action buttons, conversation grouping, media BigPictureStyle

**Complexity:** M | **Layers:** Mobile (M, platform-specific)

---

## TIER 3: MEDIUM PRIORITY (Expected within first months)

### 3.1 Contact Card Sharing & Usernames

| Feature | Status |
|---------|--------|
| Share contact as message | `ContentType.CONTACT` exists, no UI |
| Username search (@username) | Not implemented |
| Multiple profile photos | Not implemented |

**Complexity:** M | **Layers:** Both

---

### 3.2 App Lock / Chat Lock

| Feature | Status |
|---------|--------|
| Biometric/PIN app lock | Not implemented |
| Lock specific chats | Not implemented |
| Hidden notification content for locked chats | Not implemented |

**Complexity:** M | **Layers:** Mobile only

---

### 3.3 Rich Text Formatting

| Feature | Status |
|---------|--------|
| Bold/Italic/Strikethrough | `parseFormattedText()` exists in `TextUtils.kt` (partial) |
| Bulleted/Numbered lists | Not implemented |
| Block quotes | Not implemented |

**Complexity:** S | **Layers:** Mobile (S)

---

### 3.4 Media Management

| Feature | Status |
|---------|--------|
| Save to gallery | Not implemented |
| Share to other apps | Not implemented |
| Auto-download settings (WiFi/mobile data) | Not implemented |
| Storage management / cache clear | Not implemented (storage stats endpoint exists) |

**Complexity:** M | **Layers:** Mobile (M)

---

### 3.5 Privacy Controls Completion

| Feature | Status |
|---------|--------|
| Read receipts toggle | Privacy dashboard exists, may not control WS ack behavior |
| Online status privacy | Not implemented |
| About privacy | Not implemented |
| Last seen / Profile photo privacy | **Implemented** in `PrivacyDashboardScreen` |

**Complexity:** S | **Layers:** Backend (S), Mobile (S)

---

## TIER 4: LOWER PRIORITY (Competitive parity, post-launch)

| Feature | Complexity | Notes |
|---------|-----------|-------|
| WhatsApp Business profiles | L | Bot platform provides partial coverage |
| AI features (chat summarization, AI stickers) | L | Differentiator opportunity for Turkish market |
| View once (single-view photo/video) | S | Disappearing exists, need single-view variant |
| Broadcast lists (1-to-many DM) | M | Different from Channels — recipient sees as DM |
| Message scheduling | S | Send later |
| Chat export as text file | S | Backup exists, need shareable format |
| Keep messages in disappearing chats | S | Save specific messages from TTL |
| Animated avatars | L | |
| Dual SIM support | M | |
| Proxy server support | M | For restricted networks |
| In-app browser | S | Links open external browser currently |
| Database sharding | XL | Single PostgreSQL OK until 100K+ users |
| Media CDN | L | MinIO single instance OK for MVP |
| Message queue (Kafka/SQS) | L | Spring ApplicationEvent OK for monolith |

---

## LAUNCH READINESS SCORECARD

| Category | Score | Blocker? |
|----------|-------|----------|
| 1:1 Messaging | 9/10 | No |
| Group Messaging | 7/10 | No |
| Voice/Video Calls | 2/10 | **YES** |
| E2E Encryption | 2/10 | **YES** |
| Media Sharing | 8/10 | No |
| Offline Support | 8/10 | No |
| Privacy Controls | 7/10 | No |
| Multi-Device | 0/10 | Soft blocker |
| UX Polish | 7/10 | No |
| Security (2FA) | 3/10 | **YES** |
| Notifications | 5/10 | Soft blocker |
| Communities | 0/10 | No |

---

## CRITICAL PATH TO LAUNCH

```
Pre-launch (must complete):
  1. Voice/Video Calls (1:1 working)      — 4-6 weeks
  2. E2E Encryption (active on messages)   — 3-4 weeks
  3. Two-Step Verification                 — 1 week
  4. Archive + Mute UI                     — 1 week
  5. Chat Wallpapers                       — 1 week
  6. Rich Notifications                    — 1-2 weeks
  7. Video Messages                        — 1-2 weeks
  ─────────────────────────────────────────────────────
  Total for credible launch:                ~12-16 weeks

Post-launch priority:
  8. Multi-device (Web + Desktop)          — 8-12 weeks
  9. Advanced Group Features               — 3-4 weeks
  10. Communities                          — 4-6 weeks
  11. Video Compression + HD Toggle        — 2 weeks
  12. App Lock / Chat Lock                 — 1-2 weeks
  13. Save to Gallery / Share              — 1 week
  14. Business Features                    — 6-8 weeks
```

---

## WHAT MUHABBET HAS THAT WHATSAPP DOESN'T

These are competitive advantages to highlight:

| Feature | Details |
|---------|---------|
| **KVKK Privacy Dashboard** | Full data export, account deletion, visibility controls — WhatsApp doesn't offer a dedicated privacy control center |
| **Bot Platform** | Built-in bot creation with API tokens and webhooks — WhatsApp requires Business API subscription |
| **Channel Analytics** | Daily stats, subscriber tracking for channel owners — WhatsApp Channels have limited analytics |
| **Voice Transcription** | On-device Turkish ASR (SpeechRecognizer) — WhatsApp doesn't transcribe voice messages |
| **Turkish-first Localization** | Native Turkish UI with proper localization — WhatsApp's Turkish translation is sometimes awkward |
| **Open Sticker/GIF Integration** | Built-in picker — WhatsApp requires downloading sticker packs |
| **Message Backup with Encryption** | Backup system with pre-signed URLs — WhatsApp backups on Google Drive are unencrypted by default |

---

## METHODOLOGY

This analysis was built by examining:
- All 23 backend REST controllers (100+ endpoints)
- All 28 database tables (Flyway migrations V1–V15)
- All 21 mobile screens and UI components
- Domain models: `Message`, `Conversation`, `CallSession`, `Device`
- Shared KMP module: protocol, DTOs, encryption port
- Infrastructure: Docker Compose, nginx, application.yml
- Cross-referenced against WhatsApp's 2026 feature set
