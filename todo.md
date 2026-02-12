# Muhabbet — Todo Tracker

## Active Bugs

- [ ] **Push notifications not firing** — FCM_ENABLED is false in production; NoOpPushNotificationAdapter is active. Need to set FCM_ENABLED=true + FIREBASE_CREDENTIALS_PATH in docker-compose.prod.yml
- [ ] **Ticks stuck at single** — Mobile never sends DELIVERED ack, only READ. Need to add global DELIVERED ack in App.kt when NewMessage arrives, keep READ ack only in ChatScreen

## Fixed Bugs

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

## Pending Features (see ROADMAP.md for full plan)

- [ ] **Profile photo upload** — P1
- [ ] **Google Play Store launch** — P2
- [ ] **Reply/quote messages** — P3
- [ ] **Message forwarding** — P3
- [ ] **Starred/saved messages** — P3
- [ ] **Link previews** — P3
- [ ] **Stickers/GIFs** — P4
- [ ] **Message search** — P4
- [ ] **File/document sharing** — P4
- [ ] **Disappearing messages** — P4
- [ ] **Voice/video calls** — P5
- [ ] **E2E encryption** — P6
- [ ] **Multi-device / web client** — P6
- [ ] **Status/Stories** — P7
- [ ] **Channels/Broadcasts** — P7
- [ ] **Location sharing** — P7

## Infrastructure

- [x] GCP VM deployment (e2-medium, europe-west1-b)
- [x] Docker Compose (PG + Redis + MinIO + nginx)
- [x] Firebase FCM
- [x] Flyway migrations (V1-V4)
- [ ] **CI/CD** — GitHub Actions pipeline
- [ ] **Monitoring** — Sentry integration

## Localization Rules

- No hardcoded strings in UI code — all user-visible text must use `stringResource(Res.string.*)`
- Default locale: Turkish (`composeResources/values/strings.xml`)
- English: `composeResources/values-en/strings.xml`
- Strings used in `scope.launch {}` blocks: resolve as `val` at composable scope level
- Language preference: `muhabbet_prefs` SharedPreferences, `app_language` key
- Applied in `MainActivity.onCreate()` via `Configuration.setLocale()` before `setContent`
