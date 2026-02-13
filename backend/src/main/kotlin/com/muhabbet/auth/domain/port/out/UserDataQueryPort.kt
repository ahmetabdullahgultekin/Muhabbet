package com.muhabbet.auth.domain.port.out

import java.util.UUID

interface UserDataQueryPort {
    fun countMessagesByUserId(userId: UUID): Long
    fun countConversationsByUserId(userId: UUID): Long
    fun countMediaFilesByUserId(userId: UUID): Long
    fun removeUserFromAllConversations(userId: UUID)
}
