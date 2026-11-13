package com.muhabbet.messaging.adapter.out.external

import com.muhabbet.messaging.domain.model.LinkPreview
import org.jsoup.nodes.Document
import java.net.URI

/**
 * Reads preview metadata out of an already-fetched, already-size-capped HTML document.
 *
 * `og:image` is resolved against the page's own URL (the final one, after redirects) and kept only
 * when it is an absolute http(s) URL: a relative path is useless to the phone that renders it, and a
 * `javascript:` or `data:` value is not an image anyone should be handed.
 */
internal object OpenGraphParser {

    private const val TITLE_MAX = 200
    private const val DESCRIPTION_MAX = 500
    private const val SITE_NAME_MAX = 100
    private const val IMAGE_URL_MAX = 2048
    private val IMAGE_SCHEMES = setOf("http", "https")

    fun parse(requestedUrl: String, document: Document): LinkPreview {
        val title = meta(document, "meta[property=og:title]") ?: document.title().ifBlank { null }
        val description = meta(document, "meta[property=og:description]")
            ?: meta(document, "meta[name=description]")
        val siteName = meta(document, "meta[property=og:site_name]")

        return LinkPreview(
            url = requestedUrl,
            title = title?.take(TITLE_MAX),
            description = description?.take(DESCRIPTION_MAX),
            imageUrl = imageUrl(document),
            siteName = siteName?.take(SITE_NAME_MAX),
        )
    }

    private fun meta(document: Document, selector: String): String? =
        document.selectFirst(selector)?.attr("content")?.trim()?.ifBlank { null }

    private fun imageUrl(document: Document): String? {
        val absolute = document.selectFirst("meta[property=og:image]")
            ?.absUrl("content")
            ?.takeIf { it.isNotBlank() && it.length <= IMAGE_URL_MAX }
            ?: return null
        val scheme = runCatching { URI(absolute).scheme?.lowercase() }.getOrNull()
        return absolute.takeIf { scheme in IMAGE_SCHEMES }
    }
}
