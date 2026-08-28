package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.Community
import com.muhabbet.messaging.domain.model.CommunityGroup
import com.muhabbet.messaging.domain.model.CommunityMember
import java.util.UUID

/**
 * The links between a community and the conversations it carries — a concern of its own, and the
 * only part of `CommunityRepository` that touches `community_groups` rather than `communities` or
 * `community_members`.
 *
 * Split out (#446) when adding one method to the parent tipped it over detekt's function-count
 * threshold. Widening the threshold or baselining the finding would both have recorded a fat port as
 * acceptable; this splits it the way the project's own ISP rule asks for. `CommunityRepository`
 * extends it, so every existing caller and every mocked `CommunityRepository` in the tests is
 * unaffected — the seam is real but the surface is unchanged.
 */
interface CommunityGroupLinkRepository {
    fun addGroup(group: CommunityGroup): CommunityGroup
    fun removeGroup(communityId: UUID, conversationId: UUID)
    fun findGroupsByCommunityId(communityId: UUID): List<CommunityGroup>

    /**
     * Group counts for many communities in one query. Communities with no groups are absent
     * from the map rather than mapped to zero.
     */
    fun countGroupsByCommunityIds(communityIds: List<UUID>): Map<UUID, Int>
}

/**
 * Who belongs to a community and in what role — the `community_members` half, plus the lookup that
 * answers "which communities is this person in".
 *
 * Split from [CommunityRepository] alongside [CommunityGroupLinkRepository] and for the same reason
 * (#446). The three interfaces together are exactly what the one port used to be, and
 * [CommunityRepository] extends both, so no caller and no mocked repository in the tests changed.
 */
interface CommunityMembershipRepository {
    /** Insert or update. Saving an existing (communityId, userId) pair changes that member's role. */
    fun saveMember(member: CommunityMember): CommunityMember
    fun findMember(communityId: UUID, userId: UUID): CommunityMember?
    fun findMembersByCommunityId(communityId: UUID): List<CommunityMember>
    fun removeMember(communityId: UUID, userId: UUID)
    fun findCommunitiesByUserId(userId: UUID): List<Community>

    /**
     * Member counts for many communities in one query. Communities with no members are absent
     * from the map rather than mapped to zero.
     */
    fun countMembersByCommunityIds(communityIds: List<UUID>): Map<UUID, Int>
}

interface CommunityRepository : CommunityGroupLinkRepository, CommunityMembershipRepository {
    fun save(community: Community): Community
    fun findById(id: UUID): Community?
    fun update(community: Community): Community

    /**
     * Deletes the community itself. `community_members` and `community_groups` both declare
     * `community_id ... REFERENCES communities(id) ON DELETE CASCADE` (`V16`), so this single
     * statement is enough to remove every membership and every group link — the database does the
     * cascading, not this adapter. `community_groups.conversation_id` carries no such clause, so
     * the linked conversations (and their messages and members) are never touched.
     */
    fun delete(id: UUID)

    /**
     * The community [creatorId] already has under [name], or `null` if that name is free (#446).
     *
     * The match is **not** on the literal string. It applies the same fold as the unique index the
     * database enforces (`ux_communities_creator_name_key`, V23): surrounding and repeated
     * whitespace collapsed, Turkish dotted/dotless i unified, case ignored. So "Kitap Kulübü" finds
     * a row stored as "kitap  kulübü ", which is the point — those two are the same name to anyone
     * reading a list.
     *
     * That fold has exactly one definition, `community_name_key(text)` in V23, and this method calls
     * it rather than reimplementing it. A second copy in Kotlin could drift from the index without
     * anything failing, and the index is the half that actually decides — the service would answer
     * "available" for a name the insert then rejects.
     *
     * Scoped to the creator, not the current owner: `created_by` never changes, while ownership can
     * move to a successor when an owner leaves.
     */
    fun findByCreatorAndName(creatorId: UUID, name: String): Community?
}
