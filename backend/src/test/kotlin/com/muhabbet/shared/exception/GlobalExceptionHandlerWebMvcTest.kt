package com.muhabbet.shared.exception

import com.muhabbet.messaging.adapter.`in`.web.ChannelAnalyticsController
import com.muhabbet.messaging.adapter.`in`.web.CommunityController
import com.muhabbet.messaging.adapter.`in`.web.SearchController
import com.muhabbet.messaging.domain.port.`in`.ChannelAnalyticsSummary
import com.muhabbet.messaging.domain.port.`in`.GetMessageHistoryUseCase
import com.muhabbet.messaging.domain.port.`in`.ManageChannelAnalyticsUseCase
import com.muhabbet.messaging.domain.port.`in`.ManageCommunityUseCase
import com.muhabbet.messaging.domain.port.`in`.SearchMessagesUseCase
import com.muhabbet.shared.TestData
import com.muhabbet.shared.security.JwtAuthFilter
import com.muhabbet.shared.security.JwtClaims
import com.muhabbet.shared.security.RateLimitFilter
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.util.UUID

/**
 * Drives real HTTP through the real `DispatcherServlet` and the real advice — the only way to see
 * what #472 was about. The bug was invisible to the plain controller unit tests in this repo
 * because it never happens *inside* a controller: Spring rejects the request before any controller
 * method is called, and `GlobalExceptionHandler`'s `@ExceptionHandler(Exception::class)` then
 * intercepted that rejection ahead of Spring's own `DefaultHandlerExceptionResolver` and answered
 * 500 for all of it.
 *
 * A slice test rather than `@SpringBootTest`: these assertions need no database, no Redis and no
 * Docker, and the whole point is that they stay runnable on a laptop.
 */
@WebMvcTest(
    controllers = [CommunityController::class, SearchController::class, ChannelAnalyticsController::class],
    // A @WebMvcTest slice pulls in every `Filter` bean, and these two drag the whole JWT and
    // rate-limit chain in behind them. The filters are switched off for these requests anyway
    // (`addFilters = false`) — what is under test happens inside the DispatcherServlet, after any
    // filter would have run.
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [JwtAuthFilter::class, RateLimitFilter::class]
        )
    ]
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandlerWebMvcTest.Mocks::class)
class GlobalExceptionHandlerWebMvcTest {

    @TestConfiguration
    class Mocks {
        @Bean fun manageCommunityUseCase(): ManageCommunityUseCase = mockk()
        @Bean fun searchMessagesUseCase(): SearchMessagesUseCase = mockk()
        @Bean fun getMessageHistoryUseCase(): GetMessageHistoryUseCase = mockk()
        @Bean fun manageChannelAnalyticsUseCase(): ManageChannelAnalyticsUseCase = mockk()
    }

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var manageCommunityUseCase: ManageCommunityUseCase
    @Autowired private lateinit var channelAnalyticsUseCase: ManageChannelAnalyticsUseCase

    private val communityId = UUID.randomUUID()

    @BeforeEach
    fun authenticate() {
        // The security filter chain is off, so the controller's AuthenticatedUser lookup has to be
        // satisfied directly. Without it every request would fail on AUTH_UNAUTHORIZED before
        // reaching the behaviour under test.
        val claims = JwtClaims(userId = TestData.USER_ID_1, deviceId = TestData.DEVICE_ID_1)
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(claims, null, emptyList())
    }

    @AfterEach
    fun clearAuthentication() = SecurityContextHolder.clearContext()

