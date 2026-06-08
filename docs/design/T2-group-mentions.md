# Design: @mentions in group chats (Muhabbet Tier 2)

> Follows `docs/design/_TEMPLATE_feature-design.md`. Design-doc-first; contract before code;
> vertical slices behind a default-OFF flag; additive/reversible.

| | |
|---|---|
| **Status** | S1 implemented (2026-06-08) — contract + migration + flag, default OFF; S2–S5 pending |
| **Author / Reviewers** | autonomous SE loop (Task 2) / owner @ahmetabdullahgultekin |
| **Feature flag** | `muhabbet.mentions.enabled` (backend) · `MentionsConfig.ENABLED` (mobile) — default **OFF** |
| **ADRs** | `docs/adr/0008-mentions-storage-and-notification.md` (this PR) |
| **Tracking** | ROADMAP "Near-term → @mentions in group chats" → slices S1…S5 |

---

## 1. Context & problem

Group chats exist and work at 1:1 grade (`GroupService.kt`, announcement mode). But in an active
group, a message addressed to a specific member is invisible — there is no way to (a) **direct** a
message at someone, (b) **notify** them even if they have the conversation muted, or (c) **highlight**
their name so they can scan for "messages that need me." This is table-stakes WhatsApp/Telegram/Slack
behaviour and the #1 group-usability gap once a group passes ~5 people. Audience: every group-chat
user; especially large/community groups where mute-by-default is common.

Two sub-behaviours WhatsApp ships that we want:
- **`@name`** — mention one or more specific members; they get a notification **even if muted**, and
  the message is highlighted for them.
- **`@everyone` / `@all`** — admin-gated broadcast mention that notifies every member.

## 2. Goals / Non-goals

**Goals**
- Type `@` in a group composer → autocomplete of members → insert a mention token.
- Persist *which users* a message mentions (structured, not regex-at-read-time).
- Deliver a **mention notification** to mentioned users that **pierces conversation mute** (but still
  respects a global "no notifications" / blocked state).
- Highlight mentions in the message bubble; badge conversations that mention me.
- `@everyone` restricted to group admins/owners; capped by group size.
- Fully **i18n** (TR default + EN); no hardcoded strings; reversible behind a flag.

**Non-goals (bound the tier)**
- **No E2E interaction.** Mention metadata travels in the message envelope; when E2E is eventually
  on, mention *display text* is inside the (encrypted) body, but the **notify-set** is derived
  server-side from plaintext today and would move client-side under E2E — explicitly deferred to the
  crypto track (see §11). This feature ships only in the current plaintext-under-TLS world and must
  not be cited as a reason to weaken E2E.
- No cross-group / channel mentions, no `@role` (only `@user` and `@everyone`).
- No mention *editing* semantics beyond the existing 15-min edit window (re-parse on edit).
- No rich mention rendering in push payloads beyond name + snippet.

## 3. Current state (what we reuse vs extend)

