package com.muhabbet.shared.exception

import com.muhabbet.messaging.adapter.`in`.web.CommunityController
import com.muhabbet.messaging.adapter.`in`.web.SearchController
import com.muhabbet.messaging.domain.port.`in`.GetMessageHistoryUseCase
import com.muhabbet.messaging.domain.port.`in`.ManageCommunityUseCase
import com.muhabbet.messaging.domain.port.`in`.SearchMessagesUseCase
import com.muhabbet.shared.TestData
import com.muhabbet.shared.security.JwtAuthFilter
import com.muhabbet.shared.security.JwtClaims
import com.muhabbet.shared.security.RateLimitFilter
import io.mockk.every
import io.mockk.mockk
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
    controllers = [CommunityController::class, SearchController::class],
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
    }

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var manageCommunityUseCase: ManageCommunityUseCase

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
}
