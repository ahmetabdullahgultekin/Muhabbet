package com.muhabbet.auth.domain.port.`in`

import com.muhabbet.auth.domain.model.UserDataExport
import java.util.UUID

interface ManageUserDataUseCase {
    fun exportUserData(userId: UUID): UserDataExport
    fun requestAccountDeletion(userId: UUID)
}
