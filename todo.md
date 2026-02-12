# Muhabbet — Todo Tracker

## Bug Fixes

- [x] **WS userId=null on disconnect** — Fixed: use sessionManager.getUserId() before unregister
- [x] **Spring bean ambiguity** — Fixed: @Primary on messagingService in AppConfig
- [x] **Mic button crash** — Fixed: added RECORD_AUDIO permission in AndroidManifest + runtime permission request via rememberAudioPermissionRequester
- [x] **Edit message fails** — Fixed: changed GroupRepository.editMessage to use raw httpClient.patch() instead of deserializing response as Message
- [x] **Turkish characters missing** — Fixed: all hardcoded strings moved to composeResources string resources (values/strings.xml + values-en/strings.xml)
- [x] **Date format broken** — Fixed: formatTimestamp now shows HH:mm for today, dd.MM for same year, dd.MM.yy for older
- [x] **Language switch** — Added: Turkish/English toggle in Settings with radio buttons, persisted in SharedPreferences, activity restart to apply

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

## Pending Features

- [ ] **Profile viewing** — Tap user avatar/name to view profile (displayName, about, phone)
- [ ] **Last seen / online in chat** — Show online dot / "son görülme" in chat header (presence data exists but may not display on initial load)
- [ ] **Contact details** — Show phone number, about, saved contact name in conversation screens (currently only nickname)
- [ ] **E2E encryption** — Signal Protocol integration (deferred)
- [ ] **iOS release** — iOS platform implementations (currently stubs)

## Localization Rules

- No hardcoded strings in UI code — all user-visible text must use `stringResource(Res.string.*)`
- Default locale: Turkish (`composeResources/values/strings.xml`)
- English: `composeResources/values-en/strings.xml`
- Strings used in `scope.launch {}` blocks: resolve as `val` at composable scope level
- Language preference: `muhabbet_prefs` SharedPreferences, `app_language` key
- Applied in `MainActivity.onCreate()` via `Configuration.setLocale()` before `setContent`

## Infrastructure

- [x] GCP VM deployment (e2-medium, europe-west1-b)
- [x] Docker Compose (PG + Redis + MinIO + nginx)
- [x] Firebase FCM
- [x] Flyway migrations (V1-V4)
- [ ] **CI/CD** — GitHub Actions pipeline
- [ ] **Monitoring** — Sentry integration
