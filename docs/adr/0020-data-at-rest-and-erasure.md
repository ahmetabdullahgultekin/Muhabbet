# ADR 0020 — Data at rest and erasure: SQLCipher from 0.5; `SessionTeardown` is crypto-erase

**Status:** Proposed — pending owner approval; no code may be written against this ADR until approval is recorded in [`docs/architecture/multi-transport/APPROVAL.md`](../architecture/multi-transport/APPROVAL.md) · **Date:** 2026-11-12 · **Source:** [decision brief §3, ADR-0020; §9 (erasure); §11.2; §13 (0.4, 0.5.0, 1.0.0)](../architecture/multi-transport/decision-brief-2026-11-12.md) · Depends on ADR-0011 (DB is the SSOT), ADR-0012, ADR-0014 (resync by seq), ADR-0028. Supersedes nothing.

## Context

- **Logout and account deletion leave the plaintext DB on the device** (MOB-01, critical, verified):
  `RootComponent.kt:61-63` `onLogout` only clears tokens; `clearConversations`, `clearMessages` and
  `clearPendingMessages` exist (`LocalCache.kt:73,184,225`) but have no callers. The next user on the
  same phone sees the previous user's conversation list, and `WsClient.drainPendingMessages` sends the
  previous user's queued messages over the new user's socket. Background sync is never cancelled
  (MISS-02, mobile verifier). The DB is a plain `AndroidSqliteDriver(…, "muhabbet.db")`
  (`DatabaseDriverFactory.android.kt:10`; iOS `NativeSqliteDriver`, `DatabaseDriverFactory.ios.kt:9`).
  `android:allowBackup="false"` mitigates backup extraction only.
- ADR-0011 makes the local DB the **single source of truth**, and proximity/offline modes (1.2+) make
  the phone the primary store of messages that may never have touched the server. A plaintext SSOT is
  not acceptable for a privacy-first product (KVKK Art. 12 security duty; Art. 7 erasure).
- Gemini's schema stored decrypted content and a plaintext FTS5 index beside the ciphertext (GEM-14).
- The schema is being replaced at 0.5 anyway (ADR-0011/0013), and the current DB is a **cache**
  (ARCHITECTURE.md §4 as it stands), so nothing authoritative lives only on the device today.

## Decision

### 1. Encrypted database

