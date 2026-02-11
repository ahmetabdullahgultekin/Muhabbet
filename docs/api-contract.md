# Muhabbet — API Contract
### REST + WebSocket Protocol Specification

---

## Base URL
- **Development:** `http://localhost:8080/api/v1`
- **Production:** `https://muhabbet.rollingcatsoftware.com/api/v1`
- **WebSocket:** `wss://muhabbet.rollingcatsoftware.com/ws?token={jwt_access_token}`

## Authentication
All endpoints (except auth) require JWT in Authorization header:
```
Authorization: Bearer {access_token}
```

## Response Envelope
Every response follows this structure:
```json
// Success (2xx)
{
  "data": { ... },
  "timestamp": "2026-02-11T14:30:00.000Z"
}

// Error (4xx/5xx)
{
  "error": {
    "code": "AUTH_OTP_EXPIRED",
    "message": "OTP süresi doldu. Lütfen yeni kod talep edin."
  },
  "timestamp": "2026-02-11T14:30:00.000Z"
}
```

---

## 1. Auth Endpoints

### POST /api/v1/auth/otp/request
Request OTP for phone number.

**Request:**
```json
{
  "phoneNumber": "+905321234567"
}
```

**Response (200):**
```json
{
  "data": {
    "ttlSeconds": 300,
    "retryAfterSeconds": 60
  }
}
```

**Errors:**
| Code | HTTP | Description |
|------|------|-------------|
| `AUTH_INVALID_PHONE` | 400 | Not a valid Turkish phone number |
| `AUTH_OTP_COOLDOWN` | 429 | Must wait before requesting new OTP |
| `AUTH_OTP_RATE_LIMIT` | 429 | Too many OTP requests |

---

### POST /api/v1/auth/otp/verify
Verify OTP and receive JWT tokens.

**Request:**
```json
{
  "phoneNumber": "+905321234567",
  "otp": "123456",
  "deviceName": "Samsung Galaxy S24",
  "platform": "android"
}
```

**Response (200):**
```json
{
  "data": {
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "dGhpcyBpcyBh...",
    "expiresIn": 900,
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "deviceId": "660e8400-e29b-41d4-a716-446655440001",
    "isNewUser": true
  }
}
```

**Errors:**
| Code | HTTP | Description |
|------|------|-------------|
| `AUTH_OTP_INVALID` | 401 | Wrong OTP code |
| `AUTH_OTP_EXPIRED` | 401 | OTP has expired |
| `AUTH_OTP_MAX_ATTEMPTS` | 401 | Too many wrong attempts |

---

### POST /api/v1/auth/token/refresh
Refresh access token.

**Request:**
```json
{
  "refreshToken": "dGhpcyBpcyBh..."
}
```

**Response (200):**
```json
{
  "data": {
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "bmV3IHJlZnJlc2g...",
    "expiresIn": 900
  }
}
```

**Errors:**
| Code | HTTP | Description |
|------|------|-------------|
| `AUTH_TOKEN_INVALID` | 401 | Invalid refresh token |
| `AUTH_TOKEN_EXPIRED` | 401 | Refresh token expired |
| `AUTH_TOKEN_REVOKED` | 401 | Token has been revoked |

---

### POST /api/v1/auth/logout
Revoke refresh token and disconnect WebSocket.

**Response (200):**
```json
{
  "data": null
}
```

---

## 2. User Endpoints

### GET /api/v1/users/me
Get current user profile.

**Response (200):**
```json
{
  "data": {
    "id": "550e8400-...",
    "phoneNumber": "+905321234567",
    "displayName": "Ahmet",
    "avatarUrl": "https://...",
    "about": "Merhaba!",
    "createdAt": "2026-01-15T10:30:00Z"
  }
}
```

---

### PATCH /api/v1/users/me
Update profile.

**Request:**
```json
{
  "displayName": "Ahmet Yılmaz",
  "about": "Muhabbet ediyoruz!"
}
```

---

### PUT /api/v1/users/me/avatar
Upload avatar image (multipart/form-data).

**Request:** `multipart/form-data` with field `file`

**Response (200):**
```json
{
  "data": {
    "avatarUrl": "https://..."
  }
}
```

---

### POST /api/v1/users/contacts/sync
Sync phone contacts to find registered users.

**Request:**
```json
{
  "phoneHashes": [
    "a1b2c3d4e5f6...",
    "f6e5d4c3b2a1..."
  ]
}
```

**Response (200):**
```json
{
  "data": {
    "matchedContacts": [
      {
        "userId": "770e8400-...",
        "phoneHash": "a1b2c3d4e5f6...",
        "displayName": "Mehmet",
        "avatarUrl": "https://..."
      }
    ]
  }
}
```

