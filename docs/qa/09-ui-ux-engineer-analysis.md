# 09 — Lead UI/UX Engineer Analysis

> Comprehensive review of Muhabbet's mobile UI layer: architecture, accessibility, design system, interaction patterns, and remediation roadmap.

**Review Date:** 2026-02-14
**Scope:** `/mobile/composeApp/src/commonMain/kotlin/com/muhabbet/app/ui/` — 34 files, 8,407 lines
**Framework:** Compose Multiplatform (CMP) + Decompose navigation + Material 3

---

## 1. Executive Summary

Muhabbet's mobile UI implements a feature-rich messaging experience across 14 navigation destinations with 3 theme variants. The codebase demonstrates strong architectural decisions (Decompose navigation, Koin DI, shared KMP module) but has significant gaps in **accessibility**, **design system consistency**, and **production polish**.

### Scorecard

| Dimension | Score | Rating |
|-----------|-------|--------|
| Feature completeness | 9/10 | Excellent |
| Navigation architecture | 9/10 | Excellent |
| Theme system | 7/10 | Good |
| Accessibility (a11y) | 3/10 | Critical |
| Design consistency | 5/10 | Needs work |
| Performance patterns | 7/10 | Good |
| Localization | 8/10 | Very good |
| Testability | 2/10 | Critical |

---

## 2. UI Architecture Overview

### 2.1 Screen Inventory (34 files)

| Module | Files | Lines | Key Screens |
|--------|-------|-------|-------------|
| Authentication | 3 | 561 | PhoneInput, OtpVerify, ProfileSetup |
| Chat/Messaging | 15 | 2,564 | ChatScreen, MessageBubble, MessageInputPane, ChatDialogs, VoiceBubble, ReactionBar, PollBubble, LocationBubble, LinkPreviewCard, GifStickerPicker, TypingIndicatorBubble, DateSeparator, BubbleTailShape, VoiceRecordButton, MessageInfoScreen |
| Conversations | 2 | 1,150 | ConversationListScreen (837), NewConversationScreen |
| Groups | 2 | 695 | GroupInfoScreen, ChannelListScreen |
| Calls | 3 | 617 | IncomingCallScreen, ActiveCallScreen, CallHistoryScreen |
| Profile | 2 | 798 | UserProfileScreen, StatusViewerScreen |
| Media | 2 | 656 | SharedMediaScreen, StarredMessagesScreen |
| Settings | 1 | 582 | SettingsScreen |
| Components | 2 | 250 | UserAvatar, EmptyStateIllustration |
| Theme | 1 | 110 | MuhabbetTheme |

### 2.2 Navigation Architecture (Decompose)

```
App.kt
  └─ RootComponent (fade transition)
       ├─ AuthComponent (slide transition)
       │   ├─ PhoneInputScreen
       │   ├─ OtpVerifyScreen
       │   └─ ProfileSetupScreen
       │
       └─ MainComponent (slide transition) — 14 destinations
            ├─ ConversationListScreen (root)
            ├─ ChatScreen (with scrollToMessageId)
            ├─ NewConversationScreen
            ├─ CreateGroupScreen
            ├─ GroupInfoScreen
            ├─ UserProfileScreen (with contactName, conversationId)
            ├─ StatusViewerScreen
            ├─ SettingsScreen
            ├─ StarredMessagesScreen
            ├─ SharedMediaScreen
            ├─ MessageInfoScreen
            ├─ IncomingCallScreen
            ├─ ActiveCallScreen
            └─ CallHistoryScreen
```

**Navigation patterns:**
- `push()` — sequential screens (auth flow, settings)
- `navigate()` — smart stack deduplication (profiles, media)
- `replaceAll()` — auth/logout transitions
- `pop()` + `_refreshTrigger` StateFlow — back navigation with list refresh

**Verdict:** Navigation architecture is **excellent** — type-safe configs, proper serialization, clean separation of concerns.

### 2.3 State Management

| Pattern | Usage | Files |
|---------|-------|-------|
| `mutableStateOf()` | Single values (loading, text, dialogs) | All screens |
| `mutableStateMapOf()` | Maps (typing indicators, presence) | ConversationList, ChatScreen |
| `LaunchedEffect()` | Data loading, WS subscriptions | All screens |
| `DisposableEffect()` | Cleanup (WS disconnect, call engine) | ActiveCallScreen |
| `derivedStateOf()` | Computed values (scroll position) | ChatScreen |
| `snapshotFlow()` | Reactive state transitions | ChatScreen |
| `collectAsState()` | StateFlow → State | ConversationListScreen |
| `rememberCoroutineScope()` | UI-initiated async actions | All screens |

