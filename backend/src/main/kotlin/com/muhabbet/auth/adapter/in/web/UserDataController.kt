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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users/me")
class UserDataController(
    private val manageUserDataUseCase: ManageUserDataUseCase
) {

    /**
     * The KVKK m.11 / GDPR Art. 15 & 20 data export (#341). [messages] and [media files][UserDataExport.mediaFiles]
     * are cursor-paginated because a full account's history can be too large for one response —
     * pass the previous response's `nextCursor` back as [messagesCursor]/[mediaCursor] to continue;
     * `hasMore = false` on both means the export is complete.
     */
    @GetMapping("/data-export")
    fun exportUserData(
        @RequestParam(required = false) messagesCursor: String?,
        @RequestParam(required = false) mediaCursor: String?,
        @RequestParam(required = false, defaultValue = "200") pageSize: Int
    ): ResponseEntity<ApiResponse<UserDataExport>> {
        val userId = AuthenticatedUser.currentUserId()
        val export = manageUserDataUseCase.exportUserData(
            userId = userId,
            messagesCursor = messagesCursor,
            mediaCursor = mediaCursor,
            pageSize = pageSize
        )
        return ApiResponseBuilder.ok(export)
    }

    @DeleteMapping
    fun requestAccountDeletion(): ResponseEntity<ApiResponse<Nothing>> {
        val userId = AuthenticatedUser.currentUserId()
        manageUserDataUseCase.requestAccountDeletion(userId)
        return ApiResponseBuilder.noContent()
    }
}
