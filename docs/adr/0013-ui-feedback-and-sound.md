# ADR 0013 — One feedback engine for haptics, sound and motion, with a tea-glass notification sound

**Status:** Accepted · **Date:** 2026-11-13 · Tracking: #758, epics #764, #766.

## Context

`MuhabbetHaptics` maps 14 semantic intents to Compose `HapticFeedbackType`, behind one user switch.
There is no platform haptic engine (no Android `VibrationEffect.Composition`, no iOS Core Haptics), so
strength and timing cannot be tuned or synchronised with anything; there is no UI sound at all; and
nothing reads the system reduce-motion setting. Several intents are wired to the wrong moment or not
at all (the primary button fires `MessageSent`; a real send fires nothing).

## Decision

1. **`LocalReduceMotion`** (expect/actual: Android animator/transition scale = 0, iOS
   `UIAccessibility.isReduceMotionEnabled`) is provided at the theme root. Every motion token degrades
   to a snap or a crossfade under it; decorative loops stop.
2. **`MuhabbetFeedback`** replaces direct haptic calls: one semantic intent produces haptic + sound +
   (optional) visual on the same frame. Android uses `VibrationEffect.Composition` primitives when
   `areAllPrimitivesSupported` says so and falls back to `HapticFeedbackType`; iOS uses soft impact
   generators. The intent table in the design doc §7 is the single source of truth.
3. **`MuhabbetSounds`**: a small set (~12) of short UI sounds (< 150 ms, soft attack, ±4 % pitch
   variance, quiet), played through `SoundPool` / `AVAudioPlayer` with an `.ambient` session. They
   respect silent/ringer mode and the system touch-sound setting, have their own user toggle
   (persisted, read at the theme root, enforced inside the engine), and are rate-limited (rapid sends
   keep the haptic and drop the sound).
4. **Default notification sound: a spoon stirring a tea glass.** Android notification channels are
   immutable after creation, so it ships as a new channel id with migration of the old one; iOS ships
   it as a bundled `UNNotificationSound`. The audio asset must be original or licence-clear, and is
   approved by ear on a physical phone before any release that carries it.
5. **Rules that do not change:** no haptics on scroll, keystrokes, incoming messages or back
   navigation; sound is never the only signal; every expressive moment has a reduce-motion fallback
   and a frequency guard.

## Consequences

- Haptics, sound and the notification tone can only be signed off on a device; every PR touching them
  says "compiled, not felt/heard" until the owner confirms.
- Asset weight is budgeted (UI sounds total < 200 KB).