**Pattern:** Direct composable state (no separate ViewModel classes). Koin-injected repositories for data access.

**Verdict:** Acceptable for MVP. Consider extracting to ViewModels when screens exceed 500 lines or share state across navigation.

---

## 3. Design System Assessment

### 3.1 Color Palette (3 Themes)

| Token | Light | Dark | OLED Black |
|-------|-------|------|------------|
| Primary | Teal 700 (`#00796B`) | Teal 200 (`#80CBC4`) | Teal 200 |
| Secondary | Green 600 (`#43A047`) | Green 300 (`#81C784`) | Green 300 |
| Tertiary | Amber 600 (`#FFB300`) | Amber 600 | Amber 600 |
| Surface | White | Dark gray (`#1C1B1F`) | Pure black (`#000000`) |
| Surface Variant | Teal 50 (`#E0F2F1`) | Dark gray (`#2C2C2E`) | Near black (`#161618`) |
| Error | Red 700 (`#D32F2F`) | Red 400 (`#EF5350`) | Red 400 |

**Strengths:**
- Three well-defined themes (Light, Dark, OLED Black)
- Teal primary aligns with messaging app conventions (WhatsApp green family)
- Material 3 `colorScheme()` properly used

**Weaknesses:**
- No custom typography scale defined (uses M3 defaults)
- No elevation scale defined
- No spacing tokens defined
- Missing semantic color tokens (online indicator, read receipt, missed call)

### 3.2 Hardcoded Colors (Design System Violations)

| File | Color | Usage | Should Use |
|------|-------|-------|------------|
| ConversationListScreen.kt | `Color(0xFF4CAF50)` | Online indicator dot | Semantic: `statusOnline` |
| UserProfileScreen.kt | `Color(0xFF4CAF50)` | Online status text | Semantic: `statusOnline` |
| MessageInfoScreen.kt | `Color(0xFF4FC3F7)` | Read receipt indicator | Semantic: `statusRead` |
| ActiveCallScreen.kt | `Color(0xFFE53935)` | End call button | `colorScheme.error` |
| IncomingCallScreen.kt | `Color(0xFFE53935)` | Decline button | `colorScheme.error` |
| IncomingCallScreen.kt | `Color(0xFF43A047)` | Accept button | `colorScheme.secondary` |
| CallHistoryScreen.kt | `Color(0xFFE53935)` | Missed call indicator | `colorScheme.error` |

**Impact:** These 8 hardcoded colors won't adapt to theme changes. The online indicator green and read receipt cyan are particularly problematic — they may have insufficient contrast in dark/OLED modes.

### 3.3 Typography Inconsistencies

**MaterialTheme.typography usage:** 136 occurrences across all screens (good baseline).

**Violations — inline font overrides:**

| File | Issue |
|------|-------|
| UserAvatar.kt | Hardcoded `fontSize` (36.sp, 28.sp, 18.sp, 14.sp) for avatar initials |
| StatusViewerScreen.kt | `fontSize = if (...) 16.sp else 24.sp` — conditional font size |
| GroupInfoScreen.kt | Inline `fontWeight = FontWeight.Bold/Medium` on typed text |
| UserProfileScreen.kt | Inline `fontWeight = FontWeight.Bold/Medium` duplicated |
| NewConversationScreen.kt | Inline `fontWeight = FontWeight.Medium` on bodyLarge |
| SettingsScreen.kt | Inline `fontWeight = FontWeight.Bold` on titleSmall |
| StarredMessagesScreen.kt | Inline `fontWeight = FontWeight.Bold` on primary text |
| PhoneInputScreen.kt | Inline `fontWeight = FontWeight.Bold` on logo text |

### 3.4 Spacing Patterns (No Token System)

Most frequently used spacing values:

| Value | Occurrences | Usage |
|-------|-------------|-------|
| `4.dp` | 30+ | Small gaps, icon spacing |
| `8.dp` | 40+ | Component gaps, padding |
| `12.dp` | 50+ | List item vertical padding |
| `16.dp` | 100+ | Horizontal screen padding |
| `24.dp` | 80+ | Section spacing |
| `32.dp` | 40+ | Large vertical gaps |

