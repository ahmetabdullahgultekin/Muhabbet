-- =====================================================
-- Muhabbet — V23: the only way a community can gain a second member
-- =====================================================
--
-- Eight communities exist in production. Every one has exactly one member and zero groups (#407),
-- and that is not a coincidence — it is arithmetic. `CommunityService.addMember` refuses anyone who
-- is not already in one of the community's own groups (#375 closed the IDOR that let an owner
-- attach any user id they could guess), so a community with no groups has an empty candidate set
-- and can never grow. There is no discovery, no search and no invite (#416, #387). The feature is
-- inert by construction.
--
-- This table is the missing edge. An admin mints a token, sends it through whatever channel they
-- already use, and the recipient opens it, sees who invited them to what, and **accepts**. That
-- last step is the whole point: #387's acceptance criterion is that joining a community is
-- impossible without an action taken by the person joining. A row here is an offer, never a
-- membership — `community_members` is written only when someone accepts.
--
-- Deliberately a link rather than a directed user-to-user invite. A directed invite needs the
-- inviter to name the invitee, which means a picker over some user directory, which is the exact
-- disclosure #375 closed; and it needs an inbox plus a push, or it becomes an invite the recipient
-- never sees — "the same defect in a new place", as #387 puts it. A link carries its own delivery
-- (the user's own messenger) and its own surface (the screen the link opens), so it is complete in
-- a way a directed invite is not. It also answers #416's second question narrowly: what a
-- non-member learns is name, avatar, member count and who invited them — never the group list.
--
-- Shaped after `group_invite_links` (V16) on purpose, minus one column and plus one clause:
--
--   * No `requires_approval`. Groups have an admin approval queue (`group_join_requests`);
--     communities have no such surface and building one here would be a second half-feature. A
--     community link either admits the holder or has been revoked. YAGNI.
--
--   * `ON DELETE CASCADE` on community_id, which `group_invite_links.conversation_id` lacks.
--     `ManageCommunityUseCase.delete` is a real, owner-reachable action, so links to a deleted
--     community are reachable garbage that would otherwise resolve to COMMUNITY_NOT_FOUND forever.
--     `community_members` and `community_groups` already cascade the same way (V16).
--
-- `created_by` has no cascade, matching every other table in V16: a user row is never hard-deleted
-- (KVKK erasure is a soft delete), and a link outliving its creator's departure is correct — the
-- community, not the person, is what the holder is joining.

CREATE TABLE community_invite_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    community_id UUID NOT NULL REFERENCES communities(id) ON DELETE CASCADE,
    invite_token VARCHAR(64) NOT NULL UNIQUE,
    created_by UUID NOT NULL REFERENCES users(id),
    is_active BOOLEAN NOT NULL DEFAULT true,
    max_uses INT,
    use_count INT NOT NULL DEFAULT 0,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- The token lookup is the hot path: every preview and every accept starts from it, and only an
-- active link can be resolved. Partial, like idx_invite_links_token, so revoked rows stay out of it.
CREATE INDEX idx_community_invite_links_token ON community_invite_links(invite_token)
    WHERE is_active = true;

-- Serves the admin's "which links exist for this community" list.
CREATE INDEX idx_community_invite_links_community ON community_invite_links(community_id);

COMMENT ON TABLE community_invite_links IS
    'Invite links a community admin mints. A row is an offer, not a membership: community_members '
    'is written only when the recipient accepts (#387).';

COMMENT ON COLUMN community_invite_links.use_count IS
    'Accepted joins through this link. Compared against max_uses; NULL max_uses means unlimited.';
