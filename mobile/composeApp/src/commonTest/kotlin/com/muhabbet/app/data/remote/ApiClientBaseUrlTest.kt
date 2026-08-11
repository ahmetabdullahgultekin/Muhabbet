package com.muhabbet.app.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the API host the app talks to.
 *
 * The app shipped pointing at `muhabbet.rollingcatsoftware.com` while Traefik only ever routed
 * `muhabbet-api.rollingcatsoftware.com` (see the router rule in the repo-root `docker-compose.prod.yml`).
 * DNS resolved and port 80 redirected to HTTPS, so the mismatch looked like a working host, but no
 * certificate was ever issued for it and the TLS handshake was rejected. Login could not work from
 * any build carrying that value, and nothing in the suite noticed.
 *
 * The rule lives in the **repo-root** `docker-compose.prod.yml`. `infra/docker-compose.prod.yml` is
 * the legacy nginx stack and carries no Traefik labels, so editing that one changes nothing about
 * routing. If the deployed host changes, change the root compose first, then here — the router rule
 * is the source of truth, this test is the tripwire.
 */
class ApiClientBaseUrlTest {

    @Test
    fun baseUrl_matchesTheHostTraefikRoutes() {
        assertEquals(
            "https://muhabbet-api.rollingcatsoftware.com",
            ApiClient.BASE_URL,
            "BASE_URL must match the Host() rule in the repo-root docker-compose.prod.yml",
        )
    }

    @Test
    fun baseUrl_isHttpsAndHasNoTrailingSlash() {
        assertTrue(ApiClient.BASE_URL.startsWith("https://"), "API traffic must not fall back to cleartext")
        assertFalse(ApiClient.BASE_URL.endsWith("/"), "Callers append paths that already start with '/'")
    }
}
