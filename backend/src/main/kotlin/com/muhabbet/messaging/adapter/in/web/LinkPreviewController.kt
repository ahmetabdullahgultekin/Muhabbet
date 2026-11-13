package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.model.LinkPreview
import com.muhabbet.messaging.domain.port.`in`.GetLinkPreviewUseCase
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.LinkPreviewResponse
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * HTTP only. The fetch, its SSRF defences, the cache and the per-user budget all live behind
 * [GetLinkPreviewUseCase] (#916) — this class used to do the fetching itself, on the request thread,
 * with no cache and a name-based SSRF check the HTTP client then bypassed.
 */
@RestController
@RequestMapping("/api/v1/link-preview")
class LinkPreviewController(
    private val getLinkPreviewUseCase: GetLinkPreviewUseCase
) {

    @GetMapping
    fun getLinkPreview(@RequestParam url: String): ResponseEntity<ApiResponse<LinkPreviewResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        return ApiResponseBuilder.ok(getLinkPreviewUseCase.getLinkPreview(userId, url).toResponse())
    }

    private fun LinkPreview.toResponse() = LinkPreviewResponse(
        url = url,
        title = title,
        description = description,
        imageUrl = imageUrl,
        siteName = siteName
    )
}
