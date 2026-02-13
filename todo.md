# Muhabbet — Todo Tracker

## Active Bugs

*(None — all known bugs fixed)*

## Fixed Bugs

- [x] **Push notifications not firing** — Fixed: `application-prod.yml` default changed from `FCM_ENABLED:false` to `FCM_ENABLED:true`; `docker-compose.prod.yml` already sets `FCM_ENABLED=true` + `FIREBASE_CREDENTIALS_PATH`
- [x] **Ticks stuck at single** — Fixed: Global DELIVERED ack added in `App.kt` `WebSocketLifecycle()` — sends `AckMessage(DELIVERED)` for every incoming `NewMessage` from other users
- [x] **WS userId=null on disconnect** — Fixed: use sessionManager.getUserId() before unregister
- [x] **Spring bean ambiguity** — Fixed: @Primary on messagingService in AppConfig
- [x] **Mic button crash** — Fixed: added RECORD_AUDIO permission in AndroidManifest + runtime permission request via rememberAudioPermissionRequester
- [x] **Edit message fails** — Fixed: changed GroupRepository.editMessage to use raw httpClient.patch() instead of deserializing response as Message
- [x] **Turkish characters missing** — Fixed: all hardcoded strings moved to composeResources string resources (values/strings.xml + values-en/strings.xml)
- [x] **Date format broken** — Fixed: formatTimestamp now shows HH:mm for today, dd.MM for same year, dd.MM.yy for older
- [x] **Language switch** — Added: Turkish/English toggle in Settings with radio buttons, persisted in SharedPreferences, activity restart to apply
- [x] **WS reconnect uses expired token** — Fixed: WsClient now reads fresh token from TokenStorage on each reconnect instead of reusing the initial captured token
- [x] **StatusUpdate sent to wrong user** — Fixed: broadcastStatusUpdate now sends to original message sender, not the reader
- [x] **READ ack not broadcast** — Fixed: handleAckMessage for READ now also calls updateStatus to broadcast StatusUpdate; mobile bulk-updates all own messages on READ
- [x] **thumbnailUrl lost in WS chain** — Fixed: added thumbnailUrl to SendMessage, SendMessageCommand, and Message save; mobile now sends thumbnailUrl for images
- [x] **Firebase rate limit blocking** — Fixed: PhoneInputScreen falls back to backend OTP when Firebase returns block/rate-limit errors
- [x] **Can't delete conversation** — Fixed: added DELETE /api/v1/conversations/{id} endpoint; long-press on conversation to delete
- [x] **No profile screen** — Fixed: added GET /api/v1/users/{userId} endpoint + UserProfileScreen
- [x] **Only nicknames shown** — Fixed: contact name resolution (device contact > nickname > phone number)
- [x] **Avatar only shows first letter** — Fixed: firstGrapheme() handles emojis properly
- [x] **Phone number in conv list** — Fixed: now shows lastMessagePreview like WhatsApp
- [x] **Image URLs expire after 1h** — Fixed: MinIO presigned URL expiry increased to 7 days
- [x] **Chat scrolls from top** — Fixed (Round 6): scrollToItem with scrollOffset=Int.MAX_VALUE for instant bottom positioning
- [x] **Forward button views image instead of forwarding** — Fixed (Round 6): now opens ForwardPickerDialog
- [x] **MessageInfo missing media preview** — Fixed (Round 6): added mediaUrl/thumbnailUrl to DTO + backend
- [x] **MessageInfo missing avatars** — Fixed (Round 6): added avatarUrl to RecipientDeliveryInfo DTO

## Completed Features

- [x] Auth (OTP + JWT)
- [x] 1:1 messaging (WebSocket)
- [x] Mobile app (CMP Android) — auth, chat, settings, dark mode, pull-to-refresh, pagination
- [x] Contacts sync (Android; iOS stubbed)
- [x] Typing indicators (send, receive, backend broadcast)
- [x] Media sharing (images) — upload, thumbnail, image bubbles, full-size viewer
- [x] Push notifications (FCM, push token registration, offline delivery)
- [x] Presence (online/last seen) — Redis TTL, green dot, header subtitle
- [x] Group messaging — backend endpoints + mobile UI (create, info, add/remove members, roles, leave)
- [x] Voice messages — backend audio upload + mobile record/playback UI
- [x] Netgsm SMS — production OTP sender with @ConditionalOnProperty
- [x] Message delete — soft delete, WS broadcast, "Bu mesaj silindi" placeholder
- [x] Message edit — backend PATCH endpoint, WS broadcast, "düzenlendi" indicator
- [x] Localization (i18n) — all strings in composeResources, Turkish default + English, language switch in Settings
- [x] Profile viewing — Tap chat header to view user profile
- [x] Contact name resolution — device contact name > nickname > phone number
- [x] Delete conversation — Long-press to delete from conversation list
- [x] Profile photo upload — Upload via MediaRepository, shown in Settings + UserAvatar
- [x] Reply/quote messages — Swipe-to-reply, quoted message preview in bubble
- [x] Message forwarding — ForwardPickerDialog, forwarded label on bubble
- [x] Starred/saved messages — Star from context menu, list in Settings, tap to navigate
- [x] Link previews — Open Graph metadata extraction, preview card in bubble
- [x] Stickers/GIFs — GIPHY integration, GifStickerPicker bottom sheet
- [x] Message search — Full-text search across conversations
- [x] File/document sharing — PDF, DOC, etc. upload + download
- [x] Disappearing messages — 24h, 7d, 90d timer options
- [x] Status/Stories — 24h ephemeral, text + photo, contacts visibility
- [x] Channels/Broadcasts — One-to-many, subscriber model
- [x] Location sharing — Static pin with map preview
- [x] Polls — Create, vote, real-time results
- [x] Reactions — Emoji reactions on messages
- [x] Pinch-to-zoom — MediaViewer 1x–5x zoom, double-tap toggle
- [x] Shared media viewer — Grid + list, video/voice/doc playback, forward/delete
- [x] Message info — Per-recipient delivery status, media preview, avatars
- [x] Storage usage stats — Per-user breakdown by type in Settings

