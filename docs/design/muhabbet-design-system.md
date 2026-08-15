# Muhabbet Design System

> Supersedes [`docs/whatsapp-ui-clone-spec.md`](../whatsapp-ui-clone-spec.md), which specified pixel
> parity with WhatsApp Android. That document is retained only so its hex values stay recognisable
> if any turn up somewhere that was missed.

The visual language lives in its own Gradle module, `:mobile:designsystem`. This document is why it
exists, what is in it, and which rules it enforces.

---

## 1. Why a module and not a package

DRY as a convention did not hold. Before this work the app had four shared components against **26
hand-rolled top bars in three different colours**, 37 files with a bare `CircularProgressIndicator`,
~18 screens whose empty state was a plain `Text`, and 195 scattered icon references covering 70
distinct icons.

A module boundary is enforced by the compiler rather than by review:

- The dependency points one way. `designsystem` cannot see `composeApp`, so a component can never
  reach a screen, a repository, a navigation component or the app's strings.
- `internal` becomes real. Raw palette values are `internal` to the module; outside it, only
  semantic roles are visible. **A screen cannot name a hex** — which it could when this was a
  package.

### Two rules, both machine-checked

1. **The library carries no user-visible strings.** Every label is a parameter. This is partly
   architectural (components are presentational) and partly mechanical: Compose Resources generates
   a separate `Res` per module, and `Res.string.*` lives in `composeApp`. Fonts are the one
   exception — a typeface belongs with the type scale that applies it, and the generated `Res` stays
   internal.
2. **No navigation, Koin, repositories or domain types.** Compose and the module's own tokens only.

### The limit of centralisation

`MuhabbetTopBar` serves 23 screens. `ChatScreen` (avatar + name + presence subtitle) and
`HomeShellScreen` (transforms into a search field) keep their own bars — but take their colours from
`MuhabbetTopBarDefaults` and their metrics from `MuhabbetSizes`. Two bespoke bars is correct; one
universal bar with nine parameters is a god-component.

The same judgement applies elsewhere: 20 of 28 text fields use `MuhabbetTextField`; the OTP boxes,
the chat composer, two search fields and two password fields stay raw because they are genuinely
doing their own thing.

**What is centralised is the tokens and the defaults, not enforced uniformity.**

---

## 2. One entry point

```kotlin
import com.muhabbet.designsystem.Muhabbet

Muhabbet.colors.bubbleOwn      Muhabbet.spacing.Large     Muhabbet.motion.spatialDefault()
Muhabbet.text.ChatBody         Muhabbet.icons.Back        Muhabbet.haptics.perform(…)
```

The individual token objects stay public, so migration is incremental and existing
`MuhabbetSpacing.Large` call sites keep working. `Muhabbet` is the front door, not a replacement.

---

## 3. Colour — Copper Warmth

### Rationale

The inherited palette was a literal clone. The replacement is built from a **warm neutral ramp** and
a **copper accent**. Neutrals are warm rather than blue-grey because next to copper a blue-grey
neutral reads as dirty.

`surfaceContainerLowest…Highest` are **derived by walking the ramp**, one step per level, instead of
each container being picked by hand. That is the difference between a dark theme that reads as
designed and one that reads as assembled.

Their previous absence was a live bug: M3 derives `NavigationBar`, `Card`, `DropdownMenu`,
`ModalBottomSheet` and the default `TopAppBar` container from `surfaceContainer*`, and with them
unset M3 fell back to its own tonal defaults — which is why two bottom-nav tabs rendered a different
bar from every other screen and switching tabs changed the bar colour for no reason.

### The ramps

| Ink | | Copper | |
|---|---|---|---|
| `I00` `#0B0A09` | `I40` `#5C534E` | `C30` `#6B3B10` | `C70` `#E08A3C` |
| `I05` `#12100E` | `I50` `#7A6F68` | `C40` `#8A4E17` | `C80` `#F0A868` |
| `I10` `#1C1917` | `I60` `#9C8F86` | `C50` `#A85F1C` | `C90` `#F8CFA6` |
| `I15` `#262220` | `I70` `#BDB0A6` | `C60` `#C9752C` | |
| `I20` `#322D2A` | `I80` `#DAD1C9` | | |
| `I30` `#453E3A` | `I90` `#EFE9E3` · `I95` `#F7F3EF` · `I99` `#FDFBF9` | | |

