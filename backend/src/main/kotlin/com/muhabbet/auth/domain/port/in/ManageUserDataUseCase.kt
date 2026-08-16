package com.muhabbet.auth.domain.port.`in`

import com.muhabbet.auth.domain.model.UserDataExport
import java.util.UUID

interface ManageUserDataUseCase {
    /**
     * The KVKK m.11 / GDPR Art. 15 & 20 data export (#341). [messagesCursor]/[mediaCursor] are the
     * `nextCursor` values from a previous [UserDataExport.messages]/[UserDataExport.mediaFiles]
     * page — pass null to start from the beginning of each. A malformed cursor is treated as null
     * (starts over) rather than rejected, matching [com.muhabbet.messaging.domain.service.MessageService.getMessages].
     */
    fun exportUserData(
        userId: UUID,
        messagesCursor: String? = null,
        mediaCursor: String? = null,
        pageSize: Int = 200
    ): UserDataExport

    fun requestAccountDeletion(userId: UUID)
}
