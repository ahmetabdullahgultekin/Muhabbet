# 06 — Usability & Responsiveness

> Quality attribute: How effectively and efficiently users can accomplish their goals.

---

## 1. UI/UX Quality Standards

### 1.1 Interaction Design Checklist

| Principle | Requirement | Status |
|-----------|------------|--------|
| **Feedback** | Every action has visible response (<100ms) | Partial |
| **Consistency** | Same patterns for same actions across screens | Deployed |
| **Error prevention** | Confirmation for destructive actions (delete, leave group) | Deployed |
| **Recovery** | Undo or confirmation before irreversible actions | Partial |
| **Recognition** | Icons + text labels for important actions | Deployed |
| **Flexibility** | Long-press context menus + swipe gestures | Deployed |
| **Aesthetics** | Clean, minimal WhatsApp-like design | Deployed |

### 1.2 Response Time Requirements

| User Action | Maximum Delay | Visual Feedback |
|-------------|--------------|-----------------|
| Button tap | <50ms | Ripple effect |
| Screen navigation | <200ms | Transition animation |
| Pull-to-refresh | <100ms start | Refresh indicator |
| Message send | <100ms | Clock icon → sent tick |
| Image load (thumbnail) | <500ms | Placeholder → fade in |
| Image load (full) | <2s | Progress indicator |
| Search results | <300ms | Loading indicator |
| Typing indicator | <100ms | Animation starts |

### 1.3 Animation Standards

| Animation | Duration | Easing |
|-----------|----------|--------|
| Screen transition | 300ms | EaseInOut |
| List item appear | 200ms | EaseOut |
| Bottom sheet | 250ms | Spring |
| Typing indicator dots | Loop (300ms per dot) | Linear |
| Message bubble enter | 150ms | EaseOut |
| FAB show/hide | 200ms | EaseInOut |
| Tab switch (Crossfade) | 300ms | EaseInOut |

---

## 2. Responsiveness Testing

### 2.1 Screen-Specific Tests

| Screen | Test Cases | Priority |
|--------|-----------|----------|
| **ConversationListScreen** | Load 100+ conversations, scroll smoothness, pull-to-refresh, unread badge accuracy, pinned chats at top | P0 |
| **ChatScreen** | Load 1000+ messages, scroll to bottom on open, send message optimistic UI, image upload progress, typing indicator, message context menu | P0 |
| **GroupInfoScreen** | Member list scroll (100+ members), role badges, action buttons | P1 |
| **MediaViewer** | Pinch-to-zoom (1x-5x), double-tap toggle, swipe to dismiss | P1 |
| **SharedMediaScreen** | Grid/list toggle, tab Crossfade, long-press context menu | P1 |
| **StatusScreen** | Story viewer progression, tap forward/back, swipe to dismiss | P1 |
| **SearchScreen** | Debounced search, result highlighting, tap-to-navigate | P1 |
| **SettingsScreen** | Storage stats loading, language switch restart, theme toggle | P2 |

### 2.2 Frame Rate Testing

| Screen | Action | Target FPS | Tool |
|--------|--------|-----------|------|
| Chat | Scroll message list | 60fps | GPU Profiler |
| Chat | Receive rapid messages (10/s) | 60fps | Custom load |
| Conversations | Scroll conversation list | 60fps | GPU Profiler |
| Media | Pinch zoom | 60fps | Manual observation |
| Status | Story auto-progress | 60fps | Manual |

### 2.3 Memory Under UI Load

| Scenario | Memory Target | Alert Threshold |
|----------|--------------|-----------------|
| Idle (conversation list) | <60MB | 80MB |
| Active chat (100 messages) | <100MB | 130MB |
| Active chat (1000 messages) | <150MB | 200MB |
| Media viewer (10 images) | <180MB | 250MB |
| Background (WS connected) | <40MB | 60MB |

---

## 3. Accessibility

### 3.1 WCAG 2.1 AA Compliance Targets

| Criterion | Description | Status | Priority |
|-----------|------------|--------|----------|
| 1.1.1 | Non-text content has text alternatives | Partial (images lack alt text) | P1 |
| 1.3.1 | Content structure conveyed programmatically | Partial (semantic elements) | P1 |
| 1.4.1 | Color not sole means of conveying info | Partial (status dots + labels) | P1 |
| 1.4.3 | Contrast ratio ≥4.5:1 (text), ≥3:1 (large text) | Untested | P1 |
| 1.4.11 | Non-text contrast ≥3:1 (UI components) | Untested | P1 |
| 2.1.1 | All functionality accessible via keyboard/gestures | Partial | P2 |
| 2.4.3 | Focus order meaningful and logical | Untested | P2 |
| 4.1.2 | UI components have accessible names/roles | Partial | P1 |

### 3.2 Screen Reader Support