    @Nested
    inner class WrongVerb {

        /**
         * `/api/v1/communities/{id}` answers GET, PATCH and DELETE; PUT is mapped nowhere on this
         * controller. Production answered the issue's original request — a DELETE, before #447
         * added that verb — with 500 "Beklenmeyen bir hata olustu", logged at ERROR as
         * "Unexpected error".
         *
         * The verb under test deliberately is NOT the one from the issue. It was DELETE until #447
         * shipped `@DeleteMapping("/{communityId}")` and turned this test's "unmapped" example into
         * a mapped route, so it started calling the controller and failing on an unstubbed mock.
         * Both changes were correct alone and only collided once merged. Pick a verb this
         * controller has no reason to ever map, and re-check that when adding one.
         */
        @Test
        fun `should answer 405 in the error envelope when the verb is not mapped`() {
            mockMvc.perform(put("/api/v1/communities/$communityId"))
                .andExpect(status().isMethodNotAllowed)
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.error.message").isNotEmpty)
                .andExpect(jsonPath("$.timestamp").isNotEmpty)
                .andExpect(jsonPath("$.data").doesNotExist())
        }

        /** RFC 9110 §15.5.6: a 405 must say which verbs the address does accept. */
        @Test
        fun `should name the verbs the address accepts`() {
            mockMvc.perform(put("/api/v1/communities/$communityId"))
                .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("GET")))
                .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("PATCH")))
        }

        @Test
        fun `should not reach the use case at all`() {
            // Nothing is stubbed on the mock, so any call would fail the test with a MockK error.
            mockMvc.perform(post("/api/v1/communities/$communityId"))
                .andExpect(status().isMethodNotAllowed)
        }
    }

    @Nested
    inner class BadRequests {

        /**
         * `q` has no default, so omitting it raises `MissingServletRequestParameterException`.
         * Three live endpoints depend on a required query parameter — this one, `/search/messages`,
         * plus `/messages/since?timestamp=` and `/link-preview?url=`.
         */
        @Test
        fun `should answer 400 when a required query parameter is missing`() {
            mockMvc.perform(get("/api/v1/search/messages"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty)
        }

        /** A body Jackson cannot read is the caller's mistake, not a server fault. */
        @Test
        fun `should answer 400 when the request body is not readable`() {
            mockMvc.perform(
                post("/api/v1/communities")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{ this is not json")
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        }
    }

    /**
     * #401 again, one layer out from the request body it was reported against. The body half landed
     * in #410: a payload the deserializer cannot read is now a 400 carrying the decoder's message.
     * A **query parameter or path variable** whose text will not convert was still answered with
     * 500 `INTERNAL_ERROR` and an ERROR-level stack trace, for the identical reason and with the
     * identical consequences — the caller is told the server broke and invited to retry something
     * that cannot succeed, and anyone can fill the production log with stack traces by sending junk.
     *
     * `/channels/{id}/analytics` was the only endpoint that could still reach it, because it was
     * the only one that took a date as a `String` and called `LocalDate.parse` itself.
     * `DateTimeParseException` extends `DateTimeException`, **not** `IllegalArgumentException`, so
     * it missed [GlobalExceptionHandler.handleBadRequest] entirely and landed on
     * [GlobalExceptionHandler.handleUnexpected]. A malformed UUID on that same request was a 400
     * and a malformed date beside it was a 500 — an asymmetry, not a considered distinction.
     */
    @Nested
    inner class MalformedRequestValues {

        private val channelId = UUID.randomUUID()

        @Test
        fun `should answer 400 when a date query parameter is not a date`() {
            mockMvc.perform(get("/api/v1/channels/$channelId/analytics?startDate=not-a-date"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty)
                .andExpect(jsonPath("$.data").doesNotExist())
        }

        /**
         * The status alone does not answer #401. Its third complaint was that `INTERNAL_ERROR`
         * "gave no hint that the field was named wrong", and a 400 whose body says only
         * "Dogrulama hatasi" repeats that failure one status code down. The parameter's name and
         * the type it needed are both facts about our own mapping, so naming them leaks nothing;
         * the offending value is deliberately not echoed back.
         */
        @Test
        fun `should name the parameter that could not be converted`() {
            mockMvc.perform(get("/api/v1/channels/$channelId/analytics?endDate=yarin"))
                .andExpect(status().isBadRequest)
                .andExpect(
                    jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("endDate"))
                )
                .andExpect(
                    jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("LocalDate"))
                )
        }

        /** A request Spring cannot bind must never reach the domain. */
        @Test
        fun `should not reach the use case when a parameter will not convert`() {
            // Nothing is stubbed on the mock, so a call would fail this test with a MockK error.
            mockMvc.perform(get("/api/v1/channels/$channelId/analytics?startDate=dun"))
                .andExpect(status().isBadRequest)

            verify(exactly = 0) { channelAnalyticsUseCase.getAnalytics(any(), any(), any(), any()) }
        }

        /** The path variable was already a 400, by way of `UUID.fromString`. It must stay one. */
        @Test
        fun `should answer 400 when the channel id in the path is not a UUID`() {
            mockMvc.perform(get("/api/v1/channels/not-a-uuid/analytics"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
        }

        /**
         * The counterweight to the four above. Moving the parsing out of the controller and into
         * Spring's binder is only safe if the binder accepts exactly what `LocalDate.parse`
         * accepted, so the wire format is pinned here rather than assumed — and the values must
         * still arrive at the use case, converted, rather than being rejected wholesale.
         */
        @Test
        fun `should pass well-formed ISO dates through to the use case`() {
            every {
                channelAnalyticsUseCase.getAnalytics(
                    channelId,
                    TestData.USER_ID_1,
                    LocalDate.of(2026, 1, 5),
                    LocalDate.of(2026, 2, 6)
                )
            } returns ChannelAnalyticsSummary(
                channelId = channelId.toString(),
                totalSubscribers = 0,
                dailyStats = emptyList()
            )

            mockMvc.perform(
                get("/api/v1/channels/$channelId/analytics?startDate=2026-01-05&endDate=2026-02-06")
            ).andExpect(status().isOk)

            verify(exactly = 1) {
                channelAnalyticsUseCase.getAnalytics(
                    channelId,
                    TestData.USER_ID_1,
                    LocalDate.of(2026, 1, 5),
                    LocalDate.of(2026, 2, 6)
                )
            }
        }
    }

    @Nested
    inner class WrongContentType {

        @Test
        fun `should answer 415 when the Content-Type is one the endpoint cannot read`() {
            mockMvc.perform(
                post("/api/v1/communities")
                    .contentType(MediaType.TEXT_PLAIN)
                    .content("Mahalle")
            )
                .andExpect(status().isUnsupportedMediaType)
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_CONTENT_TYPE"))
        }
    }

    @Nested
    inner class UnknownAddress {

        /**
         * Every typo'd URL was a 500 too, for the same reason and with more volume: with
         * `spring.web.resources.add-mappings` at its default the static-resource handler claims
         * the catch-all path and raises `NoResourceFoundException`, which the generic arm ate. Any
         * scanner walking the host was writing ERROR "Unexpected error" lines.
         */
        @Test
        fun `should answer 404 in the error envelope for an address that maps to nothing`() {
            mockMvc.perform(get("/api/v1/there-is-no-such-thing"))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("ENDPOINT_NOT_FOUND"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty)
        }
    }

    @Nested
    inner class GenuinelyUnexpected {

        /**
         * The counterweight to everything above: a fault that really is the server's must still be
         * a 500 with `INTERNAL_ERROR`. Narrowing the generic arm must not have narrowed it away.
         */
        @Test
        fun `should still answer 500 when the failure is the server's own`() {
            every { manageCommunityUseCase.listForUser(any()) } throws IllegalStateException("boom")

            mockMvc.perform(get("/api/v1/communities"))
                .andExpect(status().isInternalServerError)
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
        }
    }

    @Nested
    inner class BusinessErrors {

        /**
         * #446: a second community under a name its creator already used is a conflict, and the
         * create dialog can only show that against the name field if both the status and the code
         * arrive intact. The status lives on the `ErrorCode` entry rather than at the throw site, so
         * an entry given the wrong one would surface here rather than on somebody's phone.
         */
        @Test
        fun `should answer 409 with the name conflict code`() {
            every { manageCommunityUseCase.create("Muhabbet", null, TestData.USER_ID_1) } throws
                BusinessException(ErrorCode.COMMUNITY_NAME_ALREADY_EXISTS)

            mockMvc.perform(
                post("/api/v1/communities")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"Muhabbet"}""")
            )
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.error.code").value("COMMUNITY_NAME_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.error.message").isNotEmpty)
                .andExpect(jsonPath("$.data").doesNotExist())
        }
    }
}