**Recommendation:** Extract to spacing tokens:
```kotlin
object MuhabbetSpacing {
    val XSmall = 4.dp
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 16.dp
    val XLarge = 24.dp
    val XXLarge = 32.dp
}
```

### 3.5 Elevation Inconsistencies

| Component | Tonal Elevation | Shadow Elevation |
|-----------|----------------|-----------------|
| MessageBubble | 1.dp | 1.dp |
| MessageInputPane | 2.dp | — |
| ReplyPreviewBar | 4.dp | — |
| TopAppBar | M3 default | — |

No consistent elevation scale — makes the depth hierarchy feel arbitrary.

---

## 4. Accessibility Audit (WCAG 2.1 + Material Design Guidelines)

### 4.1 Critical: Missing Content Descriptions (28+ violations)

Screen readers (TalkBack/VoiceOver) cannot announce icon purposes. This is a **WCAG 2.1 Level A failure** (Success Criterion 1.1.1: Non-text Content).

**By screen:**

| Screen | Missing Descriptions | Icons Affected |
|--------|---------------------|----------------|
| MessageInputPane.kt | 7 | Reply, Close, Image, Document, Poll, Location, Mic |
| MessageBubble.kt | 8 | Forward indicator, Image, Video, Document, Reply, Star, Edit, Delete |
| ConversationListScreen.kt | 4 | FAB (Add), Search, Close, Settings |
| ChatScreen.kt | 3 | Back arrow, Timer on/off |
| GroupInfoScreen.kt | 3 | Back, Edit, Leave |
| UserProfileScreen.kt | 1 | Back arrow |
| SharedMediaScreen.kt | 1 | Back arrow |
| GifStickerPicker.kt | 1 | Close |
| UserAvatar.kt | 1 | Group icon |

### 4.2 Critical: Touch Target Sizes

Material Design requires minimum **48x48dp** touch targets. Violations:

| Component | Measured Size | Required | File |
|-----------|-------------|----------|------|
| VoiceBubble play/pause | 36dp | 48dp | VoiceBubble.kt |
| Reaction emoji buttons | 36dp | 48dp | ReactionBar.kt |
| Profile action buttons | ~36dp (12dp padding) | 48dp | UserProfileScreen.kt |
| Context menu icons | 20dp icon, no button wrapper | 48dp | MessageBubble.kt |

### 4.3 Critical: No Semantic Annotations

Zero `Modifier.semantics { }` blocks found across all 34 UI files. This means:
- Interactive elements without `role = Role.Button` — screen readers may not announce them as tappable
- Custom components (swipe-to-reply, long-press menus) are invisible to assistive technology
- No custom accessibility actions defined

### 4.4 Major: Missing Keyboard/IME Configuration

| Input Field | KeyboardType | ImeAction | Issue |
|-------------|-------------|-----------|-------|
| OtpVerifyScreen | `Number` | **Missing** | No "Done" button on keyboard |
| PhoneInputScreen | `Phone` | **Missing** | No "Next" button on keyboard |
| MessageInputPane | Default | **Missing** | No IME config at all |
| Search fields | Default | **Missing** | No "Search" action |

### 4.5 Minor: Color Contrast Concerns

| Element | Foreground | Background | Estimated Ratio | WCAG AA (4.5:1) |
|---------|-----------|-----------|-----------------|-----------------|
| Online dot (dark mode) | `#4CAF50` | `#1C1B1F` | ~5.8:1 | Pass |
| Online dot (OLED) | `#4CAF50` | `#000000` | ~6.5:1 | Pass |
| Read receipt cyan (dark) | `#4FC3F7` | `#1C1B1F` | ~7.2:1 | Pass |
| Amber tertiary (dark) | `#FFB300` | `#1C1B1F` | ~8.9:1 | Pass |

Note: While estimated ratios pass, these should be verified with actual component backgrounds, not just surface colors.

---

## 5. Interaction Design Review

### 5.1 Strengths

| Pattern | Implementation | Quality |
|---------|---------------|---------|
| Swipe-to-reply | Horizontal drag gestures on messages | Good |
| Long-press context menu | `combinedClickable` on messages and conversations | Good |
| Pull-to-refresh | `PullToRefreshBox` on conversation list | Good |
| Pinch-to-zoom | `transformable` state (1x–5x) in media viewer | Good |
| Optimistic UI | Local state updates before server confirms | Good |
| Real-time updates | `wsClient.incoming` SharedFlow broadcasts | Good |
| Cursor pagination | `LazyColumn` with scroll-to-load-more | Good |
| Bubble tails | Custom `BubbleTailShape` with Bezier curves | Good |
| Empty states | Animated `EmptyChatsIllustration` with floating motion | Good |

