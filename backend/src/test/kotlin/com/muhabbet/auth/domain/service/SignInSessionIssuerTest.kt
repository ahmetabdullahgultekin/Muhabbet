package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.Device
import com.muhabbet.auth.domain.model.DevicePlatform
import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.model.UserStatus
import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.auth.domain.port.out.RefreshTokenRepository
import com.muhabbet.shared.AuthTestFixtures
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Which device a sign-in lands on (#882). The device used to be found by `(userId, platform)`, so a
 * second Android phone took over the first one's row, push token and sessions.
 */
class SignInSessionIssuerTest {

    /** A device table with the lookups' real semantics, including "revoked rows never match". */
    private class InMemoryDevices : DeviceRepository {
        val rows = linkedMapOf<UUID, Device>()
        override fun save(device: Device) = device.also { rows[it.id] = it }
        override fun findActiveByInstallation(userId: UUID, installationId: UUID) =
            rows.values.firstOrNull { it.userId == userId && it.installationId == installationId && it.isActive }
        override fun findLegacyPrimaryCandidate(userId: UUID, platform: DevicePlatform) =
            rows.values.filter {
                it.userId == userId && it.platform == platform && it.installationId == null &&
                    !it.isCompanion && it.isActive
            }.maxByOrNull { it.lastActiveAt ?: it.createdAt }
        override fun findByUserId(userId: UUID) = rows.values.filter { it.userId == userId }
        override fun findByUserIdIn(userIds: Collection<UUID>) = rows.values.filter { it.userId in userIds }
        override fun findById(id: UUID) = rows[id]
        override fun findActiveByUserId(userId: UUID) = rows.values.filter { it.userId == userId && it.isActive }
        override fun bumpTokenVersion(deviceId: UUID) {
            rows[deviceId]?.let { rows[deviceId] = it.copy(tokenVersion = it.tokenVersion + 1) }
        }
        override fun bumpTokenVersionForUser(userId: UUID) {
            findByUserId(userId).forEach { bumpTokenVersion(it.id) }
        }
    }

    private val devices = InMemoryDevices()
    private val refreshTokens = mockk<RefreshTokenRepository>(relaxed = true)
    private val issuer = SignInSessionIssuer(
        deviceRepository = devices,
        refreshTokenRepository = refreshTokens,
        sessionRevocation = SessionRevocation(refreshTokens, devices),
        jwtProvider = AuthTestFixtures.jwtProvider(),
        refreshTokenExpirySeconds = 3600
    )
    private val user = User(id = UUID.randomUUID(), phoneNumber = "+905000000001", status = UserStatus.ACTIVE)

    private fun signIn(installationId: UUID?, platform: DevicePlatform = DevicePlatform.ANDROID) =
        issuer.issue(user, DeviceSignIn("Phone", platform, installationId), isNewUser = false)

    @Test
    fun `should create two devices for two installations on the same platform`() {
        val first = signIn(UUID.randomUUID())
        val second = signIn(UUID.randomUUID())

        assertNotEquals(first.deviceId, second.deviceId)
        assertEquals(2, devices.findActiveByUserId(user.id).size)
    }

    @Test
    fun `should return to the same device when the same installation signs in again`() {
        val installation = UUID.randomUUID()

        val first = signIn(installation)
        val again = signIn(installation)

        assertEquals(first.deviceId, again.deviceId)
    }

    @Test
    fun `should keep each installation's push token its own`() {
        val phoneA = signIn(UUID.randomUUID())
        val idA = UUID.fromString(phoneA.deviceId)
        devices.save(devices.rows.getValue(idA).copy(pushToken = "token-A"))

        signIn(UUID.randomUUID())

        assertEquals("token-A", devices.rows.getValue(idA).pushToken, "the second phone took over the first one's row")
    }

    @Test
    fun `should not resurrect a revoked device when its installation signs in again`() {
        val installation = UUID.randomUUID()
        val first = UUID.fromString(signIn(installation).deviceId)
        devices.save(devices.rows.getValue(first).copy(revokedAt = Instant.now()))

        val again = UUID.fromString(signIn(installation).deviceId)

        assertNotEquals(first, again)
        assertNotNull(devices.rows.getValue(first).revokedAt, "the revoked row was brought back")
    }

    @Test
    fun `should let a primary sign in again beside a companion of the same platform`() {
        // With the old single-result lookup this was an IncorrectResultSizeDataAccessException.
        val primaryInstall = UUID.randomUUID()
        val primary = UUID.fromString(signIn(primaryInstall).deviceId)
        devices.save(Device(userId = user.id, platform = DevicePlatform.ANDROID, linkedByDeviceId = primary))

        val again = UUID.fromString(signIn(primaryInstall).deviceId)

        assertEquals(primary, again)
    }

    @Test
    fun `should adopt the legacy row on the first sign-in of an upgraded app`() {
        val legacy = devices.save(Device(userId = user.id, platform = DevicePlatform.ANDROID, pushToken = "fcm"))
        val installation = UUID.randomUUID()

        val result = signIn(installation)

        assertEquals(legacy.id.toString(), result.deviceId)
        assertEquals(installation, devices.rows.getValue(legacy.id).installationId)
    }

    @Test
    fun `should start each sign-in as its own refresh-token family`() {
        signIn(UUID.randomUUID())

        verify { refreshTokens.save(match { it.familyId == it.id }) }
    }

    @Test
    fun `should sign out the least recently active device beyond the cap`() {
        val stalest = UUID.fromString(signIn(UUID.randomUUID()).deviceId)
        devices.save(devices.rows.getValue(stalest).copy(lastActiveAt = Instant.now().minusSeconds(86_400)))
        repeat(SignInSessionIssuer.MAX_SIGNED_IN_DEVICES - 1) { signIn(UUID.randomUUID()) }

        signIn(UUID.randomUUID())

        assertEquals(SignInSessionIssuer.MAX_SIGNED_IN_DEVICES, devices.findActiveByUserId(user.id).size)
        assertNotNull(devices.rows.getValue(stalest).revokedAt)
        assertNull(devices.rows.getValue(stalest).pushToken)
        verify { refreshTokens.revokeAllForDevice(user.id, stalest) }
        assertTrue(devices.rows.getValue(stalest).tokenVersion > 0, "the evicted device's access tokens survived")
    }
}
