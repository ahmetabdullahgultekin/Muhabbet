# D1 — Brand & Color Identity (İznik Çini)

| | |
|---|---|
| **Status** | Implemented 2026-06-08 (palette applied to `MuhabbetTheme.kt`) |
| **Pillar** | A — Visual identity & motion (`docs/design/PRODUCT_DESIGN_INNOVATION_VISION.md`) |
| **Decision** | Owner chose "Türk turkuazı / çini" direction |

## Why
Until now Muhabbet shipped **WhatsApp's exact palette** (`0xFF00A884` accent, `0xFF53BDEB` read-tick,
`0xFF25D366` unread) — a screenshot was indistinguishable from WhatsApp. D1 establishes an **own**
identity drawn from **İznik çini** (classical Turkish tile art): a heritage that is unmistakably
Turkish, warm, and *not* any Western messenger's look.

## The çini triad
| Role | Name | Light | Dark | Heritage |
|---|---|---|---|---|
| **Primary accent** | Firuze (turquoise) | `#0E94A8` | `#26B3C7` (bright) | İznik turquoise glaze |
| **Read-tick / links** | Kobalt (cobalt blue) | `#3E8FD0` tick · `#1E5AA8` link | `#5BB7D8` tick | İznik cobalt underglaze |
| **Warm accent** | Mercan (coral red) | `#E2553D` | `#FFB4A2` | İznik "Armenian bole" coral |

Supporting: warm ivory chat wallpaper (`#EDE7DA`, light) replaces WhatsApp's beige; own-message
bubble is a pale firuze tint (`#CDEFF3` light / `#0A5560` dark) instead of WhatsApp mint/green; dark
surfaces shift from blue-grey toward a subtle teal family so they pair with firuze.

## Principles honored
- **Own it, don't clone it** — all `WhatsApp*` constants renamed to `Cini*`; no competitor exact
  values remain. The accent is a blue-leaning firuze, deliberately distinct from WhatsApp's
  green-teal.
- **Convention kept where it aids users** — ticks stay in the blue family (familiar "read = blue"),
  call-accept stays green / call-decline red (universal affordance, accessibility).
- **Accessibility** — text inks darkened to teal-ink (`#0E2A2F`) for contrast on the warm wallpaper;
  sender differentiation remains alignment+shape+color, not color alone.
- **Reversible & themeable** — pure token swap inside `MuhabbetTheme.kt`; all three schemes
  (light / dark / OLED) + semantic-color sets updated coherently. No call sites changed.

## Applied to
`mobile/.../ui/theme/MuhabbetTheme.kt`: the palette block, `Light/Dark/OledSemanticColors`, and
`MuhabbetLight/Dark/OledBlackColorScheme`. Verified by `:mobile:composeApp:compileCommonMainKotlinMetadata`.

## Next (Pillar A continuation)
D2 — Motion spec + signature send/react micro-interactions; logo/app-icon refresh to the çini
identity (separate slice, needs asset work).
