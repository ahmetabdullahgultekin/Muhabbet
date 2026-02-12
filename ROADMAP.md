# Muhabbet — Product Roadmap & Implementation Plan

> **Purpose**: This document is a self-contained, exhaustive implementation plan. Each task includes context, affected files, implementation steps, and verification criteria — designed to be handed to Claude Code for autonomous execution.

---

## Table of Contents

1. [Priority 0 — Critical Bug Fixes](#priority-0--critical-bug-fixes)
2. [Priority 1 — Profile Photo Upload](#priority-1--profile-photo-upload)
3. [Priority 2 — Google Play Store Launch](#priority-2--google-play-store-launch)
4. [Priority 3 — Polish & Parity Essentials](#priority-3--polish--parity-essentials)
5. [Priority 4 — Daily-Use Features](#priority-4--daily-use-features)
6. [Priority 5 — Voice/Video Calls](#priority-5--voicevideo-calls)
7. [Priority 6 — Trust & Platform](#priority-6--trust--platform)
8. [Priority 7 — Engagement & Growth](#priority-7--engagement--growth)
9. [Feature Research Summary](#feature-research-summary)
10. [Architecture Reference](#architecture-reference)

---

## Priority 0 — Critical Bug Fixes

### BUG-01: Push Notifications Not Working

**Problem**: Push notifications are implemented but disabled in production. The `NoOpPushNotificationAdapter` is active instead of `FcmPushNotificationAdapter` because environment variables aren't set.

**Root Cause**: `FCM_ENABLED` defaults to `false` in `application.yml`. The Firebase credentials path is not configured on the GCP VM.

**Fix (Infrastructure — No Code Changes Needed)**:

1. Ensure `infra/firebase-adminsdk.json` exists on the GCP VM at `/home/ahabg/Muhabbet/infra/firebase-adminsdk.json`
2. In `infra/docker-compose.prod.yml`, add these environment variables to the `backend` service:
   ```yaml
   FIREBASE_ENABLED: "true"
   FIREBASE_CREDENTIALS_PATH: "/app/firebase-adminsdk.json"
   FCM_ENABLED: "true"
   FCM_CREDENTIALS_PATH: "/app/firebase-adminsdk.json"
   ```
3. Ensure the Dockerfile or volume mount copies `firebase-adminsdk.json` into the container at `/app/`
4. Restart backend: `docker compose -f docker-compose.prod.yml up -d --build backend`

**Verification**: Send a message to a user whose app is in the background. They should receive an Android notification. Check backend logs for `FcmPushNotificationAdapter` (not `NoOpPushNotificationAdapter`).

**Files**:
- `infra/docker-compose.prod.yml` — add env vars
- `infra/Dockerfile` (or backend Dockerfile) — ensure firebase credentials are copied
- `backend/src/main/resources/application.yml` — reference (no change needed, reads from env)
- `backend/src/main/kotlin/com/muhabbet/shared/config/FirebaseConfig.kt` — reference
- `backend/src/main/kotlin/com/muhabbet/messaging/adapter/out/external/FcmPushNotificationAdapter.kt` — reference
- `backend/src/main/kotlin/com/muhabbet/messaging/adapter/out/external/NoOpPushNotificationAdapter.kt` — reference

---

### BUG-02: Message Ticks Stuck at Single Tick

**Problem**: Messages show clock (SENDING) then single tick (SENT) but never progress to double tick (DELIVERED) or blue double tick (READ).

**Root Cause**: The mobile app skips the DELIVERED acknowledgment. When receiving an incoming message, `ChatScreen.kt` immediately sends `AckMessage(status=READ)` instead of first sending `DELIVERED` then `READ`. Additionally, the DELIVERED ack should be sent when the message arrives (even if the chat is not open), and the READ ack only when the user actually sees the message.

**Fix**:

**Step 1: Send DELIVERED ack from WsClient/App level (not ChatScreen)**

The DELIVERED ack should fire at the WebSocket level whenever a `NewMessage` arrives, regardless of which screen the user is on. This ensures the sender sees double ticks even if the recipient hasn't opened that specific conversation yet.

Modify `App.kt` (or `WsClient.kt`) — add a global collector on `wsClient.incoming` that sends DELIVERED for every incoming NewMessage:

```kotlin
// In the WebSocket lifecycle (App.kt), after connecting:
wsClient.incoming.collect { msg ->
    if (msg is WsMessage.NewMessage && msg.senderId != currentUserId) {
        try {
            wsClient.send(WsMessage.AckMessage(
                messageId = msg.messageId,
                conversationId = msg.conversationId,
                status = MessageStatus.DELIVERED
            ))
        } catch (_: Exception) { }
    }
}
```

**Step 2: Send READ ack only from ChatScreen when viewing the conversation**

In `ChatScreen.kt`, the existing AckMessage sends should remain as `MessageStatus.READ` — this is correct because the user has the conversation open and is reading the messages.

- Lines ~226-234 (page load): Keep as READ — user opened the conversation
- Lines ~259-269 (incoming message while chat open): Change to READ — user is actively viewing

**Step 3: Handle StatusUpdate properly on sender side**

Verify `ChatScreen.kt` lines ~289-308 handle both DELIVERED and READ status updates:
- `StatusUpdate(status=DELIVERED)` → update specific message to DELIVERED (gray double tick)
- `StatusUpdate(status=READ)` → bulk-update all SENT/DELIVERED to READ (blue double tick)

**Files to modify**:
- `mobile/composeApp/src/commonMain/kotlin/com/muhabbet/app/App.kt` — add global DELIVERED ack collector
- `mobile/composeApp/src/commonMain/kotlin/com/muhabbet/app/ui/chat/ChatScreen.kt` — verify READ ack logic, verify StatusUpdate handling

**Verification**: Send a message from User A to User B.
- User B's app is open but on conversation list → A should see double gray tick (DELIVERED)
- User B opens the chat → A should see blue double tick (READ)
- User B's app is closed → A stays at single tick (push notification fires instead)

---

## Priority 1 — Profile Photo Upload

**Scope**: Users can upload a profile photo from Settings. Photos appear in conversation list avatars, chat headers, group member lists, and profile screens. Falls back to letter avatar when no photo.

### 1A: Backend — Accept avatarUrl in Profile Update

**What exists**: `avatar_url TEXT` column in users table, `avatarUrl: String?` in User domain model and JPA entity. Media upload endpoint exists at `POST /api/v1/media/upload`.

**What's missing**: `UpdateProfileRequest` only accepts `displayName` and `about`, not `avatarUrl`.

**Changes**:

1. **`shared/src/commonMain/kotlin/com/muhabbet/shared/dto/Dtos.kt`** — Add `avatarUrl` field:
   ```kotlin
   @Serializable
   data class UpdateProfileRequest(
       val displayName: String? = null,
       val about: String? = null,
       val avatarUrl: String? = null  // ← ADD
   )
   ```

2. **`backend/src/main/kotlin/com/muhabbet/auth/adapter/in/web/UserController.kt`** — In `updateMe()`, pass avatarUrl to the update logic. The existing `updateProfile` use case or repository must save avatarUrl to the user entity.

3. **`backend/src/main/kotlin/com/muhabbet/auth/domain/service/AuthService.kt`** (or wherever profile update logic lives) — Include `avatarUrl` in the user update.

4. **`backend/src/main/kotlin/com/muhabbet/auth/adapter/out/persistence/entity/UserJpaEntity.kt`** — Ensure `avatarUrl` is persisted (it already has the column, verify the update path writes it).

### 1B: Mobile — Photo Upload in Settings

**What exists**: `ImagePickerLauncher` (expect/actual), `MediaRepository.uploadImage()`, `AsyncImage` from Coil3.

**Changes**:

1. **`mobile/composeApp/src/commonMain/kotlin/com/muhabbet/app/data/repository/AuthRepository.kt`** — Add `avatarUrl` parameter to `updateProfile()`:
   ```kotlin
   suspend fun updateProfile(displayName: String? = null, about: String? = null, avatarUrl: String? = null)
   ```

2. **`mobile/composeApp/src/commonMain/kotlin/com/muhabbet/app/ui/settings/SettingsScreen.kt`** — Major changes:
   - Add `ImagePickerLauncher` and `MediaRepository` injection
   - Replace the letter-avatar Surface with a clickable avatar that shows:
     - `AsyncImage(model = avatarUrl)` when user has a photo
     - Letter fallback (`firstGrapheme`) when no photo
   - On click: launch image picker → upload via `mediaRepository.uploadImage()` → call `authRepository.updateProfile(avatarUrl = uploadResponse.url)` → update local state
   - Show a camera/edit icon overlay on the avatar circle

3. **`shared/src/commonMain/kotlin/com/muhabbet/shared/model/Models.kt`** — Ensure `UserProfile` has `avatarUrl: String?` field (it likely already does).

### 1C: Mobile — Display Avatars Everywhere

Replace letter-only avatars with photo-capable avatars across all screens:

1. **`ConversationListScreen.kt`** — In `ConversationItem`, check `otherParticipant?.avatarUrl`:
   - If non-null: `AsyncImage(model = avatarUrl, ...)` inside the 48dp circle
   - If null: existing `firstGrapheme()` letter avatar
   - Note: `ParticipantResponse` already has `avatarUrl` field

2. **`ChatScreen.kt`** — In the TopAppBar, add a small circular avatar (32dp) next to the conversation name. For group chats, use group avatar or group icon.

3. **`UserProfileScreen.kt`** — Replace the 96dp letter-avatar with `AsyncImage` when `profile.avatarUrl` is non-null.

4. **`GroupInfoScreen.kt`** — Member list items: show avatar photos where available.

5. **Create a reusable composable** `UserAvatar(avatarUrl: String?, displayName: String, size: Dp)` to avoid duplication:
   ```kotlin
   @Composable
   fun UserAvatar(avatarUrl: String?, displayName: String, size: Dp, modifier: Modifier = Modifier) {
       Surface(modifier = modifier.size(size).clip(CircleShape), color = MaterialTheme.colorScheme.primaryContainer) {
           if (avatarUrl != null) {
               AsyncImage(model = avatarUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
           } else {
               Box(contentAlignment = Alignment.Center) {
                   Text(firstGrapheme(displayName), style = /* scale based on size */)
               }
           }
       }
   }
   ```
   Place in `ui/components/UserAvatar.kt`.

### 1D: Localization

Add to `values/strings.xml` and `values-en/strings.xml`:
- `profile_change_photo` / "Change photo"
- `profile_uploading_photo` / "Uploading photo..."
- `profile_photo_failed` / "Photo upload failed"

**Verification**: Open Settings → tap avatar → pick photo → photo uploads → avatar updates everywhere (conversation list, chat header, profile screen). Other users see the new photo.

---

## Priority 2 — Google Play Store Launch

### 2A: App Signing & Release Build

1. **Generate upload keystore**:
   ```bash
   keytool -genkey -v -keystore muhabbet-upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias muhabbet
   ```
   Store securely. NEVER commit to git.

2. **Configure signing in `mobile/composeApp/build.gradle.kts`**:
   ```kotlin
   android {
       signingConfigs {
           create("release") {
               storeFile = file("path/to/muhabbet-upload.jks")
               storePassword = System.getenv("KEYSTORE_PASSWORD")
               keyAlias = "muhabbet"
               keyPassword = System.getenv("KEY_PASSWORD")
           }
       }
       buildTypes {
           release {
               isMinifyEnabled = true
               isShrinkResources = true
               proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
               signingConfig = signingConfigs.getByName("release")
           }
       }
   }
   ```

3. **Create `proguard-rules.pro`** with rules for:
   - Ktor client
   - kotlinx.serialization
   - Compose
   - Coil
   - Firebase

4. **Build release AAB**: `./gradlew :mobile:composeApp:bundleRelease`

5. **Enable Google Play App Signing** (recommended): Upload the signing key to Google Play Console, use upload key for signing locally.

### 2B: Store Listing Preparation

1. **App Icon**:
   - Design app icon (512x512 PNG for store, adaptive icon for Android)
   - Place in `mobile/composeApp/src/androidMain/res/mipmap-*` directories
   - Follow Material Design adaptive icon guidelines

2. **Feature Graphic**: 1024x500 PNG banner for Play Store header

3. **Screenshots**: Minimum 2, recommended 8:
   - Conversation list (with messages)
   - Chat screen (with text + image messages)
   - Group chat
   - Voice message recording
   - Profile screen
   - Settings (dark mode)
   - New conversation (contact list)
   - Login/OTP screen

4. **Store Description**:
   - Short description (80 chars): "Yerli ve gizlilik odaklı mesajlaşma uygulaması" / "Domestic privacy-first messaging app"
   - Full description in Turkish and English
   - Feature highlights, privacy emphasis, KVKK compliance

5. **Privacy Policy**: Required by Google Play. Host on a public URL. Must cover:
   - Data collected (phone number, messages, contacts hash)
   - Data storage (Turkey-based servers, KVKK compliance)
   - Third-party services (Firebase, GCP)
   - User rights (data deletion, export)

6. **Content Rating**: Complete the IARC questionnaire in Play Console

### 2C: Branding Assets

1. **App name**: "Muhabbet" (already set)
2. **Package name**: `com.muhabbet.app` (already set — cannot change after publishing)
3. **Version management** in `build.gradle.kts`:
   ```kotlin
   android {
       defaultConfig {
           versionCode = 1        // Increment for each upload
           versionName = "1.0.0"  // Semantic versioning
       }
   }
   ```
4. **App category**: Communication → Messaging
5. **Target audience**: 13+ (messaging app)

### 2D: Pre-Launch Checklist

- [ ] Remove `OTP_MOCK_ENABLED=true` from production (or ensure Netgsm is active)
- [ ] Verify all API endpoints work on production server
- [ ] Test fresh install flow (OTP → contacts → first message)
- [ ] Test on multiple Android versions (API 24+)
- [ ] Test on multiple screen sizes
- [ ] Verify ProGuard doesn't break serialization
- [ ] Crash-free rate testing (open/close app repeatedly, send many messages, switch networks)
- [ ] Accessibility: content descriptions on icons, sufficient contrast ratios

---

## Priority 3 — Polish & Parity Essentials

These features remove the "unfinished app" feel. They're quick wins with outsized user impact.

### FEAT-01: Reply/Quote Messages (1-2 days)

**What**: Long-press a message → "Reply" option → quoted message preview appears above input → send reply with quote bubble.

**Backend**:
- `shared/src/commonMain/kotlin/com/muhabbet/shared/model/Models.kt` — `Message` already has `replyToId: String?`
- `shared/src/commonMain/kotlin/com/muhabbet/shared/protocol/WsMessage.kt` — `SendMessage` already has `replyToId: String?`
- Backend already saves `replyToId` in the messages table. **No backend changes needed.**

**Mobile changes**:
1. **`ChatScreen.kt`** — Add reply state:
   ```kotlin
   var replyingTo by remember { mutableStateOf<Message?>(null) }
   ```
   - Long-press message → show context menu (Reply, Edit, Delete, Forward)
   - "Reply" sets `replyingTo = message`
   - Show reply preview bar above text input (quoted message snippet + X to cancel)
   - On send: include `replyToId = replyingTo?.id` in SendMessage
   - Clear `replyingTo` after send

2. **`ChatScreen.kt` (message bubble)** — When `message.replyToId != null`:
   - Find the referenced message in the messages list
   - Show a small quoted bubble above the message content:
     ```
     ┌─ Replied to: <sender name> ──────┐
     │ <original message preview>        │
     └──────────────────────────────────┘
     <actual reply message>
     ```
   - Tap the quote to scroll to the original message

3. **`MessageResponse` from REST API** — When loading message history, ensure `replyToId` is included. The referenced message content should be available (either preloaded or the client finds it in the loaded messages list).

**Localization**: `reply` / "Yanıtla", `replying_to` / "Yanıtlanıyor"

---

### FEAT-02: Message Forwarding (1-2 days)

**What**: Long-press message → "Forward" → pick conversation(s) → send copy with "Forwarded" label.

**Backend**:
- Add `isForwarded: Boolean = false` to `Message` model and `SendMessage` WS type
- Add `is_forwarded BOOLEAN DEFAULT FALSE` column via migration `V5__add_forwarded_flag.sql`
- Save the flag when message arrives

**Mobile**:
1. **`ChatScreen.kt`** — Long-press context menu: add "Forward" option
2. On forward: navigate to a conversation picker (reuse NewConversationScreen or create a simple picker)
3. Send the message content to selected conversation(s) with `isForwarded = true`
4. Show "Forwarded ↗" label on forwarded message bubbles

**Localization**: `forward` / "İlet", `forwarded` / "İletildi"

---

### FEAT-03: Starred/Saved Messages (1-2 days)

**What**: Long-press message → "Star" → starred messages accessible from Settings/menu.

**Backend**:
- New migration: `V5__add_starred.sql` — `CREATE TABLE starred_messages (user_id UUID, message_id UUID, starred_at TIMESTAMPTZ, PRIMARY KEY(user_id, message_id))`
- `POST /api/v1/messages/{id}/star` — star/unstar toggle
- `GET /api/v1/messages/starred` — list starred messages with cursor pagination

**Mobile**:
1. Long-press context menu: "Star" option (toggle)
2. Star icon on starred message bubbles
3. New `StarredMessagesScreen` accessible from Settings
4. Shows all starred messages grouped by conversation

**Localization**: `star_message` / "Yıldızla", `unstar_message` / "Yıldızı kaldır", `starred_messages` / "Yıldızlı mesajlar"

---

### FEAT-04: Link Previews (3-5 days)

**What**: When a message contains a URL, fetch Open Graph metadata and display a preview card (title, description, thumbnail).

**Backend**:
- New endpoint: `POST /api/v1/link-preview` with `{ url: String }` → returns `{ title, description, imageUrl, siteName }`
- Use JSoup or OkHttp to fetch the URL, parse `<meta property="og:title">`, `og:description`, `og:image`
- Cache results in Redis (24h TTL) to avoid re-fetching
- Rate limit per user to prevent abuse

**Mobile**:
1. In `ChatScreen.kt`, detect URLs in message content (regex for http/https links)
2. For messages with URLs, call `/api/v1/link-preview` and cache locally
3. Render a card below the message text:
   ```
   ┌─────────────────────────────┐
   │ [thumbnail]  Site Name      │
   │              Title          │
   │              Description... │
   └─────────────────────────────┘
   ```
4. Tap card → open URL in system browser

---

## Priority 4 — Daily-Use Features

### FEAT-05: Stickers & GIFs (1-2 weeks)

**What**: Sticker/GIF button next to emoji in chat input. Integrates GIPHY/Tenor API for GIF search. Custom Turkish sticker packs.

**Backend**:
- New `ContentType.STICKER` and `ContentType.GIF` in shared model
- Store sticker pack metadata (pack ID, sticker URLs, pack name)
- `GET /api/v1/stickers/packs` — list available packs
- `GET /api/v1/stickers/packs/{id}` — get stickers in a pack

**Mobile**:
1. Add sticker/GIF button in chat input row (next to attachment button)
2. Bottom sheet with tabs: Stickers | GIFs
3. GIF tab: search bar + GIPHY API integration (`api.giphy.com/v1/gifs/search`)
4. Sticker tab: grid of sticker packs, tap to view, tap sticker to send
5. New bubble type for sticker (larger, no background, just the image)
6. GIF bubble: auto-plays, no sound, tap to expand

**Note**: Register for a free GIPHY API key. Respect attribution requirements.

---

### FEAT-06: Message Search (1 week)

**What**: Search icon in main screen and within conversations.

**Backend**:
- `GET /api/v1/messages/search?q=query&conversationId=optional` — full-text search
- Option A (simple): PostgreSQL `ILIKE '%query%'` on message content
- Option B (better): PostgreSQL `tsvector/tsquery` with Turkish text search configuration
- Return messages with conversation context (sender name, timestamp, conversation name)
- Cursor pagination for results

**Mobile**:
1. Search icon in TopAppBar of ConversationListScreen → opens search bar
2. Search icon in ChatScreen TopAppBar → search within conversation
3. Search results screen: list of matching messages with context preview
4. Tap result → navigate to conversation, scroll to that message, highlight it

---

### FEAT-07: File/Document Sharing (1 week)

**What**: Send PDFs, documents, and other file types through chat.

**Backend**:
- Extend `MediaService` with `uploadDocument()` — validates file type, stores in MinIO at `documents/{userId}/{uuid}.ext`
- Add `ContentType.DOCUMENT` to shared model
- `POST /api/v1/media/document` — upload endpoint
- Allowed types: PDF, DOC/DOCX, XLS/XLSX, PPT/PPTX, TXT, ZIP
- Max size: 100MB

**Mobile**:
1. Add document picker (Android: `Intent(Intent.ACTION_OPEN_DOCUMENT)` or `ActivityResultContracts.OpenDocument()`)
2. Create `expect/actual` for `DocumentPickerLauncher`
3. New bubble type for documents: file icon + filename + file size + download button
4. Tap to download/open with system viewer
5. Progress indicator during upload/download

**Localization**: `send_document` / "Belge gönder", `download` / "İndir"

---

### FEAT-08: Disappearing Messages (3-5 days)

**What**: Per-conversation setting to auto-delete messages after a timer (24h, 7d, 90d).

**Backend**:
- New migration: add `disappearing_duration` column to `conversations` table (NULL = off, value in seconds)
- New migration: add `expires_at TIMESTAMPTZ` column to `messages` table
- `PATCH /api/v1/conversations/{id}` — set disappearing duration
- When saving a message: if conversation has disappearing duration, set `expires_at = now + duration`
- Scheduled job (`@Scheduled`): delete expired messages every hour
- WS broadcast: `DisappearingModeChanged` event

**Mobile**:
1. Conversation settings (accessible from chat header) → "Disappearing messages" toggle with duration picker
2. System message bubble when mode is enabled/disabled
3. Timer icon on conversation items that have disappearing enabled

---

## Priority 5 — Voice/Video Calls

### FEAT-09: Voice & Video Calls (4-8 weeks)

**What**: 1:1 voice and video calls using WebRTC.

**Recommended approach**: Use **LiveKit** (open-source WebRTC SFU) to avoid building signaling/TURN from scratch.

**Infrastructure**:
1. Deploy LiveKit server on GCP (or use LiveKit Cloud free tier for MVP)
2. Backend generates LiveKit room tokens via LiveKit Server SDK
3. STUN/TURN servers for NAT traversal (LiveKit provides built-in)

**Backend**:
- `POST /api/v1/calls/start` — create call room, return LiveKit token
- `POST /api/v1/calls/end` — end call
- WS events: `IncomingCall`, `CallAccepted`, `CallRejected`, `CallEnded`
- Store call history in DB (caller, callee, duration, type)

**Mobile (Android)**:
1. Add LiveKit Android SDK dependency
2. Call button in chat header (phone icon for voice, video icon for video)
3. Incoming call screen: full-screen notification with Accept/Reject
4. In-call screen: timer, mute, speaker, video toggle, end call
5. Call history in a new tab or accessible from profile
6. Handle Android audio focus, proximity sensor, Bluetooth

**Mobile (iOS)**: Implement with LiveKit iOS SDK when doing iOS release.

**Note**: This is the biggest feature. Consider implementing voice-only first, then adding video.

---

## Priority 6 — Trust & Platform

### FEAT-10: E2E Encryption — Signal Protocol (6-12 weeks)

**What**: End-to-end encryption for all messages using the Signal Protocol (Double Ratchet).

**Approach**: Use `libsignal-client` library (available for JVM and mobile platforms).

**Implementation phases**:
1. **Key generation**: Generate identity key pair, signed pre-key, one-time pre-keys on device
2. **Key distribution**: Upload public keys to server (`POST /api/v1/keys/bundle`)
3. **Session establishment**: X3DH key agreement when starting a new conversation
4. **Message encryption**: Double Ratchet for forward secrecy
5. **Key verification**: QR code / safety number for verifying contacts
6. **Multi-device**: Handle key sync across devices (complex)

**Start with**: Opt-in "Secret Chat" mode (like Telegram), then make default later.

**Backend changes**: Store encrypted blobs instead of plaintext. Server cannot read message content.

---

### FEAT-11: Multi-Device / Web Client (4-8 weeks)

**What**: Access Muhabbet from a web browser or desktop app.

**Approach**: Build a web client using Kotlin/JS (share KMP code) or React.

**Backend**:
- Multi-session WebSocket support (user can have multiple active WS connections)
- Message sync across devices via REST API
- Device linking: QR code scan (like WhatsApp Web)

**Web client**: React + TypeScript, connect to same REST API and WebSocket.

---

## Priority 7 — Engagement & Growth

### FEAT-12: Status/Stories (2-3 weeks)

**What**: 24-hour ephemeral content visible to contacts (photos, text, video).

**Backend**:
- New `statuses` table: user_id, media_url, text, created_at, expires_at
- `POST /api/v1/statuses` — create status
- `GET /api/v1/statuses` — get contacts' statuses
- Scheduled cleanup job for expired statuses

**Mobile**: Status tab or section at top of conversation list (horizontal scroll of contact circles).

---

### FEAT-13: Channels/Broadcasts (2-3 weeks)

**What**: One-to-many content distribution (like Telegram channels).

**Backend**:
- New conversation type: `CHANNEL` with `subscribers` instead of `members`
- Channel admin can post, subscribers can only read
- `POST /api/v1/channels` — create channel
- `POST /api/v1/channels/{id}/subscribe` — subscribe
- Channel discovery/search

**Mobile**: Channel feed UI, channel creation, subscriber management.

---

### FEAT-14: Location Sharing (3-5 days)

**What**: Send static location pin or share live location.

**Backend**:
- New `ContentType.LOCATION` — store lat/lng/label in message content as JSON
- For live location: Redis key with TTL, periodic updates via WS

**Mobile**:
1. Google Maps SDK integration
2. Location picker screen
3. Location bubble with map preview
4. Tap to open in Google Maps
5. Live location: background location service, periodic WS updates

---

### FEAT-15: Polls (2-3 days)

**What**: Create polls in group chats.

**Backend**:
- New `ContentType.POLL`
- Store poll options and votes in DB
- `POST /api/v1/messages/{id}/vote` — cast vote
- WS broadcast vote updates

**Mobile**: Poll creation UI, vote display with progress bars, real-time vote counts.

---

## Feature Research Summary

### Competitive Analysis: What Makes Users Switch

| Rank | Feature | WhatsApp | Telegram | User Impact | Difficulty | Phase |
|------|---------|----------|----------|-------------|------------|-------|
| 1 | Reply/Quote | Yes | Yes | High | Easy | 3 |
| 2 | Profile Photos | Yes | Yes | High | Easy | 1 |
| 3 | Voice/Video Calls | Yes | Yes | Critical | Hard | 5 |
| 4 | Stickers/GIFs | Yes | Yes | High | Medium | 4 |
| 5 | Message Search | Yes | Yes | High | Medium | 4 |
| 6 | Link Previews | Yes | Yes | Medium-High | Easy-Medium | 3 |
| 7 | File/Document Sharing | Yes | Yes | High | Medium | 4 |
| 8 | Message Forwarding | Yes | Yes | Medium-High | Easy | 3 |
| 9 | E2E Encryption | Yes (default) | Partial | Critical (trust) | Hard | 6 |
| 10 | Multi-Device/Desktop | Yes | Yes | High | Hard | 6 |
| 11 | Status/Stories | Yes | Yes | High | Medium | 7 |
| 12 | Disappearing Messages | Yes | Yes | Medium | Easy-Medium | 4 |
| 13 | Starred/Saved Messages | Yes | Yes | Medium | Easy | 3 |
| 14 | Channels/Broadcasts | Yes | Yes | Medium-High | Medium | 7 |
| 15 | Location Sharing | Yes | Yes | Medium | Easy-Medium | 7 |

### Turkish Market Insight

- WhatsApp has **60M users in Turkey** (88.6% of internet users)
- 2021 privacy controversy caused mass exodus to BiP/Signal/Telegram — users returned because alternatives lacked features
- **Privacy + KVKK compliance** is Muhabbet's key differentiator
- **Voice calls are non-negotiable** for WhatsApp replacement — Turkish culture is voice-heavy
- Telegram channels are used by 85% of its users — channel ecosystem creates network effects

---

## Architecture Reference

### Project Structure
```
muhabbet/
├── backend/          Spring Boot 3 + Kotlin (modular monolith, hexagonal arch)
├── shared/           KMP shared module (models, DTOs, protocol, validation)
├── mobile/           Compose Multiplatform (Android + iOS stubs)
├── infra/            Docker Compose, nginx, deploy scripts
└── docs/             Architecture docs, API contract
```

### Backend Module Pattern
```
module/domain/model/       → Aggregates, entities, value objects
module/domain/port/in/     → Use case interfaces
module/domain/port/out/    → Repository/external service interfaces
module/domain/service/     → Business logic
module/adapter/in/web/     → REST controllers
module/adapter/in/websocket/ → WS handlers
module/adapter/out/persistence/ → JPA repos + entities
module/adapter/out/external/   → Third-party integrations
```

### Key Technologies
| Component | Technology |
|-----------|-----------|
| Backend | Spring Boot 3.4, Kotlin, PostgreSQL 16, Redis 7, MinIO |
| Mobile | Compose Multiplatform, Ktor, Koin, Decompose, Coil3 |
| Shared | KMP, kotlinx.serialization, kotlinx.datetime |
| Infra | Docker Compose, nginx, GCP (e2-medium), Firebase FCM |

### Deployment
```bash
# Backend deploy
gcloud compute ssh muhabbet-vm --zone=europe-west1-b --project=muhabbet-app-prod \
  --command='cd /home/ahabg/Muhabbet && git pull && cd infra && docker compose -f docker-compose.prod.yml up -d --build backend'

# Mobile build
./gradlew :mobile:composeApp:assembleDebug      # Debug APK
./gradlew :mobile:composeApp:bundleRelease       # Release AAB for Play Store
```

### Localization Rules
- All UI text: `stringResource(Res.string.*)` — no hardcoded strings
- Turkish default: `composeResources/values/strings.xml`
- English: `composeResources/values-en/strings.xml`
- Strings in `scope.launch {}`: resolve as `val` at composable scope level

---

## Estimated Timeline

| Priority | Scope | Duration |
|----------|-------|----------|
| P0 | Bug fixes (notifications, ticks) | 1-2 days |
| P1 | Profile photo upload | 2-3 days |
| P2 | Play Store launch prep | 1-2 weeks |
| P3 | Reply, Forward, Starred, Link Previews | 1-2 weeks |
| P4 | Stickers, Search, Files, Disappearing | 3-4 weeks |
| P5 | Voice/Video Calls | 4-8 weeks |
| P6 | E2E Encryption, Multi-Device | 8-16 weeks |
| P7 | Stories, Channels, Location, Polls | 4-6 weeks |
