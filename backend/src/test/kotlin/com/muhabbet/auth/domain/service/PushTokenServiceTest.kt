package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.Device
import com.muhabbet.auth.domain.model.MAX_PUSH_TOKEN_LENGTH
import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant
import java.util.UUID

/**
 * Push registration rules (#908, #469, #159). The cross-account scenario against a real database is
 * in `PushTokenOwnershipIntegrationTest`; these pin the decisions the service makes on its own.
 */
class PushTokenServiceTest {

    private val deviceRepository: DeviceRepository = mockk()
    private val service = PushTokenService(deviceRepository)

    private fun device(locale: String? = null, revokedAt: Instant? = null) = Device(
        userId = UUID.randomUUID(),
        platform = "android",
        locale = locale,
        revokedAt = revokedAt
    )

    /** Registers [token] on [existing] and returns the row that was written. */
    private fun register(existing: Device, token: String = "fcm-token", locale: String?): Device {
        every { deviceRepository.findById(existing.id) } returns existing
        every { deviceRepository.detachPushTokenFromOtherDevices(any(), any()) } returns 0
        val saved = slot<Device>()
        every { deviceRepository.save(capture(saved)) } answers { firstArg() }

        service.registerPushToken(existing.userId, existing.id, token, locale)

        return saved.captured
    }

    // ─── Ownership (#908) ───────────────────────────────

    @Test
    fun `should take the token away from every other device before writing it to the caller's`() {
        // The whole of #908 on the write side: registering used to *copy* the token onto the
        // caller's row, so the previous account on the same phone kept receiving notifications.
        val existing = device()
        register(existing, token = "shared-token", locale = null)

        verifyOrder {
            deviceRepository.detachPushTokenFromOtherDevices("shared-token", existing.id)
            deviceRepository.save(match { it.id == existing.id && it.pushToken == "shared-token" })
        }
    }

    @Test
    fun `should reject a push token for a device that is not the caller's`() {
        val someoneElses = device()
        every { deviceRepository.findById(someoneElses.id) } returns someoneElses

        val ex = assertThrows<BusinessException> {
            service.registerPushToken(UUID.randomUUID(), someoneElses.id, "fcm-token", "en")
        }

        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, ex.errorCode)
        verify(exactly = 0) { deviceRepository.detachPushTokenFromOtherDevices(any(), any()) }
        verify(exactly = 0) { deviceRepository.save(any()) }
    }

    @Test
    fun `should reject a push token for a device that does not exist`() {
        every { deviceRepository.findById(any()) } returns null

        val ex = assertThrows<BusinessException> {
            service.registerPushToken(UUID.randomUUID(), UUID.randomUUID(), "fcm-token", "en")
        }

        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, ex.errorCode)
    }

    @Test
    fun `should reject a push token for a revoked device`() {
        // Revocation clears the token; re-registering it would quietly undo that.
        val revoked = device(revokedAt = Instant.now())
        every { deviceRepository.findById(revoked.id) } returns revoked

        val ex = assertThrows<BusinessException> {
            service.registerPushToken(revoked.userId, revoked.id, "fcm-token", null)
        }

        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, ex.errorCode)
        verify(exactly = 0) { deviceRepository.save(any()) }
    }

    // ─── Input (#159) ───────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = ["", "   ", "has space", "tab\tinside", "new\nline", "türkçe"])
    fun `should reject a value that is not a push token`(token: String) {
        val ex = assertThrows<BusinessException> {
            service.registerPushToken(UUID.randomUUID(), UUID.randomUUID(), token, null)
        }

        assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode)
        verify(exactly = 0) { deviceRepository.save(any()) }
    }

    @Test
    fun `should reject a token longer than the unique index can hold`() {
        val ex = assertThrows<BusinessException> {
            service.registerPushToken(UUID.randomUUID(), UUID.randomUUID(), "a".repeat(MAX_PUSH_TOKEN_LENGTH + 1), null)
        }

        assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode)
    }

    @Test
    fun `should store the token trimmed so one token is one value to the index`() {
        val saved = register(device(), token = "  fcm:APA91b-x_y  ", locale = null)

        assertEquals("fcm:APA91b-x_y", saved.pushToken)
        verify { deviceRepository.detachPushTokenFromOtherDevices("fcm:APA91b-x_y", any()) }
    }

    // ─── Language (#469) ────────────────────────────────
    //
    // Push text is composed on the server, so the device row is the only place the reader's
    // language can come from.

    @Test
    fun `should store the language the device registered with its push token`() {
        val saved = register(device(), locale = "en")

        assertEquals("en", saved.locale)
        assertEquals("fcm-token", saved.pushToken)
    }

    @Test
    fun `should normalise the language before storing it`() {
        assertEquals("en-GB", register(device(), locale = "  EN-gb ").locale)
    }

    @Test
    fun `should keep the previous language when the caller sends none`() {
        // onNewToken fires in a system callback with no UI and nothing to report.
        assertEquals("en", register(device(locale = "en"), locale = null).locale)
    }

    @Test
    fun `should keep the previous language when the caller sends nonsense`() {
        assertEquals("en", register(device(locale = "en"), locale = "!!!").locale)
    }

    @Test
    fun `should leave the language unknown when nothing has ever been registered`() {
        assertNull(register(device(locale = null), locale = null).locale)
    }

    // ─── Provider rejection (#908) ──────────────────────

    @Test
    fun `should clear a token the provider rejected wherever it is held`() {
        every { deviceRepository.clearPushToken("dead-token") } returns 1

        service.invalidatePushToken("dead-token")

        verify(exactly = 1) { deviceRepository.clearPushToken("dead-token") }
    }
}
