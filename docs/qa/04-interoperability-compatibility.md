# 04 — Interoperability & Compatibility

> Quality attribute: System's ability to work across platforms, devices, and integration points.

---

## 1. Platform Compatibility Matrix

### 1.1 Android Support

| Android Version | API Level | Status | Coverage |
|-----------------|-----------|--------|----------|
| Android 8.0 (Oreo) | 26 | Minimum | Compile target |
| Android 9.0 (Pie) | 28 | Supported | |
| Android 10 | 29 | Supported | |
| Android 11 | 30 | Supported | Scoped storage |
| Android 12 | 31 | Supported | Splash screen API |
| Android 13 | 33 | Supported | Notification permissions |
| Android 14 | 34 | Supported | |
| Android 15 | 35 | Target SDK | Primary test target |

**Testing targets:**
- Low-end: Samsung Galaxy A14 (2GB RAM, API 33)
- Mid-range: Samsung Galaxy A54 (6GB RAM, API 34)
- High-end: Samsung Galaxy S24 (8GB RAM, API 35)
- Pixel: Pixel 7a (stock Android, API 35)

### 1.2 iOS Support

| iOS Version | Status | Coverage |
|------------|--------|----------|
| iOS 16 | Minimum | Compose Multiplatform minimum |
| iOS 17 | Supported | Secondary test |
| iOS 18 | Target | Primary test target |

**Testing targets:**
- iPhone SE 3 (iOS 16 baseline)
- iPhone 14 (iOS 17)
- iPhone 15 Pro (iOS 18)

### 1.3 Screen Size & Density

