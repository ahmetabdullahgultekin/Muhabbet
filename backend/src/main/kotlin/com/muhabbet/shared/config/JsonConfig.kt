package com.muhabbet.shared.config

import com.muhabbet.shared.protocol.MuhabbetJson
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
 * The bean is the shared [MuhabbetJson] itself, not a configuration of its own (#887). It used to
 * declare one here, and so did the app's REST client, and the two disagreed — the app's was
 * `isLenient`. The REST bodies the server writes are now produced by exactly the configuration the
 * app, the WebSocket and the local cache read them with; `MuhabbetJson` documents each setting.
 */
@Configuration
class JsonConfig {

    @Bean
    fun json(): Json = MuhabbetJson
}
