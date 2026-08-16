package com.muhabbet.shared.config

import kotlinx.serialization.json.Json
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Without this bean Spring uses `Json.Default`, whose `encodeDefaults` is **false** — so any field
 * whose value happens to equal its declared default is dropped from the response entirely.
 *
 * That is not a formatting preference, it changes the contract. `UserProfile.isOnline` is declared
 * `Boolean = false`, so a user who has hidden their online status produced a body with no
 * `isOnline` key at all rather than `isOnline: false`. "Absent" and "false" say different things:
 * false is a definite answer, absent is no opinion, and a client that treats absent as unknown may
 * fall back to showing something. For a privacy control that is the wrong way round, which is how
 * #269 was found — a test asserting the field is there.
 *
 * The same omission applies to every default-valued field in every response: `isPinned`,
 * `isMuted`, `isArchived`, `isLocked` and `announcementOnly` on a conversation all vanish when
 * false. Kotlin clients decode them back because they declare the same defaults, which is why this
 * survived; anything that is not this codebase sees a different API from the one documented.
 *
 * Responses get slightly larger. That is the cost of the payload matching the contract.
 */
@Configuration
class JsonConfig {

    @Bean
    fun json(): Json = Json {
        encodeDefaults = true
        // A field the server does not know about should not fail the request. Mobile clients update
        // out of step with the backend, so an older server must tolerate a newer client's extra
        // field rather than answering 400 for the whole call.
        ignoreUnknownKeys = true
    }
}