### 5.2 Issues

| Issue | Severity | Details |
|-------|----------|---------|
| No skeleton loading states | Major | Conversation list shows only `CircularProgressIndicator` while loading — should show shimmer placeholders |
| No image error states | Major | `AsyncImage` components have no error/fallback display — blank space on network failure |
| Filter chip toggle behavior | Major | Clicking selected filter chip toggles it off (radio behavior) — contradicts multi-select chip visual pattern |
| Search not clearing on close | Major | Search text persists when search bar is toggled off and back on |
| Edit mode not visually distinct | Minor | Edit mode only changes send button color to tertiary — no banner or clear label |
| No haptic feedback | Minor | Message reactions, swipe-to-reply have no haptic feedback |
| No unread badge animation | Minor | Badge appears/disappears abruptly — should fade in/out |
| Keyboard covers dialogs | Minor | Alert dialogs with text inputs don't handle keyboard show/hide |
| No "scroll to bottom" indicator | Minor | Chat shows FAB but no unread count on it |

### 5.3 Animation Inventory

| Animation | Duration | Implementation |
|-----------|----------|---------------|
| Screen transitions (Root) | 300ms | Decompose `fade()` |
| Screen transitions (Main) | 300ms | Decompose `slide()` |
| Typing indicator dots | Loop (2s) | `EmptyChatsIllustration` infinite animation |
| Empty state floating | 2000ms loop | `animateFloatAsState` + `RepeatMode.Reverse` |
| Tab transitions (SharedMedia) | 300ms | `Crossfade` |
| Pull-to-refresh | Material 3 default | `PullToRefreshBox` |

**Missing animations:**
- Message bubble enter animation
- Unread badge fade in/out
- Edit mode banner slide in
- Dialog appear/dismiss transitions
- List item reorder animations (pinned chats)

---

## 6. Localization Assessment

### 6.1 String Resources

| Locale | File | Strings | Status |
|--------|------|---------|--------|
| Turkish (default) | `values/strings.xml` | 238 | Complete |
| English | `values-en/strings.xml` | 238 | Complete |

**All 238 keys present in both locales — no missing translations.**

### 6.2 String Categories

| Category | Count | Coverage |
|----------|-------|----------|
| Chat/Messaging | 45 | Full |
| Authentication | 22 | Full |
| Settings | 18 | Full |
| Calls | 19 | Full |
| Groups | 15 | Full |
| Media/Storage | 12 | Full |
| Status/Stories | 8 | Full |
| Notifications | 7 | Full |
| Errors | 10 | Full |
| UI Elements | 82 | Full |

### 6.3 Hardcoded String Violations

| File | String | Severity |
|------|--------|----------|
| PhoneInputScreen.kt:72 | `"M"` (logo letter) | Low — brand constant |
| OtpVerifyScreen.kt:97 | `"Dev Mode — Code: $mockCode"` | Medium — should gate behind debug build |

### 6.4 RTL Readiness

| Aspect | Status | Notes |
|--------|--------|-------|
| `Icons.AutoMirrored.*` | Used | ArrowBack, Reply, Send, Forward |
| `Arrangement.Start/End` | Used | Not hardcoded Left/Right |
| Explicit RTL layout | Not set | No `LayoutDirection.Rtl` configuration |
| Arabic locale strings | Not present | Not required for Turkish MVP |

**Verdict:** Good foundation for RTL; no blockers for Turkish market.

---

## 7. Performance Considerations

### 7.1 Good Patterns

| Pattern | Usage |
|---------|-------|
| `LazyColumn` for lists | Conversations, messages, contacts, media |
| Cursor pagination | Messages load in pages, not all at once |
| `derivedStateOf()` | Computed scroll position in ChatScreen |
| `remember { }` for expensive objects | CallEngine, coroutine scopes |
| `DisposableEffect` cleanup | CallEngine disconnection |
| `snapshotFlow()` | Reactive scroll position tracking |

### 7.2 Concerns

