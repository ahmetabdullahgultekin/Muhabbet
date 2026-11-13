package com.muhabbet.shared.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Where a group invite link points.
 *
 * The token on its own is not shareable, and `InviteLinkResponse.inviteUrl` is what the invite
 * sheet copies to the clipboard and hands to the system share sheet — so the server has to build
 * it. There is nowhere else the host could come from.
 *
 * The default is the project's own site, which answers `/invite/<token>` with a page telling the
 * recipient to open the app. The host must be one this project controls: the token is in the
 * path, so whoever serves the host sees every invite anyone shares. It is the same host the
 * Android manifest claims for `/chat` app links and that `docs/site/.well-known/assetlinks.json`
 * verifies. Overridable by environment because it changes the day the real domain lands, which is
 * a deployment fact rather than a code one.
 */
@ConfigurationProperties("muhabbet.invite")
data class InviteLinkProperties(
    val baseUrl: String = DEFAULT_BASE_URL
) {
    /** Trailing slash is trimmed here so no caller has to think about doubling it. */
    fun urlFor(token: String): String = "${baseUrl.trimEnd('/')}/$token"

    companion object {
        const val DEFAULT_BASE_URL = "https://muhabbet.rollingcatsoftware.com/invite"
    }
}
