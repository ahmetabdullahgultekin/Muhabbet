package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.model.GroupInviteLink
import com.muhabbet.messaging.domain.port.`in`.ManageInviteLinkUseCase
import com.muhabbet.shared.config.InviteLinkProperties
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.CreateInviteLinkRequest
import com.muhabbet.shared.dto.InviteLinkResponse
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
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * The request and response types here are the shared KMP DTOs the app decodes with, not local
 * copies of them.
 *
 * This file used to declare its own `InviteLinkResponse`, and the copy had neither `inviteUrl`
 * nor `isActive` — both of which the client's DTO declares without a default. `ignoreUnknownKeys`
 * forgives a field the client does not know; it does nothing for one the client requires and the
 * server never sends, so kotlinx-serialization threw `MissingFieldException` on every response
 * this endpoint produced (#695). The local `CreateInviteLinkRequest` had drifted the other way —
 * it read `expiresAt`, while the app has always sent `expiresInHours` — so an expiry the user
 * asked for was silently dropped and the link never expired.
 *
 * Using the shared type is the only thing that stops the two sides drifting again: a field added
 * to `shared/.../dto/Dtos.kt` now fails this file's compile rather than the phone's decode.
 */
@RestController
@RequestMapping("/api/v1/conversations/{conversationId}/invite-link")
class InviteLinkController(
    private val manageInviteLinkUseCase: ManageInviteLinkUseCase,
    private val inviteLinkProperties: InviteLinkProperties
) {

    @PostMapping
    fun createLink(
        @PathVariable conversationId: UUID,
        @RequestBody request: CreateInviteLinkRequest
    ): ResponseEntity<ApiResponse<InviteLinkResponse>> {
        val userId = AuthenticatedUser.currentUserId()

        val link = manageInviteLinkUseCase.createLink(
            conversationId = conversationId,
            userId = userId,
            requiresApproval = request.requiresApproval,
            maxUses = request.maxUses,
            expiresAt = request.expiresInHours?.let { Instant.now().plus(it.toLong(), ChronoUnit.HOURS) }
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

    private fun GroupInviteLink.toResponse() = InviteLinkResponse(
        id = id.toString(),
        conversationId = conversationId.toString(),
        inviteToken = inviteToken,
        inviteUrl = inviteLinkProperties.urlFor(inviteToken),
        requiresApproval = requiresApproval,
        isActive = isActive,
        maxUses = maxUses,
        useCount = useCount,
        expiresAt = expiresAt?.toString(),
        createdAt = createdAt.toString()
    )
}
