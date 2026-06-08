# ADR-0008: @mention storage & mute-piercing notification

**Date:** 2026-06-08
**Status:** Accepted (design only; implementation tracked as ROADMAP "@mentions" S1…S5)
**Decision Makers:** Solo engineer
**Supersedes / relates:** design `docs/design/T2-group-mentions.md`

## Context
Group chats need `@user` and admin-gated `@everyone` mentions (notify + highlight), a table-stakes
group feature (WhatsApp/Telegram/Slack). Two design questions are significant enough to fix here so
implementation can't drift:
1. **How are mentions stored/transported** — re-parsed from `@name` text at read time, or structured?
2. **How do mention notifications interact with conversation mute** — and with the (currently
   disabled, NoOp) E2E path?

## Decision
1. **Structured, client-resolved, server-validated.** Mentions are a list of
   `{userId, startOffset, length}` produced by the **client at compose time** (it has the group
   roster + cursor), carried on `SendMessage`/`NewMessage` as optional fields, and **validated** by
   the server (each `userId` must be a current member; non-members are silently dropped). The server
   **never parses `@name` from free text** — display names are non-unique and ambiguous. `@everyone`
   is a single boolean column, not N rows.
2. **Persist as additive metadata.** New `message_mentions` table (FK-cascade to `messages`) + a
   `messages.mentions_everyone` boolean (migration `V19`, additive). No hard cross-module FK to
   `users` (hexagonal/ArchUnit decoupling — membership is validated in the service, mirroring the
   media-access pattern).
3. **Mentions pierce conversation mute, not global mute/block.** A mentioned member who is offline
   **or** has the conversation muted still receives a push ("X mentioned you in <group>"). A member
   who has disabled notifications globally, or who has blocked the sender, is **not** notified. This
   is the whole point of a mention and matches WhatsApp.
4. **`@everyone` is admin-gated and size-capped.** Only group ADMIN/OWNER may send it
   (`MSG_MENTION_EVERYONE_FORBIDDEN` otherwise); expansion is capped by
   `MentionProperties.everyoneMaxMembers`.
5. **Flag-gated, reversible, default OFF.** `muhabbet.mentions.enabled` (backend) /
   `MentionsConfig.ENABLED` (mobile). OFF ⟹ server treats mention fields as empty and persists
   nothing; mobile never shows the `@` popup → byte-identical to today. Kill-switch is the flag, not
   a redeploy. New WS/DTO fields are optional with empty defaults → old clients unaffected.
6. **E2E boundary is explicit and untouched.** Today (plaintext-under-TLS) the server derives the
   notify-set from the plaintext `mentions` list. This feature ships **only** in that world and
   **must not be cited to weaken or delay E2E**. When the libsignal block clears, revisit moving the
   notify-set computation client-side; the structured userId list is metadata and can travel
   regardless. No crypto flags are flipped and no disabled Signal files are touched by this feature.

## Consequences
**Positive**
- Highlights resolve by `userId` (rename-stable); no fragile name re-parsing.
- Notification semantics are precise and testable (mute-pierce vs global-mute vs block).
- Fully additive + flag-gated → zero-risk dark ship, trivial rollback.
- Reuses existing fan-out, push port, and membership checks; no new endpoints.

**Negative / deferred**
- Two new optional fields on hot WS/DTO types (small surface-area cost; covered by serialization
  round-trip tests for old-client compat).
- Under future real E2E, the server-side notify-set derivation must move client-side — explicitly
  deferred to the crypto track (gated on the libsignal re-integration).
- `@everyone` noise risk in large groups → mitigated by admin-gate + size cap; rate-limiting deferred.

## Alternatives rejected
- **Regex `@name` at read time** — ambiguous (duplicate display names), can't reliably target a
  userId, breaks on rename, invites notification-spoofing. Rejected.
- **Hard FK `message_mentions.mentioned_user_id → users(id)`** — couples the messaging module's
  schema across the module boundary; rejected for the same reason media uses a soft reference.
- **Notify on every mention regardless of block/global-mute** — violates user intent and
  moderation/blocking guarantees. Rejected.
</content>
