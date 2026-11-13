package com.muhabbet.auth.adapter.`in`.web

import com.muhabbet.auth.domain.model.DevicePlatform
import com.muhabbet.auth.domain.model.UserStatus
import com.muhabbet.auth.domain.port.`in`.AuthResult
import com.muhabbet.auth.domain.port.`in`.LinkDeviceUseCase
import com.muhabbet.auth.domain.port.`in`.LogoutUseCase
import com.muhabbet.auth.domain.port.`in`.RefreshTokenUseCase
import com.muhabbet.auth.domain.port.`in`.RequestOtpUseCase
import com.muhabbet.auth.domain.port.`in`.VerifyOtpUseCase
import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.auth.domain.port.out.OtpRepository
import com.muhabbet.auth.domain.port.out.OtpSender
import com.muhabbet.auth.domain.port.out.RefreshTokenRecord
import com.muhabbet.auth.domain.port.out.RefreshTokenRepository
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.auth.domain.service.AuthService
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.security.JwtProvider
import com.redis.testcontainers.RedisContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The auth session model against a real PostgreSQL and Redis (#880, #923, #882, #886): the parts a
 * mock cannot show — row locks deciding a race, a commit surviving a refusal, a transaction that is
 * or is not open while the SMS provider is called, and the REST filter consulting the database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(AuthSessionHardeningIntegrationTest.RecordingSmsProvider::class)
class AuthSessionHardeningIntegrationTest {

    /**
     * Stands in for the SMS provider: records each call, whether a transaction was open during it,
     * and is slow enough that a parallel request would overlap it. Fails for [FAILING_PHONE].
     */
    @TestConfiguration
    class RecordingSmsProvider {
        @Bean
        @Primary
        fun recordingOtpSender(): OtpSender = object : OtpSender {
            override fun send(phoneNumber: String, otp: String) {
                AuthSessionHardeningIntegrationTest.calls += SmsCall(phoneNumber, TransactionSynchronizationManager.isActualTransactionActive())
                Thread.sleep(AuthSessionHardeningIntegrationTest.PROVIDER_LATENCY_MS)
                check(phoneNumber != AuthSessionHardeningIntegrationTest.FAILING_PHONE) { "provider down" }
            }
        }
    }

    data class SmsCall(val phone: String, val insideTransaction: Boolean)

    companion object {
        const val PROVIDER_LATENCY_MS = 300L
        const val FAILING_PHONE = "+905009999999"
        val calls = ConcurrentLinkedQueue<SmsCall>()

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

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var requestOtp: RequestOtpUseCase
    @Autowired private lateinit var verifyOtp: VerifyOtpUseCase
    @Autowired private lateinit var refresh: RefreshTokenUseCase
    @Autowired private lateinit var logout: LogoutUseCase
    @Autowired private lateinit var linkDevice: LinkDeviceUseCase
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var deviceRepository: DeviceRepository
    @Autowired private lateinit var refreshTokenRepository: RefreshTokenRepository
    @Autowired private lateinit var otpRepository: OtpRepository
    @Autowired private lateinit var jwtProvider: JwtProvider
    @Autowired private lateinit var jdbc: JdbcTemplate

    private fun uniquePhone() = "+90500" + (1_000_000..8_999_999).random()

    /** A full sign-in through the real services; [phone] may be signed in again from a new install. */
    private fun signIn(phone: String = uniquePhone(), installationId: UUID = UUID.randomUUID()): AuthResult {
        // A second sign-in for the same number inside the cooldown is a test artefact, not a case.
        jdbc.update("UPDATE otp_requests SET created_at = created_at - interval '2 minutes' WHERE phone_number = ?", phone)
        val code = requestOtp.requestOtp(phone).mockCode ?: error("mock-enabled is on in this test")
        return verifyOtp.verifyOtp(phone, code, "Pixel", "android", installationId = installationId.toString())
    }

    private fun meStatus(accessToken: String): Int =
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer $accessToken"))
            .andReturn().response.status

    private fun liveTokensOfDevice(deviceId: String): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM refresh_tokens WHERE device_id = ?::uuid AND revoked_at IS NULL",
            Int::class.java, deviceId
        ) ?: 0

    // ─── #880: rotation, reuse, revocation ───────────────

    @Test
    fun `should let exactly one of two concurrent refreshes of one token succeed`() {
        val session = signIn()
        val pool = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        val outcomes = (1..2).map {
            pool.submit<Result<String>> {
                start.await()
                runCatching { refresh.refresh(session.refreshToken).refreshToken }
            }
        }
        start.countDown()
        val results = outcomes.map { it.get(30, TimeUnit.SECONDS) }
        pool.shutdown()

        assertEquals(1, results.count { it.isSuccess }, "both racing refreshes minted a chain")
        val refusal = results.single { it.isFailure }.exceptionOrNull() as? BusinessException
        assertEquals(ErrorCode.AUTH_TOKEN_REVOKED, refusal?.errorCode)
        // The race is reuse: the whole family is gone, the winner's successor included.
        assertEquals(0, liveTokensOfDevice(session.deviceId))
    }

    @Test
    fun `should revoke the whole family when a rotated token is replayed`() {
        val session = signIn()
        val successor = refresh.refresh(session.refreshToken)

        val replay = assertThrows<BusinessException> { refresh.refresh(session.refreshToken) }
        assertEquals(ErrorCode.AUTH_TOKEN_REVOKED, replay.errorCode)

        val afterReuse = assertThrows<BusinessException> { refresh.refresh(successor.refreshToken) }
        assertEquals(ErrorCode.AUTH_TOKEN_REVOKED, afterReuse.errorCode)
        // The reuse response also ended the device's access tokens — committed despite the refusal.
        assertEquals(401, meStatus(successor.accessToken))
    }

    @Test
    fun `should refuse refresh and REST after logout`() {
        val session = signIn()
        assertEquals(200, meStatus(session.accessToken))

        logout.logout(UUID.fromString(session.userId), UUID.fromString(session.deviceId))

        assertEquals(401, meStatus(session.accessToken), "a logged-out access token still worked on REST")
        val ex = assertThrows<BusinessException> { refresh.refresh(session.refreshToken) }
        assertEquals(ErrorCode.AUTH_TOKEN_REVOKED, ex.errorCode)
    }

    @Test
    fun `should refuse refresh and REST for a suspended account`() {
        val session = signIn()
        val user = userRepository.findById(UUID.fromString(session.userId)) ?: error("signed in")
        userRepository.save(user.copy(status = UserStatus.SUSPENDED))

        assertEquals(401, meStatus(session.accessToken))
        val ex = assertThrows<BusinessException> { refresh.refresh(session.refreshToken) }
        assertEquals(ErrorCode.AUTH_TOKEN_REVOKED, ex.errorCode)
    }

    // ─── #923: REST revocation and live privilege ────────

    @Test
    fun `should answer 401 on REST to a revoked companion's earlier access token, and refuse its refresh`() {
        val primary = signIn()
        val userId = UUID.fromString(primary.userId)
        val link = linkDevice.beginLink(userId, UUID.fromString(primary.deviceId))
        val companion = linkDevice.completeLink(link.linkToken, "web", "Chrome", null)
        val companionAccess = jwtProvider.generateAccessToken(userId, companion.id, companion.tokenVersion)
        val companionRefresh = jwtProvider.generateRefreshToken()
        refreshTokenRepository.save(
            RefreshTokenRecord(
                userId = userId, deviceId = companion.id,
                tokenHash = AuthService.sha256(companionRefresh), expiresAt = Instant.now().plusSeconds(3600)
            )
        )
        assertEquals(200, meStatus(companionAccess))

        linkDevice.revokeDevice(userId, companion.id)

        assertEquals(401, meStatus(companionAccess), "a revoked device kept REST access")
        val ex = assertThrows<BusinessException> { refresh.refresh(companionRefresh) }
        assertEquals(ErrorCode.AUTH_TOKEN_REVOKED, ex.errorCode)
        assertEquals(200, meStatus(primary.accessToken), "revoking the companion ended the primary's session")
    }

    @Test
    fun `should apply an admin demotion on the very next request`() {
        val session = signIn()
        val user = userRepository.findById(UUID.fromString(session.userId)) ?: error("signed in")
        userRepository.save(user.copy(isAdmin = true))
        val pending = get("/api/v1/moderation/reports/pending").header("Authorization", "Bearer ${session.accessToken}")
        mockMvc.perform(pending).andExpect(status().isOk)

        userRepository.save(user.copy(isAdmin = false))

        mockMvc.perform(get("/api/v1/moderation/reports/pending").header("Authorization", "Bearer ${session.accessToken}"))
            .andExpect(status().isUnauthorized)
    }

    // ─── #882: device identity ───────────────────────────

    @Test
    fun `should keep two phones of one account as two devices with independent sessions`() {
        val phone = uniquePhone()
        val phoneA = signIn(phone, UUID.randomUUID())
        val phoneB = signIn(phone, UUID.randomUUID())
        assertEquals(phoneA.userId, phoneB.userId)
        assertNotEquals(phoneA.deviceId, phoneB.deviceId, "the second phone took over the first one's device")

        logout.logout(UUID.fromString(phoneA.userId), UUID.fromString(phoneA.deviceId))

        // Phone B is untouched by phone A signing out.
        val rotated = refresh.refresh(phoneB.refreshToken)
        assertEquals(phoneB.deviceId, rotated.deviceId)
        assertEquals(200, meStatus(rotated.accessToken))
    }

    @Test
    fun `should sign a primary in again beside a same-platform companion`() {
        // With the single-result (user, platform) lookup this was a 500 on every later sign-in.
        val phone = uniquePhone()
        val install = UUID.randomUUID()
        val primary = signIn(phone, install)
        val userId = UUID.fromString(primary.userId)
        val link = linkDevice.beginLink(userId, UUID.fromString(primary.deviceId))
        linkDevice.completeLink(link.linkToken, "android", "Tablet", null)

        val again = signIn(phone, install)

        assertEquals(primary.deviceId, again.deviceId)
    }

    @Test
    fun `should not reuse a revoked device row when its installation signs in again`() {
        val phone = uniquePhone()
        val install = UUID.randomUUID()
        val first = signIn(phone, install)
        val device = deviceRepository.findById(UUID.fromString(first.deviceId)) ?: error("signed in")
        deviceRepository.save(device.copy(revokedAt = Instant.now()))

        val again = signIn(phone, install)

        assertNotEquals(first.deviceId, again.deviceId)
        assertEquals(DevicePlatform.ANDROID, deviceRepository.findById(UUID.fromString(again.deviceId))?.platform)
    }

    @Test
    fun `should enforce one active row per installation and the platform set in the schema itself`() {
        // V28/V29: the invariants hold for every writer, not only for the service that knows them.
        val session = signIn()
        val install = deviceRepository.findById(UUID.fromString(session.deviceId))?.installationId
        val insert = "INSERT INTO devices (id, user_id, platform, installation_id, created_at, is_primary) " +
            "VALUES (gen_random_uuid(), ?::uuid, ?, ?, now(), false)"

        assertThrows<org.springframework.dao.DataIntegrityViolationException> {
            jdbc.update(insert, session.userId, "android", install)
        }
        assertThrows<org.springframework.dao.DataIntegrityViolationException> {
            jdbc.update(insert, session.userId, "symbian", UUID.randomUUID())
        }
        // Once the first row is revoked, the same installation may have a new active row.
        jdbc.update("UPDATE devices SET revoked_at = now() WHERE id = ?::uuid", session.deviceId)
        assertEquals(1, jdbc.update(insert, session.userId, "android", install))
    }

    @Test
    fun `should reject an unknown platform with VALIDATION_ERROR`() {
        val phone = uniquePhone()
        val code = requestOtp.requestOtp(phone).mockCode
        mockMvc.perform(
            post("/api/v1/auth/otp/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"phoneNumber":"$phone","otp":"$code","deviceName":"X","platform":"symbian"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
    }

    // ─── #886: provider I/O outside transactions ─────────

    @Test
    fun `should send exactly one SMS for parallel requests and never inside a transaction`() {
        val phone = uniquePhone()
        val pool = Executors.newFixedThreadPool(4)
        val start = CountDownLatch(1)
        val outcomes = (1..4).map {
            pool.submit<Result<Unit>> {
                start.await()
                runCatching { requestOtp.requestOtp(phone); Unit }
            }
        }
        start.countDown()
        val results = outcomes.map { it.get(30, TimeUnit.SECONDS) }
        pool.shutdown()

        val forThisPhone = calls.filter { it.phone == phone }
        assertEquals(1, forThisPhone.size, "parallel taps each sent an SMS")
        assertTrue(forThisPhone.none { it.insideTransaction }, "the provider was called inside a transaction")
        assertEquals(1, results.count { it.isSuccess })
        assertTrue(
            results.filter { it.isFailure }
                .all { (it.exceptionOrNull() as? BusinessException)?.errorCode == ErrorCode.AUTH_OTP_COOLDOWN }
        )
    }

    @Test
    fun `should leave no active OTP and no cooldown when the provider fails`() {
        assertThrows<IllegalStateException> { requestOtp.requestOtp(FAILING_PHONE) }

        assertNull(otpRepository.findActiveByPhoneNumber(FAILING_PHONE), "an undelivered code stayed verifiable")
        // Not cooling down: the code never arrived, so the user may ask again at once.
        assertThrows<IllegalStateException> { requestOtp.requestOtp(FAILING_PHONE) }
    }
}
