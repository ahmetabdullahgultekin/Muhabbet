# ADR 0010 — Every screen is a Decompose component, and every stateful screen owns a retained Store

**Status:** Accepted · **Date:** 2026-11-13 · **Supersedes:** the CLAUDE.md rule "there are no
ViewModels … do not add a ViewModel layer" (2026-08-21) and "Composables: hoist state simply".
Tracking: #758, epic #761.

## Context

The app has 35 destinations and no presentation state holders. At `2434324` screens keep their state
in `remember { mutableStateOf(...) }`, pull repositories with `koinInject()`, and drive I/O from
`LaunchedEffect` and `rememberCoroutineScope().launch`. Decompose children are bare configurations
(`childFactory = { config, _ -> config }`), so no screen has its own `ComponentContext`: no lifecycle,
no `InstanceKeeper`, no `StateKeeper`, no `childSlot`, no `childPages`.

The 2026-08-21 rule called this a deliberate KISS choice. It was reasonable while screens were
small. It no longer is, and the costs are measured, not speculative:

- **State does not survive the platform.** The Activity declares no `configChanges`, so a rotation, a
  theme switch or a language change recreates every composition. The chat draft, the loaded
  messages, open dialogs and the reading position are lost and re-fetched. Process death loses all
  of it. Only one `rememberSaveable` exists in `ui/`.
- **Effects race each other.** In `ChatScreen` the history load and the WebSocket collector are two
  `LaunchedEffect(conversationId)` blocks writing the same `messages` variable; the load assigns the
  list wholesale and silently drops a message the socket appended while the request was in flight.
- **Work dies with the composition.** A save launched from `rememberCoroutineScope()` is cancelled
  when the user navigates away mid-request.
- **Behaviour cannot be unit-tested.** The scroll policy, outgoing-message lifecycle, merge rules and
  role checks live inside 1,000-line composables. The chat-scroll and failed-send bugs fixed in epic
  #760 survived precisely because nothing could test them.
- **Koin singletons are the only escape hatch**, so cross-screen state is either a global singleton
  or duplicated `remember` copies.

## Decision

1. **Every destination is a Decompose child component** created with its own `ComponentContext`
   (`childFactory = ::createChild`). Navigation stays in `RootComponent` / `AuthComponent` /
   `MainComponent`; the HomeShell tabs become `childPages`, dialogs and sheets that must survive
   recreation become `childSlot`.
2. **Every stateful destination owns a Store**, retained with
   `instanceKeeper.getOrCreate { XxxStore(...) }`:
   - `val state: StateFlow<XxxUiState>` — one immutable data class, the only thing the screen renders;
   - `val effects: Flow<XxxEffect>` — one-shot effects (scroll to, highlight, show undo, haptic);
   - `fun onEvent(event: XxxEvent)` — the only way the screen talks back;
   - a `CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)` cancelled in `onDestroy`;
   - user input that must survive process death (drafts, query text) saved via `stateKeeper`.
   A destination with no state at all (About) is a component without a Store. There is no third model.
3. **Screens are pure renderers**: `XxxScreen(state, onEvent)`. `koinInject` is forbidden under `ui/`
   (`verifyUi` rule `koinInjectInUi`, ratcheted to 0). View-only ephemeral state — a menu's expanded
   flag, focus, an animation — legitimately stays `remember`/`rememberSaveable`.
4. **Lifecycles with more than two states are explicit state machines** (ADR 0012), owned by the
   Store or a pipeline, never by booleans spread across a composable.
5. **Dependencies:** Stores receive repositories and controllers through their constructor; the
   component resolves them from Koin (`factory { params -> XxxStore(...) }`). Stores never see Compose.
6. **Lifecycle-aware collection:** screens collect with `collectAsStateWithLifecycle`.

## Why Decompose components and not AndroidX `ViewModel`

Decompose is already the navigation and lifecycle owner; `InstanceKeeper` is its documented
equivalent of `ViewModel`, is multiplatform, and is scoped to exactly the navigation entry it belongs
to. Adding AndroidX `lifecycle-viewmodel` (even its KMP flavour) would put a second lifecycle model
beside Decompose's, with bridge code at every destination and two answers to "when does this die".
One model is the point. MVIKotlin was considered and rejected: a plain `StateFlow` + `onEvent` Store
is enough, and the extra framework buys nothing the tests need (YAGNI, boring technology).

## Consequences

- Every screen migration is a self-contained PR: component + Store + tests + pure screen. Goldens
  (ADR/epic #759) must be unchanged by a migration that intends no visual change.
- Store tests live in `composeApp/commonTest` with fake repositories: state transitions, merge rules,
  cancellation on destroy, and `stateKeeper` round-trips.
- Global controllers (`ThemeController`, `PrivacySettingsController`, `AppLockController`,
  `WsClient`) remain singletons — they are app-scoped by nature. Stores observe them.
- CLAUDE.md's "Mobile" section is rewritten to describe this model, and the "do not add a ViewModel
  layer" rule is removed with a pointer here.

## Alternatives considered

- **Keep `remember` + Koin singletons** (status quo): rejected for the five costs above.
- **Holders only for large screens** (threshold rule): rejected by the owner — two models for the same
  problem is itself a maintainability defect, and "is this screen big enough yet" is a question every
  change would have to re-litigate.
- **AndroidX ViewModel (KMP):** rejected, see above.