---

## 3. Conversation Endpoints

### GET /api/v1/conversations
List conversations (inbox).

**Query Params:**
- `cursor` (optional): pagination cursor
- `limit` (optional, default 20, max 50)

**Response (200):**
```json
{
  "data": {
    "items": [
      {
        "id": "880e8400-...",
        "type": "direct",
        "name": null,
        "avatarUrl": null,
        "participants": [
          {
            "userId": "770e8400-...",
            "displayName": "Mehmet",
            "avatarUrl": "https://...",
            "role": "member",
            "isOnline": true
          }
        ],
        "lastMessagePreview": "Merhaba, nasılsın?",
        "lastMessageAt": "2026-02-11T14:25:00Z",
        "unreadCount": 3,
        "createdAt": "2026-02-10T09:00:00Z"
      }
    ],
    "nextCursor": "eyJsYXN0...",
    "hasMore": true
  }
}
```

---

### POST /api/v1/conversations
Create a new conversation.

**Request (Direct):**
```json
{
  "type": "DIRECT",
  "participantIds": ["770e8400-..."]
}
```

**Request (Group):**
```json
{
  "type": "GROUP",
  "participantIds": ["770e8400-...", "880e8400-..."],
  "name": "Arkadaşlar"
}
```

**Response (201):**
```json
{
  "data": {
    "id": "990e8400-...",
    "type": "direct",
    "participants": [...]
  }
}
```

**Errors:**
| Code | HTTP | Description |
|------|------|-------------|
| `CONV_ALREADY_EXISTS` | 409 | Direct conversation already exists |
| `CONV_INVALID_PARTICIPANTS` | 400 | Invalid participant IDs |

---

### GET /api/v1/conversations/{conversationId}/messages
Get message history.

**Query Params:**
- `cursor` (optional): message ID to paginate from
- `limit` (optional, default 50, max 100)
- `direction` (optional, default `before`): `before` or `after` cursor

**Response (200):**
```json
{
  "data": {
    "items": [
      {
        "id": "msg-uuid-v7",
        "conversationId": "880e8400-...",
        "senderId": "550e8400-...",
        "contentType": "TEXT",
        "content": "Merhaba!",
        "replyToId": null,
        "mediaUrl": null,
        "thumbnailUrl": null,
        "status": "READ",
        "serverTimestamp": "2026-02-11T14:25:00Z",
        "clientTimestamp": "2026-02-11T14:24:59Z"
      }
    ],
    "nextCursor": "018d8f3a-...",
    "hasMore": true
  }
}
```

---

## 4. Media Endpoints

### POST /api/v1/media/upload
Upload a media file.

**Request:** `multipart/form-data`
- `file`: the media file
- `conversationId`: target conversation

**Response (200):**
```json
{
  "data": {
    "mediaId": "med-uuid",
    "url": "https://.../{key}",
    "thumbnailUrl": "https://.../{thumb_key}",
    "contentType": "image/jpeg",
    "sizeBytes": 245760
  }
}
```

**Errors:**
| Code | HTTP | Description |
|------|------|-------------|
| `MEDIA_TOO_LARGE` | 413 | File exceeds size limit |
| `MEDIA_UNSUPPORTED_TYPE` | 415 | File type not allowed |

---

### GET /api/v1/media/{mediaId}
Get media download URL (pre-signed, 1 hour expiry).

**Response (200):**
```json
{
  "data": {
    "url": "https://...?X-Amz-Signature=...",
    "expiresIn": 3600
  }
}
```

---

## 5. Device Endpoints

### PUT /api/v1/devices/push-token
Register or update push notification token.

**Request:**
```json
{
  "token": "fcm_token_here",
  "platform": "android"
}
```

---

## 6. WebSocket Protocol

### Connection
```
wss://host/ws?token={jwt_access_token}
```

On connect, client sends `GoOnline`. Server starts sending messages.

### Message Flow

#### Sending a message:
```
Client                          Server
  │                               │
  ├── SendMessage ───────────────→│  (client sends message)
  │                               ├── Validates, persists
  │                               ├── Generates serverTimestamp
  │←─────────────── ServerAck ────┤  (server ACKs receipt)
  │                               │
  │                               ├── Routes to recipient(s)
  │                               ├── NewMessage ──→ Recipient
  │                               │
  │←──── StatusUpdate(DELIVERED) ─┤  (recipient's device ACKed)
  │←──── StatusUpdate(READ) ──────┤  (recipient opened conversation)
```

