# WhatsApp UI Clone Specification
> Research date: March 2026 | Target: WhatsApp Android v2.26.x

This document captures the exact design specifications of WhatsApp Android (March 2026)
to guide the Muhabbet UI overhaul. Goal: pixel-level visual parity.

---

## 1. Color System

### Dark Mode (primary target)

| Element | Hex | Usage |
|---|---|---|
| Background | `#111B21` | Main scaffold/screen background |
| Surface | `#1F2C34` | Cards, top bar, bottom nav, chat items |
| Surface Elevated | `#2A3942` | Input field, dividers elevated |
| Primary Accent | `#00A884` | FAB, active nav icon, active chip, unread badge, links |
| Own Bubble | `#005C4B` | Sent message bubble |
| Other Bubble | `#1F2C34` | Received message bubble |
| Chat Wallpaper | `#0D1418` | Conversation screen background |
| Primary Text | `#E9EDEF` | Names, message text |
| Secondary Text | `#8696A0` | Timestamps, last message preview, subtitles |
| Divider | `#2A3942` | List dividers |
| Unread Badge | `#00A884` | Unread message count circle |
| Read Tick (blue) | `#53BDEB` | Double tick when message is read |
| Delivered Tick | `#8696A0` | Double tick when delivered, not read |
| Sent Tick | `#8696A0` | Single tick when sent |
| Online Dot | `#00A884` | Online presence indicator |
| Input Bar BG | `#1F2C34` | Bottom input bar container |
| Input Field BG | `#2A3942` | Text input pill background |
| Active Chip BG | `#00A884` | Selected filter chip background |
| Inactive Chip BG | `#1F2C34` | Unselected filter chip background |

### Light Mode

| Element | Hex | Usage |
|---|---|---|
| Background | `#FFFFFF` | Main background |
| Surface | `#FFFFFF` | Cards, bottom nav |
| Top Bar | `#FFFFFF` | App bar (post-2024 redesign, no longer green) |
| Primary Accent | `#00A884` | FAB, active nav, chips |
| Own Bubble | `#D9FDD3` | Sent message bubble (pale mint) |
| Other Bubble | `#FFFFFF` | Received message bubble |
| Chat Wallpaper | `#ECE5DD` | Conversation background (warm beige) |
| Primary Text | `#111B21` | Names, message text |
| Secondary Text | `#667781` | Timestamps, previews |
| Divider | `#E9EDEF` | List dividers |
| Unread Badge | `#25D366` | Unread count (classic green) |
| Read Tick (blue) | `#4FB6EC` | Read double tick |
| Online Dot | `#25D366` | Online indicator |
| Input Field BG | `#F0F2F5` | Text input pill |

### AMOLED Black (bonus — rolling out)
- Background: `#000000`
- Surface: `#0A1014`
- Everything else same as dark mode

---

## 2. Navigation Structure

### Bottom NavigationBar (since May 2024 redesign)
**4 tabs, left to right:**

| Index | Label | Icon |
|---|---|---|
| 0 | Communities | Groups/community icon |
| 1 | **Chats** | Chat bubble (default selected) |
| 2 | Updates | Camera/circle |
| 3 | Calls | Phone |

- Nav bar background: `#1F2C34` (dark) / `#FFFFFF` (light)
- Active icon + label color: `#00A884`
- Inactive icon + label: `#8696A0`
- No top `TabRow` — tabs are at the bottom only

### Top App Bar (Chats tab)
- Title: **"WhatsApp"** left-aligned, `20sp`, Medium weight
- Right actions: Search (magnifying glass), More (3-dot vertical menu)
- Background: `#1F2C34` (dark) / `#FFFFFF` (light)
- No camera icon in top bar (moved to Updates tab)

### Filter Chips (below top bar in Chats tab)
Horizontally scrollable, order:

| Chip | State |
|---|---|
| All | Active by default |
| Unread | |
| Favorites | |
| Groups | |
| Custom lists | User-created |

- Active chip: filled `#00A884` bg, white text, no border
- Inactive chip: transparent bg, `#8696A0` text, subtle border

### FAB
- Position: bottom-right of Chats screen
- Size: 56dp diameter
- Color: `#00A884` background, white pencil/compose icon
- Elevation: standard Material 3 FAB

---

## 3. Typography

System font: **Roboto** (Android default)

