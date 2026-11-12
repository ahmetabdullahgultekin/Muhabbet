package com.muhabbet.shared.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * An invite link carries a join token in its path, so whoever serves the host it names sees every
 * token anyone shares. The default must therefore be a host this project controls — the same one
 * the Android manifest claims as an app link and the site publishes `assetlinks.json` for.
 */
class InviteLinkPropertiesTest {

    @Test
    fun `should build links on the project's own site when no base url is configured`() {
        assertEquals(
            "https://$OWN_SITE_HOST/invite/abc123",
            InviteLinkProperties().urlFor("abc123")
        )
    }

    @Test
    fun `should default to the project's own site in application yml when the env var is unset`() {
        val yaml = javaClass.getResource("/application.yml")?.readText().orEmpty()

        assertTrue(
            yaml.contains("\${INVITE_LINK_BASE_URL:https://$OWN_SITE_HOST/invite}"),
            "application.yml must default muhabbet.invite.base-url to https://$OWN_SITE_HOST/invite"
        )
    }

    @Test
    fun `should not double the slash when the base url ends with one`() {
        assertEquals(
            "https://$OWN_SITE_HOST/invite/t",
            InviteLinkProperties(baseUrl = "https://$OWN_SITE_HOST/invite/").urlFor("t")
        )
    }

    private companion object {
        const val OWN_SITE_HOST = "muhabbet.rollingcatsoftware.com"
    }
}
