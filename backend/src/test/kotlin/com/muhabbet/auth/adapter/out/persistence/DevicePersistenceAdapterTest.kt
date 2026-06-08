package com.muhabbet.auth.adapter.out.persistence

import com.muhabbet.auth.adapter.out.persistence.entity.DeviceJpaEntity
import com.muhabbet.auth.adapter.out.persistence.repository.SpringDataDeviceRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class DevicePersistenceAdapterTest {

    private val springData = mockk<SpringDataDeviceRepository>(relaxed = true)
    private val adapter = DevicePersistenceAdapter(springData)

    private fun deviceWithToken(token: String) = DeviceJpaEntity(
        id = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        platform = "android",
        pushToken = token
    )

    @Test
    fun `clearPushToken should null the token and save every device holding it`() {
        val d1 = deviceWithToken("dead-token")
        val d2 = deviceWithToken("dead-token")
        every { springData.findByPushToken("dead-token") } returns listOf(d1, d2)
        val saved = slot<List<DeviceJpaEntity>>()
        every { springData.saveAll(capture(saved)) } answers { firstArg() }

        adapter.clearPushToken("dead-token")

        assertTrue(saved.captured.all { it.pushToken == null })
        assertNull(d1.pushToken)
        assertNull(d2.pushToken)
        verify(exactly = 1) { springData.saveAll(any<List<DeviceJpaEntity>>()) }
    }

    @Test
    fun `clearPushToken should be a no-op when no device holds the token`() {
        every { springData.findByPushToken("unknown") } returns emptyList()

        adapter.clearPushToken("unknown")

        verify(exactly = 0) { springData.saveAll(any<List<DeviceJpaEntity>>()) }
    }
}
