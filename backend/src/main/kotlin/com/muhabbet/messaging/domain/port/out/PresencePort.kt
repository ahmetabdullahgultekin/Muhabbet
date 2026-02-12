package com.muhabbet.messaging.domain.port.out

import java.util.UUID

interface PresencePort {
    fun setOnline(userId: UUID, ttlSeconds: Long = 60)
    fun setOffline(userId: UUID)
    fun isOnline(userId: UUID): Boolean
    fun getOnlineUserIds(userIds: Collection<UUID>): Set<UUID>
}
