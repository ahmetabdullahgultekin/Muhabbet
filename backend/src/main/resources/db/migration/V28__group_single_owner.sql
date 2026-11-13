-- #890 — a group has exactly one OWNER.
--
-- Until now `updateMemberRole` let the owner promote a second member to OWNER or demote themselves,
-- so production may already hold groups with two owners or none. The domain now refuses both
-- (`GroupRoles`), and ownership moves only through an explicit transfer. This migration repairs any
-- damage the old code did and then adds the database backstop, in that order, because the index
-- cannot be created while a violating row exists.

-- 1. Several owners: keep the longest-standing one, demote the rest to ADMIN (they keep their
--    powers short of ownership, which is the least surprising repair).
UPDATE conversation_members cm
SET role = 'ADMIN'
WHERE cm.role = 'OWNER'
  AND EXISTS (
      SELECT 1 FROM conversation_members older
      WHERE older.conversation_id = cm.conversation_id
        AND older.role = 'OWNER'
        AND (older.joined_at, older.user_id) < (cm.joined_at, cm.user_id)
  );

-- 2. No owner: a GROUP with members but nobody who can change a role. Promote the longest-standing
--    admin, else the longest-standing member — the same order `GroupRoles.successorOnLeave` uses.
UPDATE conversation_members cm
SET role = 'OWNER'
FROM (
    SELECT DISTINCT ON (m.conversation_id) m.conversation_id, m.user_id
    FROM conversation_members m
    JOIN conversations c ON c.id = m.conversation_id AND c.type = 'GROUP'
    WHERE NOT EXISTS (
        SELECT 1 FROM conversation_members o
        WHERE o.conversation_id = m.conversation_id AND o.role = 'OWNER'
    )
    ORDER BY m.conversation_id, (m.role = 'ADMIN') DESC, m.joined_at, m.user_id
) heir
WHERE cm.conversation_id = heir.conversation_id
  AND cm.user_id = heir.user_id;

-- 3. The backstop. A second OWNER insert or update now fails at the database, whichever code path
--    attempts it. The ownership transfer demotes before it promotes for exactly this reason.
CREATE UNIQUE INDEX uq_conversation_members_single_owner
    ON conversation_members (conversation_id)
    WHERE role = 'OWNER';
