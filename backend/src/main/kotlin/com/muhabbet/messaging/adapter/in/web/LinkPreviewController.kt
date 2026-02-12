package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.LinkPreviewResponse
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/link-preview")
class LinkPreviewController {

    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun getLinkPreview(@RequestParam url: String): ResponseEntity<ApiResponse<LinkPreviewResponse>> {
        AuthenticatedUser.currentUserId() // Ensure authenticated

        return try {
            val doc = Jsoup.connect(url)
                .userAgent("MuhabbetBot/1.0")
                .timeout(5000)
                .get()

            val title = doc.selectFirst("meta[property=og:title]")?.attr("content")
                ?: doc.title()
            val description = doc.selectFirst("meta[property=og:description]")?.attr("content")
                ?: doc.selectFirst("meta[name=description]")?.attr("content")
            val imageUrl = doc.selectFirst("meta[property=og:image]")?.attr("content")
            val siteName = doc.selectFirst("meta[property=og:site_name]")?.attr("content")

            ApiResponseBuilder.ok(
                LinkPreviewResponse(
                    url = url,
                    title = title?.take(200),
                    description = description?.take(500),
                    imageUrl = imageUrl,
                    siteName = siteName
                )
            )
        } catch (e: Exception) {
            log.debug("Failed to fetch link preview for {}: {}", url, e.message)
            ApiResponseBuilder.ok(LinkPreviewResponse(url = url))
        }
    }
}
