package com.muhabbet.auth.adapter.out.persistence

import com.muhabbet.auth.adapter.out.persistence.entity.DeviceJpaEntity
import com.muhabbet.auth.adapter.out.persistence.repository.SpringDataDeviceRepository
import com.muhabbet.auth.domain.model.Device
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import java.util.UUID

/**
 * Unit tests for the device persistence adapter with Spring Data mocked out.
 *
 * The point of the round-trip test is the mapper pair, not the repository: `Device` has gained
 * columns three times since it was written (`locale` in V22, the three multi-device columns in
 * V18), and each time both `fromDomain` and `toDomain` had to be edited by hand. A field added to
 * one and forgotten in the other compiles, persists, and silently reads back as null.
 */
class DevicePersistenceAdapterTest {

    private val springData = mockk<SpringDataDeviceRepository>(relaxed = true)
    private val adapter = DevicePersistenceAdapter(springData)

    private val userId: UUID = UUID.randomUUID()

    /** Every nullable field populated on purpose — a dropped mapping shows up as null. */
    private fun fullyPopulatedDevice() = Device(
        id = UUID.randomUUID(),
        userId = userId,
        platform = "android",
        deviceName = "Pixel 8",
        pushToken = "fcm-token",
        lastActiveAt = Instant.parse("2026-08-01T10:15:30Z"),
        createdAt = Instant.parse("2026-07-01T09:00:00Z"),
        isPrimary = true,
        locale = "tr-TR",
        linkedByDeviceId = UUID.randomUUID(),
        displayName = "Chrome on macOS",
        revokedAt = Instant.parse("2026-08-10T12:00:00Z")
    )

    @Test
    fun `save should round-trip every domain field through the JPA entity`() {
        val device = fullyPopulatedDevice()
        val entitySlot = slot<DeviceJpaEntity>()
        every { springData.save(capture(entitySlot)) } answers { firstArg() }

        val saved = adapter.save(device)

        // Field-by-field on the way in…
        val entity = entitySlot.captured
        assertEquals(device.id, entity.id)
        assertEquals(device.userId, entity.userId)
        assertEquals(device.platform, entity.platform)
        assertEquals(device.deviceName, entity.deviceName)
        assertEquals(device.pushToken, entity.pushToken)
        assertEquals(device.lastActiveAt, entity.lastActiveAt)
        assertEquals(device.createdAt, entity.createdAt)
        assertEquals(device.isPrimary, entity.isPrimary)
        assertEquals(device.locale, entity.locale)
        assertEquals(device.linkedByDeviceId, entity.linkedByDeviceId)
        assertEquals(device.displayName, entity.displayName)
        assertEquals(device.revokedAt, entity.revokedAt)
        // …and as one value on the way back out.
        assertEquals(device, saved)
    }

    @Test
    fun `findByUserIdAndPlatform should map the row when one exists`() {
        val device = fullyPopulatedDevice()
        every { springData.findByUserIdAndPlatform(userId, "android") } returns
            DeviceJpaEntity.fromDomain(device)

        assertEquals(device, adapter.findByUserIdAndPlatform(userId, "android"))
    }

    @Test
    fun `findByUserIdAndPlatform should return null when there is no row`() {
        every { springData.findByUserIdAndPlatform(userId, "ios") } returns null

        assertNull(adapter.findByUserIdAndPlatform(userId, "ios"))
    }

    @Test
    fun `findById should return null when the row is absent`() {
        val id = UUID.randomUUID()
        every { springData.findById(id) } returns Optional.empty()

        assertNull(adapter.findById(id))
    }

    @Test
    fun `findById should map the row when present`() {
        val device = fullyPopulatedDevice()
        every { springData.findById(device.id) } returns
            Optional.of(DeviceJpaEntity.fromDomain(device))

        assertEquals(device, adapter.findById(device.id))
    }

    @Test
    fun `findByUserId should map every row`() {
        val a = fullyPopulatedDevice()
        val b = fullyPopulatedDevice().copy(platform = "ios", revokedAt = null)
        every { springData.findByUserId(userId) } returns
            listOf(DeviceJpaEntity.fromDomain(a), DeviceJpaEntity.fromDomain(b))

        assertEquals(listOf(a, b), adapter.findByUserId(userId))
    }

    @Test
    fun `findActiveByUserId should use the revoked-excluding finder, not the plain one`() {
        // "Active" is a soft-tombstone rule (revoked_at IS NULL). Routing this through the plain
        // finder would hand revoked companion devices back into push fan-out.
        val active = fullyPopulatedDevice().copy(revokedAt = null)
        every { springData.findByUserIdAndRevokedAtIsNull(userId) } returns
            listOf(DeviceJpaEntity.fromDomain(active))

        val result = adapter.findActiveByUserId(userId)

        assertEquals(listOf(active), result)
        verify(exactly = 1) { springData.findByUserIdAndRevokedAtIsNull(userId) }
        verify(exactly = 0) { springData.findByUserId(any()) }
    }
}
