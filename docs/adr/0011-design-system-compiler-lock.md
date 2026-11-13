# ADR 0011 — The design system is the only UI surface a screen can reach, enforced by the compiler

**Status:** Accepted · **Date:** 2026-11-13 · Tracking: #758, epics #762, #763, #766.

## Context

`:mobile:designsystem` holds the visual language (tokens, Copper/Ink palette, Manrope, springs,
haptic intents, components) and its module boundary already stops the library reaching the app.
The opposite direction is unguarded: `composeApp` declares `implementation(compose.material3)`
itself, so any screen can import any Material 3 component. At `2434324` ~73 files import Material 3,
there are ~576 `MaterialTheme.*` reads, dozens of raw Buttons, Cards, Dividers, text fields and
progress indicators, and three public design-system signatures leak Material 3 types
(`SnackbarHostState`, `TopAppBarScrollBehavior`, `SheetState`). The text-based `verifyUi` ratchets
caught some of this but can be dodged (import aliases) and only cover what someone thought to write
a rule for.

## Decision

1. The end state is that **`composeApp` does not depend on `compose.material3` at all.** The design
   system keeps it as `implementation` (never `api`), so Material 3 types are invisible outside it.
   From then on, a screen that reaches for Material 3 does not compile.
2. To get there, the design system grows the vocabulary screens need:
   `Muhabbet.scheme` and `Muhabbet.shapes` (Muhabbet-owned roles instead of `MaterialTheme.*`),
   Muhabbet-owned state types replacing the leaked ones (`MuhabbetSnackbarState`,
   `MuhabbetTopBarScroll`, `MuhabbetSheetController`), the missing components (epic #763), and thin
   `MuhabbetText` / `MuhabbetIcon` / `MuhabbetSurface` wrappers.
3. Migration is ratcheted: `materialThemeRead`, `rawM3Controls`, `rawDivider`, `bareProgress`,
   `rawAsyncImage`, `rawClickable`, `snackbarHostState`, `importAlias` and finally `m3Import`, each a
   `verifyUi` rule whose count may only fall. The dependency is removed in the commit that takes
   `m3Import` to zero.
4. **Centralise tokens and defaults, not uniformity** (design doc §1) still holds: bespoke surfaces
   such as the chat composer and the chat top bar stay bespoke, but they are built from design-system
   parts, not from Material 3.

## Consequences

- A one-time migration touching most UI files, done one package per PR with screenshot goldens as
  the safety net.
- Wrappers that only rename (`MuhabbetText`) exist purely so the compiler can enforce the boundary;
  that is their whole value and is accepted.
- Material 3 upgrades and any future swap (e.g. a different component library on iOS) become a
  change inside one module.

## Alternatives considered

- **Permanent allowlist** (`Text`, `Icon`, `LocalContentColor` stay importable, text rules police the
  rest): cheaper, but the boundary is then a regex, which can be bypassed and silently decays.
- **Konsist architecture tests:** viable for import rules, but adds a dependency whose Kotlin 2.4
  support is unverified, and is still weaker than "it does not compile".