| Issue | Risk | Details |
|-------|------|---------|
| `LaunchedEffect(Unit)` WS collection | Medium | Unbounded collection on `wsClient.incoming` in ConversationListScreen — may accumulate if composable recomposes |
| No `key()` on some list items | Low | Missing explicit keys in some `LazyColumn` items could cause unnecessary recomposition |
| `EmptyChatsIllustration` infinite animation | Low | Continuous 2000ms animation loop on low-end devices |
| `mutableStateMapOf()` for typing indicators | Low | Map recomposition on any key change — fine for small maps |

### 7.3 Image Loading

- **Library:** Coil 3 (`AsyncImage`)
- **Missing:** Error placeholders, loading skeletons, memory cache configuration
- **Risk:** Images failing silently leave blank spaces in UI

---

## 8. Testability Assessment

### 8.1 Current State

| Metric | Value |
|--------|-------|
| `testTag` usage | **0** (zero files) |
| `Modifier.semantics` usage | **0** (zero files) |
| Compose UI tests | **0** (none exist) |
| Screenshot tests | **0** (none exist) |

### 8.2 Critical Elements Needing testTag

| Element | Suggested Tag | File |
|---------|--------------|------|
| Send button | `"send_button"` | MessageInputPane.kt |
| Message input field | `"message_input"` | MessageInputPane.kt |
| OTP input | `"otp_input"` | OtpVerifyScreen.kt |
| Phone input | `"phone_input"` | PhoneInputScreen.kt |
| Conversation list items | `"conversation_{id}"` | ConversationListScreen.kt |
| New chat FAB | `"new_chat_fab"` | ConversationListScreen.kt |
| Back buttons | `"back_button"` | All detail screens |
| Settings toggles | `"setting_{key}"` | SettingsScreen.kt |

---

## 9. Reusable Component Opportunities

### 9.1 Extraction Candidates (DRY Violations)

| Pattern | Files Affected | Proposed Component |
|---------|---------------|-------------------|
| Timestamp formatting | ConversationListScreen, MessageBubble | `DateTimeFormatter.kt` utility |
| Dialog confirm/dismiss buttons | ChatDialogs (5 dialogs), ConversationList (2) | `ConfirmDialog()` wrapper |
| Avatar + AsyncImage loading | UserAvatar, ConversationListScreen, ChatDialogs | `LoadableAvatar()` with error state |
| List item click/selection | ConversationItem, SearchResults, ContactItem | `SelectableListItem()` |
| Section header | SettingsScreen, GroupInfoScreen, UserProfileScreen | `SectionHeader()` |
| Loading/error/empty state | Every screen | `AsyncContent()` tri-state wrapper |

### 9.2 Design Token Extraction

```kotlin
// Proposed: MuhabbetTokens.kt
object MuhabbetTokens {
    // Spacing
    val SpacingXS = 4.dp
    val SpacingS = 8.dp
    val SpacingM = 12.dp
    val SpacingL = 16.dp
    val SpacingXL = 24.dp
    val SpacingXXL = 32.dp

    // Elevation
    val ElevationNone = 0.dp
    val ElevationLow = 1.dp
    val ElevationMedium = 2.dp
    val ElevationHigh = 4.dp

    // Touch targets
    val MinTouchTarget = 48.dp

    // Corner radius
    val RadiusSmall = 8.dp
    val RadiusMedium = 12.dp
    val RadiusLarge = 16.dp
    val RadiusFull = 999.dp

    // Message bubble
    val BubbleMaxWidth = 300.dp
    val BubbleMinWidth = 80.dp
    val BubbleCornerRadius = 48f
}
```

---

## 10. Remediation Roadmap

### Phase 1: Accessibility Critical (P0 — Must Fix Before Release)

| Task | Files | Effort |
|------|-------|--------|
| Add `contentDescription` to all 28+ icons | 9 files | 2h |
| Increase touch targets to 48dp minimum | VoiceBubble, ReactionBar, UserProfile | 1h |
| Add `semantics { role = Role.Button }` to clickable elements | All screens | 2h |
| Add IME actions to all input fields | 4 files | 30m |
| Add AsyncImage error/loading placeholders | MessageBubble, UserAvatar, LinkPreviewCard | 2h |

**Total: ~1 day**

### Phase 2: Design System Hardening (P1 — High Priority)

