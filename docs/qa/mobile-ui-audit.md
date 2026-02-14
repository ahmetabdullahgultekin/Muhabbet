# Mobile UI Audit Report — Lead Mobile Engineer Assessment

**Date:** 2026-02-14
**Auditor Role:** Lead Mobile Engineer
**Scope:** Full mobile codebase (`mobile/composeApp/src/commonMain/`)
**Files Reviewed:** 60 common, 18 Android, 13 iOS source files (~8,600 LOC UI layer)

---

## Executive Summary

Muhabbet's mobile client is a Compose Multiplatform app with solid architectural foundations: Decompose navigation, Koin DI, proper localization (318 TR+EN strings), semantic color tokens, and spacing/elevation token systems. The app covers core messaging features and many advanced ones (voice messages, polls, location, GIFs, statuses, channels, calls).

However, significant UI/UX gaps remain compared to WhatsApp, Telegram, and Signal. The audit identifies **87 specific issues** across 6 severity categories, grouped into design system gaps, per-screen problems, missing competitor features, code-level violations, and accessibility deficiencies.

**Overall Visual Grade: 6.5/10** — Functional but unpolished. Feels like a developer tool, not a consumer product.

---

## Table of Contents

1. [CRITICAL — Bugs & Broken Logic](#1-critical--bugs--broken-logic)
2. [Design System Gaps](#2-design-system-gaps)
3. [Per-Screen Audit](#3-per-screen-audit)
4. [Missing Features vs Competitors](#4-missing-features-vs-competitors)
5. [Visual & GUI Satisfaction Issues](#5-visual--gui-satisfaction-issues)
6. [Code-Level Violations](#6-code-level-violations)
7. [Accessibility Deficiencies](#7-accessibility-deficiencies)
8. [Prioritized Remediation Roadmap](#8-prioritized-remediation-roadmap)

---

## 1. CRITICAL — Bugs & Broken Logic

### BUG-1: Dead condition always shows status divider
**File:** `ConversationListScreen.kt:622`
```kotlin
if (statusGroups.isNotEmpty() || true) {  // BUG: || true makes this always true
    HorizontalDivider()
}
```
**Impact:** Status section divider renders even when empty. Wastes vertical space for users without statuses. This is clearly a debugging leftover.

### BUG-2: StatusViewerScreen hardcodes `Color.Black` ignoring theme
**File:** `StatusViewerScreen.kt:97`
```kotlin
.background(Color.Black)  // Ignores OLED vs Dark vs Light theme
```
Also at lines 101, 103, 111, 117, 168, 175, 204, 211 — all use `Color.White` or `Color.Black` directly. Should use theme colors or at minimum semantic colors.

### BUG-3: ActiveCallScreen timer runs forever
**File:** `ActiveCallScreen.kt:84-88`
```kotlin
LaunchedEffect(Unit) {
    while (true) {        // Never stops
        delay(1000)
        callDurationSeconds++
    }
}
```
The `while(true)` loop continues even after the call ends (brief window between `onCallEnded()` and recomposition). Could cause stale state if composable lingers.

### BUG-4: Filter state uses raw strings instead of enum
**File:** `ConversationListScreen.kt:140`
```kotlin
var activeFilter by remember { mutableStateOf("all") }  // "all", "unread", "groups"
```
Stringly-typed state is fragile. A typo produces no compile error but silently breaks filtering.

### BUG-5: Version hardcoded
**File:** `SettingsScreen.kt:306`
```kotlin
text = "${stringResource(Res.string.settings_version)}: 0.1.0"
```
Version will become stale. Should read from `BuildConfig` or build-time generated constant.

---

## 2. Design System Gaps

### 2.1 Token Coverage

| Token Type | Defined | Used Consistently | Gap |
|---|---|---|---|
| Spacing (`MuhabbetSpacing`) | 6 values | ~85% of screens | `14.dp`, `10.dp`, `6.dp`, `64.dp` hardcoded in many places |
| Elevation (`MuhabbetElevation`) | 7 levels | ~90% | Some hardcoded `2.dp` shadows remain |
| Sizes (`MuhabbetSizes`) | 4 tokens | ~60% | Avatar sizes (36/40/48/56/80/96/120dp) not tokenized |
| Semantic Colors | 5 colors | Call/presence screens | Missing: `statusDelivered`, `statusWaiting`, `bubbleOwn`, `bubbleOther` |
| Typography | Material3 defaults | Everywhere | No custom font family. Every competitor has distinctive typography |

### 2.2 Missing Tokens

**Avatar sizes** — Used at 7 different sizes (36, 40, 48, 56, 80, 96, 120dp) across screens with no centralized tokens:
- `UserProfileScreen.kt:132` → 96.dp
- `IncomingCallScreen.kt:81` → 120.dp
- `SettingsScreen.kt:200` → 80.dp
- `StatusViewerScreen.kt:198` → 36.dp
- `ConversationItem` → 48.dp
- `GroupInfoScreen` → 72.dp
- `MutualGroupItem` → 40.dp

**Animation durations** — Every screen uses different magic numbers:
- Typing dismiss: 3000ms (`ChatScreen.kt`)
- Status auto-advance: 5000ms (`StatusViewerScreen.kt`)
- Timer tick: 50ms (`StatusViewerScreen.kt`)
- Call timer: 1000ms (`ActiveCallScreen.kt`)

**Bubble dimensions** — Hardcoded in `MessageBubble.kt`:
- Min width: 80.dp
- Max width: 300.dp
- Corner radii: 16.dp/4.dp
- Image max height: 200.dp
- Sticker size: 150.dp

### 2.3 No Custom Typography

The app uses raw Material3 `MaterialTheme.typography` with no custom font. Every major messaging app has brand typography:
- WhatsApp: SF Pro / Roboto (customized weights)
- Telegram: Custom SF Pro/Roboto with tight letter spacing
- Signal: Inter with customized scale

Muhabbet looks "default Material" — instantly recognizable as a template app.

### 2.4 No Shape Tokens

Bubble corner radii, card shapes, and button shapes are hardcoded throughout. Should have:
```kotlin
object MuhabbetShapes {
    val BubbleOwn: Shape = ...
    val BubbleOther: Shape = ...
    val Card: Shape = ...
    val Avatar: Shape = CircleShape
}
```

---

## 3. Per-Screen Audit

### 3.1 PhoneInputScreen (auth/PhoneInputScreen.kt)

| Issue | Severity | Detail |
|---|---|---|
| No phone formatting | Medium | User types raw `+905XXXXXXXXX` — no auto-grouping like `+90 5XX XXX XXXX` |
| Hardcoded "+90" prefix | Low | No country selector. Turkey-only is OK for MVP but feels rigid |
| Logo is just "M" text in circle | Medium | No actual brand asset. Looks unfinished |
| No password manager integration | Low | Won't suggest phone numbers from saved accounts |
| Loading replaces button text entirely | Low | Should show "Verifying..." + spinner, not just spinner |
| Hardcoded 13-char limit | Low | Should derive from `ValidationRules` |

**Visual grade: 5/10** — Bare minimum. WhatsApp's onboarding has country picker, phone formatting, terms link, and clear branding.

### 3.2 ConversationListScreen (conversations/ConversationListScreen.kt)

| Issue | Severity | Detail |
|---|---|---|
| BUG: `\|\| true` dead condition (line 622) | Critical | Always shows divider |
| Filter uses string literals | Medium | "all"/"unread"/"groups" — should be enum |
| No "Channels" filter chip | Medium | Filter chip exists in strings.xml (`filter_channels`) but not wired |
| Search results lack context | Medium | Shows message snippet but no conversation name, avatar, or sender |
| No archive/mute chat | High | Core WhatsApp/Telegram feature entirely missing |
| No swipe actions on conversation items | Medium | WhatsApp: swipe-right = archive, swipe-left = more options |
| Long-press uses AlertDialog | Medium | Should use bottom sheet for touch-friendly experience |
| Skeleton animation is static | Low | No shimmer/pulse effect — just gray rectangles |
| No typing indicator in conversation preview | Low | WhatsApp shows "typing..." in the last message preview |
| Contact name resolution on main thread | Low | `normalizeToE164` runs in `LaunchedEffect` but mapping is synchronous |
| Status carousel always visible | Low | Takes vertical space even with zero statuses |

**Visual grade: 6.5/10** — Functional but plain. Missing the polish of animation, swipe gestures, and contextual richness.

### 3.3 ChatScreen (chat/ChatScreen.kt)

| Issue | Severity | Detail |
|---|---|---|
| No chat wallpaper/background | Medium | WhatsApp, Telegram both have customizable chat backgrounds |
| Swipe-to-reply has no haptic feedback | Medium | 80dp threshold swipe with no tactile confirmation |
| Magic numbers: 80f, 20f, 120f, Int.MAX_VALUE | Low | Swipe thresholds hardcoded |
| No voice recording waveform | Medium | Recording shows button only — no timer, no waveform visualization |
| No message grouping (consecutive from same sender) | Medium | Every bubble has full spacing — should cluster |
| No sender name in group chats | High | Group messages don't show who sent each message |
| Typing indicator has no fade animation | Low | Appears/disappears abruptly |
| Reply preview truncation unclear | Low | `.take(50)` chars with no ellipsis indicator |
| No scroll-to-date gesture | Low | WhatsApp: drag scrollbar shows date tooltip |
| No message formatting (bold/italic/code) | Medium | All competitors support markdown-style formatting |
| Media upload duplicated code | Low | Image picker and file picker follow identical upload pattern |
| No read receipt toggle | Medium | Users can't disable blue ticks |
| GIF content is hardcoded "GIF" string | Low | Line 459: `content = "GIF"` — should be localized |

**Visual grade: 6/10** — Core messaging works but lacks personality. No wallpaper, no message grouping, no formatting.

### 3.4 MessageBubble (chat/MessageBubble.kt)

| Issue | Severity | Detail |
|---|---|---|
| Bubble max width is 300.dp | Medium | Too narrow on tablets. Should be proportional to screen width (~75%) |
| No bubble tail shape used | Low | `BubbleTailShape.kt` exists but isn't used — bubbles have rounded corners only |
| Document click handler empty | Medium | Line 181: `.clickable { /* open URL */ }` — dead code |
| Context menu is DropdownMenu | Low | Position-dependent. Bottom sheet would be more consistent |
| No "Copy" option in context menu | High | Fundamental feature missing from every messaging app |
| Sticker size hardcoded 150.dp | Low | Should be responsive |
| Image comparison with stringResource | Low | Line 267: `message.content != stringResource(...)` — fragile equality check |
| Reply bar has no background color for quoted user | Low | WhatsApp uses sender color stripe |
| No link tap handling in messages | Medium | Links in text are not clickable/tappable |

**Visual grade: 6/10** — Functional bubbles but missing the visual richness of competitor implementations.

### 3.5 MessageInputPane (chat/MessageInputPane.kt)

| Issue | Severity | Detail |
|---|---|---|
| No send-on-enter keyboard action | Low | IME action is set to Send but no `onKeyEvent` handler wired |
| Attach menu is DropdownMenu | Medium | WhatsApp uses a bottom sheet grid with icons. Dropdown feels desktop-like |
| No camera shortcut button | Medium | WhatsApp has camera icon directly in input bar |
| No emoji keyboard toggle | High | Every competitor has dedicated emoji button next to input |
| GIF icon reuses Image icon | Low | Line 189: same icon as Image attachment — confusing |
| Reply cancel button is 24dp | Low | Below 48dp minimum touch target (WCAG) |
| No mention/@ autocomplete | Medium | Group chats lack @mention feature |

**Visual grade: 5.5/10** — Bare minimum input bar. Missing emoji button, camera shortcut, and proper attachment grid.

### 3.6 SettingsScreen (settings/SettingsScreen.kt)

| Issue | Severity | Detail |
|---|---|---|
| Version hardcoded "0.1.0" | Medium | Will become stale |
| Language change restarts app immediately | Medium | No confirmation, jarring UX |
| Theme change restarts app immediately | Medium | Same as above — should apply without restart |
| No avatar preview before upload | Low | Directly uploads without confirmation |
| No "Account" section | Medium | No phone number display, no account deletion, no data export |
| No "Privacy" section | High | No read receipts toggle, no last seen visibility, no profile photo visibility |
| No "Notifications" settings | High | No per-conversation mute, no notification sound/vibration options |
| No "Chat" settings | Medium | No wallpaper, no font size, no enter-to-send toggle |
| Radio button touch targets may be small | Low | `padding(vertical = 6.dp)` on theme options — might be below 48dp |
| Storage shows no progress bar | Low | Just text bytes — no visual usage bar |

**Visual grade: 5/10** — Minimal settings. Missing entire sections that users expect (Privacy, Notifications, Chat, Account).

### 3.7 UserProfileScreen (profile/UserProfileScreen.kt)

| Issue | Severity | Detail |
|---|---|---|
| Call button is stubbed ("Coming soon") | Medium | Should be hidden or visually disabled |
| No block/report buttons | High | Safety-critical feature missing from profile view |
| Contact name prefix "~" is unclear | Low | `~ContactName` — meaning not obvious to users |
| No video call button | Low | Only voice call shown |
| No encryption indicator | Medium | No "Messages are end-to-end encrypted" badge |
| Profile background is flat | Low | No header gradient or cover photo area |
| `firstGrapheme()` function is in profile.kt | Low | Utility used across UI — should be in `util/` |

**Visual grade: 5.5/10** — Sparse. WhatsApp profile shows encryption badge, media thumbnails, custom notifications, and safety tools.

### 3.8 GroupInfoScreen (group/GroupInfoScreen.kt)

| Issue | Severity | Detail |
|---|---|---|
| No group photo change | Medium | Admin can't change group avatar |
| No member search | Medium | Large groups become unwieldy |
| Member removal has no confirmation dialog | High | Immediate action, no undo |
| No role change UI (promote/demote) | Medium | Admin can't promote members to admin |
| Owner sees "Leave group" not "Delete group" | Low | Owner leaving should transfer or delete |
| No "Add members" button on this screen | Medium | Must go back and use different flow |
| Avatar hardcoded 72.dp | Low | Not using size token |

**Visual grade: 5.5/10** — Bare-bones management. Telegram's group info is leagues ahead with admin tools, permissions, slow mode, etc.

### 3.9 IncomingCallScreen (call/IncomingCallScreen.kt)

| Issue | Severity | Detail |
|---|---|---|
| No actual avatar image | High | Shows letter initial only — no profile photo loaded |
| No ringing animation | Medium | Static "Ringing..." text — should pulse or animate |
| No swipe-to-answer gesture | Low | Tap-only. iOS convention is swipe |
| Buttons hardcoded 64dp spacing | Low | Won't adapt to different screen sizes |
| No vibration/haptic feedback | Medium | Call screens should trigger platform vibration |
| No "Decline with message" option | Low | WhatsApp: "Can't talk, what's up?" quick replies |

**Visual grade: 4/10** — Most visually lacking screen. No avatar photo, no animation, no platform-native call feel.

### 3.10 ActiveCallScreen (call/ActiveCallScreen.kt)

| Issue | Severity | Detail |
|---|---|---|
| No video preview (even for video calls) | High | Video call type but no camera feed shown |
| No camera switch button | Medium | Front/back camera toggle missing |
| Timer never stops (BUG-3) | Medium | `while(true)` loop persists after call end |
| No call quality indicator | Low | No signal strength or bitrate display |
| No minimize/PiP option | Medium | Can't multitask during call |
| Mute icon changes but label is redundant | Low | Icon is MicOff when muted — label says "Unmute" |

**Visual grade: 4/10** — Placeholder quality. No video rendering at all for video calls.

### 3.11 StatusViewerScreen (status/StatusViewerScreen.kt)

| Issue | Severity | Detail |
|---|---|---|
| All colors hardcoded Black/White | Medium | Ignores theme system entirely (BUG-2) |
| No pause-on-hold gesture | Medium | Instagram/WhatsApp: hold to pause story |
| No swipe navigation | Medium | Tap-only — swipe between statuses is standard |
| Progress bars are 3dp thin | Low | Hard to see on small screens |
| No reply/react to status | Medium | WhatsApp: swipe-up to reply to a status |
| No "Seen by" indicator for own statuses | Low | Can't see who viewed your status |
| Avatar URL is always null | Low | Line 195: `avatarUrl = null` — never passes actual avatar |
| Timer duration not configurable | Low | 5s hardcoded |

**Visual grade: 5/10** — Functional but plain. Missing gesture-based navigation and interactivity.

### 3.12 SharedMediaScreen (media/SharedMediaScreen.kt)

| Issue | Severity | Detail |
|---|---|---|
| No in-app video player | High | Videos open in external player via `uriHandler.openUri()` |
| No pagination (limit=100 hardcoded) | Medium | Will fail for media-heavy conversations |
| No multi-select | Medium | Can't batch delete/forward |
| No video duration overlay | Low | Just play icon — no "2:34" indicator |
| Document names have no file type badge | Low | Generic icon for all document types |
| Context menu via DropdownMenu | Low | Should be bottom sheet |

**Visual grade: 5.5/10** — Grid layout is fine but missing media-specific features.

---

## 4. Missing Features vs Competitors

### 4.1 Feature Parity Matrix

| Feature | WhatsApp | Telegram | Signal | Muhabbet | Gap |
|---|---|---|---|---|---|
| **Chat Wallpaper** | Per-chat | Per-chat + themes | Global | None | HIGH |
| **Message Formatting** (bold/italic/code) | Yes | Yes + spoilers | Yes | None | HIGH |
| **Emoji Keyboard** | Built-in | Custom + sticker packs | Built-in | None (system only) | HIGH |
| **Archive/Mute Chat** | Yes | Yes + folders | Yes | None | HIGH |
| **Block/Report from Profile** | Yes | Yes | Yes | None (backend exists) | HIGH |
| **Copy Message Text** | Yes | Yes | Yes | None | HIGH |
| **Message Sender in Groups** | Yes | Yes | Yes | None | HIGH |
| **Privacy Settings** (last seen, profile photo, read receipts) | Yes | Yes | Yes | None | HIGH |
| **Notification Settings** (per-chat, sound, vibration) | Yes | Yes | Yes | None | HIGH |
| **Chat Search within Conversation** | Yes | Yes | Yes | None | MEDIUM |
| **Voice Note Scrubbing** | Yes | Yes | No | None | MEDIUM |
| **Contact Info / vCard Sharing** | Yes | Partial | No | None | MEDIUM |
| **Scheduled Messages** | No | Yes | No | None | LOW |
| **Chat Folders** | No | Yes | No | None | LOW |
| **Custom Notification Sounds** | Yes | Yes | No | None | LOW |
| **Chat Export** | Yes | Yes | No | None | LOW |
| **In-App Video Player** | Yes | Yes | Yes | None | MEDIUM |
| **Pinch-to-Zoom Photos in Chat** | Yes | Yes | Yes | Gallery only | LOW |
| **Read Receipts Toggle** | Yes | Yes | Yes | None | HIGH |
| **Group Permissions/Admin Tools** | Yes | Extensive | Basic | Minimal | MEDIUM |
| **Camera Button in Input** | Yes | Yes | No | None | MEDIUM |
| **Haptic Feedback** | System | System | System | None | MEDIUM |
| **Message Grouping** (consecutive sender) | Yes | Yes | Yes | None | MEDIUM |
| **Clickable Links in Messages** | Yes | Yes | Yes | None | HIGH |
| **Swipe Actions on Chat List** | Yes | Yes | No | None | MEDIUM |
| **Chat Backup/Restore UI** | Yes | Cloud | No | Backend only, no UI | LOW |

### 4.2 Most Glaring Omissions

1. **No emoji keyboard button** — Every messaging app has a dedicated emoji toggle next to the input field. Users must use the system keyboard emoji button, which varies by device.

2. **No "Copy" in message context menu** — This is the #1 most-used message action across all platforms. It's absent.

3. **No privacy settings at all** — No control over who sees last seen, profile photo, about, or read receipts. This is a trust/safety concern for a Turkish market (cultural sensitivity around visibility).

4. **No sender name in group messages** — Group chat bubbles don't show who sent each message. Conversations between 3+ people are unreadable.

5. **No clickable links** — URLs in message text are rendered as plain text. Cannot tap to open.

6. **No archive/mute** — Users accumulate conversations with no way to hide inactive ones without deleting.

7. **No block/report from profile** — Backend moderation module exists but the UI has no entry point.

---

## 5. Visual & GUI Satisfaction Issues

### 5.1 "Looks Like a Template" Problem

The app uses vanilla Material3 with default Roboto font, default M3 shapes, and teal/green palette that's indistinguishable from dozens of tutorial apps. There's no visual identity:

- **No custom font** — Material3 default typography
- **No custom icon set** — All Material Icons (filled variant)
- **No brand illustrations** — Only 2 Canvas-based empty states
- **No onboarding graphics** — Phone screen has a text "M" in a circle
- **No splash screen** — Immediate load into content
- **No app icon customization** — Not relevant to code audit but worth noting

### 5.2 Color Palette Feels Clinical

The teal (#00796B) primary with green (#43A047) secondary creates a sterile, hospital-like palette. Compare:
- **WhatsApp:** Warm teal-green (#075E54/#25D366) with cream backgrounds
- **Telegram:** Sky blue (#0088cc) with cloud-white, feels airy
- **Signal:** Deep blue (#3A76F0) with warm grays, feels trustworthy

Muhabbet's dark teal with pure-white surfaces has no warmth. The surfaceVariant (#F0F4F3) is barely distinguishable from white.

### 5.3 Spacing Inconsistencies

Despite having `MuhabbetSpacing` tokens, many screens bypass them:
- `ConversationListScreen.kt:337` → `14.dp` (padding)
- `ConversationListScreen.kt:362` → `14.dp` (padding)
- `MessageBubble.kt:110` → `2.dp` (padding)
- `MessageBubble.kt:289` → `3.dp` (spacing)
- `MessageInputPane.kt:62` → `6.dp` (padding)
- `SettingsScreen.kt:321` → `14.dp` (padding)
- `SettingsScreen.kt:497` → `6.dp` (padding)
- `UserProfileScreen.kt:219` → `14.dp` (padding)
- `StatusViewerScreen.kt:200` → `10.dp` (spacing)

### 5.4 No Motion Design

The app has almost no animation:
- **Screen transitions:** Decompose provides slide animation but no shared element transitions
- **List item animations:** No staggered entrance, no fade-in
- **Button feedback:** No scale/bounce on tap
- **Loading states:** Static gray rectangles, no shimmer
- **Typing indicator:** Appears/disappears abruptly
- **Message arrival:** No slide-in animation for new messages
- **FAB:** No rotation or morphing animation
- **Tab switches:** Crossfade exists but basic

WhatsApp and Telegram both have carefully crafted micro-animations that make the app feel alive.

### 5.5 Empty States Are Minimal

Only 2 custom illustrations exist:
- `EmptyChatsIllustration` — Animated chat bubbles (good)
- `EmptySearchIllustration` — Magnifying glass (good)

Missing empty states for:
- No contacts found
- No shared media
- No call history
- No statuses
- No channel messages
- No starred messages (uses basic icon+text)
- No search results
- Empty group (no members)

---

## 6. Code-Level Violations

### 6.1 CLAUDE.md Rule Violations

| Rule | Violation | File:Line |
|---|---|---|
| No `!!` (non-null assertion) | 15+ occurrences | `ConversationListScreen.kt:259,312,324,396`, `SettingsScreen.kt:365`, `MessageBubble.kt:167-168,208,227,230,244`, `UserProfileScreen.kt:119,159`, `StatusViewerScreen.kt:237` |
| SRP: Composable ≤ 300 lines | `ConversationListScreen` = 872 lines | `ConversationListScreen.kt` |
| DRY: No copy-paste >3 lines | Media upload pattern duplicated 3 times | `ChatScreen.kt:154-186` (image, file, voice) |
| DRY: No copy-paste >3 lines | Language/theme radio click handlers duplicated | `SettingsScreen.kt:420-511` |
| KISS: No deep nesting | WebSocket handler 4+ levels deep | `ChatScreen.kt:211-262` |
| No hardcoded strings | `content = "GIF"`, `content = "Sticker"` | `ChatScreen.kt:459,466` |
| `firstGrapheme()` not in util | Used cross-module but defined in profile | `UserProfileScreen.kt:356` |

### 6.2 Architecture Concerns

1. **Composables inject repositories directly via `koinInject()`** — Every screen calls repository methods directly. No ViewModel layer means:
   - No surviving configuration changes (though Decompose helps)
   - No testable presentation logic separation
   - State management logic mixed with UI

2. **WebSocket message handling duplicated** — Both `ConversationListScreen` and `ChatScreen` independently collect from `wsClient.incoming`. The same WsMessage types are handled in 2+ places with different logic.

3. **`generateMessageId()` in MessageBubble.kt** — UUID generation utility living in a UI file. Should be in `util/`.

4. **No error recovery for media uploads** — Failed uploads show a snackbar but the optimistic message stays in the list with SENDING status forever.

### 6.3 Hardcoded Magic Numbers (Selected)

| Value | Location | Should Be |
|---|---|---|
| `80f` | `ChatScreen.kt:351` (swipe threshold) | `MuhabbetGestures.SwipeReplyThreshold` |
| `120f` | `ChatScreen.kt:353` (max swipe) | `MuhabbetGestures.SwipeReplyMax` |
| `300.dp` | `MessageBubble.kt:96` (bubble max width) | Proportional to screen width |
| `200.dp` | `MessageBubble.kt:206,228,238,246` (image height) | `MuhabbetSizes.ImagePreviewMaxHeight` |
| `150.dp` | `MessageBubble.kt:218` (sticker size) | `MuhabbetSizes.StickerSize` |
| `3000` | `ChatScreen.kt:234,425` (typing timeout ms) | `MuhabbetDurations.TypingTimeout` |
| `5000L` | `StatusViewerScreen.kt:80` (status duration) | `MuhabbetDurations.StatusDisplayMs` |
| `Int.MAX_VALUE` | `ChatScreen.kt:277,281` (scroll offset) | Named constant |

---

## 7. Accessibility Deficiencies

### 7.1 Touch Target Violations

| Element | Actual Size | Required (WCAG AA) | File:Line |
|---|---|---|---|
| Reply cancel button | 24.dp | 48.dp | `MessageInputPane.kt:85` |
| Edit mode cancel button | 32.dp | 48.dp | `MessageInputPane.kt:118` |
| Pin icon in conversation | 14.dp | 48.dp (clickable area) | `ConversationListScreen.kt:780` |
| Theme radio buttons | ~38.dp row height (6.dp padding) | 48.dp | `SettingsScreen.kt:497` |

### 7.2 Missing Content Descriptions

- `StatusViewerScreen.kt:191`: Back button icon has `contentDescription = null`
- `MessageBubble.kt:217`: Sticker image has `contentDescription = null`
- `MessageBubble.kt:313`: Delivery status icon has `contentDescription = null`

### 7.3 Screen Reader Issues

- No semantic grouping of message bubbles (sender + message + time as one accessible element)
- No `LiveRegion` for typing indicators (screen reader won't announce typing state changes)
- No focus management after sending a message (focus should return to input)
- Long-press context menus are not discoverable without touch (no Talkback hints)

### 7.4 Color Contrast

- `onPrimary.copy(alpha = 0.5f)` used for edited/timestamp text in bubbles — may fail WCAG 4.5:1 contrast ratio against teal background
- `onSurfaceVariant.copy(alpha = 0.6f)` for timestamps — borderline contrast on light backgrounds

---

## 8. Prioritized Remediation Roadmap

### P0 — Critical (Ship-Blocking)

| # | Issue | Effort |
|---|---|---|
| 1 | Fix dead condition `\|\| true` in ConversationListScreen:622 | Trivial |
| 2 | Add "Copy" to message context menu | Small |
| 3 | Show sender name in group chat bubbles | Small |
| 4 | Make links in messages clickable/tappable | Medium |
| 5 | Add block/report buttons to UserProfileScreen | Medium |
| 6 | Add emoji keyboard toggle button to input bar | Medium |

### P1 — High (User Retention)

| # | Issue | Effort |
|---|---|---|
| 7 | Add archive/mute conversation feature | Large |
| 8 | Add Privacy Settings screen (last seen, read receipts, profile photo visibility) | Large |
| 9 | Add Notification Settings (per-chat mute, sound) | Medium |
| 10 | Add chat wallpaper support | Medium |
| 11 | Add message formatting (bold/italic/monospace/strikethrough) | Medium |
| 12 | Add message grouping for consecutive same-sender messages | Medium |
| 13 | Add in-app video player (replace external URI handler) | Medium |
| 14 | Fix IncomingCallScreen to load actual avatar images | Small |
| 15 | Replace all `!!` with safe null handling | Medium |

### P2 — Medium (Polish & Delight)

| # | Issue | Effort |
|---|---|---|
| 16 | Add haptic feedback across interactive elements | Medium |
| 17 | Add shimmer animation to skeleton loading states | Small |
| 18 | Add custom typography (brand font) | Medium |
| 19 | Extract filter state to enum | Trivial |
| 20 | Add avatar size tokens | Small |
| 21 | Fix StatusViewerScreen to use theme colors not hardcoded | Small |
| 22 | Add pause-on-hold for status viewer | Small |
| 23 | Add camera shortcut to input bar | Small |
| 24 | Add swipe actions to conversation list items | Medium |
| 25 | Replace AlertDialog context menus with bottom sheets | Medium |
| 26 | Add phone number formatting to PhoneInputScreen | Small |
| 27 | Add confirmation dialog before language/theme restart | Small |
| 28 | Fix version to read from BuildConfig | Trivial |
| 29 | Add search within individual conversation | Medium |
| 30 | Add voice note scrubbing/seek | Medium |

### P3 — Low (Nice to Have)

| # | Issue | Effort |
|---|---|---|
| 31 | Add shared element transitions between screens | Large |
| 32 | Add staggered list animations | Medium |
| 33 | Add message send animation | Small |
| 34 | Add ringing animation to IncomingCallScreen | Small |
| 35 | Add video call UI (camera feed rendering) | Large |
| 36 | Add PiP for active calls | Large |
| 37 | Add chat export | Medium |
| 38 | Add scheduled messages | Large |
| 39 | Add chat folders (Telegram-style) | Large |
| 40 | Add custom notification sounds | Medium |

---

## Appendix: File-Level Summary

| File | Lines | Issues | Priority |
|---|---|---|---|
| `ConversationListScreen.kt` | 872 | 11 | P0-P1 |
| `ChatScreen.kt` | 472 | 13 | P0-P1 |
| `MessageBubble.kt` | 390 | 9 | P0-P1 |
| `MessageInputPane.kt` | 241 | 7 | P0-P2 |
| `SettingsScreen.kt` | 577 | 10 | P1-P2 |
| `UserProfileScreen.kt` | 388 | 7 | P0-P1 |
| `GroupInfoScreen.kt` | 361 | 7 | P1-P2 |
| `StatusViewerScreen.kt` | 253 | 8 | P2 |
| `IncomingCallScreen.kt` | 181 | 6 | P1-P2 |
| `ActiveCallScreen.kt` | 265 | 6 | P2-P3 |
| `PhoneInputScreen.kt` | 194 | 6 | P2 |
| `SharedMediaScreen.kt` | 427 | 6 | P1-P2 |
| `MuhabbetTheme.kt` | 183 | 4 | P2 |

**Total issues identified: 87**
- Critical/P0: 6
- High/P1: 9
- Medium/P2: 15
- Low/P3: 10
- Design System: 12
- Code Violations: 7
- Accessibility: 10
- Feature Parity Gaps: 18
