package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.port.`in`.ManageInviteLinkUseCase
import com.muhabbet.shared.dto.ApiResponse
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
import java.util.UUID

data class CreateInviteLinkRequest(
    val requiresApproval: Boolean = false,
    val maxUses: Int? = null,
    val expiresAt: String? = null
)

data class InviteLinkResponse(
    val id: String,
    val conversationId: String,
    val inviteToken: String,
    val requiresApproval: Boolean,
    val maxUses: Int?,
    val useCount: Int,
    val expiresAt: String?,
    val createdAt: String
)

@RestController
@RequestMapping("/api/v1/conversations/{conversationId}/invite-link")
class InviteLinkController(
    private val manageInviteLinkUseCase: ManageInviteLinkUseCase
) {

    @PostMapping
    fun createLink(
        @PathVariable conversationId: UUID,
        @RequestBody request: CreateInviteLinkRequest
    ): ResponseEntity<ApiResponse<InviteLinkResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val expiresAt = request.expiresAt?.let { Instant.parse(it) }

        val link = manageInviteLinkUseCase.createLink(
            conversationId = conversationId,
            userId = userId,
            requiresApproval = request.requiresApproval,
            maxUses = request.maxUses,
            expiresAt = expiresAt
        )

        return ApiResponseBuilder.created(link.toResponse())
    }

    @DeleteMapping("/{linkId}")
    fun revokeLink(
        @PathVariable conversationId: UUID,
        @PathVariable linkId: UUID
    ): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        manageInviteLinkUseCase.revokeLink(linkId, userId)
        return ApiResponseBuilder.ok(Unit)
    }

    @PostMapping("/join/{token}")
    fun joinViaLink(@PathVariable token: String): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        manageInviteLinkUseCase.joinViaLink(token, userId)
        return ApiResponseBuilder.ok(Unit)
    }

    @GetMapping("/info/{token}")
    fun getLinkInfo(@PathVariable token: String): ResponseEntity<ApiResponse<InviteLinkResponse>> {
        val link = manageInviteLinkUseCase.getLinkInfo(token)
        return ApiResponseBuilder.ok(link.toResponse())
    }
}

private fun com.muhabbet.messaging.domain.model.GroupInviteLink.toResponse() = InviteLinkResponse(
    id = id.toString(),
    conversationId = conversationId.toString(),
    inviteToken = inviteToken,
    requiresApproval = requiresApproval,
    maxUses = maxUses,
    useCount = useCount,
    expiresAt = expiresAt?.toString(),
    createdAt = createdAt.toString()
)
