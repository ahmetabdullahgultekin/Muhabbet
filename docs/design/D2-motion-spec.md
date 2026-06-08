# D2 — Motion Language Spec

| | |
|---|---|
| **Status** | Foundation implemented 2026-06-08 (`MuhabbetMotion` tokens + `pressBounce` wired) |
| **Pillar** | A — Visual identity & motion (`docs/design/PRODUCT_DESIGN_INNOVATION_VISION.md`) |
| **Source of truth** | `mobile/.../ui/theme/MuhabbetMotion.kt` |

## Principle
**Motion = meaning.** Every animation communicates a state change (sent, pressed, reacted, arrived).
Nothing is decoration. 2026 research: micro-interactions are *communication*, and measurable
flow/conversion lift comes from feedback that confirms actions without interrupting them.

## Tokens (`MuhabbetMotion`)

| Token | Value | Use |
|---|---|---|
| `DurationQuick` | 120 ms | taps, toggles, tick state changes |
| `DurationStandard` | 220 ms | most transitions |
| `DurationEmphasized` | 360 ms | hero / entrance moments |
| `EasingStandard` | cubic(0.2, 0, 0, 1) | in-place changes |
| `EasingEmphasized` | cubic(0.05, 0.7, 0.1, 1) | arrivals / emphasis |
| `EasingDecelerate` | cubic(0, 0, 0, 1) | incoming elements |
| `PressSpring` | medium-bouncy / medium-stiff | tactile press |
| `BubbleSpring` | low-bouncy / medium-low-stiff | message entrance lift+settle |
| `ReactionSpring` | high-bouncy / medium-stiff | reaction pop |
| `PressScale` | 0.97 | pressed shrink magnitude |
| `EntranceFromScale` | 0.94 | entrance start scale |

Use these everywhere instead of ad-hoc `tween(300)` / `spring()` literals (DRY — there are 7 chat
files currently rolling their own animation params; migrate them to these tokens over time).

## Reusable modifiers

### `Modifier.pressBounce(interactionSource)` — **wired in `MessageBubble`**
Element gently shrinks (→0.97) while pressed and springs back on release. **Scroll-safe**: driven by
the shared press `InteractionSource`, not by appearance, so it never re-fires when a `LazyColumn`
recycles items. Share the `interactionSource` with the element's `combinedClickable` so press state
stays in sync (as done on the chat bubble Surface).

### `Modifier.bubbleEntrance(isOwn)` — **library, opt-in**
Signature "lift + settle": a newly-added bubble springs up from the sending side (transform origin =
bottom-right for own, bottom-left for other) and fades in. **Apply only to genuinely new items** (the
just-sent message), never to every list item — otherwise scroll recycling re-triggers it. Wiring this
to "only the last just-sent message" is the next motion slice (needs a `isNew` flag threaded from the
chat list).

## Signature interactions — roadmap

| Moment | Spec | Status |
|---|---|---|
| Bubble press | `pressBounce` (PressSpring, 0.97) | **Done** (wired) |
| New message arrival | `bubbleEntrance` (BubbleSpring) | Lib ready; wire to last-sent next |
| Reaction add | scale pop via `ReactionSpring` | Spec'd; wire in `ReactionBar`/bubble reaction |
| Tick state change (sent→delivered→read) | crossfade `DurationQuick` + color to çini cobalt | Next |
| Send button | press + send "whoosh" + haptic | Next |
| Pull-to-refresh | branded firuze spinner | Next |

## Accessibility
- Respect reduced-motion: future work should gate entrance/pop on a system "reduce motion" setting
  (fall back to a quick fade). Press feedback (0.97) is subtle and within safe bounds.
- Motion never the sole carrier of meaning — tick *shape* + color both change, not motion alone.

## Verification
`:mobile:composeApp:compileCommonMainKotlinMetadata` green with `MuhabbetMotion` + `pressBounce`
wired into `MessageBubble`. Device-visual verification pending a real APK build (full Android app does
not assemble on the CI host).