| Element | Size | Weight |
|---|---|---|
| Top bar title ("WhatsApp") | 20sp | Medium (500) |
| Bottom nav labels | 12sp | Medium (500) |
| Chat list — contact name | 17sp | SemiBold (600) |
| Chat list — last message | 14sp | Normal (400) |
| Chat list — timestamp | 12sp | Normal (400) |
| Message bubble text | 15sp | Normal (400) |
| Message timestamp (in bubble) | 11sp | Normal (400) |
| Unread badge count | 12sp | Bold (700) |
| Date pill (e.g. "Today") | 13sp | Medium (500) |
| Section headers | 13sp | Medium (500) |
| Filter chip labels | 13sp | Medium (500) |

---

## 4. Chat List Item

| Dimension | Value |
|---|---|
| Row height | 72dp |
| Avatar diameter | 52dp |
| Avatar left padding | 16dp |
| Avatar → text gap | 16dp |
| Text right padding | 16dp |
| Divider left inset | 84dp (starts after avatar) |
| Unread badge min size | 20dp diameter |

---

## 5. Conversation / Chat Screen

### Background
- Dark: `#0D1418`
- Light: `#ECE5DD` (warm beige default wallpaper)

### Message Bubbles (March 2026)

**Current stable:**
- Corner radius: `8dp` all corners except tail corner
- Tail: triangular pointer (left for received, right for sent)
- Tail corner radius: `4dp`

**Beta (rolling out March 2026 — v2.26.10.2):**
- Corner radius: `18–24dp` fully rounded (pill-shaped)
- **No tail** — bubbles flush to edge
- Max width: ~65% of screen width

### Bubble Padding
- Internal: `8dp` horizontal, `6dp` vertical
- Between bubbles (same sender): `2dp`
- Between bubbles (different sender): `8dp`

### Input Bar
- Container height: ~60dp
- Container bg: `#1F2C34` (dark) / `#FFFFFF` (light)
- Input pill bg: `#2A3942` (dark) / `#F0F2F5` (light)
- Input pill corner radius: `24dp`
- Icons: Emoji (left), Attachment, Camera (right of field), Mic/Send (rightmost)
- Send button replaces Mic when text is present

### Top Bar (Chat Screen)
- Background: `#1F2C34`
- Back arrow + avatar (42dp) + contact name (17sp bold) + subtitle (online/last seen, 13sp)
- Right icons: Video call, Voice call, More (3-dot)

---

## 6. Status / Updates Tab
- Story circles at top: 72dp diameter, `#00A884` ring for unseen
- My Status first, with `+` add indicator
- Contact status list below

---

## 7. Key Design Changes Timeline
| Date | Change |
|---|---|
| May 2024 | Bottom nav bar on Android, darker dark mode (`#111B21`), new green (`#00A884`), outlined icons |
| Late 2024 | Chat themes (8 presets, 20+ bubble colors, 30+ wallpapers) |
| Early 2025 | Filter chips expanded (Favorites added), animated bottom nav tabs (beta) |
| Feb 2026 | Status avatar row at top of Chats tab |
| Mar 2026 | Fully rounded pill bubbles, no tail (beta v2.26.10.2) |

---

## 8. Implementation Plan for Muhabbet

### Priority 1 — Colors (MuhabbetTheme.kt)
Replace teal palette with WhatsApp dark/light palette exactly as above.

### Priority 2 — Navigation (HomeShellScreen.kt)
Replace `TopAppBar + TabRow` with `Scaffold + NavigationBar` (bottom).
Add Communities tab. Move tabs to bottom.

### Priority 3 — Top App Bar
Update to show "Muhabbet" title + Search + 3-dot menu icons.
Remove Settings gear from top bar (move to bottom nav or profile).

### Priority 4 — Filter Chips
Add Favorites chip. Reorder: All | Unread | Favorites | Groups.

### Priority 5 — Chat Bubbles
Increase corner radius to `18dp`, remove tail for new design.
Or keep tail with `8dp` for stable design.

### Priority 6 — Chat Screen Colors
Update wallpaper bg, input bar, top bar to spec above.

---

## 9. Differences to Keep (Muhabbet Identity)
- App name: **Muhabbet** (not WhatsApp)
- Turkish locale as default
- No Meta/Facebook branding
- KVKK compliance features (Privacy Dashboard)