### Three decisions

**Green survives only where it is semantic** — `statusOnline` and `callAccept` — and is shifted off
`#25D366`. Read ticks stay a cool blue: copper cannot hold 3:1 against a copper bubble, and
warm-brand / cool-status is useful chromatic separation in its own right.

**"Sending" and "delivered" share a neutral.** Separating them by colour is what pushed the old
palette under the contrast floor — on a copper bubble, holding both above 3:1 while keeping them
distinguishable lands at 2.61:1. The distinction is already carried by the glyph (clock / one tick /
two ticks); a second, weaker encoding in colour bought nothing.

**The top bar is `surface`, not `primary`.** A saturated bar across 19 screens was the most
recognisable inherited signature in the product, and restraint is most of what reads as premium. It
lifts to `surfaceContainer` on scroll — M3's own elevation-by-colour, legible only now that the
container ramp is filled in.

### Contrast is a merge gate

`SemanticColorContrastTest` measures 16 pairs × 3 schemes against WCAG relative luminance: 4.5:1 for
text, 3:1 for non-text (ticks, dots, badges), with translucent foregrounds flattened onto their
background first.

Its `knownDebt` set is **empty and stays empty**. It previously held 21 pairs inherited with the
clone — the unread badge at 1.98:1, the "sending" clock at 1.69:1 on its own bubble. The test fails
**in both directions**: a pair that regresses fails the build, and a pair that is fixed must be
removed from the set. Adding an entry back is a deliberate act with a reviewer attached.

Two Material roles were adjusted off the obvious choice to clear the floor, both recorded in
`MuhabbetPalette.kt`: dark `surfaceVariant` is `I15` not `I20` (`I60` on `I20` is 4.33:1), and
`outline` is `I50` not `I40` (2.33:1 against its surface — and an outline carries information).

---

## 4. Typography — Manrope

Four static instances at 400/500/600/700, ~50 KB each, cut from the variable original and subset to
Latin + Latin Extended-A + general punctuation + `₺`. Latin Extended-A is **not optional**: it
carries `İ ı Ğ ğ Ş ş`, and a subset that drops them renders the app's own name wrong.

Static rather than variable because Google Fonts publishes only the variable file and variable-axis
support on Skia could not be verified.

### The trap worth knowing about

`Res.font.*` is `@Composable`, so the typography cannot be a top-level `val` — it is built inside
`MuhabbetTheme`. **`MuhabbetTextStyles` is therefore a class provided as `LocalTextStyles`, not an
object.** As an object its properties initialise once from a family-free `Typography`, and every
chat bubble, conversation title, timestamp and top-bar title would silently keep the system font
while everything around them switched. Those are the highest-traffic surfaces in the product.

The family is applied by `Typography.withFontFamily` — fifteen `copy(fontFamily = …)` lines, because
M3 has no `defaultFontFamily` and rebuilding roles with `TextStyle(...)` discards the
`platformStyle` (`includeFontPadding = false`) and centred `lineHeightStyle` that `Base =
Typography()` exists to preserve.

### Scale changes off the Material baseline

The scale previously matched M3 to the decimal place, which is a clone signal in itself.

| Change | Why |
|---|---|
| `bodyLarge` 0.5sp → 0, `bodyMedium` 0.25sp → 0 | that 0.5sp is the most recognisable stock-Android tell there is |
| negative tracking on display/headline | large type wants to be set tighter than small type |
| display line height ~1.12 → ~1.05 | a headline that leads like body copy reads as an accident |
| `titleLarge` Normal → SemiBold | it is a title |

`ChatMeta` at 11sp is the floor; nothing user-visible goes below it.

---

## 5. Depth

Four levels — `Flat`, `Raised`, `Floating`, `Overlay` — expressed differently per variant, because a
shadow is invisible on `#000`:

- **Light** → two stacked shadows (contact + ambient), both tinted with the deepest warm ink,
  **never pure black**. Black over a warm surface desaturates it and reads as dirty grey. Two layers
  is most of the difference between "has a shadow" and "looks designed".
- **Dark** → luminance, not shadow: one step up the `surfaceContainer*` ramp, plus a ~6% white
  hairline at `Floating`/`Overlay`.
- **OLED** → no shadow at all (invisible, and it costs fill rate). A ~10% white hairline outline.

**Neumorphism is never used.** It collapses under contrast requirements and at 1.3× font scale, and
it dates instantly.

### Gradients

| Use | Verdict |
|---|---|
| Auth hero, call screens (full-bleed) | **yes** — identity moments |
| Chat wallpaper (≤4% luminance travel) | **yes** |
| Avatar fallback, deterministic from the display name | **yes** — cheapest high-impact change available |
| Status ring, empty-state illustrations | **yes** |
| Message bubbles | **no** — 2014 skeuomorphism, wrecks text contrast, and the contrast test cannot express a gradient |
| Buttons / CTAs | **no** — a gradient CTA is a crypto-app tell |
| Top bar, bottom nav, list rows, icons, text | **no** |

**Rule: a gradient either covers ≥25% of the viewport or it is decoration carrying no text. Never
behind body copy.**

### Blur

**Blur may degrade decoratively. It may never be the thing protecting something.** `Modifier.blur`
is a no-op below API 31 while `minSdk` is 26 — a view-once thumbnail "protected" by a 20dp blur
rendered fully sharp on Android 8–11. Even working, a blurred thumbnail leaks composition, dominant
colour and usually the subject.

---

## 6. Motion

Hand-written spring tokens. **`MaterialExpressiveTheme` is deliberately not adopted**: it changes
button shapes, press-morph and progress indicators app-wide with no per-screen opt-out, its
experimental opt-in spreads to every file touching `MaterialTheme`, the result looks like stock M3
Expressive (trading one recognisable clone for another), and it is off-brand on iOS.

Its *structure* is borrowed, because the distinction is correct and teachable:

- **Spatial** (movement, size) — damping `0.80`, may overshoot slightly.
- **Effects** (colour, alpha) — damping `1.0`, must never overshoot, or a colour lands on a tone
  that is not in the palette.

Damping is `0.80` rather than M3E's 0.6: a list that bounces on every new message reads as a toy.

Spatial types with units (`Dp`, `IntOffset`, `Rect`) use the typed spring builders so they keep their
visibility thresholds — a bare `spring<T>()` settles late for them.

---

## 7. Haptics

Call sites name a **semantic intent**, never a platform constant, so retuning the whole app's feel is
one file. The user's on/off preference is checked in exactly one place, inside
`MuhabbetHaptics.perform`.

| Interaction | Intent |
|---|---|
| Send button | `MessageSent` |
| Long-press a bubble or row | `ItemLongPressed` |
| Swipe-to-reply crossing / releasing the threshold | `SwipeArmed` / `SwipeCommitted` |
| Bottom-nav tab change | `TabSwitched` |
| Settings switch | `ToggleOn` / `ToggleOff` |
| Call accept / decline | `CallAccepted` / `CallDeclined` |
| OTP digit, story advance | `SegmentAdvanced` |
| Delete confirm, invalid OTP | `DestructiveConfirmed` / `InputRejected` |

**Never**: scrolling, individual keystrokes, incoming messages (that is the notification channel's
job), back navigation, anything on a repeating animation. Over-haptics is the fastest way to make an
app feel cheap, and it costs battery.

Components carry the haptic by default, so no screen has to remember.

---

## 8. Component catalogue

