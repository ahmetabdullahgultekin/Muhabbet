package com.muhabbet.messaging.adapter.out.external

import com.muhabbet.auth.domain.port.out.DeviceRepository
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class DeviceRepositoryPushTokenInvalidationAdapterTest {

    private val deviceRepository = mockk<DeviceRepository>(relaxed = true)
    private val adapter = DeviceRepositoryPushTokenInvalidationAdapter(deviceRepository)

    @Test
    fun `should delegate invalidate to deviceRepository clearPushToken`() {
        adapter.invalidate("dead-token")

        verify(exactly = 1) { deviceRepository.clearPushToken("dead-token") }
    }
}
