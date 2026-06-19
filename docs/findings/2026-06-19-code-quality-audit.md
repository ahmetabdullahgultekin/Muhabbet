# Code Quality & SE-Principle Audit — Muhabbet (2026-06-19)

Read-only static audit of `backend/src/main`, `shared/src`, `mobile/composeApp/src/commonMain`
(excluding `*.kt.disabled` + generated code). Every finding was verified against the actual files.

**Net assessment:** Backend hexagonal discipline is **strong** — the architecture rules are genuinely
honored (no controller imports JPA/SpringData, no `@Transactional` on controllers, domain never
imports persistence). The real debt is concentrated in **mobile UI composables**: a few 500–800-line
"screen" functions and pervasive silent exception swallowing.

## Top 10 highest-impact fixes

1. `mobile/.../ui/conversations/ConversationListScreen.kt` — single composable **815 lines** (limit 300) — **HIGH**
2. `mobile/.../ui/settings/SettingsScreen.kt` — `SettingsScreen()` **707 lines** — **HIGH**
3. `mobile/.../ui/chat/ChatScreen.kt` — `ChatScreen()` **491 lines** — **HIGH**
4. **56 empty `catch (_: Exception) {}` blocks** swallowing errors silently across commonMain — **HIGH**
5. `backend/.../auth/domain/service/AuthService.kt:51` — implements **6** use-case interfaces (limit 3) — **MEDIUM**
6. `UserProfileScreen.kt` (336) & `GroupInfoScreen.kt` (336) single composables >300 — **MEDIUM**
7. `backend/.../messaging/domain/service/MessageService.kt:29` — implements **4** use-case interfaces — **MEDIUM**
8. **15 `!!` non-null assertions** in commonMain (banned by CLAUDE.md) — **MEDIUM**
9. `shared/.../dto/Dtos.kt` — 617-line file, 71 declarations (file-level SRP) — **LOW/MEDIUM**
10. 2 hardcoded `contentDescription` literals + 1 inline-string exception — **LOW**

## Details by category

### 1. God classes / oversized composables
- Backend: **none** >500 lines (clean). `shared/.../dto/Dtos.kt` is 617 lines but pure DTOs (file-level only).
- Composables over the 300-line hard limit (measured per-function): `ConversationListScreen()` 815,
  `SettingsScreen()` 707, `ChatScreen()` 491, `UserProfileScreen()` 336, `GroupInfoScreen()` 336.
- Files >300 but built of smaller composables (lower priority): `ChatDialogs.kt` 472,
  `PrivacyDashboardScreen.kt` 471, `MessageBubble.kt` 432, `UpdatesTabScreen.kt` 429, `SharedMediaScreen.kt` 426.

### 2. Services implementing >3 use cases
- `AuthService.kt:51` — 6 (`RequestOtp, VerifyOtp, RefreshToken, Logout, RegisterPushToken, FirebaseVerify`). Split by concern.
- `MessageService.kt:29` — 4 (`SendMessage, GetMessageHistory, UpdateDeliveryStatus, ManageMessage`).
- All other domain services ≤3.

### 3. Hexagonal violations
**Clean.** No controller imports `*JpaEntity`/`SpringData*`/`JpaRepository`; no `@Transactional` on
controllers; no domain file imports persistence or JPA entities.
- *Nuance (rule contradiction, not bad code):* 107 `@Transactional` annotations + one
  `ApplicationEventPublisher` import live in `domain/service/*`. CLAUDE.md says "no Spring annotations
  in domain" but *also* explicitly accepts `@Transactional` on services. **Recommend documenting the
  carve-out** so the rule reads honestly. (LOW)

### 4. `!!` in commonMain (15)
Worst: `ChatScreen.kt` (4: L340/345/346/348), `MessageInfoScreen.kt` (3: L110/114/316),
`LinkPreviewCard.kt` (3: L79/88/99 — riskiest, asserts nullable DTO fields). Most are guarded by a
preceding `!= null`; convert to `let`/smart-cast.

### 5. Hardcoded UI strings — nearly clean
No `Text("literal")` violations (i18n discipline is excellent). Minor leaks: `UserAvatar.kt:49`
(`contentDescription = "Group"`), `NewConversationScreen.kt:147,193` (`"Contacts"`).

### 6. Backend inline error strings
`NetgsmOtpSender.kt:56` — `RuntimeException("Netgsm SMS failed: …")` should map to an `ErrorCode` (MEDIUM).
`JavaImageThumbnailAdapter.kt:22` — `IllegalArgumentException("Cannot read image")` (LOW). `SsrfGuard.kt`
throws code-style constants (`URL_*`) — effectively compliant.

### 7. Dead code / empty catch — **most material**
**56 empty `catch (_: Exception) {}` in commonMain**, concentrated in `ConversationListScreen.kt`
(L214, 254, 313, 356, 390, 415, 447, …) and `GroupInfoScreen.kt:107`, `GroupEventScreen.kt:86`,
`InviteLinkSheet.kt:79`. Some are defensible fire-and-forget WS acks; the bulk silently hide data-load
and network failures (no log, no snackbar). Only 2 TODO/FIXME in the whole scope (very clean).
Backend: no empty catches.

### DRY
`WsMessage.AckMessage(...DELIVERED)` send pattern duplicated across `ConversationListScreen.kt` +
`ChatScreen.kt` (3 sites) — extractable to a `WsClient` helper (borderline). No other significant copy-paste.

## Recommended remediation order (next code-quality pass)
1. Decompose `ConversationListScreen`, `SettingsScreen`, `ChatScreen` into sub-composables (own files).
2. Replace empty catches with at least a log + user-facing error state on data-load paths.
3. Remove the 15 `!!` (start with `LinkPreviewCard`).
4. Split `AuthService` (6 → ≤3 interfaces); consider splitting `MessageService` (4 → 3).
5. Map `NetgsmOtpSender` inline message to an `ErrorCode`; fix 3 hardcoded `contentDescription`s.
6. Document the `@Transactional`-in-domain carve-out in CLAUDE.md so the rule is honest.
