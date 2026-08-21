package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.model.CommunityInviteLink
import com.muhabbet.messaging.domain.port.`in`.CommunityInvitePreview
import com.muhabbet.messaging.domain.port.`in`.ManageCommunityInviteUseCase
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.CommunityInviteLinkResponse
import com.muhabbet.shared.dto.CommunityInvitePreviewResponse
import com.muhabbet.shared.dto.CreateCommunityInviteRequest
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID

/**
 * Community invite links (#387, #416) — minting, listing, revoking, previewing and accepting.
 *
 * **Who may call what**, since every one of these is a decision rather than a lookup:
 *
 * | Endpoint | Caller must be |
 * |---|---|
 * | `POST   /communities/{id}/invite-links` | admin or owner of that community |
 * | `GET    /communities/{id}/invite-links` | admin or owner (a token is a bearer credential) |
 * | `DELETE /communities/{id}/invite-links/{linkId}` | admin or owner of the **link's own** community |
 * | `GET    /communities/invites/{token}` | any authenticated user holding the token |
 * | `POST   /communities/invites/{token}/accept` | any authenticated user holding the token |
 *
 * The last two are the only community endpoints readable by a non-member, which is the whole point
 * of the feature and also its main risk — see [CommunityInvitePreviewResponse] for exactly what they
 * disclose and why it is that short a list. Every check is enforced in
 * [ManageCommunityInviteUseCase]'s implementation, not here; this class only maps.
 *
 * Mapped under `/api/v1/communities` alongside [CommunityController] and [CommunityMemberController]
 * because it is the same resource, and separate from both because it serves a different screen and
 * depends on a different in-port.
 */
@RestController
@RequestMapping("/api/v1/communities")
class CommunityInviteController(
    private val manageCommunityInviteUseCase: ManageCommunityInviteUseCase
) {

    @PostMapping("/{communityId}/invite-links")
    fun createLink(
        @PathVariable communityId: UUID,
        @RequestBody request: CreateCommunityInviteRequest
    ): ResponseEntity<ApiResponse<CommunityInviteLinkResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val link = manageCommunityInviteUseCase.createLink(
            communityId = communityId,
            requesterId = userId,
            maxUses = request.maxUses,
            expiresAt = request.expiresAt.toInstantOrNull()
        )
        return ApiResponseBuilder.created(link.toResponse())
    }

    @GetMapping("/{communityId}/invite-links")
    fun listLinks(
        @PathVariable communityId: UUID
    ): ResponseEntity<ApiResponse<List<CommunityInviteLinkResponse>>> {
        val userId = AuthenticatedUser.currentUserId()
        val links = manageCommunityInviteUseCase.listLinks(communityId, userId)
        return ApiResponseBuilder.ok(links.map { it.toResponse() })
    }

    @DeleteMapping("/{communityId}/invite-links/{linkId}")
    fun revokeLink(
        @PathVariable communityId: UUID,
        @PathVariable linkId: UUID
    ): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        manageCommunityInviteUseCase.revokeLink(communityId, linkId, userId)
        return ApiResponseBuilder.ok(Unit)
    }

    /**
     * What the token holder is being offered. Does not join anything and does not spend a use, so it
     * is safe for the join screen to call on every open, including for someone already a member.
     */
    @GetMapping("/invites/{token}")
    fun preview(@PathVariable token: String): ResponseEntity<ApiResponse<CommunityInvitePreviewResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        return ApiResponseBuilder.ok(manageCommunityInviteUseCase.preview(token, userId).toResponse())
    }

    /**
     * The accept step. Returns the community so the app can navigate straight into it rather than
     * re-fetching a list and hunting for the new row.
     */
    @PostMapping("/invites/{token}/accept")
    fun accept(@PathVariable token: String): ResponseEntity<ApiResponse<CommunityResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val joined = manageCommunityInviteUseCase.accept(token, userId)
        // The package-local CommunityResponse from CommunityController, not the shared one: they are
        // field-for-field identical and both are in scope here, so naming the local one keeps the
        // accept response byte-identical to every other community payload this API returns.
        return ApiResponseBuilder.ok(
            CommunityResponse(
                id = joined.community.id.toString(),
                name = joined.community.name,
                description = joined.community.description,
                avatarUrl = joined.community.avatarUrl,
                groupCount = joined.groupCount,
                memberCount = joined.memberCount,
                createdAt = joined.community.createdAt.toString()
            )
        )
    }
}

/**
 * The scheme the app registers, and the reason [CommunityInviteLinkResponse.inviteUrl] is built here
 * rather than on the device: the phone should not be deciding what a Muhabbet link looks like.
 *
 * `muhabbet://` rather than `https://muhabbet.app/...` because the manifest's `muhabbet` filter
 * carries no host restriction and needs no domain verification, whereas an App Link needs an
 * `assetlinks.json` served from a domain this project does not own yet. The cost is real and
 * should be stated rather than hidden: a recipient **without** the app installed gets a URL their
 * browser cannot open. Replacing this constant is the whole change once a domain exists.
 */
private const val INVITE_LINK_SCHEME = "muhabbet://community-invite/"

private fun CommunityInviteLink.toResponse() = CommunityInviteLinkResponse(
    id = id.toString(),
    communityId = communityId.toString(),
    inviteToken = inviteToken,
    inviteUrl = INVITE_LINK_SCHEME + inviteToken,
    isActive = isActive,
    maxUses = maxUses,
    useCount = useCount,
    expiresAt = expiresAt?.toString(),
    createdAt = createdAt.toString()
)

private fun CommunityInvitePreview.toResponse() = CommunityInvitePreviewResponse(
    communityId = communityId.toString(),
    name = name,
    avatarUrl = avatarUrl,
    memberCount = memberCount,
    inviterDisplayName = inviterDisplayName,
    alreadyMember = alreadyMember
)

/**
 * Parses the optional expiry a client sends.
 *
 * A malformed value becomes a 400 with a code the app already knows, rather than the
 * `DateTimeParseException` → 500 that an unguarded `Instant.parse` produces. `InviteLinkController`
 * still has that bug for groups; not fixed here because it is a different endpoint with its own
 * tests, but it is the reason this is a named function and not an inline `?.let { Instant.parse(it) }`.
 */
private fun String?.toInstantOrNull(): Instant? =
    this?.let {
        try {
            Instant.parse(it)
        } catch (_: DateTimeParseException) {
            throw BusinessException(ErrorCode.COMMUNITY_INVITE_INVALID_EXPIRY)
        }
    }