## Recently Completed (Engineering Session — Feb 2026)

- [x] **System optimization** — 12 database indexes, N+1 batch fetching, Redis/Ktor connection pooling, nginx gzip/caching, PostgreSQL tuning
- [x] **Dependency upgrades** — Kotlin 2.3.10, Spring Boot 4.0.2, Java 25, Gradle 8.14.4, Ktor 3.1.3, Compose BOM 2025.04.01
- [x] **CI/CD pipeline** — GitHub Actions: backend CI, mobile CI (Android + iOS), security scanning (Trivy, Gitleaks, CodeQL), deployment automation
- [x] **iOS platform completion** — ImagePicker (PHPickerViewController), FilePicker (UIDocumentPickerViewController), ImageCompressor (CoreGraphics), CrashReporter (NSLog + Sentry hooks), PushTokenProvider (NSUserDefaults persistence), LocaleHelper (AppleLanguages), FirebasePhoneAuth (fallback stub)
- [x] **Mobile test infrastructure** — kotlin-test, coroutines-test, ktor-mock, koin-test; FakeTokenStorageTest, AuthRepositoryTest, PhoneNormalizationTest, WsMessageSerializationTest (25+ tests)
- [x] **Call UI screens** — IncomingCallScreen, ActiveCallScreen, CallHistoryScreen with Decompose navigation
- [x] **E2E encryption infrastructure** — E2EKeyManager interface + NoOpKeyManager (MVP), EncryptionRepository (mobile key exchange client)
- [x] **Security hardening** — HSTS, X-Frame-Options DENY, CSP, XSS protection, Referrer-Policy, Permissions-Policy headers; InputSanitizer (HTML escaping, control char stripping, URL validation) with 15 unit tests

## Pending Features

- [ ] **Voice/video calls (WebRTC)** — LiveKit client SDK integration — signaling backend + call UI screens are ready
- [ ] **E2E encryption client** — Signal Protocol (X3DH, Double Ratchet) via libsignal-client — key exchange infra + NoOp manager are ready
- [ ] **iOS APNs delivery** — FCM→APNs bridge or direct APNs adapter
- [ ] **iOS TestFlight + App Store** — TestFlight distribution, App Store submission
- [ ] **Google Play Store launch** — App signing, listing, review
- [ ] **Security penetration testing** — OWASP ZAP/Burp Suite scan
- [ ] **Load testing** — k6/Gatling for WS + HTTP at scale
- [ ] **Web/Desktop client** — React+TS or Kotlin/JS, QR device linking
- [ ] **Multi-device E2E** — Signal Protocol multi-device support

## Infrastructure

- [x] GCP VM deployment (e2-medium, europe-west1-b)
- [x] Docker Compose (PG + Redis + MinIO + nginx)
- [x] Firebase FCM
- [x] Flyway migrations (V1-V13)
- [x] Sentry integration (Android SDK)
- [x] CI/CD — GitHub Actions (backend CI, mobile CI, security scanning, deploy)
- [x] Database performance indexes (12 indexes)
- [x] Security headers (HSTS, CSP, X-Frame-Options, etc.)
- [x] Input sanitization (InputSanitizer utility)

## Localization Rules

- No hardcoded strings in UI code — all user-visible text must use `stringResource(Res.string.*)`
- Default locale: Turkish (`composeResources/values/strings.xml`)
- English: `composeResources/values-en/strings.xml`
- Strings used in `scope.launch {}` blocks: resolve as `val` at composable scope level
- Language preference: `muhabbet_prefs` SharedPreferences, `app_language` key
- Applied in `MainActivity.onCreate()` via `Configuration.setLocale()` before `setContent`
