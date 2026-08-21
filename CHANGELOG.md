# Changelog

All notable changes to this project will be documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/). While the app is pre-1.0 the minor number carries
breaking changes; 1.0.0 is reserved for the first release that ships end-to-end encryption on.

## [Unreleased]

### Security — blocking someone now stops two more things it did not stop

- **The person you blocked could still watch your stories** (#294). Status is scoped to your
  contacts, and blocking someone does not delete the conversation the two of you share — so by the
  only definition of "contact" this app has, they stayed one, and every status you posted afterwards
  went to them. The per-status audience list was no defence: it is chosen in the composer, and
  nobody goes back to edit it when they block someone.
- **The person you blocked could still add you to their community** (#294). Adding someone to a
  group has refused this since #554; adding them to a *community* did not, and that path ends by
  enrolling them in the announcement channel — a group conversation the owner can post to. The
  existing "must already be in one of the community's groups" rule narrowed it without closing it,
  because a shared group is exactly what two people still have after one blocks the other. The
  member picker no longer offers someone the add would refuse, either.

Both refusals reuse the error code the ordinary "cannot add this person" case already returns. A
code of their own would be a reliable way to test who has blocked you.

**The other four ways a blocked person could reach you were already closed** — direct messages,
presence and last-seen, push notifications, and your profile and about text — but only three of
them had any test that said so. They do now, including one that wires the real push chain and
asserts that nothing reaches FCM. That one was never a guard at all: push lives two hops from the
send path and is correct today only as a consequence of the message never being stored, which is
exactly the kind of correctness a refactor removes without noticing.

### Fixed — failures that were invisible, or reported as the wrong thing

- **One scheduled message that could not be sent silently stopped all of them** (#560). Every due
  message shared a single database transaction, so a failure on the third undid the first two as
  well — they were still pending, the next run a minute later picked the same batch in the same
  order and hit the same message, forever. Each message now gets its own transaction, one failure is
  logged with the message id and skipped, and the rest go out. A run also takes a bounded batch
  instead of the entire backlog.
- **The health check could not see the one thing most likely to fail quietly** (#494). Every photo,
  voice note and document lives in MinIO, and after startup nothing ever checked it: it could be
  down, out of disk, or have lost its bucket while the server reported itself healthy and a deploy
  of a build with completely broken media passed its gate. There is now a media check, reported in a
  way that makes an outage visible **without** rolling back a release — restarting the backend does
  not fix storage, and neither does reverting.
- **Anyone logged in could read the server's internals** (#494). The health endpoint hid its detail
  from the public internet but not from ordinary users: any valid account could read the database
  vendor, the Redis version and the host's free disk space. That now requires an admin account.
- **The app was told the same thing whatever went wrong** (#572). Refusing to send a message
  because it was too long, because you are not in the conversation, because the group is
  announcement-only, because it had already been sent, or because the server genuinely broke — all
  five arrived as one indistinguishable code, so the app could not say which had happened in the
  reader's own language. Each now carries its own, matching what the REST API has always done.
- **A malformed typing indicator could drop your connection** (#572). One unparseable id thrown from
  a frame nobody was even waiting on escaped far enough to close the socket, and the chat stopped
  working for a reason with nothing to do with chatting. No frame can cost a connection now.

**What is not proven yet:** the media check has not been exercised against a real MinIO — that needs
a running server, so it runs on CI, not on a machine without Docker. The ordering that keeps a media
outage from failing a deploy *is* proven here, against Spring's real aggregator.


### Fixed — the path every message takes

Three defects on the same code path — saving a message and handing it to its recipients — fixed
together because they touch the same five files and because two of them only make sense as a pair.

- **Twenty people could be sending at once, and the twenty-first was refused** (#491). The database
  transaction that saved a message stayed open across the whole delivery fan-out: the WebSocket
  writes, the Redis publishes, the push notifications. A WebSocket write blocks, and Tomcat waits up
  to twenty seconds for a phone that has stopped reading, so one stalled recipient held a database
  connection for that whole time. There are twenty connections. That was the ceiling for the entire
  server — not twenty per second, twenty at any one moment — while the database sat idle. Delivery
  now happens after the save has committed.
  The same change fixes something quieter: recipients could be handed a message *before* it was
  committed, so if the save then failed they were left holding a message the server did not have.
- **A message could stop arriving for someone whose connection was fine** (#490). Three things write
  to a phone's connection — the reply to what it just sent, someone else's incoming message, and the
  keep-alive — and only some of them took the lock that was supposed to keep them apart. When two
  collided, the server concluded the connection was dead and removed it: the person stayed logged
  in, appeared offline to everyone, and silently received nothing further until they reconnected.
  Every write now goes through one place, and a phone that has stopped reading is disconnected on a
  ten-second budget instead of blocking everyone else for twenty.
- **Sending one message cost eight database round trips, and a group message hundreds** (#492).
  Every insert on this path first read the row it was about to create — a row that by definition did
  not exist yet — because of how the persistence layer decides whether something is new. In a group
  that was one wasted read per member, plus one lookup per member to find their devices for the
  notification. A message to a 256-person group now writes the delivery rows in batches and looks up
  devices once for the whole group.

None of this is visible as a feature. It is what stops the app falling over once more than a handful
of people use it at the same time.

**What is not proven yet:** the connection-pool relief is argued from the code and held in place by
tests; it has not been measured under load on a production-like server. The statement-count test that
proves the third fix needs a real database, so it runs on CI and not on a machine without Docker.

### Changed
- **Archived chats are somewhere you can find them** (#612). The archive was a section at the
  *bottom* of the conversation list that vanished whenever nothing was in it — so the one person who
  wrote the app used the feature and concluded it did not exist. Archived chats now leave the main
  list entirely and sit behind a single **Arşivlenmiş Sohbetler · N** row pinned to the top, which
  opens a screen of its own. Unarchiving is the same long-press gesture that archived the chat, and
  the chat is back in the main list when you come back.
No code has landed on `dev` since `v0.3.10` — the tag and the branch tip are the same commit.

### Changed — documentation
- **The system is described somewhere a human can read it.** New
  [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md): the modular monolith and its boundaries, what each
  backend module owns, how the shared KMP module feeds the server and the app from one definition,
  the end-to-end path of a sent message, and a section on what has deliberately *not* been built.
  Until now the only description of the architecture was `CLAUDE.md`, which is 77 KB of agent
  instructions and was never written to be read by a person.
- **The README says what the project is for, and what state it is actually in.** It was a stub with
  a repository tree; it now carries the positioning that had never been written down anywhere, and a
  status table that says plainly which parts do not work.
- **`README.en.md` removed** — it duplicated `README.md`. English lives in `README.md`, Turkish in
  `README.tr.md`, and each fact exists in exactly two places rather than three.
- **`ROADMAP.md` aligned with the milestones that actually exist**, and the release process now
  includes the two steps that were only written down elsewhere: run the gates with Docker up, and
  install the built artifact on the emulator before publishing.
- **Six stale one-shot documents deleted from `docs/`** — a superseded WhatsApp-clone UI spec, a
  deployment guide for a server that was never bought, the MVP sprint plan, an engineering roadmap
  claiming we host on GCP, a feature-gap analysis superseded by a tracker issue, and a release plan
  that contradicted `ROADMAP.md`. Five other dated documents were **kept and given a correction
  banner** instead, because each still holds a decision or a verified-open finding that lives
  nowhere else. Decision records (`docs/adr/`, `docs/decisions.md`) were kept whether or not they
  have aged well; that is what they are for.
### Fixed
- **The chat wallpaper picker offered twelve near-identical swatches** (#380). Six warm creams a few
  percent apart and six near-black inks — one hue family, no gradients. There are now **24 solids
  across seven low-chroma families** (warm, clay, wheat, sage, sea, harbour, mauve) and a new **Renk
  Geçişi** tab with 8 gradients, wired end to end: stored, resolved and painted. Nothing was
  removed, so no saved selection loses its swatch.
- **"Arka planı kaldır" left its own tick behind** (#380). It nulled the stored colour and left the
  grid marking it as chosen — a picker claiming a selection the chat was not painting.
- **The date-separator pill failed WCAG AA over any non-default wallpaper** (#380). It draws on the
  wallpaper at what was a hardcoded 80% opacity, so a fifth of the user's pick bled into the ground
  its label is read against: 4.03:1 over a light wallpaper in the dark theme, 3.88:1 over a white
  photo. Its opacity is now a measured design-system token, and `WallpaperContrastTest` holds every
  wallpaper × theme combination against the 4.5:1 floor.
- **`/api/v1/wallpapers` would have wiped the wallpaper it was called to set** (#380). The controller
  declared private request/response classes whose field names disagreed with the shared DTOs of the
  same name, with a `"DEFAULT"` default behind the mismatch. It uses the shared DTOs now. The
  endpoint still has no client — wallpaper stays device-local, deliberately; see `WallpaperRepository`.

### Added
- **The app tells you what changed when it updates** (#672). Three releases went out between 0.3.8
  and 0.3.10 carrying dozens of fixes, several of them to features that had never worked, and the
  person using the app had no way to learn about any of it: `CHANGELOG.md` sits in the repository
  and Play's "What's new" is somewhere you only look if you go there. There is now a **Neler
  değişti** sheet, shown once after an update and never again for that version, plus a **Sürüm
  notları** screen under Settings → Hakkında for anyone who dismissed it or wants to look back.
  It is deliberately silent on a fresh install — telling a brand-new user what is new since a
  version they have never run is meaningless.

  The notes are **written by hand in `strings.xml`, in both locales, and are not derived from this
  file.** Parsing the changelog at build time was considered and rejected: this file is the
  engineering record, with issue numbers, file names and the reasoning for things that were *not*
  done. Two readers, two texts. The rule that comes with it: every version that ships writes its own
  three-to-six lines, and a test fails the build if the shipping version has none.

## [0.3.10] — 2026-08-18

Thirteen merged changes, most of them the same shape: a feature whose code was written and whose last
connection was never made. That pattern is what the `0.4 — the app tells the truth` milestone exists
to remove, and this is the release where most of it goes.

### Fixed — things that were written and never wired
- **Blocking someone did nothing.** The Block and Report buttons showed a success message and called
  no endpoint — grep for `blockUser` in `composeApp` returned zero hits. They now block, and there is
  a **Blocked Contacts** screen to review and undo it (#613).
- **Archiving a chat changed nothing** (#612, #655). `conversation_members.archived` was written and
  never read back: `isArchived` rode its `false` default to the client, so the *Arşivlenmiş Sohbetler*
  section could never render. `isMuted` and `isLocked` had the same defect.
- **Muting silenced nothing** (#571). The push path never consulted `muted_until`. It does now,
  server-side, where it still works with the app closed — and an expired mute lets pushes resume.
- **A community had nowhere to talk** (#584). `communities.announcement_group_id` existed in the
  schema, on the domain model and in the JPA entity, and **no code path ever wrote it**. Every
  community now has an announcement channel; members are enrolled, including ones added later.
- **App Lock locked nothing** (#378). No persistence, no reader, no mechanism. Now: biometric or
  device credential, a re-arming lifecycle gate, `FLAG_SECURE` so the recent-apps thumbnail does not
  leak the chat list, and a stated grace period.
- **Voice transcription crashed or opened the microphone** (#381). `EXTRA_AUDIO_SOURCE` is API 33 and
  wants a `ParcelFileDescriptor` of raw PCM; the old code passed a file path string to an API-31 call
  with no version guard. Audio is now decoded and streamed properly, and the control is hidden where
  the platform cannot do it.

### Fixed — reported from a real phone
- **A push arrived and the sender still saw one tick**, and separately **two ticks appeared with no
  notification** (#596, #618). Delivery is now acknowledged over HTTP from the push path, and a push
  is suppressed only for the conversation the recipient is actually looking at.
- **A second message replaced the first notification instead of stacking** (#623, #595). Now
  `MessagingStyle`, with the history read back from the system's own notification so it survives the
  FCM service restarting.
- **The full-screen photo viewer was not full-screen** (#651) — every photo ever opened had the chat
  showing down both sides.
- **The typing bubble and the online/last-seen subtitle never appeared** (#643, #644). Neither was a
  broken wire: the typing bubble was composed correctly but landed one row below the viewport,
  because auto-scroll was keyed on `messages.size` and the bubble is not one of the messages; and
  nothing on the chat screen ever *asked* for the peer's presence, so a chat opened onto someone
  whose status did not happen to change while you watched showed a blank subtitle. The subtitle is
  now seeded from `GET /api/v1/users/{id}`, which means it shows exactly what that peer's own
  visibility setting allows and nothing when it hides everything. Shipped in 0.3.10 and missing from
  its notes; recorded here on 2026-08-21.

### Added
- **About** screen with the version and the three legal documents (#614).
- **Profile photos open full-screen** from the chat title, the profile, group info and Settings (#615).
- **Voice recordings can be cancelled, locked or previewed** instead of sending the moment you let go
  (#601).

### Not fixed, and not claimed
- **#590** — a chat opening mid-history could not be reproduced with a 93-message conversation, on
  open or across a pagination boundary. #657's scroll-anchor fix is real and merged as hygiene, but
  it is not evidence that #590 is gone. The issue stays open.

## [0.3.9] — 2026-08-17

Everything here was found by the owner using 0.3.8 on a real phone, and every fix was reproduced and
then re-checked on the emulator before this was cut. That order is the change: 0.3.7 and 0.3.8 both
shipped defects that one launch and two taps would have caught.

### Fixed
- **Playing one voice message moved every voice bubble in the chat** (#641). A single `AudioPlayer`
  is shared across the conversation and no bubble asked whether the loaded audio was its own, so all
  of them rendered whatever it was doing. Reproduced on 0.3.8 — two bubbles, both showing ⏸ 0:01 —
  and re-checked after the fix, where the untouched bubble stays at ▶ 0:00.
- **A tapped notification stacked a second chat over the one already open, titled with the wrong
  name** (#642). `openChat` pushed unconditionally instead of returning to a conversation already on
  the stack — the idiom four functions away in the same file — so back revealed a duplicate. And the
  title preferred the name the server put in the push payload over the one in the phone's address
  book, which is why it changed when the duplicate was popped.
- **The search you can reach never searched messages, and its field never took focus** (#638).
  Typing did nothing because the keyboard never came up; and with text in the field the screen
  filtered the loaded conversation list without ever calling `/api/v1/search/messages`. Message
  search had exactly one caller in the app, on a screen the bottom navigation does not open.
- **Voice playback had no speed control, no seek, and restarted from zero after a pause** (#602).

### Added
- Playback speed (1×/1.5×/2×) and a draggable position slider on voice messages.
- Message hits in the home search, in their own section under conversations, with a failure that says
  so rather than rendering as "no results".

## [0.3.8] — 2026-08-17

**0.3.7 crashed on launch for every user. Do not install it.** This release exists to undo that, and
carries the two fixes that were already queued behind it.

### Fixed
- **The app would not start** (#633). `WsClient`'s `localCache` parameter was narrowed to the
  `PendingMessageCache` interface in #578/#579 and `AppModule` still resolved it with a bare `get()`
  — Koin resolves by the parameter's declared type, nothing registers that interface, so the app
  threw `NoDefinitionFoundException` the first time it injected `WsClient`, which is at launch. The
  same hazard was already documented two definitions below, for `ConversationRepository`.
- **The 15-minute edit window was invisible in the app** (#597), so the only way to learn it was to
  retype a message and be told "mesaj gönderilemedi" — which was wrong twice over, since nothing was
  being sent and that was not the reason. The rule now lives in `ValidationRules` and both halves
  read it; past the window the menu item is disabled and says why.
- **The read-receipts switch described WhatsApp's rule, and ours is the opposite** (#620). It said
  turning receipts off costs you the ability to see others'. It does not: the setting gates only
  your own published receipt. The app had been talking users out of a setting by inventing a cost.

### Process
- `CLAUDE.md` now separates the **Windows dev machine** (which has a working emulator) from the
  **Hetzner CI host** (which cannot have one), and states the rule that would have stopped 0.3.7:
  no Play release without installing the built APK on the emulator and launching it. A green CI
  build proves the app compiles; it proves nothing about whether it starts.

## [0.3.7] — 2026-08-17

> **Withdrawn — crashes on launch (#633).** Superseded by 0.3.8.

A release driven almost entirely by one afternoon of the owner using the app on a real phone. Every
item below has an issue behind it with the evidence; the fixes that are **not** device-verified say
so, because there is no emulator on the build host and colour, motion and gesture cannot be signed
off here.

### Fixed
- **Tapping a notification opened the chat list instead of the chat** (#594). The notification wrote
  the conversation id onto its intent and nothing ever read it: `MainActivity` had no `onNewIntent`
  and never called `getStringExtra`. The same hole swallowed deep links — `muhabbet://chat/{id}` is
  declared in the manifest, which is why it opened the app, and nothing then looked at the URL.
  Handled now on both the cold-start and already-running paths; the second is the common one and is
  what a lazier fix would have missed. `launchMode` is `singleTask`, previously unset.
- **The conversation list said `16.08` for yesterday, next to times it looked identical to** (#585).
  Yesterday is *Dün*, the last week is weekday names, and older dates carry the full year so
  `16.08` can no longer be read as `16:08`.
- **A message that could not be queued was reported to the user as queued** (#578). `WsClient.send()`
  threw "queued, will retry" even when the insert into the pending table had failed, so the UI showed
  a clock over a message that was gone. Also: the reconnect guard tested `isActive` on a
  `CoroutineStart.LAZY` job, which is `false` while New, so every call started another connect loop.
- **An `&` in a search silently truncated it** (#622). The query was interpolated into the URL, so
  `kahve & çay` reached the server as `kahve` and `c++` as `c  `. Worth recording that the first
  report of this was wider than the truth: spaces and Turkish characters were never affected, and
  measuring is what showed so. `cursor` and `timestamp` were encoded at the same time — a base64
  cursor containing `+` would have silently reset pagination to the first page.
- **The WebSocket buffer bean failed every integration test context** (#598). Introduced with the
  #493 fix and invisible until CI could run again; production was never affected, since Tomcat is
  real there.

### Security
- **A document upload accepted any MIME type, `text/html` included** (#287). `uploadImage` and
  `uploadAudio` both had allow-lists; `uploadDocument` checked only size, so script could be stored
  and served from the media host. Verified against production: `text/html`, `text/html; charset=utf-8`
  and `image/svg+xml` all refused with `MEDIA_UNSUPPORTED_TYPE`; `application/pdf` still accepted.

### Infrastructure
- **Every CI job had been failing before it started** (#563), rate-limited by `codeload.github.com`
  while downloading `actions/checkout`. `infra/scripts/seed-runner-action-cache.sh` seeds the
  runner's action cache from github.com instead. The trade is stated in the script: a seeded action
  is pinned until the script is re-run with `--force`.

## [0.3.6] — 2026-08-17

Everything below was already sitting under Unreleased when 0.3.6 was cut, so it is recorded here.
Some of it shipped in the 0.3.5 build without a heading being cut for it; all of it is in 0.3.6.

### Security
- **Anyone holding a message id could react into a conversation they were not in**, and the
  reaction was broadcast live over WebSocket to every member of it. They could also vote in its
  poll, read who had reacted, and read the poll results. Neither `ReactionService` nor
  `PollService` checked membership. A message id is not a secret — it travels in WebSocket frames,
  REST responses and push payloads — so anyone removed from a group kept the ability to react into
  it. Membership is now required on all five paths, and the emoji, previously free text into a
  `VARCHAR(16)`, is an allow-list shared with the reaction bar. (#557)
- **Two-step verification could not be set up at all**, and the bearer token was withheld from every
  `/api/v1/auth/**` path — including the two-step endpoints, which identify the caller purely from
  the token. Confirmed against production: `POST /api/v1/auth/two-step` answered `405`, while the
  path the server actually serves answered `200`. Enabling two-step still does not gate sign-in;
  that is #566, and the setup screen says so rather than implying protection it does not give. (#544)

### Fixed
- **Sending a long message disconnected the chat instead of being refused.** Validation allowed
  10,000 characters; Tomcat's untouched WebSocket buffer closed the socket at 8,192. Measured
  against production: the last frame accepted carried 7,949 characters, and 9,000 closed the
  connection with 1009. The limit counts decoded characters, not bytes, so Turkish text was cut off
  at the same character count as ASCII. Both buffers are now set explicitly. (#493)
- **The language radio named a language the app was not rendering**, and tapping the language you
  wanted did nothing because it already looked selected — the way out of the trap was closed. The
  radio now reports what is on screen and the tap always acts. Switching to OLED also appeared to
  lose the chat wallpaper: that suppression is deliberate and controlled by a toggle, and is now
  explained rather than silent. (#548)
- **A chat opened right after creating it, or right after forwarding into it, had no avatar and a
  dead tap on the title.** Every one of those paths already held the user id and avatar it was
  dropping. (#555)
- **A scheduled message that failed to send said so in one line with no cause in it.** The failure
  is now logged with its exception, and a run reports how many it delivered — "the job ran and did
  nothing" and "the job never ran" used to produce identical logs, which is what hid scheduled
  delivery being dead. (#556)
- **Notification permission was never requested**, so a fresh install on Android 13+ silently got
  no notifications; it only ever appeared to work because the permission had been granted by hand
  over adb. Verified on a device: revoke, launch, the dialog appears; grant, and it is not asked
  again. (#547, #552)

### Changed
- `CLAUDE.md` records CI's 429 failure mode, and no longer states a shared-module test count — the
  written figure had gone stale in exactly the way the neighbouring instruction exists to prevent.

### Added
- **`GET /api/v1/users/me/privacy`.** `PATCH` shipped without a read side, so the settings screen
  had no way to fetch the stored values and could only assume them — always the most permissive
  option, which it then wrote back on the next edit. (#377)

### Fixed
- **The KVKK privacy screen did nothing.** Read receipts, last seen and about visibility were
  `remember { mutableStateOf }` seeded with the most permissive setting: nothing loaded on open,
  nothing saved on change, and reopening reported "everyone" to a user who may have chosen
  "nobody". All three now load and save. The read-receipts switch in Settings was a second,
  independent copy that could contradict the first; both now read one shared controller. (#377, #382)
- **Two privacy settings were stored but never read.** Turning off read receipts changed nothing —
  the READ was still broadcast to the sender and still aggregated into their tick on every history
  load. Restricting "about" changed nothing — the text was returned to every caller regardless.
  Both are now enforced server-side. Read receipts are suppressed at publish time, not storage
  time: the reader's own row stays READ so their unread badge still clears, while the sender sees
  DELIVERED. Existing accounts are unaffected until they change a setting. (#377)
- **The HD media-quality setting was inert at both ends.** `TokenStorage.getMediaQuality`/
  `setMediaQuality` had default bodies that no implementation overrode, so the picker reset on
  every open; and no upload path read the value, so every photo compressed at a hardcoded 1280/80.
  Both halves are fixed — the members are abstract and overridden on all three implementations, and
  uploads resolve the selected profile per upload. (#383)
- **Settings → Hesap showed a UUID under "Telefon".** The screen already fetched the profile and
  discarded the phone number it contained. (#383)
- **"Yeni Grup" was unreachable where it was needed and dead where it appeared.** It lived inside
  the contacts list, so it was invisible in the three states where that list does not render, and
  on the Calls tab it rendered while doing nothing. It now sits beside the by-number row, hidden on
  the call surface for the same reason that one is. (#383)
- **Opening a community always failed.** `GET /api/v1/communities/{id}` answered with a nested
  `{community, groups, members}` object, but the app has only ever been able to read the flat shape
  declared in the shared DTOs — so every community opened onto "Could not load content". The
  endpoint now returns that flat shape, and each group carries its own name, avatar and member
  count instead of just an id.
- **Every community claimed 0 groups and 0 members.** The list endpoint never sent the two counts
  the row displays, and the client's defaults filled in zeros. Both counts are now served, batched
  into one query each rather than one per community.
- **The community list reshuffled between refreshes** — it was assembled from an unordered
  `findAllById`. It is now ordered by creation time.

### Removed
- **The "Profil Fotoğrafı" visibility picker**, the notification and vibration switches, and the
  "Video mesajı" attachment entry. None of them had a mechanism to control: there is no
  `profile_photo_visibility` column or request field and avatars are returned unconditionally;
  push delivery is off in the deploying stack (`FCM_ENABLED: "false"`), so no notification
  preference could take effect; and no video recorder exists on either platform. Each is removed
  rather than given persistence, which would have made them look repaired while still doing
  nothing. They return with the mechanism, not before it. (#377, #382, #383)

### Security
- **Any authenticated user could read any community.** `GET /api/v1/communities/{id}` checked only
  that the caller held a valid token, and answered with the community's name, description, avatar
  and member count plus every linked group's name, avatar and size. Reading a community now
  requires membership. Nothing legitimate is lost: the app has no discovery, search, invite or
  deep-link path, so a community id only ever reaches the detail screen from the caller's own list
  or straight after they created it. (#375)
- **Adding a group to a community disclosed arbitrary conversation metadata.** `POST
  /api/v1/communities/{id}/groups` checked that the caller ran the community and nothing else, so
  anyone could create a community — they are free and uncapped — post a conversation id they had no
  relationship with, and read back that conversation's name, avatar and member count from the
  detail endpoint. Only the unguessability of a random UUID stood in the way, which is not
  authorization. The caller must now be a member of the conversation, and it must be a group rather
  than a direct chat or a channel. (#375)
- **An owner could enrol any user id in a community without their knowledge.** The community then
  appeared in that person's Communities tab having never been shown to them. Membership now derives
  from group membership, as it does in WhatsApp: an owner may only add someone already in one of
  the community's own groups. This is a restriction rather than a feature, and it is deliberately
  not half of an invite system — a real invite the recipient accepts is #387. A community with no
  groups yet cannot gain a second member until then. No client flow is affected: the app has never
  had a way to add a community member at all. (#375)

## [0.3.4] — 2026-08-16

The first build meant to be installed by someone other than us. Signed with the release key and
headed for Play internal testing, which is why the notes below are blunter than usual about what
still does not work.

### Fixed
- **Messages were being thrown away by the server.** A message sent to anyone not on an open socket
  at that exact moment was published to Redis, received, and discarded — the subscriber was reading
  the subscription pattern where the recipient's id should have been. It logged its own failure on
  every occurrence, which is how it was finally found. (#397)
- **Tapping the camera button killed the app.** The permission was declared in the manifest and
  never requested, so Android refused the capture with a `SecurityException`. (#399)
- **Your address book was uploaded the moment you granted the contacts permission**, with no
  explanation and no way to decline, while the privacy documents described it as opt-in. There is a
  consent step now, and declining leaves a usable app. (#425)
- **"Delete my account" did not delete anything** beyond a status flag — the phone number stayed in
  plaintext, the contact-sync hash kept matching, and devices kept their push tokens. (#426)
- **Photos uploaded rotated.** EXIF orientation was ignored and then destroyed by the re-encode, so
  the information needed to correct it was gone. All eight orientations are handled now. (#408)
- **Every field whose value happened to equal its default vanished from API responses**, so a user
  who hid their online status sent no `isOnline` at all rather than `false`. (#269)
- Typing at the left of the login field produced `5000000001+90`. The country code is a fixed prefix
  now, and pasting a number with spaces or a leading zero works. (#439)
- The verification screen stacked a stale error under the current one. (#403)
- `last_seen_at` had never once been written — a `@Modifying` query ran with no transaction. (#402)
- A malformed request body answered 500 instead of 400. (#401)

### Changed
- **The app icon is Muhabbet's own** — the copper mark from the login screen — instead of Material
  Green 900 with a white speech bubble. (#418)
- **Login can no longer run up an unbounded bill.** Nothing capped how many billed verifications
  could be started; there are now hourly and per-number ceilings, plus rate limiting at the edge.
  (#440)
- The auth rate limiter no longer trusts a header the client controls. (#270)
- Flyway checksum validation is on in production, so an edited migration can no longer be silently
  ignored. (#429)
- A community can be deleted by its owner. (#407)

### Known issues
- **Calls do not work and never have.** (#367–#373)
- **Push notifications do not arrive.** Two independent causes, both being worked. (#398)
- End-to-end encryption is off, and the app says so.
- Broadcast lists reach the server now but cannot gain a recipient or be sent to. (#449)
- App Lock does nothing. (#378)

## [0.3.3] — 2026-08-16

0.3.2 was never published; everything in it is here too. This adds the second batch of
drawn-but-dead controls.

### Fixed
- **Broadcast lists have never worked.** The app was asking the server at an address it does not
  answer at, so every request failed — and because failures used to look like emptiness, the screen
  said "you have no broadcast lists" instead. Two more problems were behind that: the member count
  was never sent (so every list would have shown "0 members"), and the member list printed internal
  ids instead of names. (#392)
- **Privacy settings now actually apply.** Read receipts and "about" visibility were being saved and
  then ignored by the server; both are now enforced. The screen loads your real settings instead of
  guessing, and tells you if saving failed instead of pretending. (#377, #382)
- **HD media quality works.** The setting was saved nowhere and read by nothing; photos were always
  compressed the same way regardless. (#383)
- **Video messages and links written inside a message are now tappable.** (#361, #362)
- Settings showed an internal id where your phone number should be. (#383)

### Removed, because they never did anything
- **Notification and vibration switches.** Push does not work, so there was nothing for them to
  switch off. They will come back when there is.
- **Profile-photo visibility.** The server has no such setting; avatars were always visible.
- **"Video mesajı"** from the attachment sheet — no video recorder exists on either platform.

### Known issues
Unchanged from 0.3.2: calls do not work (#367–#373), App Lock and wallpaper still do nothing (#378,
#380), voice transcription crashes on Android 8–11 (#381), push notifications do not arrive, and E2E
encryption is off.

**Worth checking first:** long-pressing a message that contains a link, to reach reply/forward/delete.
Links became separately tappable in this release and that gesture has not been tested on a device.

## [0.3.2] — 2026-08-16

Mostly things that looked like they worked. Three audits went through the app after 0.3.1 and found
around twenty controls that were drawn but never wired; this release fixes the ones that were cheap
to fix and files the rest openly.

### Added
- **You can now start a chat with a number that is not in your phone's contacts.** Until now the only
  way to reach anyone was to add them in the phone's address book first and wait for a sync — and
  nothing in the app said so. If the number is not on Muhabbet you get an invite instead. (#389)
- **Communities can finally be managed.** See who is in one, add someone, remove a group, rename it,
  and leave. Before this a community could only be created and looked at, which is why every
  community in existence had exactly one member. (#376)

### Fixed
- **Buttons kept spinning for about four seconds after the work was already done.** Reporting the
  result blocked the spinner from being cleared. Nineteen places, six of them on the *success* path,
  which is why it happened on ordinary use and not just on errors. (#390)
- **The app treated server errors as success.** Every request ignored the HTTP status, so a
  rejection came back looking like an empty answer: "no communities yet" after a server failure, and
  a cheerful "added" after a request the server refused. This one was hiding the true state of
  several other features. (#374)
- **Anyone could read any community**, including the name, avatar and size of every group inside it,
  and could attach a conversation they were not part of in order to read its details. (#375)
- **Removing a group from a community always failed** — the query was built as a read, so it threw
  instead of removing anything. It now has a test that runs against a real database. (#360)
- **The app was sending placeholder encryption keys to the server on every single launch**, for
  everyone, while encryption is switched off. Nothing is sent now, and the 2,802 junk records
  already stored have been deleted. (#379)
- Opening a group from inside a community no longer shows a blank title bar.
- Wrong or expired login codes now explain themselves in your own language instead of showing the
  server's raw text.

### Known issues
- **Calls do not work and never have.** The app never tells the server a call started, no microphone
  is ever switched on, and the call server is not configured. Tapping call shows "connecting"
  forever. (#367–#373)
- Broadcast lists have always failed — the app asks for an address the server does not answer at.
  Now that errors are visible, this will show as an error rather than an empty list. (#392)
- App Lock, HD media quality and wallpaper still do nothing. (#378, #380, #383)
- The privacy screen's visibility settings still do not save. (#377)
- Transcribing a voice message crashes on Android 8–11. (#381)
- Video messages, and links written inside a message, are still not tappable. (#361, #362)
- Push notifications still do not arrive.
- End-to-end encryption is still off, and the app still says so.
- **Nothing here was seen on a screen before release** — the build host has no emulator and no
  device. Compilation, 92 unit tests and 85 backend tests pass; how it looks and feels does not
  follow from that.

## [0.3.1] — 2026-08-15

Everything here came out of driving 0.3.0 on a real phone. The first item is a crash 0.3.0
introduced and should have blocked its release.

### Fixed
- **Swiping a message to reply crashed the app.** 0.3.0 replaced the swipe offset with a spring-back
  animation, and that spring is under-damped by design — so returning to rest crossed zero and
  settled from below for about 270 ms. The offset feeds a `padding`, which rejects a negative value,
  and the app died with `IllegalArgumentException: Padding must be non-negative`. The offset is now
  bounded to its own domain rather than clamped where it is read, which fixes all three places that
  consume it. 0.2.x could not hit this: the offset was a plain value that only ever moved by
  clamping into range. (#359)
- **Communities could not be opened.** The server sent a community's details in a shape the app has
  never been able to decode — the app expected the community's own fields at the top level and the
  server nested them, and the two disagreed about what a group in a community looks like. Decoding
  threw every time, so the screen could only ever report failure. The server now sends what the app
  asks for, and the group and member counts, which were always displayed as 0, are real. Community
  and group ordering is now stable between refreshes instead of reshuffling. (#358)
- **Four things that looked tappable and were not.** A photo that arrived as a thumbnail rendered
  perfectly and ignored every tap; a PNG or file attachment had an empty click handler; a shared
  location had no click handler at all; and a link preview's handler was never passed to it, so the
  default did nothing. All four now open, and when nothing on the device can open them, the app says
  so instead of appearing to ignore you. (#357)

### Known issues
- Video messages still have the thumbnail-only dead tap that photos had. (#361)
- A bare link inside message text is still not tappable — only the preview card is. (#362)
- The full-screen image viewer has no swipe between images, no save, no share and no error state. (#363)
- Removing a group from a community fails. (#360)
- Everything above was verified by compiler and tests, not on a screen — the build host has no
  emulator and no device.

## [0.3.0] — 2026-08-15

The app stops looking like a clone of another messenger and starts looking like itself. The palette,
the type scale and the components are Muhabbet's own, and the whole visual language now lives in a
Gradle module that the compiler stops any screen from working around.

### Fixed
- **The theme setting never did anything.** Settings offered system / light / dark / OLED and saved
  the choice, but the theme only ever read the system setting — so picking *light* on a dark-mode
  phone changed nothing, and the OLED scheme, which was fully written, could not be reached at all.
  Choosing a theme now repaints immediately instead of restarting the app. (#355)
- **View-once photos were not hidden on Android 8.0–11.** The preview was blurred with an effect that
  does nothing below Android 12, so on those versions the "hidden" photo rendered fully sharp. It is
  now a sealed placeholder on every version. Anyone on Android 8–11 who sent or received a view-once
  photo before this build should assume its preview was visible. (#355)
- **Contact search could not find Turkish names.** Lowercasing `İsmail` produced an `i` followed by an
  invisible combining dot, which nobody types, so searching for `ismail` matched nothing. Search now
  folds the four i-shapes and the Turkish diacritics together, so `ismail` finds `İsmail` and `oz`
  finds `Öz`. (#355)
- **A status upload could be cancelled out from under itself** — the composer blocked the Cancel
  button and the scrim while an upload was in flight, but not the back gesture. (#355)
- **Six screens ignored the notch and the navigation bar.** They have no scaffold, so nothing was
  applying insets while the app drew edge to edge. (#355)
- The unread badge and the "sending" clock were unreadable — white on green at 1.98:1 and 1.69:1
  against WCAG's 4.5:1. All 21 measured contrast failures in the old palette are gone. (#355)

### Changed
- **New palette and typeface.** Ink and Copper replace the cloned green; Manrope replaces the stock
  Material type scale. The old constants were literally named `WhatsAppAccent`, which the roadmap
  tracked as a brand and legal risk before any screenshot goes public.
- **Every screen shares one frame.** 27 hand-rolled scaffolds, 26 top bars in three different
  colours, 13 dialogs and 4 bottom sheets collapse onto shared components; 181 icons are now named by
  meaning rather than by glyph. Loading states are skeletons instead of a bare spinner, and empty
  states say something instead of repeating the screen title.
- **The app has physics.** Springs, haptics and a depth scale, one vocabulary everywhere: list
  animation, chat entry, navigation transitions, a shared-element handoff that carries the avatar
  from the conversation row into the chat title, and a back gesture you can preview and abandon on
  Android 14+.

### Added
- `:mobile:designsystem`, a separate library module. It cannot see the app, so a component in it can
  never reach a screen, a repository or a string; the raw colour values are `internal`, so no screen
  can name a hex.
- `./gradlew verifyUi` — ratcheted checks over the UI layer that run without an Android SDK, plus
  WCAG contrast tests that fail in both directions, so fixing a known failure forces the list to be
  tightened rather than left to rot.
- Mobile CI now runs the mobile tests. Until this release it ran none of them.

### Known issues
- **Nothing in this release has been seen on a screen by its author.** The build host has no emulator
  and no device. Compilation, 69 unit tests and the contrast maths all pass, but motion and feel are
  exactly what none of those prove. Please report anything that looks wrong.
  - Watch for **back skipping a screen** (two back handlers are registered where predictive back is
    now active), and for a **stray avatar** floating over the screen when leaving a chat.
- Push notifications still do not arrive; FCM registration fails with `FIS_AUTH_ERROR` against a
  restricted Firebase API key.
- Sharing a location still asks for latitude and longitude by hand.
- End-to-end encryption is still off, and the UI still says so.

## [0.2.2] — 2026-08-12

0.2.1 could not open a conversation at all: the chat screen showed "Failed to load conversation"
and no message ever appeared, even though the messages were on the server the whole time.

### Fixed
- **"Failed to load conversation".** The backend runs with
  `spring.jackson.default-property-inclusion: non_null`, so a field it leaves null is absent from
  the JSON entirely — and a direct conversation has no `name`. `ConversationResponse` declared
  `name`, `avatarUrl`, `lastMessagePreview` and `lastMessageAt` as required, so decoding threw and
  the screen reported a failure it could not explain. Thirty nullable fields across fourteen DTOs
  had the same shape; all now carry defaults. (#122)
- **Posting a status returned 500.** Spring Boot 4 serialises with Jackson 3, and only the Jackson 2
  Kotlin module was on the classpath — so Kotlin default parameter values in *every* request DTO
  were ignored, and any field the client omitted arrived as null. (#124)
- **Two-Step Verification failed on open**, calling an endpoint that was never implemented. (#125)
- **Three controls that could not be pressed**: the Calls tab had no way to start a call, the camera
  badge on the avatar swallowed taps, and the keyboard covered *Get Started* on the profile screen
  with no way back. (#126)

### Verified by driving the app
Text with Turkish characters, long messages, links, a photo, a poll, a location and a document all
sent and landed on the server. Community creation, status posting and contact sync all work from the
UI rather than from a response code.

### Known issues
- Push notifications are not arriving; the FCM registration fails with `FIS_AUTH_ERROR` against a
  restricted API key.
- Sharing a location asks for latitude and longitude to be typed by hand — there is no map picker.
- Settings can claim Turkish is selected while the app runs in the device language. (#114)
- The overflow menu has no accessibility label. (#127)
- End-to-end encryption is still off; the UI says so.

## [0.2.1] — 2026-08-12

0.2.0 shipped with a bug that gated everything social: contact sync ran at most once per screen and
had no refresh, so a new user never got a contact — and with no contact there is no way to start a
conversation. The app read as though nothing worked.

### Fixed
- **Contacts sync more than once, and there is now a refresh button.** The sync sat in a
  `LaunchedEffect` keyed only on the permission, guarded by `contacts.isEmpty()`; once it matched
  anyone the guard was false forever, and a sync that matched nobody could not retry because the key
  never changed. `CreateGroupScreen` had the same defect. (#113)
- **SMS actually arrives.** Twilio Verify is live. The provider variables had been added to
  `infra/docker-compose.prod.yml`, which deploys nothing, so selecting the provider had no effect
  and codes kept going to the server log. (#115)
- **Every OTP request crashed the coroutine machinery.** The dependency bump left the classpath
  split — `kotlinx-coroutines-core` at 1.11.0 against `core-jvm` at 1.10.2 — so code compiled
  against one API called into the other and threw `NoSuchMethodError`. Found by sending a real SMS,
  not by a test. (#116)

### Verified by driving the app, not by assuming
Chat sends and delivers (confirmed from the recipient's side), communities create (`201`), media
uploads and fetches back as a real JPEG, Starred/App Lock/Wallpaper/Media Quality screens open,
Updates and Calls history load.

### Known issues
- **Two-Step Verification is broken** — the screen calls `GET /api/v1/auth/two-step/status`, which
  the backend never implemented. (#117)
- **The Calls tab has no way to start a call.** Calling only works from inside a conversation. (#119)
- The camera badge on the profile avatar swallows the tap that opens the picker. (#109)
- Settings can claim Turkish is selected while the app runs in the device language. (#114)
- End-to-end encryption is still off; the UI says so.

## [0.2.0] — 2026-08-12

First build where a user can log in, and the first with working media. Everything below was
verified against the live server and on an emulator, not just in tests.

### Fixed
- **Login was impossible in every shipped build.** The app pointed at
  `muhabbet.rollingcatsoftware.com`; Traefik only ever routed `muhabbet-api.rollingcatsoftware.com`.
  DNS resolved and `:80` redirected, so the name looked alive, but no certificate existed for it and
  the TLS handshake was rejected. (#99)
- **The Updates tab returned 500.** `StatusService.getContactStatusesForUser` and
  `createStatusWithAudience` were plain `fun`s — final in Kotlin — so Spring's CGLIB proxy could not
  override them and the call ran on the proxy, where every injected field is null. Both now sit on
  `ManageStatusUseCase`. (#103)
- **Photos, voice notes and documents never loaded.** `MINIO_PUBLIC_ENDPOINT` was unset on the
  compose file that actually deploys, so clients were handed `http://shared-minio:9000/...`. There
  was also no public route to MinIO at all; Traefik now serves one. (#106)

### Added
- **Twilio Verify as an OTP provider** (`muhabbet.sms.provider=twilio-verify`). Unlike the Messages
  API adapter it needs no purchased sender number. Verify owns the code, so it gets its own
  `OtpVerifier` port rather than being forced into `OtpSender`; cooldown, expiry and attempt limits
  stay on our side. Default stays `mock`. (#100)
- **ArchUnit rule**: a public `@Transactional` method may not be final. It found exactly the two
  methods behind the Updates 500 and nothing else. (#103)
- **`verifyBuildInfoVersion`** Gradle check, wired into `check`. `BuildInfo` duplicates the version
  for commonMain and had drifted — Gradle said 0.1.0 while the settings screen told users 1.0.0.

### Changed
- **`BuildInfo.DEBUG` is no longer a hardcoded `true`.** It now resolves from the platform build
  type (`BuildConfig.DEBUG` on Android, `Platform.isDebugBinary` on iOS). Release builds were
  logging every request method, URL and status.
- Version is `0.2.0` / code `2`, consistent across Gradle and `BuildInfo` for the first time.

### Tests
- `ApiClientBaseUrlTest` pins the API host against the Traefik router rule.
- `AuthRepositoryTest` no longer passes for the wrong reasons: its mock engine was built and then
  discarded, so every case ran against the real production server, and the failure case asserted a
  connection error that only held while the host was unroutable.
- `AuthServiceVerifierTest` covers the verifier branch, including refusing to start with no delivery
  path configured or with two.

### Known issues
- End-to-end encryption remains **off** (NoOp/plaintext under TLS). The UI states this honestly.
- CI cannot run: the GitHub account is billing-locked and this repo has no self-hosted runner (#108).
- Media and API hosts are temporary nip.io/subdomain names pending a real domain.
- The camera badge on the profile avatar swallows the tap that opens the picker (#109).

## [Unreleased]

> **2026-06-07:** all session PRs below are **merged to `main`** — #49 (Android build unblock), #57
> (scheduled-send), #58 (communities add-group), #59 (mute), #60 (Ktor test fix), #55 (backend IDOR
> guards + JWT boot guard), #61 (honest E2E UI + OTP fallback + no auth-header logging), #54 (docs).
> Build green via #49; **E2E stays DISABLED (NoOp/plaintext)** — UI now honest (no false padlock).

### Security & Correctness — Mobile Polish (Jun 7, 2026)
- **[CRITICAL-trust] Honest E2E UI**: the profile padlock (`UserProfileScreen`) and the privacy-dashboard E2E card (`PrivacyDashboardScreen`) unconditionally claimed "end-to-end encrypted" while E2E is OFF in production (plaintext under TLS). Both are now gated on `E2EConfig.ENABLED`: when false, the padlock is replaced with an info icon and an honest message — TR "Aktarım sırasında şifreli (TLS) — uçtan uca şifreleme yakında" / EN "Transport-encrypted (TLS) — end-to-end encryption coming soon" (and the dashboard's `privacy_transport_info`). New TR+EN strings `profile_transport_encrypted` and `privacy_transport_info`. No crypto semantics changed; libsignal stays untouched.
- **[MED] OTP fallback locale bug**: `PhoneInputScreen.shouldFallbackToBackendOtp` substring-matched localized Firebase message text, false-negating on Turkish-locale devices. Now a structured `PhoneAuthErrorCode` (`RATE_LIMITED` / `CONFIGURATION` / `INVALID_PHONE` / `UNKNOWN`) is carried on `PhoneVerificationResult.Error`, mapped on Android from `FirebaseAuthException.errorCode` (locale-invariant), and the fallback branches on the code. Substring matching survives only as a last resort on the generic catch path, using Kotlin's locale-invariant `String.lowercase()`. (`platform/FirebasePhoneAuth.kt`, `FirebasePhoneAuth.android.kt`, `ui/auth/PhoneInputScreen.kt`)
- **[MED] Ktor logged the Authorization header**: `ApiClient` used `Logging { level = LogLevel.HEADERS }`, leaking the bearer token to logs. Now `LogLevel.INFO` (method + URL + status, no headers) in debug and `LogLevel.NONE` in release, gated on the new `BuildInfo.DEBUG` flag — headers are never logged in any build.

### Security — Backend IDOR + JWT Guard + Config Hygiene (Jun 7, 2026)
- **[HIGH] `getMessageInfo` IDOR closed**: the endpoint looked up a message by id with no membership check, leaking content, senderId and the full recipient list to anyone who knew (or guessed) a messageId. The lookup now lives behind `GetMessageHistoryUseCase.getMessageInfo(messageId, requesterId)` which authorizes *first* (`conversationRepository.findMember` → `MSG_NOT_MEMBER`/403) before returning anything. The controller no longer touches `MessageRepository`. (`MessageController.kt`, `GetMessageHistoryUseCase.kt`, `MessageService.kt`)
- **[HIGH] `markViewOnceViewed` IDOR closed**: a non-member who knew a messageId could burn a view-once message. Added a conversation-membership guard before any mutation. (`MessageService.kt`)
- **[HIGH] JWT dev-secret fail-closed startup guard**: `JwtProvider.validateSecret()` (`@PostConstruct`, all profiles) aborts boot when `JWT_SECRET` is still the world-known `application.yml` dev default or is shorter than 32 bytes (HS256 minimum). Prevents shipping a forgeable token signer. (`JwtProvider.kt`)
- **[LOW] Config hygiene**: `docker-compose.prod.yml` now reads `MINIO_ACCESS_KEY` from the env (`${MINIO_ACCESS_KEY:-minioadmin}`) instead of hardcoding `minioadmin`, matching `infra/docker-compose.prod.yml`; base `application.yml` log level for `com.muhabbet` defaulted to `INFO` (dev profile keeps `DEBUG`).
- **Tests**: IDOR guards covered at the service layer (`MessagingServiceTest` — member allowed / non-member `MSG_NOT_MEMBER` / not-found, plus burn-blocked for view-once) and end-to-end through the real Spring Security chain (`MessageIdorIntegrationTest`, Testcontainers); startup guard covered by `JwtProviderSecretGuardTest`.

## 2026-06-07

### Fixed — Android Debug Build Unblock (PR #49, branch `claude/fix-firebase-bom-ktx`)
- **Firebase BoM dropped the `-ktx` artifacts**: switched `com.google.firebase:firebase-auth-ktx` → `firebase-auth` and `firebase-messaging-ktx` → `firebase-messaging` (Firebase BoM `34.11.0`). Updated the corresponding imports in `FirebasePhoneAuth.android.kt` (`com.google.firebase.auth.ktx.auth` → `com.google.firebase.auth.auth`, `com.google.firebase.ktx.Firebase` → `com.google.firebase.Firebase`).
- **`compileSdk` bump**: `35` → `36` in `mobile/composeApp/build.gradle.kts`.
- **libsignal E2E temporarily DISABLED** (NoOp placeholder, **NOT secure**): the Android Signal Protocol code does not compile against the pinned `libsignal-android:0.86.5` (see `CLAUDE.md` → "libsignal upgrade (BLOCKED)"). The 4 Signal files (`SignalKeyManager.kt`, `SignalEncryption.kt`, `InMemorySignalProtocolStore.kt`, `PersistentSignalProtocolStore.kt`) were renamed to `*.kt.disabled`, and `PlatformModule.android.kt` now wires `NoOpKeyManager()` + `NoOpEncryption()` — the same NoOp path iOS already uses. This is byte-identical to current prod behavior because E2E is flag-OFF by default (`E2EConfig.ENABLED = false`); **messages are NOT encrypted** and E2E must stay OFF until the libsignal rewrite lands. Pending real libsignal API re-integration.

### Fixed — Splash "green circle" (PR #49)
- **Stray green circle bleeding into the login screen**: `splash_background.xml` / `splash_background_dark.xml` previously drew a centered green oval (`#1B5E20`) in a `layer-list`. Because the Compose content is transparent and never paints an opaque background, the oval bled through the first composable and rendered as a stray green circle over the login screen. Flattened to a plain solid window background (flat color only).

### Fixed — Login backend-OTP fallback (PR #49)
- **Firebase phone-auth degrades gracefully to backend OTP**: `PhoneInputScreen.kt` now routes Firebase phone-auth failures through a new `shouldFallbackToBackendOtp()` helper that covers both transient throttling (rate limiting) **and** Firebase configuration/internal errors (e.g. "API key not valid", "internal error", "configuration"). A misconfigured Firebase build (API-key restriction) now falls back to the backend OTP flow instead of dead-ending on a raw error. Firebase remains the primary path; user-facing errors now resolve to the localized `phone_auth_failed` string (TR+EN added).

### Added — Scheduled-message send UI (branch `claude/feat-scheduled-send-ui`)
- **Schedule outgoing text messages from chat**: long-press the send button opens a two-step Material3 date + time picker; the chosen epoch-millis is attached as `scheduledAt` on the existing `WsMessage.SendMessage` frame (the field and the backend `ScheduledMessageJob` already existed). A normal tap still sends immediately. A "Scheduled" chip above the input bar surfaces session-pending scheduled messages with per-item cancel (reuses `GroupRepository.deleteMessage`). New `ScheduledSend.kt`; updates to `ChatScreen.kt`, `MessageInputPane.kt`, `DateTimeFormatter.kt`; 12 `schedule_*` strings (TR+EN). Android-verified.

### Added — Communities: real group-picker sheet (branch `claude/feat-communities-add-group`)
- **Add groups to a community via a real picker**: replaced the "coming soon" stub in `CommunityDetailScreen` with an `AddGroupToCommunitySheet` `ModalBottomSheet` that lists the user's `GROUP` conversations (excluding ones already in the community) and calls the existing `CommunityRepository.addGroupToCommunity()` endpoint, then refreshes. Dropped unused `coming_soon`/`ok` strings; added subtitle/empty/added/failed strings (TR+EN).

### Changed — Mute-duration picker cleanup (branch `claude/feat-mute-duration-ui`)
- **Honest mute-duration rows (8h / 1w / Always)**: `MutePickerDialog` previously rendered a permanently-unselected `RadioButton` with a duplicated tap handler. Replaced with plain clickable rows carrying a leading `NotificationsOff` icon, `MuhabbetSpacing` tokens, `onSurfaceVariant` tint, and a 48dp (`MuhabbetSizes.MinTouchTarget`) minimum height. No behaviour change — still calls `onMuteDuration(key)`.

### Fixed — Ktor 3.x mobile test compile (branch `claude/fix-tests`)
- **`HttpResponseData` package move**: `AuthRepositoryTest.createApiClientWithMock` referenced `io.ktor.client.engine.mock.HttpResponseData`, but in Ktor 3.x that type lives in `io.ktor.client.request.HttpResponseData` (the `mock` package only holds `MockRequestHandleScope`). The stale FQN broke `:mobile:composeApp:testDebugUnitTest` with "Unresolved reference HttpResponseData". One-line, test-only fix.

### Fixed — Production Deployment (Feb 17, 2026)
- **Sentry auto-configuration crash**: Excluded `SentryAutoConfiguration` from Spring Boot 4.x — Sentry 8.26.0 references removed `RestClientAutoConfiguration`
- **MessageBroadcaster bean ambiguity**: Added `@Primary` to `RedisMessageBroadcaster` — Spring Boot 4.x stricter bean resolution rejected two `MessageBroadcaster` implementations
- **Jackson 3.x config**: Removed deprecated `write-dates-as-timestamps: false` from `application.yml` — Jackson 3.x (Spring Boot 4.x) defaults to ISO-8601
- **Flyway V14/V15 migrations**: Applied performance indexes and moderation/analytics/backup/bot tables to production DB

### Changed — Infrastructure (Feb 17, 2026)
- **GCP VM upgrade**: `e2-medium` (4GB) → `e2-standard-2` (8GB) to prevent OOM kills during Docker builds
- **Static IP**: Promoted ephemeral IP to static for DNS stability
- **Docker runtime**: Java 21 (eclipse-temurin:21-jre-jammy) — matches build toolchain

### Fixed — Compilation & Dependency Upgrades (Feb 15, 2026)
- **Backend compilation fixes for Spring Boot 4.0.2**: Moved scheduled jobs from `domain/service/` to `adapter/in/scheduler/` (hexagonal compliance); added missing repository port methods; fixed Spring Boot 4.x API changes; updated all test mocks
- **Mobile compilation fixes for Kotlin 2.3.10 + kotlinx-datetime 0.7.1**: Migrated `kotlinx.datetime.Clock` to `kotlin.time.Clock` (stdlib move) across 7 files; fixed Compose 1.10 animation imports; fixed Koin 4.1.1 API; fixed Compose Resources package
- **Signal Protocol store fixes for libsignal-android 0.64.1**: Added Kyber pre-key methods; fixed JVM declaration clashes; fixed nullable `SenderKeyRecord`
- **WsClient API fixes**: Fixed `SendMessage` constructor, `NewMessage.messageId` property, `CallEngine` Koin Context resolution
- **Docker**: Updated to `eclipse-temurin:25-jdk-noble` / `25-jre-noble` (Java 25)
- **Results**: 332/333 backend tests pass, mobile APK builds and installs successfully

### Added — Monitoring & Load Testing (Feb 15, 2026)
- **Prometheus + Grafana monitoring stack**: `infra/monitoring/` with pre-configured dashboards targeting Spring Boot Actuator metrics
- **k6 load test scripts**: `infra/load-tests/http-endpoints.js` (REST) and `websocket-load.js` (WS)

### Added — Production Hardening (Feb 2026)
- **SQLDelight offline caching**: `MuhabbetDatabase.sq` with `CachedConversation`, `CachedMessage`, `PendingMessage` tables; cache-first repository pattern in `ConversationRepository` and `MessageRepository`; platform drivers (Android `AndroidSqliteDriver`, iOS `NativeSqliteDriver`)
- **WebSocket connection resilience**: `ConnectionState` StateFlow, exponential backoff with ±25% jitter, offline message queue via `PendingMessage` table, drain-on-reconnect, deduplication via LinkedHashSet (500 entries max), `sendOrQueue()` method
- **KVKK Privacy Dashboard**: `PrivacyDashboardScreen` with 4 sections (Visibility, Security, My Data, KVKK Rights), data export/account deletion via existing backend endpoints, 32 new localized strings (TR+EN)
- **Media compression pipeline**: `MediaUploadHelper` centralizing all media uploads with guaranteed compression (images 1280px/80%, profiles 512px/75%, thumbnails 320px/60%); replaced direct `MediaRepository` + manual `compressImage` calls in ChatScreen, SettingsScreen, ConversationListScreen
- **Persistent E2E key storage**: Android `PersistentSignalProtocolStore` with `EncryptedSharedPreferences` (identity keys, sessions, pre-keys, signed pre-keys, sender keys); iOS `KeychainHelper` for secure token storage; `SignalKeyManager` now accepts store via constructor injection
- **Background message sync**: Backend `GET /api/v1/messages/since?timestamp=` endpoint with JPQL join query; Android `WorkManager` 15-min periodic sync; iOS `BackgroundSyncManager` with `performSync()` for BGTask; `TokenStorage.lastSyncTimestamp` for state tracking
- **Camera picker**: `CameraPicker` (expect/actual) — Android `TakePicture` contract with `FileProvider`, iOS `UIImagePickerController` with camera source; CAMERA permission + FileProvider in AndroidManifest
- **iOS audio recorder fix**: Returns proper `RecordedAudio(bytes, mimeType, durationSeconds)` matching common data class (was returning incompatible `filePath`/`durationMs`)
- **Voice message transcription**: `SpeechTranscriber` (expect/actual) — Android `SpeechRecognizer`, iOS `SFSpeechRecognizer`; Turkish tr-TR primary; "Transcribe" button on VoiceBubble with loading indicator and inline transcript display; 3 new localized strings (TR+EN)

### Added — Mobile UI Audit + 87 Issue Fixes (Feb 2026)
- **Lead Mobile Engineer audit**: Comprehensive review of 60+ UI files (~8,600 LOC) across 14 navigation destinations, identifying 87 issues in 6 severity categories
- **5 critical bug fixes**: Dead `|| true` condition in status divider, hardcoded `Color.Black/White` in StatusViewer, infinite `while(true)` timer loop in ActiveCallScreen, stringly-typed filter state in ConversationList, hardcoded version "0.1.0" in Settings
- **6 ship-blocking features**: Copy message to clipboard, group chat sender names, emoji button in input bar, block/report dialogs with confirmation, privacy settings section (read receipts), channels filter chip
- **Design system expansion**: 7 new semantic colors (statusDelivered, statusSending, bubbleOwn/Other, linkColor), avatar size tokens (XSmall→Call), duration tokens (TypingTimeout, StatusDisplay, CallTimer, Shimmer), gesture tokens (SwipeReplyThreshold/Max), bubble dimension tokens
- **62 new localized strings** (Turkish + English): copy, block/report, privacy, notifications, account, camera, encryption, delivery status a11y, channels filter, formatting hint
- **15+ `!!` assertion removals**: Replaced with safe null handling (`?.`, `?:`, `let`, `return@`)
- **4 WCAG touch target fixes**: Reply cancel button, edit cancel button (36dp→48dp MinTouchTarget)
- **Files changed**: 15 files, 784 insertions, 173 deletions
- **New utilities**: `TextUtils.kt` (firstGrapheme, parseFormattedText), `BuildInfo.kt` (centralized version)

### Implemented — UI/UX Remediation (Feb 2026)
- **Design system tokens**: `MuhabbetSemanticColors` (statusOnline, statusRead, callDecline, callAccept, callMissed), `MuhabbetSpacing` (XSmall→XXLarge), `MuhabbetSizes` (touch targets, icons) — all via `CompositionLocalProvider`
- **Accessibility (P0 fixes)**: 28+ contentDescription fixes across MessageInputPane, ChatScreen, CallScreens, ConversationList, GroupInfo, SharedMedia, Settings, StarredMessages, NewConversation; 12 new localized string resources (TR+EN)
- **Touch targets**: VoiceBubble play button 36→48dp, ReactionBar emoji buttons 36→48dp
- **IME actions**: Phone input (Done), OTP input (Done), search fields (Search), message input (Send), GIF search (Search)
- **Hardcoded colors eliminated**: 8 `Color(0xFF...)` replaced with `LocalSemanticColors.current.*` in IncomingCallScreen, ActiveCallScreen, CallHistoryScreen, ConversationListScreen, UserProfileScreen, MessageInfoScreen
- **Skeleton loading**: ConversationListScreen shimmer placeholders replacing spinner
- **Edit mode banner**: Visually distinct `tertiaryContainer` background with larger icons and labels
- **TestTags**: `message_input`, `send_button`, `phone_input`, `phone_continue`, `otp_input`, `otp_verify`, `new_chat_fab`, `search_input`
- **KeyboardOptions**: Added to all text input fields across the app

### Added — Lead UI/UX Engineer Analysis (Feb 2026)
- **Comprehensive UI audit**: 34 files / 8,407 lines reviewed across 14 navigation destinations
- **Accessibility audit**: 28+ missing contentDescription violations, touch target sizing analysis, semantic annotation gaps
- **Design system assessment**: Hardcoded color inventory (8 violations), typography inconsistency catalog, spacing token recommendations
- **Interaction design review**: Strengths (swipe-to-reply, pinch-to-zoom, pull-to-refresh) and gaps (no skeleton loaders, search state reset, filter chip logic)
- **Localization verification**: 238/238 strings fully translated TR/EN, 2 minor hardcoded string violations
- **Performance analysis**: LazyColumn pagination patterns, image loading concerns, animation inventory
- **Testability audit**: Zero testTags, zero semantic annotations — critical gap for UI testing
- **6-phase remediation roadmap**: P0 accessibility (1d) → P1 design system (1d) → P1 interaction polish (1d) → P2 components (1d) → P2 testability (1.5d) → P3 tokens (1d)
- **Document**: `docs/qa/09-ui-ux-engineer-analysis.md` — 9th QA document in ISO/IEC 25010 series

### Added — WebRTC Voice Calls via LiveKit (Feb 2026)
- **CallRoomInfo WsMessage**: New `call.room` WS message type carrying `serverUrl`, `token`, `roomName` for LiveKit room connection
- **Backend room management**: `ChatWebSocketHandler` now creates LiveKit room + generates participant tokens on `CallAnswer(accepted=true)`, closes room on `CallEnd`
- **CallEngine expect/actual**: Platform abstraction for WebRTC — `connect(serverUrl, token)`, `disconnect()`, `setMuted()`, `setSpeaker()`
- **Android CallEngine**: `io.livekit:livekit-android:2.5.0` — connects to LiveKit room, manages audio tracks, handles mute/speaker
- **iOS CallEngine**: Stub (awaits LiveKit Swift SDK bridge)
- **ActiveCallScreen**: Wired to `CallEngine` — auto-connects on `CallRoomInfo`, disconnects on `CallEnd`/dispose, mute/speaker controls delegate to engine

### Added — Signal Protocol E2E Encryption (Feb 2026)
- **SignalKeyManager**: Implements `E2EKeyManager` using `org.signal:libsignal-android:0.64.1` — Curve25519 identity keys, signed pre-keys, one-time pre-keys, X3DH session initialization, Double Ratchet encrypt/decrypt
- **InMemorySignalProtocolStore**: Full `SignalProtocolStore` + `SenderKeyStore` implementation — identity, pre-key, signed pre-key, session, and sender key stores (in-memory for MVP)
- **SignalEncryption**: Implements `EncryptionPort` — delegates to `SignalKeyManager` with plaintext fallback when no session exists
- **E2ESetupService**: Post-login key registration — generates identity key pair, signed pre-key, 100 OTPKs, registers with backend via `EncryptionRepository`
- **Platform DI split**: Android provides `SignalKeyManager` + `SignalEncryption`, iOS provides `NoOpKeyManager` + `NoOpEncryption` (moved from `AppModule` to `PlatformModule`)
- **App.kt integration**: E2E key registration runs on app startup for logged-in users

### Added — QA Engineering & Tooling (Feb 2026)
- **JaCoCo code coverage**: Added to `backend/build.gradle.kts` with HTML/XML reports, coverage verification (30% min project, 60% min for domain services)
- **detekt static analysis**: Kotlin linter with project-specific rules (`backend/detekt.yml`), SARIF/HTML reports
- **ArchUnit architecture tests**: 13 tests in `HexagonalArchitectureTest` — domain independence, module boundaries, naming conventions, no Spring in domain
- **TestData factory**: Shared test data factory object (`com.muhabbet.shared.TestData`) with builders for User, Message, Conversation, Member, DeliveryStatus
- **Controller tests** (18 test files, 100+ tests): MessageController, ModerationController, UserDataController, ConversationController, GroupController, StatusController, ChannelController, PollController, EncryptionController, BackupController, BotController, ReactionController, DeviceController, ContactController, CallHistoryController, DisappearingMessageController, StarredMessageController, LinkPreviewController
- **CI pipeline**: JaCoCo coverage report + verification, detekt static analysis, artifact uploads (test results, coverage, detekt), PR coverage comments via jacoco-report action
- **k6 performance scripts**: `infra/k6/auth-load-test.js` (OTP flow), `infra/k6/api-load-test.js` (REST endpoints), `infra/k6/websocket-load-test.js` (WS connections) with P50/P95/P99 thresholds
- **QA documentation**: 8 ISO/IEC 25010 quality attribute documents in `docs/qa/` — updated with verified codebase metrics

### Added — Phase 6: Growth Features (Feb 2026)
- **Channel analytics**: `ChannelAnalyticsService` with daily stats (messages, views, reactions, shares), subscriber tracking, REST API at `GET /api/v1/channels/{channelId}/analytics` with date-range queries
- **Bot platform**: `Bot` domain model with `BotPermission` enum, `BotService` with secure API token generation (`mhb_` prefix + Base64), webhook support, permissions system, REST API at `/api/v1/bots` (CRUD, token regeneration, webhook management)
- **Bot JPA persistence**: `BotJpaEntity`, `BotPersistenceAdapter`, `SpringDataBotRepository` — full hexagonal chain
- **Channel analytics persistence**: `ChannelAnalyticsJpaEntity`, `ChannelAnalyticsPersistenceAdapter`, `SpringDataChannelAnalyticsRepository`

### Added — Phase 5: Horizontal Scaling (Feb 2026)
- **Redis Pub/Sub message broadcaster**: `RedisMessageBroadcaster` replaces in-memory broadcaster — publishes WS messages to `ws:broadcast:{userId}` Redis channels for cross-instance routing
- **Redis broadcast listener**: `RedisBroadcastListener` subscribes to `ws:broadcast:*` pattern, delivers messages to local WebSocket sessions, enabling multi-instance deployment

### Added — Phase 4: Message Backup (Feb 2026)
- **BackupService**: Implements `ManageBackupUseCase` — initiate backup, check status, list backups, delete backup
- **BackupController**: REST API at `/api/v1/backups` — `POST` (initiate), `GET` (list), `GET /{id}` (status), `DELETE /{id}`
- **Backup persistence**: `MessageBackupJpaEntity`, `BackupPersistenceAdapter`, `SpringDataBackupRepository`
- **BackupRepository out-port**: `MessageBackup` data class with status tracking (PENDING, IN_PROGRESS, COMPLETED, FAILED)

### Added — Phase 3: LiveKit Integration (Feb 2026)
- **CallRoomProvider out-port**: Interface for call room creation/token generation/room termination
- **LiveKitRoomAdapter**: LiveKit server SDK integration with `@ConditionalOnProperty(muhabbet.livekit.enabled)` — creates rooms, generates participant tokens, terminates rooms
- **NoOpCallRoomProvider**: Fallback when LiveKit is disabled — returns stub tokens and room IDs
- **Outgoing call initiation**: `MainComponent` now generates callId and opens ActiveCall screen on call button press
- **LiveKit configuration**: `muhabbet.livekit.*` properties in `application.yml` (enabled, api-key, api-secret, server-url)

### Added — Phase 2: Content Moderation (Feb 2026)
- **Moderation module**: Full hexagonal architecture — `UserReport` + `UserBlock` domain models, `ReportReason` enum (SPAM, HARASSMENT, INAPPROPRIATE_CONTENT, IMPERSONATION, OTHER), `ReportStatus` enum (PENDING, REVIEWED, RESOLVED, DISMISSED)
- **ModerationService**: Implements `ReportUserUseCase`, `BlockUserUseCase`, `ReviewReportsUseCase` — report users, block/unblock, admin review with status updates
- **ModerationController**: REST API at `/api/v1/moderation/reports` (CRUD), `/api/v1/moderation/blocks` (block/unblock/list), admin endpoints for report review
- **Moderation persistence**: `ReportJpaEntity`, `BlockJpaEntity`, `ModerationPersistenceAdapter`, Spring Data repositories
- **Error codes**: `REPORT_NOT_FOUND`, `BLOCK_SELF`, `BOT_NOT_FOUND`, `BOT_INACTIVE`, `BACKUP_NOT_FOUND`, `BACKUP_IN_PROGRESS` added to `ErrorCode` enum

### Added — Phase 1: Stabilization (Feb 2026)
- **WebSocket rate limiting**: `WebSocketRateLimiter` — per-connection sliding window (50 messages per 10-second window), integrated into `ChatWebSocketHandler`, auto-cleanup on disconnect
- **Deep linking**: `muhabbet://` custom scheme + `https://muhabbet.app` universal links for `/invite` and `/chat` paths in AndroidManifest.xml
- **Structured analytics**: `AnalyticsEvent` utility with SLF4J logger named "analytics" for structured event tracking with context maps

### Added — Backend Test Expansion (Feb 2026)
- **DeliveryStatusTest**: 6 tests — message delivery lifecycle (SENT → DELIVERED → READ), multi-recipient aggregation, status transitions
- **CallSignalingServiceTest**: 7 tests — call initiation, answer, end, busy detection, call history recording, concurrent call handling
- **EncryptionServiceTest**: 7 tests — key bundle registration, pre-key consumption, key bundle retrieval, one-time pre-key rotation
- **ModerationServiceTest**: 8 tests — report creation, duplicate reporting, block/unblock, self-block prevention, admin report review
- **WebSocketRateLimiterTest**: 4 tests — message allowance within limits, rate limiting enforcement, window expiry, user cleanup

### Added — V15 Database Migration (Feb 2026)
- **user_reports**: id, reporter_id, reported_user_id, reason, description, status, created_at, reviewed_at, reviewed_by
- **user_blocks**: id, blocker_id, blocked_id, created_at (unique constraint on blocker+blocked)
- **channel_analytics**: id, channel_id, date, message_count, view_count, reaction_count, share_count, new_subscribers, unsubscribes
- **channel_subscriptions**: id, channel_id, user_id, subscribed_at, notification_enabled
- **bots**: id, owner_id, user_id, name, description, api_token, webhook_url, is_active, permissions (JSONB), created_at, updated_at
- **message_backups**: id, user_id, status, file_url, file_size_bytes, message_count, created_at, completed_at, expires_at
- Indexes on all foreign keys and frequently queried columns

### Fixed — Bug Fixes (Feb 2026)
- **Push notifications not firing**: Changed `application-prod.yml` FCM default from `false` to `true` — `docker-compose.prod.yml` already sets `FCM_ENABLED=true` but the Spring Boot default was overriding it
- **Delivery ticks stuck at single**: Global DELIVERED ack already implemented in `App.kt` `WebSocketLifecycle()` — confirmed working

### Added — Backend Test Expansion (Feb 2026)
- **CallSignalingServiceTest**: 20 tests — initiateCall (free/busy users, video type), answerCall (success/nonexistent), endCall (ENDED/DECLINED/MISSED, history persistence, duration calculation, cleanup), getOtherParty, concurrent independent calls, history persistence failure handling
- **EncryptionServiceTest**: 10 tests — registerKeyBundle (save, userId override), getKeyBundle (exists/null), uploadPreKeys (save/empty/userId override), fetchPreKeyBundle (with/without one-time key, no bundle, key consumption)

### Added — Observability Stack (Feb 2026)
- **Prometheus config**: Scrape targets for backend (/actuator/prometheus), Redis, PostgreSQL, nginx exporters
- **Grafana provisioning**: Auto-configured Prometheus datasource, dashboard provider
- **Muhabbet Overview dashboard**: 8 panels — JVM heap, HTTP request rate, P95 latency, active WebSocket sessions, thread count, DB connection pool, GC pause, CPU usage, error rate
- **docker-compose.monitoring.yml**: Prometheus + Grafana containers with resource limits, persistent volumes

### Added — Load Testing Scripts (Feb 2026)
- **websocket-load.js** (k6): WebSocket connection ramp (0→100 VUs), message send/receive with DELIVERED acks, heartbeat pings, custom latency metrics, 95th percentile thresholds (<200ms WS, <500ms HTTP)
- **http-endpoints.js** (k6): Steady-state (50 VUs for 3m) + spike test (200 VUs), API endpoint coverage (health, profile, conversations, contact sync, OTP), custom latency trends per endpoint type

### Added — Security Hardening (Feb 2026)
- **Security headers**: HSTS (max-age 31536000), X-Frame-Options DENY, X-Content-Type-Options nosniff, CSP (`default-src 'self'; frame-ancestors 'none'; form-action 'self'`), XSS protection, Referrer-Policy strict-origin-when-cross-origin, Permissions-Policy (geolocation/camera/mic denied)
- **InputSanitizer**: Server-side input sanitization utility — HTML entity escaping (`&`, `<`, `>`, `"`, `'`), control character stripping (preserves `\n`, `\t`, `\r`), display name trimming/length limiting, message content length limiting, HTTPS-only URL validation (rejects `javascript:` and `data:` schemes)
- **InputSanitizer tests**: 15 unit tests covering XSS prevention, HTML entities, control chars, URL validation, null handling

### Added — Call UI Screens (Feb 2026)
- **IncomingCallScreen**: Full-screen incoming call overlay with avatar, caller name, accept (green) / decline (red) buttons, WebSocket signaling integration
- **ActiveCallScreen**: In-call UI with duration timer (coroutine-based), mute/speaker toggles, end call button, real-time CallEnd listener
- **CallHistoryScreen**: Paginated call history list with direction icons (incoming/outgoing/missed), duration display, call-back button
- **Decompose navigation**: 3 new Config entries (IncomingCall, ActiveCall, CallHistory) with navigation methods wired in MainComponent
- **CallRepository**: REST client for `GET /api/v1/calls/history` endpoint
- **21 call strings**: Turkish + English localization for all call UI elements

### Added — E2E Encryption Infrastructure (Feb 2026)
- **E2EKeyManager interface**: X3DH key lifecycle — generateIdentityKeyPair, generateSignedPreKey, generateOneTimePreKeys, initializeSession, hasSession, encryptMessage, decryptMessage
- **NoOpKeyManager**: MVP pass-through implementation (all encrypt/decrypt returns plaintext unchanged)
- **EncryptionRepository**: Mobile client for key bundle registration (`PUT /api/v1/encryption/keys`), pre-key upload (`POST /api/v1/encryption/prekeys`), bundle fetch (`GET /api/v1/encryption/bundle/{userId}`)
- **Koin DI**: Registered EncryptionPort, E2EKeyManager, EncryptionRepository in AppModule

### Added — iOS Platform Completion (Feb 2026)
- **ImagePicker.ios.kt**: PHPickerViewController with delegate retention pattern, image data loading via NSItemProvider
- **FilePicker.ios.kt**: UIDocumentPickerViewController with security-scoped resource access, MIME type detection
- **ImageCompressor.ios.kt**: CoreGraphics CGBitmapContext resize + UIImageJPEGRepresentation compression (matches Android logic)
- **CrashReporter.ios.kt**: NSLog-based crash logging with Sentry CocoaPod integration hooks
- **PushTokenProvider.ios.kt**: Token caching via NSUserDefaults, `onTokenReceived()` companion method for AppDelegate, polling-based token wait (5s timeout)
- **LocaleHelper.ios.kt**: AppleLanguages UserDefaults for locale switching with `exit(0)` restart
- **FirebasePhoneAuth.ios.kt**: `isAvailable()=false` stub for graceful backend OTP fallback

### Added — Mobile & Shared Test Infrastructure (Feb 2026)
- **Test dependencies**: kotlin-test, kotlinx-coroutines-test, ktor-client-mock, koin-test added to commonTest
- **FakeTokenStorageTest**: 5 tests — initial state, save/persist, clear, language, theme
- **AuthRepositoryTest**: Tests for isLoggedIn, logout, token persistence, error handling
- **PhoneNormalizationTest**: 14 tests covering E.164, Turkish phone formats, separators, edge cases
- **WsMessageSerializationTest**: 25+ tests covering all WsMessage types — SendMessage, AckMessage, TypingIndicator, CallInitiate/Answer/End/Incoming, NewMessage, StatusUpdate, ServerAck, PresenceUpdate, GroupMemberAdded/Removed, MessageDeleted/Edited/Reaction, Ping/Pong, Error, round-trip fidelity

### Added — CI/CD Pipeline (Feb 2026)
- **backend-ci.yml**: On push to `backend/` or `shared/` — Gradle test + bootJar with caching
- **mobile-ci.yml**: On push to `mobile/` or `shared/` — Android assembleDebug (with dummy google-services.json) + iOS framework build on macOS
- **security.yml**: Trivy filesystem + Docker image scanning, Gitleaks secret detection, CodeQL static analysis for java-kotlin (weekly + on push)
- **deploy.yml**: On merge to `main` — SSH to GCP, docker compose pull/up with health check and rollback

### Changed — Dependency Upgrades (Feb 2026)
- **Kotlin**: 2.1.10 → 2.3.10
- **Spring Boot**: 3.4.1 → 4.0.2
- **Java**: 21 → 25
- **Gradle**: 8.12 → 8.14.4
- **Ktor**: 3.0.3 → 3.1.3
- **Compose BOM**: 2024.12.01 → 2025.04.01
- **Koin**: 4.0.2 → 4.1.0-Beta1
- **Decompose**: 3.2.3 → 3.3.0
- **Coil**: 3.0.4 → 3.1.0
- **SQLDelight**: 2.0.2 → 2.1.0
- **kotlinx.serialization**: 1.7.3 → 1.8.1
- **kotlinx.datetime**: 0.6.1 → 0.7.0

### Changed — System Optimization (Feb 2026)
- **Database indexes**: 12 performance indexes — messages (conversation_id+created_at), delivery_status (message_id), conversations (updated_at), phone_hashes (hash), media_files (uploader+type), statuses (user_id+expires_at), etc.
- **N+1 query fixes**: `@BatchSize(size=50)` on ConversationJpaEntity.members and MessageJpaEntity.deliveryStatuses
- **Redis connection pooling**: Lettuce pool enabled (min-idle=2, max-active=8)
- **Ktor client connection pooling**: maxConnectionsCount=100, connectTimeout=10s, requestTimeout=30s
- **Nginx optimization**: gzip on (text, JSON, JS, CSS), static file caching (30d for images, 7d for JS/CSS), proxy buffering enabled
- **PostgreSQL tuning**: shared_buffers=256MB, effective_cache_size=1GB, work_mem=16MB, random_page_cost=1.1 (SSD)

### Added — Round 6: Media UX & Storage
- **Chat scroll fix**: Chat now starts at the bottom instantly on first load, subsequent messages animate smoothly (no more exhaustive top-to-bottom scroll)
- **Pinch-to-zoom**: MediaViewer supports pinch-to-zoom (1x–5x), double-tap to toggle zoom (1x ↔ 3x), pan while zoomed via `rememberTransformableState` + `graphicsLayer`
- **SharedMedia video/voice/doc playback**: Videos open in external player, voices play inline with play/pause toggle + AudioPlayer, documents open externally via `LocalUriHandler`
- **Forward button fix**: Forward now opens `ForwardPickerDialog` with conversation list (was incorrectly just viewing the image)
- **MessageInfo media preview**: Message info screen now shows image/video thumbnail preview in the message card (added `mediaUrl`/`thumbnailUrl` to `MessageInfoResponse` DTO)
- **MessageInfo avatars**: Recipient rows now display user avatars (added `avatarUrl` to `RecipientDeliveryInfo` DTO, populated from backend user record)
- **Storage usage stats**: `GET /api/v1/media/storage` — returns per-user storage breakdown by type (images, audio, documents) with byte counts and item counts. Mobile Settings screen shows storage section with colored breakdown rows
- **Hexagonal storage chain**: `GetStorageUsageUseCase` in-port → `MediaService` implementation → `MediaFileRepository` out-port → `MediaFilePersistenceAdapter` → Spring Data JPA queries with LIKE prefix matching
- **8 new string resources**: Storage UI strings in Turkish and English (storage_title/total/images/audio/documents/loading/error/items)

### Added — Round 5: UI/UX Polish
- **MediaViewer**: Full-screen image viewer with semi-transparent top bar (close), bottom action bar (forward, delete), tap-to-toggle UI overlay (WhatsApp-style). Replaces bare `FullImageViewer` dialog
- **SharedMediaScreen enhancements**: Tap grid item opens full-screen MediaViewer, long-press opens context menu (forward, delete own), Crossfade tab transitions, AnimatedVisibility for loading states
- **MessageInfoScreen polish**: Message preview in Card with elevation and rounded corners, content type icon for media, separate "Read By" (blue) / "Delivered To" (grey) / "Waiting" sections with colored dots and headers, UserAvatar in recipient rows, empty state with schedule icon
- **New string resources**: 8 new strings in Turkish and English (media_viewer_forward/share/delete/info, message_info_read_by/delivered_to/waiting/not_sent)

### Fixed — Round 4 Bug Fixes
- **Shared media JPQL query**: Changed inline enum references to `@Param` list approach — JPQL `IN` clause with fully-qualified enum constants may not resolve in Hibernate 6
- **Message info endpoint**: Added defensive error handling, filtered sender from recipients list
- **Status text position**: Text content now appears at bottom of status viewer (was centered)
- **Starred message scroll**: Clicking a starred message now navigates to chat AND scrolls to the specific message (added `scrollToMessageId` param to ChatScreen + Config.Chat)
- **Starred back navigation**: Removed `goBack()` before `openChat()` — back button now correctly returns to StarredMessages instead of skipping to Settings

### Added — Round 3 Bug Fixes & Features
- **Delivery status resolution (critical)**: Backend now batch-queries `message_delivery_status` table and resolves per-message status — sender sees aggregate min across recipients, recipient sees their own status row. Fixed `MessageMapper.toSharedMessage()` hardcoding `status = SENT`
- **Shared media screen**: `GET /api/v1/conversations/{id}/media` endpoint + `SharedMediaScreen` with grid (images/videos) and list (documents) tabs. Accessible from GroupInfoScreen and UserProfileScreen
- **Message info screen**: `GET /api/v1/messages/{id}/info` endpoint + `MessageInfoScreen` with sent time, per-recipient delivery status with icons (single tick, double grey, double blue)
- **Starred messages redesign**: Replaced chat bubble layout with list items showing sender label, content preview with type icons, timestamp. Click navigates to conversation
- **Profile contact name**: Shows `~contactName` below display name when different
- **Status image upload**: "Add Photo" button in status dialog, uploads via MediaRepository
- **Forwarded message visual improvements**: Forward icon, 12sp italic, alpha 0.8
- **Video thumbnails**: Play overlay on video messages in chat
- **Call button snackbar**: Shows "Coming soon" instead of empty click handler
- **New string resources**: 16 new strings in Turkish and English for all new features

### Added — Phase 5: iOS Platform Foundation
- **AudioPlayer.ios.kt**: Real AVAudioPlayer implementation with play/pause/stop/seekTo, progress tracking via coroutine
- **AudioRecorder.ios.kt**: AVAudioRecorder implementation with M4A output, AVAudioSession permission checking
- **ContactsProvider.ios.kt**: CNContactStore implementation with `enumerateContactsWithFetchRequest`, permission request
- **PushTokenProvider.ios.kt**: UNUserNotificationCenter permission request, `registerForRemoteNotifications()`, cached token pattern

### Added — Phase 4: E2E Encryption Architecture
- **Encryption key exchange**: `POST /api/v1/encryption/keys` (upload key bundle), `GET /api/v1/encryption/keys/{userId}` (fetch pre-key bundle)
- **Domain models**: `EncryptionKeyBundle`, `OneTimePreKey` — ready for Signal Protocol (X3DH, Double Ratchet)
- **EncryptionService**: Implements `ManageEncryptionUseCase` — key bundle CRUD, one-time pre-key consumption
- **Persistence**: `EncryptionKeyJpaEntity`, `OneTimePreKeyJpaEntity`, Spring Data repos, persistence adapter
- **Migration**: `V11__add_encryption_keys.sql` — `encryption_keys` + `one_time_pre_keys` tables

### Added — Phase 4: KVKK Compliance
- **Data export**: `GET /api/v1/users/data/export` — returns all user data (profile, messages, media, conversations)
- **Account deletion**: `DELETE /api/v1/users/data/account` — soft-deletes account with `deleted_at` timestamp
- **UserDataService**: Implements `ManageUserDataUseCase` — aggregates data from multiple repositories
- **UserDataQueryPort**: Out-port interface for cross-module data aggregation

### Added — Phase 3: Call Signaling Infrastructure
- **WebSocket call messages**: `CallInitiate`, `CallAnswer`, `CallIceCandidate`, `CallEnd` in WsMessage sealed class
- **CallSignalingService**: In-memory call state management, routes signaling messages between participants
- **Call history**: `GET /api/v1/calls/history` — persisted call records (caller, callee, status, duration)
- **CallHistoryService**: Implements `GetCallHistoryUseCase`
- **Migration**: `V12__add_call_history.sql` — `call_history` table
- **ChatWebSocketHandler**: Extended to handle call signaling frame types

### Added — Phase 3: Notification Improvements
- **Notification grouping**: Messages from same conversation grouped under one notification
- **Inline reply**: Reply directly from notification without opening app (Android `NotificationReplyReceiver`)
- **Notification channels**: Separate channels for messages, calls, system notifications

### Added — Phase 3: Crash Reporting (Sentry)
- **CrashReporter expect/actual**: Common interface, Android Sentry implementation, iOS stub
- **Sentry Android SDK**: `io.sentry:sentry-android:7.14.0`, auto-init via AndroidManifest meta-data
- **CrashReporter.init()**: Called in App.kt on launch, sets user ID from token storage
- **SENTRY_DSN**: Configurable via environment variable → manifest placeholder

### Changed — Phase 2: Architecture Refactoring
- **ChatScreen.kt refactored**: 1,771 → 405 lines — extracted `MessageBubble.kt`, `MessageInputPane.kt`, `ChatDialogs.kt`
- **MessagingService split**: Deleted monolithic `MessagingService`, replaced with `ConversationService` + `MessageService` + `GroupService`
- **5 controllers refactored to use case pattern**: `StatusController`, `ChannelController`, `PollController`, `ReactionController`, `DisappearingMessageController` — all now depend on use case interfaces instead of Spring Data repositories
- **AppConfig expanded**: Wires 11+ services as `@Bean` (ConversationService, MessageService, GroupService, StatusService, ChannelService, PollService, ReactionService, DisappearingMessageService, EncryptionService, CallHistoryService, UserDataService)
- **New use case interfaces**: `ManageStatusUseCase`, `ManageChannelUseCase`, `ManagePollUseCase`, `ManageReactionUseCase`, `ManageDisappearingMessageUseCase`, `ManageEncryptionUseCase`, `GetCallHistoryUseCase`, `ManageUserDataUseCase`
- **New out-port interfaces**: `StatusRepository`, `ReactionRepository`, `PollVoteRepository`, `EncryptionKeyRepository`, `CallHistoryRepository`, `UserDataQueryPort`

### Added — Phase 2: Stickers & GIFs
- **GiphyClient**: GIPHY API client — search/trending for GIFs and stickers, public beta key
- **GifStickerPicker**: Modal bottom sheet with tab row (GIF | Stickers), debounced search, 3-column grid, GIPHY attribution
- **ContentType expansion**: Added `STICKER` and `GIF` to shared + backend ContentType enums
- **MessageBubble**: GIF renders as full-width async image (max 200dp), sticker renders at 150dp without bubble background
- **MessageInputPane**: GIF menu item in attachment dropdown

### Added — Phase 2: Backend Tests (201 backend, 251 total)
- **MediaServiceTest**: Upload, download URL generation, thumbnail, validation, error cases
- **ConversationServiceTest**: Create DM (dedup), create group, list conversations, pagination
- **GroupServiceTest**: Add/remove members, role management, leave group, owner transfer
- **ChatWebSocketHandlerTest**: Message send/ack, typing indicators, invalid frames, auth
- **RateLimitFilterTest**: Rate limiting on auth endpoints, sliding window, IP-based

### Added — Media Sharing (Week 5)
- **Backend media module**: Hexagonal architecture — `MediaService`, `MinioMediaStorageAdapter`, `JavaImageThumbnailAdapter`, `MediaController`
- **Image upload**: `POST /api/v1/media/upload` (multipart) — validates type/size, generates thumbnail (320x320), uploads to MinIO, returns pre-signed URLs
- **Pre-signed URL refresh**: `GET /api/v1/media/{mediaId}/url` — returns fresh URLs when old ones expire
- **Nginx MinIO proxy**: `/muhabbet-media/` proxied to MinIO with `Host minio:9000` for pre-signed URL signature validation
- **Mobile image picker**: Platform `expect/actual` — Android uses `PickVisualMedia`, iOS stubbed
- **Image compression**: Client-side JPEG compression (max 1280px, quality 80) before upload
- **Image bubbles**: AsyncImage (Coil 3) with thumbnail in chat, tap for full-size dialog viewer
- **Optimistic UI**: Shows local image immediately while uploading

### Added — Push Notifications (Week 5)
- **FCM integration**: `FcmPushNotificationAdapter` sends push to offline recipients on new message
- **Push token registration**: Mobile registers FCM token on app start via `PUT /api/v1/devices/push-token`
- **Android FCM service**: `MuhabbetFirebaseMessagingService` handles `onNewToken` and `onMessageReceived`

### Added — Presence Tracking (Week 5)
- **Redis presence**: `RedisPresenceAdapter` — `presence:{userId}` keys with 60s TTL, refreshed by heartbeat/ping
- **Online/offline broadcast**: `PresenceUpdate` WS messages sent to contacts on connect/disconnect
- **Last seen persistence**: `users.last_seen_at` column (V2 migration), updated on WS disconnect
- **Mobile online indicator**: Green dot on conversation list avatars for online users
- **Chat header subtitle**: "yazıyor..." (typing) > "çevrimiçi" (online) > "son görülme HH:mm" (last seen)
- **30s heartbeat**: WsClient sends `Ping` every 30s, backend refreshes Redis TTL on Ping/GoOnline

### Fixed — Week 5
- **Koin crash on Activity recreation**: Replaced `KoinApplication` with `GlobalContext` guard + `KoinContext`
- **WS send crash**: Wrapped all unguarded `wsClient.send()` calls in try-catch (typing indicators, READ acks)
- **Empty image bubbles**: Pre-signed URLs used internal `minio:9000` endpoint. Fixed with URL rewrite + nginx proxy
- **Contact sync not matching**: Phone numbers not normalized to E.164 before hashing. Added `normalizeToE164()` for Turkish formats (05XX, 5XX, 90XX)

### Added — Mobile App (Weeks 3-4)
- **Auth flow**: Phone input → OTP verify → auto-login with platform detection (expect/actual)
- **Conversation list**: Real-time WS updates, pull-to-refresh, unread badges, participant name resolution
- **Chat screen**: Real-time messaging via WebSocket, message status ticks (clock→single→double→blue), message pagination (cursor-based scroll-to-top), typing indicator send/receive
- **Settings screen**: Profile edit (displayName + about), app version, logout with confirmation dialog
- **Dark mode**: Auto-detects system theme, full light/dark color schemes
- **Contacts sync**: Android permission flow, device contact reading, SHA-256 phone hash, backend sync endpoint
- **Navigation**: Decompose-based (Root → Auth/Main → Conversations/Chat/Settings/NewConversation)
- **DI**: Koin modules for platform-specific implementations (TokenStorage, ContactsProvider, PlatformInfo)
- **Test Bot**: Python script (`infra/scripts/test_bot.py`) for automated WS message testing

### Fixed — Backend (Weeks 3-4)
- **Participant data**: ConversationController now populates displayName/phoneNumber from UserRepository (was hardcoded null)
- **Unread badge**: READ ack now bulk-updates ALL unread messages via `markConversationRead()` (was single message only)
- **Typing broadcast**: Backend now forwards typing indicators to conversation members via PresenceUpdate (was TODO/log-only)

### Added — Messaging Module (Week 2)
- **Domain models**: Conversation, ConversationMember, Message, MessageDeliveryStatus with enums (ConversationType, MemberRole, ContentType, DeliveryStatus)
- **Domain events**: MessageSentEvent, MessageDeliveredEvent
- **Use case ports**: CreateConversation, GetConversations, SendMessage, GetMessageHistory, UpdateDeliveryStatus
- **Repository ports**: ConversationRepository (with direct lookup dedup), MessageRepository (cursor pagination, delivery status)
- **MessagingService**: Full business logic — direct conversation dedup via sorted UUID lookup, group creation with owner role, idempotent message send, cursor-based pagination, delivery status broadcast
- **JPA persistence**: 5 JPA entities (ConversationJpaEntity, ConversationMemberJpaEntity, DirectConversationLookupJpaEntity, MessageJpaEntity, MessageDeliveryStatusJpaEntity), 5 Spring Data repos, 2 persistence adapters
- **WebSocket real-time**: ChatWebSocketHandler (JWT auth via query param, handles SendMessage/AckMessage/TypingIndicator/Ping), WebSocketSessionManager (ConcurrentHashMap-based), WebSocketConfig (/ws endpoint)
- **WebSocketMessageBroadcaster**: Delivers messages to online users via WebSocket, falls back to DB queueing for offline users
- **REST endpoints**: `POST /api/v1/conversations`, `GET /api/v1/conversations`, `GET /api/v1/conversations/{id}/messages`
- **Unit tests**: 16 MessagingService tests (MockK) covering conversation creation, message sending, pagination, delivery status, all error codes

### Added — Auth Module (Week 1)
- **Domain models**: User aggregate, Device entity, OtpRequest value object
- **Use case ports**: RequestOtp, VerifyOtp, RefreshToken, Logout
- **Repository ports**: UserRepository, OtpRepository, DeviceRepository, RefreshTokenRepository, PhoneHashRepository
- **AuthService**: Full business logic — OTP request with cooldown, OTP verify with BCrypt, token refresh with rotation, logout
- **JPA persistence**: 5 JPA entities with domain mappers, 5 Spring Data repos, 5 persistence adapters
- **MockOtpSender**: Logs OTP to console for development (conditional on `muhabbet.otp.mock-enabled`)
- **JWT security**: JwtProvider (HS256 via JJWT), JwtAuthFilter, SecurityConfig (stateless, CSRF disabled)
- **AuthenticatedUser**: Utility to extract userId/deviceId from SecurityContext
- **REST endpoints**: `POST /api/v1/auth/otp/request`, `otp/verify`, `token/refresh`, `logout`
- **User endpoints**: `GET /api/v1/users/me`, `PATCH /api/v1/users/me`
- **Phone hash**: SHA-256 hash stored on user creation for contact sync
- **Unit tests**: 9 AuthService tests (MockK) covering happy paths and all error codes
- **Integration test**: AuthControllerIntegrationTest with Testcontainers PostgreSQL

### Added — Shared KMP Module (Week 1)
- Domain models: Message, Conversation, UserProfile, Contact
- WebSocket protocol: WsMessage sealed class with all frame types
- DTOs: Auth, User, Conversation, Media request/response types
- ValidationRules: Turkish phone, OTP, display name, message length, media size
- EncryptionPort interface + NoOpEncryption (TLS-only MVP)

### Added — Project Setup (Week 1)
- Gradle multi-project build (backend, shared, mobile)
- Gradle wrapper 8.12
- Docker Compose: PostgreSQL 16 + Redis 7 + MinIO
- Flyway migration V1: users, devices, otp_requests, refresh_tokens, conversations, messages, media_files
- Application profiles: dev (mock OTP, debug SQL), prod (real SMS, JSON logs)
- ErrorCode enum with Turkish messages
- GlobalExceptionHandler + ApiResponseBuilder

### Fixed
- Android Gradle Plugin declared in root build.gradle.kts to fix shared module build
- JVM target 21 set for shared module's jvm() target
- Added kotlinx-datetime dependency to backend for shared model compatibility