| Element | TalkBack (Android) | VoiceOver (iOS) | Status |
|---------|-------------------|-----------------|--------|
| Message bubble | Read sender + content + time | Same | Needs `contentDescription` |
| Send button | "Send message" | Same | Needs semantic |
| Avatar | "User avatar, [name]" | Same | Needs `contentDescription` |
| Unread badge | "[count] unread messages" | Same | Needs semantic |
| Delivery ticks | "Sent/Delivered/Read" | Same | Needs `contentDescription` |
| Voice message play | "Play voice message" | Same | Needs semantic |
| Image message | "Image from [sender]" | Same | Needs alt text |

### 3.3 Accessibility Test Cases

| Test | Method | Priority |
|------|--------|----------|
| Navigate app with TalkBack enabled | Manual | P1 |
| Navigate app with VoiceOver enabled | Manual | P1 |
| Send message using only screen reader | Manual | P1 |
| Verify all interactive elements are focusable | Automated | P1 |
| Check contrast ratios (all themes) | Accessibility Scanner | P1 |
| Font size scaling (up to 200%) | Manual | P2 |
| Reduce motion setting respected | Manual | P2 |

---

## 4. Localization Quality

### 4.1 Current Localization State

| Language | Status | Coverage | Default |
|----------|--------|----------|---------|
| Turkish | Complete | 100% (all strings) | Yes |
| English | Complete | 100% (all strings) | No |

### 4.2 Localization Test Cases

| Test | Description | Priority |
|------|-------------|----------|
| All screens render in Turkish | Walk through every screen | P0 |
| All screens render in English | Walk through every screen | P0 |
| Language switch applies immediately | Change in Settings → restart | P0 |
| Long Turkish text doesn't clip | "Konuşma oluşturuldu" etc. | P1 |
| Date/time formatting respects locale | "14:30" (TR) vs "2:30 PM" (EN) | P1 |
| Error messages in correct language | Backend returns Turkish messages | P1 |
| No hardcoded strings in code | Grep for Turkish text in .kt files | P0 |
| RTL layout (if Arabic added later) | Layout mirrors correctly | P2 |

### 4.3 String Resource Validation

```bash
# Find any hardcoded Turkish text in Kotlin files
grep -rn '[ÇçĞğİıÖöŞşÜü]' mobile/composeApp/src/commonMain/kotlin/ \
  --include="*.kt" \
  --exclude-dir="*test*"

# Verify all Res.string.* references exist in both locales
# Compare keys in values/strings.xml vs values-en/strings.xml
```

---

## 5. Offline & Poor Network UX

### 5.1 Offline Behavior

| Feature | Expected Offline Behavior | Status |
|---------|--------------------------|--------|
| View conversation list | Show cached conversations | Partial (Ktor cache) |
| View chat history | Show cached messages | Not implemented |
| Send message | Queue locally, send when online | Not implemented |
| Upload image | Queue locally, upload when online | Not implemented |
| View profile | Show cached profile | Not implemented |
| Receive message | Stored on server, delivered on reconnect | Deployed (server-side) |

### 5.2 Network State Indicators

| State | Visual Indicator | Status |
|-------|-----------------|--------|
| Connected (good) | No indicator (normal) | Deployed |
| Connecting | "Bağlanıyor..." header | Deployed |
| Disconnected | "Bağlantı yok" banner | Deployed |
| Reconnecting | "Yeniden bağlanıyor..." | Deployed |
| Sending message | Clock icon on bubble | Deployed |
| Send failed | Red error icon | Partial |

---

## 6. Dark Mode & Theme Quality

### 6.1 Theme Variants

| Theme | Description | Status |
|-------|-------------|--------|
| Light | Standard light colors | Deployed |
| Dark | Standard dark colors | Deployed |
| OLED | Pure black background | Deployed |
| System | Follow system preference | Deployed |

### 6.2 Theme Test Cases

| Test | All Themes | Priority |
|------|-----------|----------|
| Text readable on all backgrounds | Yes | P0 |
| Icons visible on all backgrounds | Yes | P0 |
| Status bar color matches theme | Yes | P1 |
| Navigation bar color matches theme | Yes | P1 |
| No white flash on screen transition (dark) | Yes | P1 |
| Image placeholders visible | Yes | P1 |
| Emoji render correctly | Yes | P1 |

---

## 7. Action Items

### P0
- [ ] Audit all screens for missing `contentDescription` attributes
- [ ] Verify no hardcoded strings (grep scan)
- [ ] Test complete flow in both Turkish and English
- [ ] Verify 60fps scrolling in chat with 500+ messages

### P1
- [ ] Run Android Accessibility Scanner on all screens
- [ ] Run contrast ratio checker on all theme variants
- [ ] Add TalkBack/VoiceOver walkthrough to QA checklist
- [ ] Test font size scaling (system 150%, 200%)
- [ ] Implement offline message queue (SQLDelight)
- [ ] Add "send failed" retry button on messages

### P2
- [ ] Test with Turkish keyboard (Gboard, SwiftKey, Samsung keyboard)
- [ ] RTL layout preparation for potential Arabic localization
- [ ] Reduce motion preference support
- [ ] Custom font support for accessibility
- [ ] Automated UI testing with Maestro
