package com.muhabbet.messaging.domain.port.`in`

import java.util.UUID

interface ManageDisappearingMessageUseCase {
    fun setDisappearTimer(conversationId: UUID, userId: UUID, seconds: Int?)
}