| Task | Files | Effort |
|------|-------|--------|
| Extract hardcoded colors to theme tokens | 8 files + MuhabbetTheme.kt | 2h |
| Define custom typography scale | MuhabbetTheme.kt | 1h |
| Remove inline fontWeight/fontSize overrides | 8 files | 1h |
| Create spacing token system | New file + refactor | 3h |
| Define elevation scale | MuhabbetTheme.kt | 30m |

**Total: ~1 day**

### Phase 3: Interaction Polish (P1 — High Priority)

| Task | Files | Effort |
|------|-------|--------|
| Add skeleton loading states | ConversationListScreen, ChatScreen | 3h |
| Fix search bar state reset | ConversationListScreen | 30m |
| Fix filter chip toggle behavior | ConversationListScreen | 30m |
| Add edit mode visual banner | MessageInputPane | 1h |
| Add haptic feedback to reactions/swipe | ReactionBar, ChatScreen | 1h |
| Animate unread badge | ConversationListScreen | 30m |

**Total: ~1 day**

### Phase 4: Reusable Components (P2 — Medium Priority)

| Task | Effort |
|------|--------|
| Extract `DateTimeFormatter.kt` utility | 1h |
| Create `ConfirmDialog()` wrapper | 2h |
| Create `AsyncContent()` tri-state wrapper | 2h |
| Create `LoadableAvatar()` with error state | 1h |
| Create `SectionHeader()` component | 30m |

**Total: ~1 day**

### Phase 5: Testability (P2 — Medium Priority)

| Task | Effort |
|------|--------|
| Add `testTag` to all critical elements | 2h |
| Write Compose UI tests for auth flow | 3h |
| Write Compose UI tests for chat flow | 4h |
| Screenshot test setup (Paparazzi/Roborazzi) | 3h |

**Total: ~1.5 days**

### Phase 6: Design Token Migration (P3 — Low Priority)

| Task | Effort |
|------|--------|
| Create `MuhabbetTokens.kt` | 1h |
| Migrate spacing values across all screens | 4h |
| Migrate elevation values | 1h |
| Document design system in docs/ | 2h |

**Total: ~1 day**

---

## 11. Summary of Findings

### By Severity

| Severity | Count | Category |
|----------|-------|----------|
| **Critical** | 5 | Missing a11y descriptions (28+), touch targets (<48dp), no semantic annotations, no image error states, no testTags |
| **Major** | 10 | No skeleton loaders, hardcoded colors (8), keyboard handling, typography inconsistencies, filter logic, search state, edit mode UX |
| **Minor** | 9 | Missing animations, elevation inconsistency, opacity scale, dialog keyboard handling, badge animation, EmptyState jank |
| **Suggestion** | 11 | Haptic feedback, spacing tokens, component extraction, dynamic placeholders, section headers, screenshot tests |

### Total Estimated Remediation: ~6.5 days

| Phase | Priority | Effort | Impact |
|-------|----------|--------|--------|
| Accessibility Critical | P0 | 1 day | Legal compliance, screen reader support |
| Design System Hardening | P1 | 1 day | Theme consistency, maintainability |
| Interaction Polish | P1 | 1 day | User experience, perceived quality |
| Reusable Components | P2 | 1 day | Code quality, DRY compliance |
| Testability | P2 | 1.5 days | Quality assurance, regression prevention |
| Design Token Migration | P3 | 1 day | Long-term maintainability |

---

## 12. Positive Highlights

Despite the issues identified, the UI layer has significant strengths worth preserving:

1. **Decompose navigation** — Type-safe, serializable configs with proper animation transitions. Best-in-class for CMP.
2. **Localization** — 238 strings, fully translated TR/EN, proper `stringResource()` usage throughout.
3. **Theme system** — Three well-defined color schemes (Light, Dark, OLED Black) using Material 3.
4. **Rich interactions** — Swipe-to-reply, pinch-to-zoom, long-press context menus, pull-to-refresh.
5. **Empty states** — Animated illustrations provide personality.
6. **BubbleTailShape** — Custom Bezier curve implementation for message bubble aesthetics.
7. **Real-time architecture** — SharedFlow-based WebSocket message distribution to all screens.
8. **Feature breadth** — 14 navigation destinations covering auth, messaging, calls, groups, media, settings, channels, status, and profiles.
9. **ChatScreen refactoring** — Previously 1,771 lines, now 469 lines with extracted sub-composables.
10. **OLED Black theme** — Energy-efficient pure black option for AMOLED displays.
