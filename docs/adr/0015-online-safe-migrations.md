# ADR 0015 — Schema migrations are online-safe, and a test enforces it

**Status:** Accepted · **Date:** 2026-11-13 · Tracking: #921 (programme #758), first applied by #920.

(0014 is left to the per-conversation message sequence #750 proposes.)

## Context

Flyway runs at backend start-up, inside the deploy, against the one production database. Until now
nothing distinguished a migration that is instant from one that holds a lock for as long as a table
is large:

- `V24` built `idx_messages_media_id` with a plain `CREATE INDEX`, which takes a `SHARE` lock — every
  message insert waits for the whole build.
- `V25` and `V26` rewrote `messages` with a single `UPDATE` inside the migration transaction.
- `V14` built five b-trees and a GIN index on `messages` in one transaction.
- No migration set `lock_timeout`, so a migration waiting behind a long transaction queued every
  writer behind itself as well.

At today's row counts each of these takes milliseconds, which is exactly why none of them was ever
noticed: running a migration locally or in CI against an empty Testcontainers database cannot show a
lock problem. With no rollback for a half-applied migration (#295), the first one that is slow in
production is an outage of message sending for as long as it runs.

## Decision

Every migration after `V27` follows these rules. `OnlineMigrationPolicyTest` (backend, runs in
`:backend:test`, no Docker or network needed) fails the build when one is broken.

1. **Lock timeout.** A migration that takes table locks starts with `SET lock_timeout = '5s'` and ends
   with `RESET lock_timeout`. A busy table fails the deploy fast; it can be retried.
2. **Indexes are built and dropped `CONCURRENTLY`.** Such a migration runs outside a transaction:
   a `V<n>__<name>.sql.conf` beside it contains `executeInTransaction=false`. Flyway is configured with
   `spring.flyway.postgresql.transactional-lock: false` — with the default transactional advisory
   lock, Flyway keeps a transaction open for the whole run and `CREATE INDEX CONCURRENTLY`, which waits
   for every older transaction, waits for it forever.
3. **A non-transactional migration is idempotent statement by statement**, because a failure leaves the
   statements before it applied and Flyway re-runs the whole script. `IF [NOT] EXISTS` everywhere, and
   a concurrently-built index is dropped first: an interrupted build leaves an `INVALID` index that
   `IF NOT EXISTS` would silently keep.
4. **Constraints are added `NOT VALID`** and validated in a later migration (`VALIDATE CONSTRAINT`
   takes only a `SHARE UPDATE EXCLUSIVE` lock). A unique constraint is built as
   `CREATE UNIQUE INDEX CONCURRENTLY` followed by `ADD CONSTRAINT ... USING INDEX`.
5. **Columns change by expand → migrate → contract.** No `ALTER COLUMN ... TYPE`, no `SET NOT NULL`
   on a populated column without a validated `CHECK` first, no `ADD COLUMN` with a volatile default
   (`gen_random_uuid()`, `random()`, `clock_timestamp()`); a constant or `now()` default is
   metadata-only on PostgreSQL 11+ and is fine.
6. **No data backfills of hot tables in a schema migration** — `messages`, `message_delivery_status`,
   `conversation_members`, `media_files`. A backfill is a batched background job that commits per
   batch and reports progress.
7. **One concern per migration.** Index consolidation, a new column and its backfill are separate
   migrations, so each can be reasoned about, and retried, on its own.

A rule that is wrong for one statement is suppressed in the migration itself, with the reason in the
same comment: `-- migration-lint: allow <rule-id> <reason>`. A suppression without a reason is
ignored by the test.

`V1`–`V27` are grandfathered. They have run in production and Flyway's checksum validation forbids
editing them; the constant that marks the boundary is never raised.

## Alternatives considered

- **squawk in CI.** The industry-standard linter, and the issue's suggestion. Rejected for now: it is
  a binary every job on the single self-hosted runner would download, and this project needs a dozen
  of its rules. The in-repo check runs inside the test suite CI already runs. If the rule set grows
  past what a lexical check can do honestly, squawk replaces it.
- **A review checklist.** Rejected because the failure is invisible in every environment except
  production; a rule nobody can see being broken needs a machine to check it.
- **Java/Kotlin migrations for every change.** Flyway's Java migrations make batching possible but
  run at start-up all the same, so a long backfill still blocks the deploy. Batched backfills belong
  in a job that runs after the deploy, not in the migration chain.

## Consequences

- The next index on `messages` (`V28`, #920) is built `CONCURRENTLY`, and the test proves the policy
  is satisfiable.
- A migration that would have blocked writes now fails `:backend:test` locally, before review.
- Non-transactional migrations can be left partly applied by a crash. Rule 3 makes a re-run safe;
  rolling back still needs a forward migration (#295).