| Category | Sizes | Test Method |
|----------|-------|-------------|
| Small phone | 320dp width (5") | Android emulator |
| Normal phone | 360-400dp width (6-6.5") | Physical device |
| Large phone | 400-430dp width (6.5-7") | Physical device |
| Tablet | 600dp+ width | Emulator (stretch test) |
| Foldable | Variable width | Samsung Fold emulator |

---

## 2. API Compatibility

### 2.1 REST API Contract

| Property | Standard | Status |
|----------|---------|--------|
| Versioning | URL prefix `/api/v1/` | Deployed |
| Content-Type | `application/json` | Deployed |
| Error format | `{"error": {"code": "...", "message": "..."}, "timestamp": "..."}` | Deployed |
| Pagination | Cursor-based (`?cursor=...&limit=20`) | Deployed |
| Date format | ISO 8601 (`2026-02-13T10:00:00Z`) | Deployed |
| UUID format | RFC 4122 v4 | Deployed |
| HTTP methods | REST standard (GET/POST/PATCH/DELETE) | Deployed |
| Status codes | Standard HTTP (200, 201, 400, 401, 403, 404, 409, 429, 500) | Deployed |

### 2.2 API Versioning Strategy

```
/api/v1/...  → Current stable API (all clients)
/api/v2/...  → Future (when breaking changes needed)
```

**Breaking change policy:**
- v1 API frozen after beta launch
- New features added as new endpoints, not modifications
- Deprecated endpoints marked with `Sunset` header (6 month notice)
- Mobile app minimum version enforcement via `X-Min-Version` header (future)

### 2.3 WebSocket Protocol

| Property | Value | Compatibility Note |
|----------|-------|--------------------|
| Transport | WSS (TLS) | Standard WebSocket |
| Subprotocol | None (JSON frames) | Any WS client compatible |
| Authentication | Query param `?token={jwt}` | Non-standard but common |
| Message format | JSON with `type` discriminator | `kotlinx.serialization` |
| Heartbeat | Client sends `Ping` every 30s | Custom (not WS ping frame) |
| Reconnect | Client-side exponential backoff | 1s → 30s max |

**Frame type compatibility table:**

| Type (JSON `type` field) | Direction | Description |
|--------------------------|-----------|-------------|
| `message.send` | Client → Server | Send a message |
| `message.ack` | Client → Server | Acknowledge delivery/read |
| `presence.typing` | Client → Server | Typing indicator |
| `presence.online` | Client → Server | Go online |
| `message.new` | Server → Client | New message broadcast |
| `message.status` | Server → Client | Delivery status update |
| `ack` | Server → Client | Server acknowledgement |
| `error` | Server → Client | Error notification |
| `ping` / `pong` | Bidirectional | Heartbeat |

### 2.4 Third-Party Integration Compatibility

| Integration | Protocol | Version | Status |
|-------------|----------|---------|--------|
| PostgreSQL | JDBC | 16 | Deployed |
| Redis | RESP3 (Lettuce) | 7 | Deployed |
| MinIO | S3 API | RELEASE.2024-01-01 | Deployed |
| Firebase FCM | HTTP v1 API | Latest | Deployed |
| Firebase Auth | REST API | Latest | Android only |
| Netgsm SMS | HTTP API | v1 | Deployed |
| GIPHY | REST API | v1 | Deployed |
| Sentry | SDK | 7.14.0 | Android deployed |
| LiveKit | Server SDK | Latest | Adapter ready |

---

## 3. Data Format Compatibility

### 3.1 Shared Module (KMP)

The `shared/` Kotlin Multiplatform module is the single source of truth for data formats:

| Type | Location | Used By |
|------|----------|---------|
| `WsMessage` sealed class | `shared/src/commonMain/kotlin/.../protocol/WsMessage.kt` | Backend + Mobile |
| DTOs (request/response) | `shared/src/commonMain/kotlin/.../dto/Dtos.kt` | Backend + Mobile |
| Domain models | `shared/src/commonMain/kotlin/.../model/Models.kt` | Backend + Mobile |
| Validation rules | `shared/src/commonMain/kotlin/.../validation/` | Backend + Mobile |

**Serialization:** `kotlinx.serialization` with JSON — same format on all platforms.

### 3.2 Content Type Support

| Content Type | Backend | Android | iOS | Bot API |
|-------------|---------|---------|-----|---------|
| TEXT | Yes | Yes | Yes | Yes |
| IMAGE | Yes | Yes | Yes | Yes |
| AUDIO | Yes | Yes | Yes | Yes |
| VIDEO | Yes | Yes | Partial (playback) | Yes |
| FILE | Yes | Yes | Yes | Yes |
| LOCATION | Yes | Yes | Yes | Yes |
| STICKER | Yes | Yes | Yes | Yes |
| GIF | Yes | Yes | Yes | Yes |
| POLL | Yes | Yes | Yes | No |
| SYSTEM | Yes | Yes | Yes | Read-only |

### 3.3 Encoding

| Data | Encoding | Standard |
|------|----------|----------|
| Text messages | UTF-8 | Unicode 15.0 |
| Phone numbers | E.164 | ITU-T E.164 |
| Emoji | Unicode | Full emoji support (grapheme cluster handling) |
| JSON | UTF-8 | RFC 8259 |
| Media files | Binary | MIME type-based |
| Base64 (tokens, keys) | URL-safe Base64 | RFC 4648 |

---

## 4. Migration & Upgrade Compatibility

### 4.1 Database Migration

| Policy | Implementation |
|--------|---------------|
| Migration tool | Flyway |
| Migration format | `V{number}__{description}.sql` |
| Migration direction | Forward-only (no rollback scripts) |
| Data preservation | All migrations are additive (new columns, tables) |
| Current version | V15 |
| Backward compatibility | New columns have defaults or are nullable |

### 4.2 App Version Compatibility

| Scenario | Handling | Status |
|----------|---------|--------|
| Old app + new backend | New fields ignored by old client (kotlinx.serialization `ignoreUnknownKeys`) | Deployed |
| New app + old backend | New features show "not available" or are hidden | Planned |
| Force update | `X-Min-Version` header + Play Store/App Store update prompt | Planned |
| Gradual rollout | Feature flags based on app version | Planned |

### 4.3 Backend Deployment Compatibility

| Strategy | Description | Status |
|----------|-------------|--------|
| Rolling deployment | Single instance, brief downtime | Current |
| Blue-green deployment | Two instances, traffic switch | Planned (GA) |
| Database migration timing | Flyway runs on boot, before traffic | Deployed |
| Breaking schema changes | Deploy migration first, then code | Policy |

---

## 5. Test Plan

### 5.1 Cross-Platform Tests

| Test | Platforms | Priority |
|------|----------|----------|
| Login flow (OTP → JWT) | Android, iOS | P0 |
| Send/receive text message | Android ↔ iOS | P0 |
| Send/receive image | Android ↔ iOS | P0 |
| Group chat (3+ members mixed platform) | Android + iOS | P0 |
| Typing indicator cross-platform | Android ↔ iOS | P1 |
| Voice message cross-platform | Android ↔ iOS | P1 |
| Message delete/edit cross-platform | Android ↔ iOS | P1 |
| Deep link handling | Android, iOS | P1 |
| Push notification tap navigation | Android, iOS | P1 |

### 5.2 Device-Specific Tests

| Test | Devices | Priority |
|------|---------|----------|
| UI on small screen (320dp) | Emulator | P1 |
| UI on large screen (430dp) | Physical | P1 |
| Dark mode rendering | All | P1 |
| OLED theme rendering | AMOLED device | P2 |
| Landscape orientation | Tablet emulator | P2 |
| RTL layout (Arabic content) | Emulator | P2 |
| Accessibility (TalkBack/VoiceOver) | Physical | P1 |

### 5.3 API Compatibility Tests

| Test | Type | Priority |
|------|------|----------|
| Unknown JSON fields ignored by client | Unit | P0 (exists) |
| API v1 contract matches shared DTOs | Contract | P1 |
| WsMessage serialization round-trip | Unit | P0 (exists, 25+ tests) |
| Bot API token authentication | Integration | P1 |
| Webhook delivery format | Integration | P1 |

### 5.4 Network Compatibility

| Condition | Test | Expected Behavior |
|-----------|------|-------------------|
| Wi-Fi | Baseline | Normal operation |
| 4G/LTE | Manual | Normal operation, slightly higher latency |
| 3G (slow) | Network throttle | Messages queue, images load slowly |
| Offline → Online | Toggle airplane mode | WS reconnects, messages sync |
| Poor connection (packet loss) | Network link conditioner | Retries, no data loss |
| VPN | Corporate/personal VPN | Normal operation (WSS traverses most VPNs) |
| Proxy | Corporate proxy | May block WSS — show error with instructions |

---

## 6. Bot API Interoperability

### 6.1 External Client Requirements

Any external client (bot, web, script) must:

1. Authenticate via JWT with `iss: "muhabbet"` claim
2. Use exact WsMessage `type` discriminators (e.g., `message.send`, not `SendMessage`)
3. Send heartbeat (`ping`) every 30s to maintain connection
4. Handle `ack`, `error`, `message.new`, `message.status` frame types
5. Use `clientMessageId` for idempotent message sending

### 6.2 Bot Platform Integration Points

| Endpoint | Method | Auth | Purpose |
|----------|--------|------|---------|
| `/api/v1/bots` | POST | JWT (owner) | Create bot |
| `/api/v1/bots/{id}` | GET | JWT (owner) | Get bot details |
| `/api/v1/bots/{id}/webhook` | PATCH | JWT (owner) | Update webhook URL |
| `/api/v1/bots/{id}/token` | POST | JWT (owner) | Regenerate API token |
| Webhook delivery | POST | Bot token header | Incoming message notification |

---

## 7. Action Items

### P0
- [ ] Test Android ↔ iOS message exchange (all content types)
- [ ] Verify `ignoreUnknownKeys` works in mobile deserialization
- [ ] Test app behavior with older backend API (missing fields)
- [ ] Validate WS protocol with external Python client (`test_bot.py`)

### P1
- [ ] Set up device test lab (BrowserStack or Firebase Test Lab)
- [ ] Test on minimum supported devices (low RAM, old API levels)
- [ ] Implement API version negotiation headers
- [ ] Test network degradation scenarios (3G, offline, packet loss)
- [ ] Validate bot webhook delivery format

### P2
- [ ] API contract testing with consumer-driven contracts (Pact)
- [ ] Automated cross-platform E2E tests (Maestro or Appium)
- [ ] Test with Turkish ISP peculiarities (Turkcell, Vodafone, Turk Telekom)
- [ ] Foldable device UI testing
