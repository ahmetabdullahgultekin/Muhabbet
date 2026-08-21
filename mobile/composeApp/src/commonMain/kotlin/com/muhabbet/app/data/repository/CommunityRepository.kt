package com.muhabbet.app.data.repository

import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.shared.dto.CommunityDetailResponse
import com.muhabbet.shared.dto.CommunityInviteLinkResponse
import com.muhabbet.shared.dto.CommunityInvitePreviewResponse
import com.muhabbet.shared.dto.CommunityMemberCandidateResponse
import com.muhabbet.shared.dto.CommunityMemberResponse
import com.muhabbet.shared.dto.CommunityResponse
import com.muhabbet.shared.dto.CreateCommunityInviteRequest
import com.muhabbet.shared.dto.CreateCommunityRequest
import com.muhabbet.shared.dto.UpdateCommunityRequest

class CommunityRepository(
    private val apiClient: ApiClient
) {

    suspend fun getCommunities(): List<CommunityResponse> {
        val response = apiClient.get<List<CommunityResponse>>("/api/v1/communities")
        return response.data ?: emptyList()
    }

    suspend fun getCommunityDetail(communityId: String): CommunityDetailResponse {
        val response = apiClient.get<CommunityDetailResponse>("/api/v1/communities/$communityId")
        return response.data ?: throw Exception("COMMUNITY_LOAD_FAILED")
    }

    suspend fun createCommunity(request: CreateCommunityRequest): CommunityResponse {
        val response = apiClient.post<CommunityResponse>("/api/v1/communities", request)
        return response.data ?: throw Exception("COMMUNITY_CREATE_FAILED")
    }

    suspend fun addGroupToCommunity(communityId: String, conversationId: String) {
        apiClient.post<Unit>(
            "/api/v1/communities/$communityId/groups",
            mapOf("conversationId" to conversationId)
        )
    }

    suspend fun removeGroupFromCommunity(communityId: String, conversationId: String) {
        apiClient.delete<Unit>("/api/v1/communities/$communityId/groups/$conversationId")
    }

    /**
     * Everyone in the community. Members only server-side, so a non-member gets an [ApiException]
     * rather than an empty list — the screen must show the failure, not an empty community.
     */
    suspend fun getCommunityMembers(communityId: String): List<CommunityMemberResponse> {
        val response = apiClient.get<List<CommunityMemberResponse>>("/api/v1/communities/$communityId/members")
        return response.data ?: emptyList()
    }

    /**
     * The people the server will currently accept into this community: members of its own groups
     * who are not in it yet. Deliberately not a contact list — since #375 an add outside that set
     * is refused, so offering one would be offering a button that always fails.
     */
    suspend fun getAddableUsers(communityId: String): List<CommunityMemberCandidateResponse> {
        val response = apiClient.get<List<CommunityMemberCandidateResponse>>(
            "/api/v1/communities/$communityId/member-candidates"
        )
        return response.data ?: emptyList()
    }

    suspend fun addMemberToCommunity(communityId: String, userId: String) {
        apiClient.post<Unit>("/api/v1/communities/$communityId/members", mapOf("userId" to userId))
    }

    suspend fun updateCommunity(communityId: String, name: String, description: String?): CommunityResponse {
        val response = apiClient.patch<CommunityResponse>(
            "/api/v1/communities/$communityId",
            UpdateCommunityRequest(name = name, description = description)
        )
        return response.data ?: throw Exception("COMMUNITY_UPDATE_FAILED")
    }

    suspend fun leaveCommunity(communityId: String) {
        apiClient.post<Unit>("/api/v1/communities/$communityId/leave", emptyMap<String, String>())
    }

    /**
     * Deletes the community outright (#407). Owner only server-side — the screen must hide this
     * action for anyone else, since a non-owner call reaches the server only to 403.
     */
    suspend fun deleteCommunity(communityId: String) {
        apiClient.delete<Unit>("/api/v1/communities/$communityId")
    }

    // ─── Invite links (#387, #416) ──────────────────────
    //
    // The only way a community can gain a member who is not already in one of its groups. Everything
    // above this line either reads a community you are already in, or enrols someone the server
    // already considers adjacent; none of it can reach a stranger, which is why every community in
    // production has exactly one member.

    /**
     * The community's active invite links. **Admins and owners only** server-side — a token is a
     * bearer credential, so a plain member reaching this would be able to admit anyone.
     *
     * Returns an empty list when the community has no links yet, which is the ordinary starting
     * state rather than an error.
     */
    suspend fun getInviteLinks(communityId: String): List<CommunityInviteLinkResponse> {
        val response = apiClient.get<List<CommunityInviteLinkResponse>>(
            "/api/v1/communities/$communityId/invite-links"
        )
        return response.data ?: emptyList()
    }

    /**
     * Mints a link. Admins and owners only.
     *
     * @param maxUses null for unlimited.
     * @param expiresAt ISO-8601, or null for no expiry.
     */
    suspend fun createInviteLink(
        communityId: String,
        maxUses: Int? = null,
        expiresAt: String? = null
    ): CommunityInviteLinkResponse {
        val response = apiClient.post<CommunityInviteLinkResponse>(
            "/api/v1/communities/$communityId/invite-links",
            CreateCommunityInviteRequest(maxUses = maxUses, expiresAt = expiresAt)
        )
        return response.data ?: throw Exception("COMMUNITY_INVITE_CREATE_FAILED")
    }

    suspend fun revokeInviteLink(communityId: String, linkId: String) {
        apiClient.delete<Unit>("/api/v1/communities/$communityId/invite-links/$linkId")
    }

    /**
     * What the holder of [token] is being offered, before they decide.
     *
     * Callable by anyone signed in who holds the token — this is the one community read that does
     * not require membership, and it discloses only name, avatar, size and inviter. It does not join
     * anything and does not spend a use, so the join screen may call it on every open.
     */
    suspend fun previewInvite(token: String): CommunityInvitePreviewResponse {
        val response = apiClient.get<CommunityInvitePreviewResponse>("/api/v1/communities/invites/$token")
        return response.data ?: throw Exception("COMMUNITY_INVITE_PREVIEW_FAILED")
    }

    /** The accept step. Returns the joined community so the caller can navigate straight into it. */
    suspend fun acceptInvite(token: String): CommunityResponse {
        val response = apiClient.post<CommunityResponse>(
            "/api/v1/communities/invites/$token/accept",
            emptyMap<String, String>()
        )
        return response.data ?: throw Exception("COMMUNITY_INVITE_ACCEPT_FAILED")
    }
}
