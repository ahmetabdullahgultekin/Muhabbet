# Conversation storage modes

**Status:** design, not started. **Owner:** Ahmet. **Raised:** 2026-08-12.

## The idea

Where a conversation's history lives should be the users' choice, not ours. Three modes, chosen
**per conversation** rather than per account:

| Mode | History lives | We hold |
|---|---|---|
| **Cloud** (default) | Muhabbet servers | messages, media, metadata |
| **Bring your own** | a server the user runs | nothing but routing |
| **Local only** | both participants' devices | nothing; we relay and forget |

The pitch is real: WhatsApp's weakest point is that everything transits and rests on infrastructure
the user does not control. Offering a mode where we provably hold nothing is a genuine
differentiator, and it cuts our storage bill at the same time.

Below is what it costs to do honestly. None of it kills the idea; all of it changes the shape.

## Three things that decide whether this is honest

### 1. Mode 3 is a lie until E2E is on

The claim "no server sees your messages" is only true if the relay cannot read what it relays.
Today `E2EConfig.ENABLED` is **false** and `NoOpEncryption` returns plaintext; messages travel
plaintext under TLS, which means our server *can* read them. Shipping "local only" on top of that
would tell users something false about the one thing the mode exists to promise.

So **Mode 3 is gated on the standing libsignal blocker**, not on this design. It cannot ship first.
This is the same failure the padlock UI already had and had to be corrected for.

Mode 2 does not have this problem — the user's own server can be trusted by the user by definition.

### 2. Someone has to hold a message the recipient has not received yet

This is the hard part of Mode 3, and it has no free answer. If Ali sends at 02:00 and Ayşe's phone
is off until 08:00, the message exists somewhere for six hours or it does not exist at all.

| Option | Cost |
|---|---|
| **Sender-side queue.** Ali's device retries until Ayşe is reachable. | Nothing is delivered while Ali is offline. Two people who are never online together never talk. Unacceptable for a messenger. |
| **Encrypted store-and-forward with TTL.** We hold ciphertext we cannot read, delete on delivery or after N days. | Requires E2E to be real. We hold *something*, so the promise becomes "we hold ciphertext briefly", not "we hold nothing". |
| **Direct P2P when both online, encrypted spool when not.** | Both paths to build and test; NAT traversal for the direct path. |

**Recommendation: encrypted store-and-forward, and say so plainly.** "We relay and delete" is a
promise we can keep and prove. "We never touch it" is not, and users will discover the difference
the first time a message survives a night. The honest line is *we cannot read it and we do not keep
it*, which is still far stronger than WhatsApp's position.

Direct P2P is worth exploring later as an optimisation for the both-online case, not as the
foundation. NAT traversal needs STUN/TURN, and a TURN relay is a server that sees traffic anyway.

### 3. You cannot force the other person's device

The asymmetric case — Ali picks local, Ayşe picks cloud — has only one honest resolution.

If Ayşe's client stores the conversation on our servers, then the conversation *is* on our servers,
whatever Ali chose. Telling Ali "local only" while his words sit in our database because of Ayşe's
setting would be a false claim about the property he selected.

So: **the stricter mode wins, and the other party is told.** Ali picks local → the conversation is
local for both, and Ayşe sees "Ali chose local-only for this chat; it will not be backed up". If
Ayşe cannot accept that, she declines and there is no conversation.

The "make the other side store it locally by force" framing in the original idea is the same thing
stated from the other end, and it is the correct behaviour — just note that it is not force, it is a
*negotiated minimum*, and the UI should present it as the other person's choice rather than as a
restriction we imposed.

## What "bring your own" can and cannot mean

Worth separating, because the original idea grouped things that behave very differently:

- **Own server (viable).** The user runs the Muhabbet backend; the client points at their base URL.
  This is the Matrix homeserver model. It is real work — versioning, migrations, a client that can
  talk to more than one origin — but it is a known shape.
- **Own object storage for media (viable).** S3-compatible endpoint with their own credentials.
  Media is blobs; this fits.
- **Google Drive / OneDrive as the message store (not viable).** Those are file sync APIs with
  per-user rate limits and no query surface. A messenger needs indexed reads, ordering and
  cursor pagination. Drive can be a **backup/export target** — which is a good feature in its own
  right — but it cannot be the live store.

So Mode 2 ships as *own server, optionally own media bucket*, and Drive/OneDrive becomes a separate
**encrypted backup destination** feature available in any mode.

## Multiple conversations with the same person

The "market chat / home chat / private chat" idea is independent of storage mode and can ship much
earlier. It is: named conversations between the same pair, each with its own settings — including
its own storage mode, so "grocery list on the server, private talk local" works exactly as
described.

This is the cheapest genuinely novel thing in the whole proposal and it does not wait on crypto.
Worth doing first.

## What this costs us

- **Multi-device.** Local-only history cannot appear on a second device without syncing it
  somewhere. That is the trade users are choosing; it must be stated at the moment of choice, not
  buried.
- **Device loss.** No server copy means a lost phone is a lost history. Needs an explicit,
  user-driven encrypted export before we let anyone pick the mode.
- **Search.** Server-side message search cannot cover local-only chats. Local search must exist
  first, or the feature silently stops working for those conversations.
- **Moderation and legal.** BTK 5651 reporting currently relies on server-held content. A report
  from a local-only chat can only carry what the reporter's device supplies. This needs a legal
  answer before launch, not after.

## Shape of the work

1. **Per-conversation settings** — the container everything else hangs off, and the prerequisite for
   named multi-chats. No crypto dependency.
2. **Named multiple conversations per contact.** Ships on top of 1.
3. **Mode negotiation** — storing the chosen mode per conversation, the strictest-wins rule, and the
   UI that explains the other party's choice. Can be built and tested with Cloud and BYO only.
4. **Mode 2: own server.** Client-side base-URL configuration, plus a self-host guide.
5. **Encrypted export/backup** to a user-chosen destination, including Drive/OneDrive. Prerequisite
   for offering Mode 3 responsibly.
6. **Mode 3: local only**, on encrypted store-and-forward, **after** E2E is genuinely on.
7. *Later:* direct P2P for the both-online case, if measurement shows it is worth the NAT work.

## Open questions

- Does the relay keep delivery receipts for local-only chats? Receipts are metadata about who talked
  to whom and when — the thing "we hold nothing" implies we do not keep.
- Retention for undelivered ciphertext: how many days before a message is dropped, and does the
  sender find out?
- Group conversations: strictest-wins across ten participants means one person can force local-only
  on the group. Is that right?
- Can a conversation change mode after the fact, and what happens to the history that already exists
  under the old mode?
