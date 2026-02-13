package com.muhabbet.media.domain.port.`in`

import com.muhabbet.shared.dto.StorageUsageResponse
import java.util.UUID

interface GetStorageUsageUseCase {
    fun getStorageUsage(userId: UUID): StorageUsageResponse
}
