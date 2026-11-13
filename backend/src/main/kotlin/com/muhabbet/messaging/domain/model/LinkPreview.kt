package com.muhabbet.messaging.domain.model

/**
 * What a page says about itself (Open Graph / `<title>` / meta description), already trimmed to
 * display length. A preview with every field null is a real answer — "nothing to show" — and is
 * cached as such, so a page that cannot be fetched is not fetched again on every render.
 */
data class LinkPreview(
    val url: String,
    val title: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val siteName: String? = null
) {
    val isEmpty: Boolean
        get() = title == null && description == null && imageUrl == null && siteName == null
}