#### Receiving a message:
```
Server                          Client
  │                               │
  ├── NewMessage ────────────────→│  (new message arrives)
  │                               ├── Stores locally
  │←─────────── AckMessage ───────┤  (client ACKs: DELIVERED)
  │                               │
  │  (user opens conversation)    │
  │←─────────── AckMessage ───────┤  (client ACKs: READ)
```

#### Typing indicator:
```
Client A                Server              Client B
  │                       │                    │
  ├── TypingIndicator ───→│                    │
  │   (isTyping: true)    ├── PresenceUpdate ─→│
  │                       │   (TYPING)         │
  │                       │                    │
  ├── TypingIndicator ───→│                    │
  │   (isTyping: false)   ├── PresenceUpdate ─→│
  │                       │   (ONLINE)         │
```

#### Heartbeat:
```
Client                          Server
  │                               │
  ├── Ping ──────────────────────→│  (every 30 seconds)
  │←──────────────────── Pong ────┤
  │                               │
  │  (no Pong within 90 seconds)  │
  ├── Reconnect ─────────────────→│
```

### Offline Message Delivery

When recipient reconnects:
1. Client sends last known `serverTimestamp` as query param: `?token={jwt}&lastSync={timestamp}`
2. Server queries: `WHERE server_timestamp > {lastSync} ORDER BY server_timestamp ASC LIMIT 500`
3. Server streams messages as `NewMessage` frames
4. Client ACKs each batch with `AckMessage(DELIVERED)`

### WebSocket Frame Format

All frames are JSON with a `type` discriminator:

```json
// Client → Server: Send a message
{
  "type": "message.send",
  "requestId": "req-uuid",
  "messageId": "msg-uuid-v7",
  "conversationId": "conv-uuid",
  "content": "Merhaba!",
  "contentType": "TEXT"
}

// Server → Client: New message received
{
  "type": "message.new",
  "messageId": "msg-uuid-v7",
  "conversationId": "conv-uuid",
  "senderId": "user-uuid",
  "senderName": "Ahmet",
  "content": "Merhaba!",
  "contentType": "TEXT",
  "serverTimestamp": 1707660300000
}

// Server → Client: ACK
{
  "type": "ack",
  "requestId": "req-uuid",
  "messageId": "msg-uuid-v7",
  "status": "OK",
  "serverTimestamp": 1707660300000
}

// Error
{
  "type": "error",
  "code": "AUTH_TOKEN_EXPIRED",
  "message": "WebSocket authentication failed"
}
```

---

## Error Code Registry

### Auth
| Code | Description |
|------|-------------|
| `AUTH_INVALID_PHONE` | Not a valid Turkish phone number (+905...) |
| `AUTH_OTP_COOLDOWN` | Must wait before requesting new OTP |
| `AUTH_OTP_RATE_LIMIT` | Too many OTP requests from this number |
| `AUTH_OTP_INVALID` | Wrong OTP code |
| `AUTH_OTP_EXPIRED` | OTP has expired (>5 min) |
| `AUTH_OTP_MAX_ATTEMPTS` | Maximum verification attempts exceeded |
| `AUTH_TOKEN_INVALID` | Invalid or malformed JWT |
| `AUTH_TOKEN_EXPIRED` | JWT has expired |
| `AUTH_TOKEN_REVOKED` | Refresh token has been revoked |
| `AUTH_UNAUTHORIZED` | Missing or invalid Authorization header |

### Messaging
| Code | Description |
|------|-------------|
| `MSG_CONVERSATION_NOT_FOUND` | Conversation does not exist |
| `MSG_NOT_MEMBER` | User is not a member of this conversation |
| `MSG_CONTENT_TOO_LONG` | Message exceeds 10,000 characters |
| `MSG_EMPTY_CONTENT` | Message content is empty |
| `MSG_DUPLICATE` | Message with this ID already processed (idempotency) |

### Conversation
| Code | Description |
|------|-------------|
| `CONV_ALREADY_EXISTS` | Direct conversation between these users exists |
| `CONV_INVALID_PARTICIPANTS` | One or more participant IDs are invalid |
| `CONV_NOT_FOUND` | Conversation not found |
| `CONV_MAX_MEMBERS` | Group has reached maximum member limit (256) |

### Media
| Code | Description |
|------|-------------|
| `MEDIA_TOO_LARGE` | File exceeds maximum size for its type |
| `MEDIA_UNSUPPORTED_TYPE` | File MIME type is not allowed |
| `MEDIA_NOT_FOUND` | Media file not found or expired |
| `MEDIA_UPLOAD_FAILED` | Failed to store media file |

### General
| Code | Description |
|------|-------------|
| `VALIDATION_ERROR` | Request validation failed (see message for details) |
| `INTERNAL_ERROR` | Unexpected server error |
| `RATE_LIMITED` | Too many requests |