| Component | Replaced |
|---|---|
| `MuhabbetTopBar` + `MuhabbetTopBarDefaults` | 26 bars, 23 duplicated colour blocks in 3 colours |
| `MuhabbetScaffold` | 27 `Scaffold`s + the one correct `WindowInsets` policy |
| `MuhabbetScreenState` (`Loading`/`Empty`/`Error`) | 44 bare spinners, ~18 `Text` empty states, 10 dead-end error surfaces |
| `MuhabbetSkeleton` + `Modifier.shimmer` | one shared shimmer transition instead of one per row |
| `MuhabbetButton` / `IconButton` / `Switch` / `Divider` | press spring + haptic by default |
| `MuhabbetTextField` | 20 of 28 `OutlinedTextField`s |
| `MuhabbetDialog` + `ConfirmDialog` | 13 `AlertDialog`s |
| `MuhabbetBottomSheet` | 4 sheets, 3 identical openings |
| `SettingsRow` (Nav/Switch/Radio/Info) | two incompatible geometries for the same row |
| `SectionHeader` | a private reimplementation in the privacy dashboard |
| `UserAvatar` | 16 sites, 6 different sizes |

### API shapes that make a bug unexpressible

This is the recurring design move and it is worth stating on its own. Several real bugs were all the
same shape — a pairing where getting one half right and the other wrong is silently possible:

- `SettingsNavRow` requires a non-null `onClick`. A row wired to `.clickable { }` with an empty body
  looked tappable and did nothing; a row that goes nowhere is now a `SettingsInfoRow`.
- `MuhabbetDialog.dismissible` gates the scrim tap, the back gesture and the dismiss button
  together. The status composer guarded two of the three, so an upload in flight could be cancelled
  out from under itself.
- `SettingsNavRow.loading` swaps the icon for a spinner *and* stops accepting taps. Half of that
  pairing means a second data export on a double tap.
- `MuhabbetTextField.error` is one `String?` instead of `isError` plus `supportingText`. Only 3 of
  28 sites wired both; one without the other is a red outline with no explanation, or an explanation
  with nothing marked.

---

## 9. Guardrails

`./gradlew verifyUi` (runs without an Android SDK, registered on the root project so it can). Every
rule is a **ratchet**: counts may go down, never up. Baselines in
`gradle/ui-guardrails-baseline.properties`, rebaselined explicitly with `-PupdateUiBaseline` and
justified in the commit message.

| Rule | Start | Now |
|---|---|---|
| `directIcons` | 181 | 0 |
| `topAppBarColors` | 23 | 0 |
| `rawScaffold` | 27 | 0 |
| `rawTopAppBar` | 27 | 2 (ChatScreen + HomeShell, deliberate) |
| `modifierBlur` | 0 | 0 |
| `bareProgress` | 44 | 30 |
| `dpLiteral` | 182 | 160 |
| `unlocaledCase` | 12 | 12 |
| `hardcodedColor` | 4 | 4 (wallpaper swatches) |
| `spLiteral` | 5 | 5 |

Both source roots are scanned. Scanning only `composeApp` would let the design system escape every
rule the moment code moved into it — counts would fall, the ratchet would look like it was
improving, and the new module would be unpoliced. Exemptions are narrow: raw colours, `dp` and `sp`
are permitted **only** under `designsystem/theme/`, not in its components.

`verifyStringResourceSync` checks `values/` ↔ `values-en/` in both directions plus every
`Res.string.X` referenced from Kotlin.

---

## 10. What is not verified

Everything above is compiled and measured. **None of it has been seen on a screen.** This container
has an Android SDK but no emulator and no device, and Kotlin/Native cannot target Apple from Linux,
so iOS is proven only by CI's `macos-latest` job.

Outstanding before this can be called done:

- **Screenshot matrix**: 4 theme modes × 8 key screens, both locales, font scale 1.0 **and 1.3**.
  The first things to break at 1.3× are `ChatMeta` at 11sp, `ChatListItemMinHeight = 72.dp` and
  `BubbleMaxWidth = 320.dp`. An app that falls apart at 1.3× is not premium.
- **API 28 device**: the only way to prove the view-once blur failure and its fix, and where
  pre-API-35 edge-to-edge behaviour differs.
- **Physical device**: haptics cannot be judged on an emulator at all, and neither can real OLED
  black, scroll feel or spring damping.
- **First-frame font resolution.** `preloadFont` does not exist in this Compose Multiplatform
  version, so the fallback-then-reflow on the first frame is unmitigated — most likely visible on
  iOS, where font resolution is async.
- The four 44dp → 48dp avatar rows, and the copper palette generally, are arithmetic so far.
