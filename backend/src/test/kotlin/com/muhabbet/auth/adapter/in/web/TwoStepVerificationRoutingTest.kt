package com.muhabbet.auth.adapter.`in`.web

import com.muhabbet.auth.domain.model.TwoStepStatus
import com.muhabbet.auth.domain.port.`in`.TwoStepVerificationUseCase
import com.muhabbet.shared.TestData
import com.muhabbet.shared.config.JsonConfig
import com.muhabbet.shared.security.JwtAuthFilter
import com.muhabbet.shared.security.JwtClaims
import com.muhabbet.shared.security.RateLimitFilter
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * The addresses the two-step screen calls, driven through the real `DispatcherServlet`.
 *
 * #544 could not have been caught by a controller unit test, and this repo's other controller tests
 * are exactly that — they invoke the method directly, so the `@RequestMapping` that decides whether
 * the app's own request even reaches it is never exercised. The screen posted the setup body to
 * `/api/v1/auth/two-step`, which was mapped for `DELETE` alone; Spring answered **405** before any
 * controller code ran, and the phone showed "bir hata oluştu".
 *
 * So every test here starts from a URL and a verb, spelled out as literals rather than built from
 * the controller's own constant — a test that reuses the constant agrees with whatever the
 * controller says and can never disagree with the client. These literals must match
 * `TwoStepRepository.BASE_PATH` and its two siblings on the mobile side.
 *
 * A slice test: no database, no Redis, no Docker.
 */
@WebMvcTest(
    controllers = [TwoStepVerificationController::class],
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [JwtAuthFilter::class, RateLimitFilter::class]
        )
    ]
)
@AutoConfigureMockMvc(addFilters = false)
// `JsonConfig` is what makes the response body match the contract: without its `encodeDefaults`,
// kotlinx drops any field equal to its declared default, so `hasEmail: false` would be **absent**
// rather than false — the #269 shape. A slice that left it out would assert a payload production
// never sends.
@Import(TwoStepVerificationRoutingTest.Mocks::class, JsonConfig::class)
class TwoStepVerificationRoutingTest {

    @TestConfiguration
    class Mocks {
        @Bean fun twoStepVerificationUseCase(): TwoStepVerificationUseCase = mockk()
    }

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var twoStepVerificationUseCase: TwoStepVerificationUseCase

    private val userId = TestData.USER_ID_1

    @BeforeEach
    fun authenticate() {
        // The filter chain is off, so AuthenticatedUser has to be satisfied directly.
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            JwtClaims(userId = userId, deviceId = TestData.DEVICE_ID_1),
            null,
            emptyList()
        )
    }

    @AfterEach
    fun clearAuthentication() = SecurityContextHolder.clearContext()

    @Test
    fun `should accept the setup body at the address the app posts it to`() {
        every { twoStepVerificationUseCase.setupPin(userId, "123456", null) } returns Unit

        mockMvc.perform(
            post("/api/v1/auth/two-step/setup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"pin":"123456","email":null}""")
        ).andExpect(status().isOk)

        verify { twoStepVerificationUseCase.setupPin(userId, "123456", null) }
    }

    @Test
    fun `should carry the recovery email through to the use case`() {
        every { twoStepVerificationUseCase.setupPin(userId, "123456", "kurtarma@example.com") } returns Unit

        mockMvc.perform(
            post("/api/v1/auth/two-step/setup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"pin":"123456","email":"kurtarma@example.com"}""")
        ).andExpect(status().isOk)

        verify { twoStepVerificationUseCase.setupPin(userId, "123456", "kurtarma@example.com") }
    }

    @Test
    fun `should accept the disable body at the address the app posts it to`() {
        // Was a `DELETE` on the bare path carrying a required body. A body on DELETE is the one
        // shape an HTTP intermediary may drop, and the client sent none at all — so this call could
        // only ever have answered 400.
        every { twoStepVerificationUseCase.disablePin(userId, "123456") } returns Unit

        mockMvc.perform(
            post("/api/v1/auth/two-step/disable")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPin":"123456"}""")
        ).andExpect(status().isOk)

        verify { twoStepVerificationUseCase.disablePin(userId, "123456") }
    }

    @Test
    fun `should report both whether two-step is on and whether a recovery email exists`() {
        // `hasEmail` is declared on the shared DTO the client decodes, and the controller's own
        // private copy of that DTO did not have the field — so it decoded to its `false` default
        // whatever the server knew.
        every { twoStepVerificationUseCase.status(userId) } returns
            TwoStepStatus(enabled = true, hasRecoveryEmail = true)

        mockMvc.perform(get("/api/v1/auth/two-step/status"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.enabled").value(true))
            .andExpect(jsonPath("$.data.hasEmail").value(true))
    }

    @Test
    fun `should report two-step off for an account that never set a PIN`() {
        every { twoStepVerificationUseCase.status(userId) } returns
            TwoStepStatus(enabled = false, hasRecoveryEmail = false)

        mockMvc.perform(get("/api/v1/auth/two-step/status"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.enabled").value(false))
            .andExpect(jsonPath("$.data.hasEmail").value(false))
    }

    @Test
    fun `should refuse a setup posted to the bare path, which is what the app used to do`() {
        // Kept as the epitaph for #544: nothing is stubbed on the mock, so if this ever started
        // routing to a handler the test would fail on an unstubbed call rather than pass quietly.
        mockMvc.perform(
            post("/api/v1/auth/two-step")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"pin":"123456","email":null}""")
        ).andExpect(status().is4xxClientError)
    }
}
