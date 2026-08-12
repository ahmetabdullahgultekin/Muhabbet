# ADR 0008 — Storage location is a per-conversation choice, and the stricter side wins

**Status:** Proposed · **Date:** 2026-08-12 · Supersedes nothing.

## Context

Message history currently lives on Muhabbet servers with no alternative. The product's stated
position is privacy-first, and the sharpest criticism of WhatsApp is that history rests on
infrastructure the user does not control. We want to offer a real choice: our servers, the user's
own server, or nothing but the two devices.

Two questions have to be settled before any of it can be built: what granularity the choice has,
and what happens when the two participants disagree.

## Decision

**The choice is per conversation, not per account.** A person can reasonably want a grocery list on
our servers, where it is searchable and survives a lost phone, and a private conversation on neither.
Forcing one setting across an account collapses that.

**When the two sides disagree, the stricter mode applies to the whole conversation, and both are
told whose choice it was.**

Strictness order: `LOCAL_ONLY` > `BRING_YOUR_OWN` > `CLOUD`.

If the other party finds the resulting mode unacceptable, they decline and the conversation does not
open. There is no mixed state.

## Why

The alternative — each side stores according to its own preference — cannot be described truthfully.
If Ali selects local-only and Ayşe's client keeps a server copy, Ali's messages are on our servers.
Any UI telling Ali his conversation is local would be false, and it would be false in exactly the
way we already had to correct once, when the profile screen claimed end-to-end encryption while
encryption was off.

A privacy control that does not actually control anything is worse than not offering it: it converts
a user's deliberate decision into a wrong belief.

Strictest-wins keeps the property the user selected true for the whole conversation. The cost is
that one participant can impose a mode on the other, which is why the other must see whose choice it
was and be able to refuse the conversation entirely.

## Consequences

- Storage mode belongs to the conversation, so it needs per-conversation settings to exist first.
- Mode must be negotiated at conversation creation and re-negotiated if either side changes it, with
  an explicit prompt rather than a silent switch.
- History already written under a previous mode does not migrate itself; changing mode must state
  what happens to what already exists.
- `LOCAL_ONLY` cannot be offered until end-to-end encryption is genuinely on. Relaying plaintext we
  can read while telling users we hold nothing would repeat the defect this ADR exists to avoid.
- Group conversations inherit the rule, which means one participant can force local-only on a group.
  Whether that is acceptable is left open in the design document.
- Server-side features that assume server-held content — search, moderation reporting under BTK
  5651, multi-device history — degrade or stop for stricter modes, and must say so at the point of
  choice.

## Alternatives considered

**Per-account setting.** Simpler to build and explain, but it makes the common case impossible: most
people want different guarantees for different conversations.

**Each side stores per its own preference.** Rejected above — it cannot be described honestly.

**Weakest mode wins.** Would mean anyone can silently downgrade someone else's privacy by choosing
convenience, which is the opposite of the point.

**Sender decides per message.** Too fine-grained to reason about; a conversation whose guarantees
change message to message cannot be summarised in any UI a person can trust.
