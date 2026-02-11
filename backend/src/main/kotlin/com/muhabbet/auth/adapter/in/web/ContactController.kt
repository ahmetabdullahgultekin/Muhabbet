package com.muhabbet.auth.adapter.`in`.web

import com.muhabbet.auth.domain.port.`in`.ContactSyncUseCase
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.ContactSyncRequest
import com.muhabbet.shared.dto.ContactSyncResponse
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/contacts")
class ContactController(
    private val contactSyncUseCase: ContactSyncUseCase
) {

    @PostMapping("/sync")
    fun syncContacts(@RequestBody request: ContactSyncRequest): ResponseEntity<ApiResponse<ContactSyncResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val matched = contactSyncUseCase.syncContacts(userId, request.phoneHashes)
        return ApiResponseBuilder.ok(ContactSyncResponse(matchedContacts = matched))
    }
}
