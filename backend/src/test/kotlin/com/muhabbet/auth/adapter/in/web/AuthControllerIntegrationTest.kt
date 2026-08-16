package com.muhabbet.auth.adapter.`in`.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.muhabbet.shared.dto.RequestOtpRequest
import com.muhabbet.shared.dto.VerifyOtpRequest
import com.redis.testcontainers.RedisContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    private val objectMapper = ObjectMapper()

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine").apply {
            withDatabaseName("muhabbet_test")
            withUsername("muhabbet")
            withPassword("muhabbet_test")
        }

        @Container
        @JvmStatic
        val redis = RedisContainer("redis:7-alpine")

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("muhabbet.otp.mock-enabled") { "true" }
            registry.add("spring.data.redis.host") { redis.redisHost }
            registry.add("spring.data.redis.port") { redis.redisPort }
        }
    }

    @Test
    fun `should request OTP and verify successfully`() {
        val phone = "+905321234567"

        // Request OTP
        mockMvc.perform(
            post("/api/v1/auth/otp/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RequestOtpRequest(phone)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.ttlSeconds").value(300))
            .andExpect(jsonPath("$.data.retryAfterSeconds").value(60))
            .andExpect(jsonPath("$.timestamp").exists())
    }

    @Test
    fun `should return 400 for invalid phone number`() {
        mockMvc.perform(
            post("/api/v1/auth/otp/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RequestOtpRequest("12345")))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_PHONE"))
    }

    @Test
    fun `should return 401 for unauthenticated users me`() {
        mockMvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isUnauthorized)
    }

    /**
     * Guards against the OTP being brute-forceable (#266).
     *
     * The unit-level test for this lives in AuthServiceTest, but it stubs the repository and hands
     * verifyOtp an OtpRequest that already has attempts = 5. That proves the guard reads the counter;
     * it cannot prove the counter ever gets there, because the defect was that the failing
     * transaction rolled the increment back. Only a real database and a real transaction show it.
     */
    @Test
    fun `should lock the OTP out after the configured number of wrong codes`() {
        val phone = "+905000000042"

        val requestBody = mockMvc.perform(
            post("/api/v1/auth/otp/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RequestOtpRequest(phone)))
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val realCode = objectMapper.readTree(requestBody).path("data").path("mockCode").asText()
        val wrongCode = if (realCode == "000000") "111111" else "000000"

        // max-attempts is 5, so guesses 1..5 are merely wrong and the 6th is locked out.
        repeat(5) {
            mockMvc.perform(
                post("/api/v1/auth/otp/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            VerifyOtpRequest(phone, wrongCode, "Test Device", "android")
                        )
                    )
            )
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("AUTH_OTP_INVALID"))
        }

        mockMvc.perform(
            post("/api/v1/auth/otp/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        VerifyOtpRequest(phone, wrongCode, "Test Device", "android")
                    )
                )
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AUTH_OTP_MAX_ATTEMPTS"))
    }

    /**
     * The lockout must survive a correct guess arriving late. Before #266 was fixed the counter was
     * only ever persisted on the success path, so an attacker who eventually guessed right was let in
     * regardless of how many attempts it took.
     */
    @Test
    fun `should refuse the correct code once the attempt limit is spent`() {
        val phone = "+905000000043"

        val requestBody = mockMvc.perform(
            post("/api/v1/auth/otp/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RequestOtpRequest(phone)))
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val realCode = objectMapper.readTree(requestBody).path("data").path("mockCode").asText()
        val wrongCode = if (realCode == "000000") "111111" else "000000"

        repeat(5) {
            mockMvc.perform(
                post("/api/v1/auth/otp/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            VerifyOtpRequest(phone, wrongCode, "Test Device", "android")
                        )
                    )
            )
        }

        mockMvc.perform(
            post("/api/v1/auth/otp/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        VerifyOtpRequest(phone, realCode, "Test Device", "android")
                    )
                )
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AUTH_OTP_MAX_ATTEMPTS"))
    }

    /**
     * The limit has to hold when the guesses arrive together, not just one after another.
     *
     * The two tests above are sequential, and a check-then-increment implementation passes both while
     * being wide open: every concurrent request reads the same under-limit count, every one is granted a
     * guess, and the effective limit becomes the attacker's concurrency. Enforcing the limit inside the
     * UPDATE is what makes this pass, so this is the test that pins the fix rather than the symptom.
     */
    @Test
    fun `should not grant more attempts than the limit when guesses arrive concurrently`() {
        val phone = "+905000000044"
        val attackers = 24

        val requestBody = mockMvc.perform(
            post("/api/v1/auth/otp/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RequestOtpRequest(phone)))
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val realCode = objectMapper.readTree(requestBody).path("data").path("mockCode").asText()
        val wrongCode = if (realCode == "000000") "111111" else "000000"

        val granted = AtomicInteger(0)
        val refused = AtomicInteger(0)
        val other = ConcurrentHashMap<String, AtomicInteger>()
        val startTogether = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(attackers)

        try {
            val done: List<Future<*>> = (1..attackers).map {
                pool.submit(Runnable {
                    startTogether.await()
                    val body = mockMvc.perform(
                        post("/api/v1/auth/otp/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                objectMapper.writeValueAsString(
                                    VerifyOtpRequest(phone, wrongCode, "Test Device", "android")
                                )
                            )
                    ).andReturn().response.contentAsString

                    when (val code = objectMapper.readTree(body).path("error").path("code").asText()) {
                        // The code was compared, so an attempt was spent.
                        "AUTH_OTP_INVALID" -> granted.incrementAndGet()
                        // Turned away before the comparison.
                        "AUTH_OTP_MAX_ATTEMPTS" -> refused.incrementAndGet()
                        // Anything else is neither, and is recorded so it cannot hide a granted
                        // attempt behind an unexamined response.
                        else -> other.computeIfAbsent(code) { AtomicInteger(0) }.incrementAndGet()
                    }
                })
            }
            startTogether.countDown()
            done.forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        // A wrong code that got as far as being compared is a spent attempt. No more than the
        // configured five may get that far, however many arrive at once.
        val breakdown = "granted=${granted.get()} refused=${refused.get()} " +
            "other=${other.mapValues { it.value.get() }}"
        assertTrue(
            granted.get() <= 5,
            "expected at most 5 of $attackers concurrent guesses to be granted — $breakdown"
        )
        // Some requests legitimately land elsewhere: 24 arriving together exceed the auth rate limit,
        // and those never reach the OTP logic at all. What must not happen is a request being counted
        // as neither because it quietly succeeded.
        assertEquals(
            0,
            other.keys.count { it.isEmpty() },
            "a concurrent guess returned no error at all, meaning it was accepted — $breakdown"
        )
    }
}
