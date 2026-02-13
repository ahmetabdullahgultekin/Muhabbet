package com.muhabbet.auth.adapter.`in`.web

import com.muhabbet.auth.domain.model.UserDataExport
import com.muhabbet.auth.domain.port.`in`.ManageUserDataUseCase
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users/me")
class UserDataController(
    private val manageUserDataUseCase: ManageUserDataUseCase
) {

    @GetMapping("/data-export")
    fun exportUserData(): ResponseEntity<ApiResponse<UserDataExport>> {
        val userId = AuthenticatedUser.currentUserId()
        val export = manageUserDataUseCase.exportUserData(userId)
        return ApiResponseBuilder.ok(export)
    }

    @DeleteMapping
    fun requestAccountDeletion(): ResponseEntity<ApiResponse<Nothing>> {
        val userId = AuthenticatedUser.currentUserId()
        manageUserDataUseCase.requestAccountDeletion(userId)
        return ApiResponseBuilder.noContent()
    }
}
