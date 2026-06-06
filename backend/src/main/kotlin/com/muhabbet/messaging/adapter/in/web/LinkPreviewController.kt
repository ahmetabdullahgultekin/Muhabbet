package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.LinkPreviewResponse
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.security.SsrfGuard
import com.muhabbet.shared.web.ApiResponseBuilder
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
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

    private companion object {
        const val TIMEOUT_MS = 5000
        const val MAX_BODY_BYTES = 1_000_000 // 1 MB — cap the fetched body to bound memory use
        const val MAX_REDIRECTS = 5
        const val USER_AGENT = "MuhabbetBot/1.0"
        const val TITLE_MAX = 200
        const val DESCRIPTION_MAX = 500
    }

    @GetMapping
    fun getLinkPreview(@RequestParam url: String): ResponseEntity<ApiResponse<LinkPreviewResponse>> {
        AuthenticatedUser.currentUserId() // Ensure authenticated

        // SSRF guard: validate the URL (and every redirect hop) before any outbound fetch. If the
        // URL targets an internal/private/loopback/link-local address we never connect — we just
        // return an empty preview (same graceful behaviour as a failed fetch) so the endpoint cannot
        // be used to probe internal services.
        val doc = try {
            fetchSafely(url)
        } catch (e: SsrfGuard.BlockedUrlException) {
            log.warn("Blocked SSRF link-preview attempt for {}: {}", url, e.message)
            return ApiResponseBuilder.ok(LinkPreviewResponse(url = url))
        } catch (e: Exception) {
            log.debug("Failed to fetch link preview for {}: {}", url, e.message)
            return ApiResponseBuilder.ok(LinkPreviewResponse(url = url))
        }

        val title = doc.selectFirst("meta[property=og:title]")?.attr("content")
            ?: doc.title()
        val description = doc.selectFirst("meta[property=og:description]")?.attr("content")
            ?: doc.selectFirst("meta[name=description]")?.attr("content")
        val imageUrl = doc.selectFirst("meta[property=og:image]")?.attr("content")
        val siteName = doc.selectFirst("meta[property=og:site_name]")?.attr("content")

        return ApiResponseBuilder.ok(
            LinkPreviewResponse(
                url = url,
                title = title?.take(TITLE_MAX),
                description = description?.take(DESCRIPTION_MAX),
                imageUrl = imageUrl,
                siteName = siteName
            )
        )
    }

    /**
     * Fetches the document with SSRF protection: each URL (including every redirect target) is
     * re-validated by [SsrfGuard], redirects are followed manually (Jsoup auto-redirect disabled),
     * the redirect chain is capped, and the response body is size-limited.
     */
    private fun fetchSafely(initialUrl: String): Document {
        var current = initialUrl
        repeat(MAX_REDIRECTS + 1) {
            SsrfGuard.assertSafe(current) // re-validates the host of THIS hop, blocks 30x-to-internal

            val response = Jsoup.connect(current)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .maxBodySize(MAX_BODY_BYTES)
                .followRedirects(false)
                .ignoreHttpErrors(true)
                .execute()

            if (response.statusCode() in 300..399) {
                val location = response.header("Location")
                    ?: throw SsrfGuard.BlockedUrlException("REDIRECT_WITHOUT_LOCATION")
                // Resolve relative redirects against the current URL.
                current = java.net.URI(current).resolve(location).toString()
                return@repeat
            }

            return response.parse()
        }
        throw SsrfGuard.BlockedUrlException("TOO_MANY_REDIRECTS")
    }
}
