-- V23: one community name per creator (#446).
--
-- Three communities called "Muhabbet" under the same account are indistinguishable in the list, so
-- neither the owner nor the app can tell which row a tap is about. This migration defines what
-- "the same name" means, disambiguates the rows that already collide, and then makes the collision
-- impossible.
--
-- Scope is (created_by, name), not a globally unique name: two different people each calling a
-- community "Aile" is not a collision, and forbidding it would let whoever registered first squat
-- on every ordinary Turkish word. `created_by` is used rather than "current owner" because it is
-- immutable — ownership can move to a successor when an owner leaves (CommunityService.leave), and
-- a unique key whose value moves would have to be re-validated on every succession.
--
-- Communities are hard-deleted (CommunityPersistenceAdapter.delete is a single DELETE, with V16's
-- FK cascades doing the rest); there is no `deleted_at` on this table, so a deleted community's
-- name is free again immediately and no partial-index predicate is needed here.

-- ─── What counts as the same name ────────────────────────────────────
--
-- One definition, in the database, used by all three of: the de-duplication below, the unique index
-- at the bottom, and the application's pre-flight check
-- (SpringDataCommunityJpaRepository.findByCreatorAndNameKey). Writing the fold in Kotlin as well
-- would be a second definition that could drift from the index silently — and the index is the one
-- that actually decides.
--
--   btrim + collapse runs of whitespace  → "Kitap  Kulübü " and "Kitap Kulübü" are one name
--   NFC                                  → "I" + U+0307 typed decomposed becomes "İ"
--   translate 'İı' → 'ii'                → the Turkish dotted/dotless i trap, below
--   lower                                → ordinary case folding
--
-- The translate step runs BEFORE lower() and is the point of doing this by hand. Turkish case
-- folding is not a subset of the invariant one: lower('I') is 'ı' in a tr_TR locale and 'i'
-- everywhere else, and lower('İ') is 'i' + U+0307 — two code points — under an invariant locale.
-- So a naive lower(btrim(name)) gives a different answer depending on the server's locale, and
-- still leaves "İSTANBUL" and "istanbul" as different names. Mapping all four of I/ı/i/İ onto 'i'
-- first makes the result locale-independent for exactly the letters where the locales disagree.
--
-- The cost is that "Kısa" and "Kisa" are treated as one name. That is deliberate: this key exists
-- so that two rows in a list cannot look alike, and ı/i is the most confusable pair in Turkish
-- rendering. An owner who genuinely wants both gets a 409 and picks another name; the alternative
-- is a fold whose answer depends on which machine ran it.
--
-- lower() remains collation-dependent for other letters (Ç, Ğ, Ö, Ş, Ü). That is the standard
-- caveat for any functional index over lower(): if this database's collation changes, this index
-- must be REINDEXed along with every other one. The same applies if this function is ever
-- redefined — CREATE OR REPLACE does not recompute stored index entries.
--
-- Requires a UTF8 database: normalize() rejects any other server encoding.
CREATE OR REPLACE FUNCTION community_name_key(raw_name TEXT) RETURNS TEXT
    LANGUAGE sql
    IMMUTABLE
    STRICT
    PARALLEL SAFE
AS $$
    SELECT lower(
        translate(
            normalize(regexp_replace(btrim(raw_name), '\s+', ' ', 'g'), NFC),
            'İı',
            'ii'
        )
    );
$$;

-- ─── The rows that already collide ───────────────────────────────────
--
-- Production holds three communities named "Muhabbet" under one account (#407's row dump), so
-- adding the index alone would fail this migration, and a failed Flyway migration is a backend that
-- will not start — an outage, not a test failure.
--
-- The colliding rows are RENAMED, never deleted. A community carries members, linked groups and an
-- announcement channel; deleting one to satisfy a constraint would destroy data the owner never
-- asked us to touch, and #407 shipped a DELETE endpoint precisely so that they can make that call
-- themselves. A rename is reversible from the app; a delete is not.
--
-- Deterministic in both directions: within each (created_by, key) group the oldest row by
-- (created_at, id) keeps its name and every later one gets " (2)", " (3)" … The suffix search skips
-- any candidate that would itself collide with a name that owner already holds, so it terminates on
-- a set of names the index will accept. The truncation keeps the result inside the column's
-- VARCHAR(256).
DO $$
DECLARE
    duplicate RECORD;
    candidate TEXT;
    suffix    INT;
BEGIN
    FOR duplicate IN
        SELECT id, created_by, name
        FROM (
            SELECT id,
                   created_by,
                   name,
                   row_number() OVER (
                       PARTITION BY created_by, community_name_key(name)
                       ORDER BY created_at, id
                   ) AS rn
            FROM communities
        ) ranked
        WHERE rn > 1
        ORDER BY created_by, name, id
    LOOP
        suffix := 2;
        LOOP
            candidate := left(btrim(duplicate.name), 256 - length(' (' || suffix || ')')) ||
                         ' (' || suffix || ')';
            EXIT WHEN NOT EXISTS (
                SELECT 1
                FROM communities c
                WHERE c.created_by = duplicate.created_by
                  AND community_name_key(c.name) = community_name_key(candidate)
            );
            suffix := suffix + 1;
        END LOOP;

        UPDATE communities
        SET name = candidate,
            updated_at = now()
        WHERE id = duplicate.id;

        RAISE NOTICE 'V23: community % (created_by %) renamed from % to % to clear a duplicate name',
            duplicate.id, duplicate.created_by, duplicate.name, candidate;
    END LOOP;
END $$;

-- ─── The constraint ──────────────────────────────────────────────────
--
-- A unique INDEX rather than a table constraint, because a constraint cannot be expressed over a
-- function of a column. Its name is matched by CommunityPersistenceAdapter, which turns the race —
-- two concurrent creates that both pass the service's pre-flight check — into
-- COMMUNITY_NAME_ALREADY_EXISTS (409) rather than letting it escape as a 500. Renaming this index
-- without updating that constant reopens that hole.
CREATE UNIQUE INDEX ux_communities_creator_name_key
    ON communities (created_by, community_name_key(name));
