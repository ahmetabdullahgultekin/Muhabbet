package com.muhabbet.auth.adapter.`in`.web

import com.muhabbet.auth.domain.model.LoginApproval
import com.muhabbet.auth.domain.port.`in`.LoginApprovalUseCase
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class LoginApprovalRequest(
    val deviceName: String? = null,
    val platform: String? = null,
    val ip: String? = null
)

data class LoginApprovalResponse(
    val id: String,
    val userId: String,
    val deviceName: String?,
    val platform: String?,
    val ipAddress: String?,
    val status: String,
    val createdAt: String,
    val expiresAt: String
)

@RestController
@RequestMapping("/api/v1/auth/login-approvals")
class LoginApprovalController(
    private val loginApprovalUseCase: LoginApprovalUseCase
) {

    @PostMapping
    fun requestApproval(@RequestBody request: LoginApprovalRequest): ResponseEntity<ApiResponse<LoginApprovalResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val approval = loginApprovalUseCase.requestApproval(userId, request.deviceName, request.platform, request.ip)
        return ApiResponseBuilder.created(approval.toResponse())
    }

    @GetMapping
    fun getPendingApprovals(): ResponseEntity<ApiResponse<List<LoginApprovalResponse>>> {
        val userId = AuthenticatedUser.currentUserId()
        val approvals = loginApprovalUseCase.getPendingApprovals(userId)
        return ApiResponseBuilder.ok(approvals.map { it.toResponse() })
    }

    @PostMapping("/{approvalId}/approve")
    fun approveLogin(@PathVariable approvalId: UUID): ResponseEntity<ApiResponse<LoginApprovalResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val approval = loginApprovalUseCase.approveLogin(approvalId, userId)
        return ApiResponseBuilder.ok(approval.toResponse())
    }

    @PostMapping("/{approvalId}/deny")
    fun denyLogin(@PathVariable approvalId: UUID): ResponseEntity<ApiResponse<LoginApprovalResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val approval = loginApprovalUseCase.denyLogin(approvalId, userId)
        return ApiResponseBuilder.ok(approval.toResponse())
    }
}

private fun LoginApproval.toResponse() = LoginApprovalResponse(
    id = id.toString(),
    userId = userId.toString(),
    deviceName = deviceName,
    platform = platform,
    ipAddress = ipAddress,
    status = status.name,
    createdAt = createdAt.toString(),
    expiresAt = expiresAt.toString()
)
