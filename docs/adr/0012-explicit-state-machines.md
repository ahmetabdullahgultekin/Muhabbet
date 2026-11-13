# ADR 0012 — Lifecycles with more than two states are explicit, pure, tested state machines

**Status:** Accepted · **Date:** 2026-11-13 · Tracking: #758.

## Context

Several of the app's most important behaviours are lifecycles that are today expressed as scattered
booleans and nullable fields mutated from multiple effects: an outgoing message (sending, sent,
delivered, read — and *failed*, which did not exist, so a failed send deleted the bubble), the chat
viewport (follow new messages or not, jump target, unread count), the media upload (compress, upload,
attach, cancel), the WebSocket connection, the session (signed in, refreshing, expired), voice
recording, and app lock. Each such lifecycle has had at least one bug of the shape "two flags
disagreed" or "an illegal transition was possible".

## Decision

Any lifecycle with more than two states is modelled as:

```kotlin
sealed interface XState { /* one subtype per state, carrying only the data valid in that state */ }
sealed interface XEvent { /* inputs: user intents, I/O results, timers */ }
fun XState.on(event: XEvent): Transition<XState, XEffect>   // pure, total, no I/O
```

- **Pure and total:** the transition function has no side effects and handles every (state, event)
  pair explicitly — an impossible pair returns the same state, never throws.
- **Effects are data:** I/O is described by returned effects and executed by the owner (a Store from
  ADR 0010, or a pipeline), which feeds results back as events.
- **Exhaustively tested** in `commonTest` (mobile) or plain JUnit (backend): every legal transition,
  every illegal pair, and the persistence round-trip where the state survives a restart.
- **Persistence where the user would lose something:** a state that represents user data in flight
  (an unsent message) is persisted, read on start, and acted on — persistence, reader and mechanism.
- Pipelines (media upload, sync) are cancellable `Flow`s whose stage is one of these states.

## Consequences

- Behaviour that was implicit becomes reviewable and testable; the failed-send and scroll-policy bugs
  are expressed as tests that fail before the fix.
- Slightly more code per lifecycle than a pair of booleans. Accepted: these are the parts of the app
  where a wrong state loses messages or misleads the user.