| Reuse | Where |
|---|---|
| Message send path | `MessageService.sendMessage`, WS `SendMessage`→`NewMessage` (`WsMessage.kt`) |
| Group membership | `conversation_members` table, `GroupService`, member lookup already used for fan-out |
| Push delivery | `PushNotificationPort` / `FcmPushNotificationAdapter` (already used for offline messages) |
| Mute state | conversation mute (mobile mute-duration picker PR #59; server mute flag on membership) |
| Notification module | `notification` adapter inside messaging |

**Extend:** add a structured `message_mentions` table + a `mentions` field on `SendMessage`/
`NewMessage`/`MessageResponse`; add a mention-aware branch in the notification fan-out so a mention
overrides conversation-mute.

## 4. Proposed design

Mentions are **structured metadata**, parsed **client-side at compose time** (the client knows the
member list and cursor) and **validated server-side** (membership + admin-gate). We do **not** parse
`@name` from free text on the server — display names are ambiguous and not unique. The wire carries a
list of mentioned **userIds** (+ an `everyone` flag); the rendered `@display` text lives in the
message body with offsets so the client can highlight without re-resolving.

```
Composer ──@──▶ member autocomplete (local group roster)
   │ user picks "Ayşe"
   ▼
content = "merhaba @Ayşe bak", mentions = [{userId: U2, start: 8, len: 5}]
   │  SendMessage{ content, mentions, mentionsEveryone:false }
   ▼
MessageService.sendMessage
   ├─ validate: every mention.userId ∈ conversation_members  (drop non-members)
   ├─ if mentionsEveryone: require sender is ADMIN/OWNER, expand to all memberIds
   ├─ persist message + rows in message_mentions
   ├─ broadcast NewMessage{...mentions...} to members (existing fan-out)
   └─ MentionNotifier: for each mentioned member who is OFFLINE or has convo MUTED
          → PushNotificationPort.send(  "Ayşe mentioned you in <group>" )   // pierces mute
                                                                            // skips globally-muted/blocked
```

Highlight + "mentions me" badge are pure client concerns driven by the `mentions` list and the
current userId.

C4/sequence: `docs/diagrams/mentions-sequence.mmd` (added with the implementing PR).

## 5. Data model

Additive migration **`V19__add_message_mentions.sql`** (UUID PKs, soft-delete-agnostic — mentions die
with their message):

```sql
CREATE TABLE message_mentions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id      UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    mentioned_user_id UUID NOT NULL,          -- FK-soft to users(id); no hard FK (cross-module)
    start_offset    INT  NOT NULL,            -- char offset into message.content (for highlight)
    length          INT  NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_message_mentions_message ON message_mentions(message_id);
CREATE INDEX idx_message_mentions_user    ON message_mentions(mentioned_user_id);

-- @everyone is recorded as a single column on the message rather than N rows.
ALTER TABLE messages ADD COLUMN mentions_everyone BOOLEAN NOT NULL DEFAULT false;
```

**Invariants:** a mention row's `mentioned_user_id` must be a member of the message's conversation at
send time (enforced in service, not DB, to avoid the cross-module FK). `start_offset + length ≤
length(content)`. `mentions_everyone = true` ⟹ sender was admin/owner at send time.

## 6. API / protocol / contract (additive, backwards-compatible)

**WS — extend `SendMessage` and `NewMessage`** (new optional fields default to empty → old clients
unaffected):

```kotlin
// shared/.../protocol/WsMessage.kt
data class MentionRef(val userId: String, val start: Int, val length: Int)

data class SendMessage(
    ...,
    val mentions: List<MentionRef> = emptyList(),
    val mentionsEveryone: Boolean = false
)
data class NewMessage(
    ...,
    val mentions: List<MentionRef> = emptyList(),
    val mentionsEveryone: Boolean = false
)
```

**REST — `MessageResponse`** gains the same two optional fields (history/pagination renders
highlights). **No new endpoints.** Member-roster for autocomplete reuses the existing group-members
endpoint.

**Error codes (ErrorCode enum):** `MSG_MENTION_NOT_MEMBER` (mention of a non-member — dropped, not
fatal), `MSG_MENTION_EVERYONE_FORBIDDEN` (non-admin used `@everyone`).

## 7. Files to add / change (hexagonal layering)

```
backend/
  src/main/resources/db/migration/
    V19__add_message_mentions.sql                                   (+)
  messaging/domain/model/
    Message.kt                                                      (~) +mentions, +mentionsEveryone
    Mention.kt                                                      (+) domain value object
  messaging/domain/port/out/
    MentionRepository.kt                                            (+) out-port
    MentionNotificationPort.kt                                      (+) (or reuse PushNotificationPort)
  messaging/domain/service/
    MessageService.kt                                               (~) validate + persist + notify branch
    MentionNotifier.kt                                              (+) mute-piercing notify logic
  messaging/adapter/out/persistence/
    entity/MessageMentionJpaEntity.kt                              (+)
    MentionPersistenceAdapter.kt                                    (+)
    repository/SpringDataMessageMentionRepository.kt               (+)
  messaging/adapter/in/web/MessageMapper.kt                         (~) map mentions to DTO
  shared/config/MentionProperties.kt                               (+) flag + @everyone cap
shared/
  protocol/WsMessage.kt                                             (~) MentionRef + fields
  dto/MessageResponse.kt                                            (~) +mentions
mobile/composeApp/.../
  crypto-or-config/MentionsConfig.kt                                (+) ENABLED flag
  ui/chat/MentionAutocomplete.kt                                    (+) @-popup over group roster
  ui/chat/MessageComposer.kt                                        (~) detect '@', insert token
  ui/chat/MessageBubble.kt                                          (~) highlight mention spans
  ui/home/HomeShellScreen.kt                                        (~) "@ mentions me" badge
  composeResources/values/strings.xml + values-en/strings.xml      (~) mention strings
```

## 8. Rollout & flags

- `muhabbet.mentions.enabled=false` (default): server ignores `mentions`/`mentionsEveryone`
  (treats as empty), persists nothing, fan-out byte-identical to today. Mobile `MentionsConfig.ENABLED
  =false`: composer never shows the `@` popup; bubbles render plain text. **OFF = no behaviour change.**
- Dark (flag OFF in prod, code present) → staging ON → canary one group/tenant → broad.
- Backwards compat: new WS/DTO fields are optional with empty defaults; pre-update clients send/receive
  without them and are unaffected. Kill-switch = flip flag, no redeploy.

## 9. Agile iteration plan (vertical slices)

| Slice | Scope | Done = |
|---|---|---|
| **S1** ✅ | Contract + migration + flag plumbing (no behaviour) — **DONE 2026-06-08** | `V19` added; `MentionRef`/fields compile on backend+shared+mobile; flags default OFF; `:shared:jvmTest` + `:backend:compileKotlin` + mobile metadata-compile green |
| **S2** | Server persist + validate (membership drop, `@everyone` admin-gate) | unit tests: non-members dropped, non-admin `@everyone` → `MSG_MENTION_EVERYONE_FORBIDDEN`, rows written; history renders `mentions` |
| **S3** | Mention-piercing notifications | `MentionNotifier` notifies muted/offline mentioned members; respects global-mute/block; covered by unit tests with a fake `PushNotificationPort` |
| **S4** | Mobile compose: `@` autocomplete + token insert | demoed in running app — typing `@` lists group members, selection inserts highlighted token, send carries `mentions` |
| **S5** | Mobile render + "mentions me" badge | bubbles highlight mention spans; conversation list badges convos that mention me; i18n TR+EN |

Each slice is independently shippable behind the flag.

## 10. Test plan

- **Unit (backend, MockK):** `MessageServiceTest` — mention rows persisted; non-member mentions
  dropped; `@everyone` requires admin; `MentionNotifierTest` — muted member still notified, blocked
  member not, globally-muted not.
- **Contract (shared):** `WsMessageSerializationTest` — round-trip `SendMessage`/`NewMessage` with and
  without `mentions` (proves old-client compat via default-empty).
- **Integration (Testcontainers):** send a group message with mentions → `message_mentions` rows +
  push invoked for the muted member.
- **Security:** `@everyone` admin-gate (privilege check), mention of a user **not** in the group is
  dropped (no notification-spam / membership-leak), offset bounds validated (no OOB highlight).
- **Mobile:** autocomplete filters roster; composer token insert; bubble highlight snapshot.

## 11. Risks & open questions

- **E2E interaction (deferred).** Today the server derives the notify-set from plaintext `mentions`.
  Under real E2E the server can't read the body, but the structured `mentions` userId list is
  *metadata* and can still travel (or move fully client-side). **This feature must not be used to
  argue against E2E**; when the libsignal block clears, revisit whether the notify-set is computed
  client-side. (Crypto boundary per `CLAUDE.md` — untouched here.)
- **`@everyone` abuse / noise** in large groups → cap at `MentionProperties.everyoneMaxMembers`
  (e.g. 256, matching `MAX_GROUP_MEMBERS`) and admin-gate; consider rate-limit later.
- **Display-name drift** — we store offsets into the *body text at send time*; if a user later renames,
  the historical mention text is unchanged (acceptable, matches WhatsApp). Highlight resolves by
  userId, not name.
- **Edit re-parse** — editing a message re-sends `mentions`; service replaces mention rows for that
  message within the edit window.
- **Open:** should muted-but-mentioned show a distinct notification channel/sound? (proposed: yes,
  "Mentions" channel) — owner decision.

## 12. Rollback

Additive only: drop behaviour by flipping `muhabbet.mentions.enabled=false` (no redeploy). Full revert
= flag OFF + (if ever needed) `DROP TABLE message_mentions; ALTER TABLE messages DROP COLUMN
mentions_everyone;` — but since the columns/tables are unused when the flag is OFF, leaving the
additive migration in place is zero-cost and preferred (never drop a shipped migration in prod).
```
