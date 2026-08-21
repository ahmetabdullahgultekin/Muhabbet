package com.muhabbet.shared.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Where a group invite link points.
 *
 * The token on its own is not shareable, and `InviteLinkResponse.inviteUrl` is what the invite
 * sheet copies to the clipboard and hands to the system share sheet — so the server has to build
 * it. There is nowhere else the host could come from.
 *
 * The default is the app link the Android manifest already claims
 * (`scheme="https" host="muhabbet.app" pathPrefix="/invite"`), so a phone with Muhabbet installed
 * opens the link in the app rather than a browser. It is overridable by environment because it
 * changes the day the real domain lands, which is a deployment fact rather than a code one.
 */
@ConfigurationProperties("muhabbet.invite")
data class InviteLinkProperties(
    val baseUrl: String = "https://muhabbet.app/invite"
) {
    /** Trailing slash is trimmed here so no caller has to think about doubling it. */
    fun urlFor(token: String): String = "${baseUrl.trimEnd('/')}/$token"
}