| Item | Decision |
|---|---|
| Android | `sqlcipher-android` with an SQLDelight driver factory replacing `AndroidSqliteDriver` |
| iOS | SQLCipher linked into `NativeSqliteDriver` (after KMP-01) |
| DB key | Random 256-bit key generated on first run |
| Android key protection | Wrapped by an Android Keystore key (StrongBox when present). The wrapping key **does not** require user authentication, so background wake and WorkManager can open the DB |
| iOS key protection | Stored in the Keychain with `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`, so background wake can read it and it never leaves the device |
| Port | `SecureKeyStorePort { getOrCreateWrappedKey(alias); destroy(alias) }` (brief §5); Android Keystore and in-memory now, iOS Keychain later |
| Passphrase | **None.** App Lock (#378) is a separate UI gate, not a key-derivation input |

### 2. Migration at 0.5 (one-shot, low risk)

| Step | Action |
|---|---|
| 1 | Create a **fresh** encrypted DB file with the new SSOT schema (ADR-0011, brief §11.2) |
| 2 | Copy only unsent `PendingMessage` rows into `outbox_entry` (state QUEUED) |
| 3 | Delete the plaintext `muhabbet.db` (and its `-wal`/`-shm` files) |
| 4 | **Re-sync from the server by `seq`** (`/changes`, ADR-0014) |
| 5 | Drop `PendingMessage`, `CachedMessage`, `CachedConversation` after the `engine.enabled` overlap release (ADR-0012) |

This is safe because the old DB is a cache: everything except unsent outbound rows is recoverable
from the server. Doing it at 0.5, together with the schema change, avoids a second migration at 1.0.

### 3. `SessionTeardown(reason)` — crypto-erase

`reason ∈ {LOGOUT, ACCOUNT_DELETED, REFRESH_REJECTED, DEVICE_REVOKED}`. Steps run in this order;
each is idempotent, and a failure in one does not stop later ones.

| # | Step | Notes |
|---|---|---|
| 1 | Best-effort server revoke | Queued; never blocks local erasure |
| 2 | Cancel WorkManager / BGTask jobs and reset the sync watermark | MISS-02 |
| 3 | Close all links (`ServerLink`, sealed links) and stop Nearby mode | No frame leaves after this point |
| 4 | **Destroy the wrapped DB key** (`SecureKeyStorePort.destroy`) | From here the DB file is unreadable ciphertext |
| 5 | Delete the DB, media cache, outbox, identity keys / libsignal store, relay/custody store, tokens | Belt and braces after step 4 |
| 6 | Verify | Assert: DB file absent, key alias absent, no scheduled jobs, in-memory queues empty |

```kotlin
// :mobile:messaging (use case, brief §4.3)
fun interface SessionTeardown { suspend operator fun invoke(reason: TeardownReason): TeardownReport }
```

Staging: the **0.4** MOB-01 fix ships `SessionTeardown` without crypto (wipe tables, tokens,
`WsClient` queues, WorkManager). **0.5** adds steps 4–5 on the encrypted DB. Callers: logout,
account deletion, and the refresh-token-rejected path in `ApiClient`.

### 4. Search and secondary stores

- **Local FTS lives only inside the encrypted DB**: a contentless FTS5 table maintained by delete
  triggers, introduced at 1.0 (`message_fts`, brief §11.2). No plaintext index anywhere else.
- **Relay/custody storage (1.3)** is a separate encrypted store (`custody.db`) with its own key and
  quota, wiped immediately when relay mode is left, and by `SessionTeardown`.
- Temporary decrypted media files are written only to the app's private cache and deleted on
  teardown; no plaintext media in shared storage.

## Why

- MOB-01 is a KVKK erasure failure on the device itself; crypto-erase (destroying one key) is the only
  erasure that is fast, verifiable and robust against flash wear-levelling.
- File-based encryption/Data Protection protects a powered-off device, not a logged-out user on a
  powered-on one, and not the next user of the same phone.
- Background delivery (wake-only push, ADR-0019; WorkManager) needs the key without user interaction,
  which rules out a passphrase-derived key.
- Row-level application crypto would break FTS and every SQL projection the UI observes (ADR-0011).
- The architecture graft: one schema migration at 0.5, not two.

## Consequences

- A small CPU/IO cost for SQLCipher; measured on a low-end device in the 0.5 exit.
- An **erasure test and a forensic check** are part of the 0.5 exit (RQ-08).
- iOS encryption depends on KMP-01; until then the iOS driver is unchanged and iOS makes no
  at-rest claim.
- **Media erasure in MinIO (#327) is a separate backend item and stays open**; this ADR covers the
  device only. The KVKK "erasure is complete" claim cannot be made until #327 closes (SSOT-09).
- Losing the Keystore key (e.g. OS-level key invalidation) makes the local DB unreadable; recovery is
  "fresh DB + resync from server" for CLOUD/BYO conversations. For LOCAL_ONLY (1.0+, ADR-0021) it is
  data loss, which the mode already discloses (no backup anywhere).

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Rely on FBE / iOS Data Protection alone | Does not protect against the next user or a logged-in extraction; no crypto-erase |
| Row-level application crypto | Breaks FTS and DB projections |
| Passphrase-derived DB key | Breaks background delivery; App Lock is a separate gate |
| Encrypt at 1.0 instead of 0.5 | Two schema migrations; the SSOT would be plaintext for a full release cycle |
| Plaintext FTS5 beside ciphertext (Gemini) | GEM-14 |
| Key the DB file by userId without encryption | Fixes cross-user leakage, not erasure or extraction |

## Evidence

- Findings: MOB-01, MISS-02 (mobile verifier), GEM-14, MOB-03, SSOT-09, MISS-02 (governance
  verifier: media erasure #327 still open), LEG-04.
- Code: `mobile/composeApp/src/androidMain/kotlin/com/muhabbet/app/data/local/DatabaseDriverFactory.android.kt:10`,
  `mobile/composeApp/src/iosMain/kotlin/com/muhabbet/app/data/local/DatabaseDriverFactory.ios.kt:9`,
  `mobile/composeApp/src/commonMain/kotlin/com/muhabbet/app/data/local/LocalCache.kt:73,184,225`,
  `mobile/composeApp/src/androidMain/AndroidManifest.xml:14`.
- External (from the legal digest): https://www.mevzuat.gov.tr/MevzuatMetin/1.5.6698.pdf (KVKK Art. 7, 12).

## Verification

| Req | Check | Method | When |
|---|---|---|---|
| RQ-08 | After logout, login as another number shows no prior data | Demonstration, emulator-5554, two test numbers, owner (P13) | 0.4 exit |
| RQ-08 | After teardown: DB file and key alias absent, no scheduled jobs, in-memory queues empty | commonTest against fakes + Android instrumentation where available | 0.4 (no crypto) / 0.5 |
| RQ-08 | Forensic check: no readable DB, no plaintext `-wal`/`-shm` remnant after logout | Demonstration (file pull + `strings`), owner | 0.5 exit |
| — | 0.5 migration keeps every unsent message and loses nothing recoverable | Engine test + emulator script | 0.5 exit |
| — | DB opens from a killed-app wake with no user interaction | Demonstration, owner | 0.5 exit |
| RQ-13 | FTS index exists only inside the encrypted DB | Inspection + test | 1.0 |

> **Reviewer note (open):** "contentless FTS5 with delete triggers" needs care. In SQLite, deleting
> from a plain contentless FTS5 table requires supplying the original column values (the special
> `'delete'` command), which a trigger can only do if the plaintext is still available at delete
> time; the `contentless_delete=1` option removes that constraint but needs a recent SQLite
> (believed to be 3.43 or later — to verify against the SQLite version bundled by the chosen
> SQLCipher build). The 1.0 spike should confirm the option is available, or fall back to an
> external-content FTS5 table *inside the same encrypted DB*. The decision (FTS only inside the
> encrypted DB) is unchanged.
