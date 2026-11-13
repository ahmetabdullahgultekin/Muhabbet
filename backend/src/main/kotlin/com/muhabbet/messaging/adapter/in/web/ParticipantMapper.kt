package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.auth.domain.model.User
import com.muhabbet.shared.dto.ParticipantResponse
import com.muhabbet.shared.model.MemberRole
import java.util.UUID

/**
 * The one place a [ParticipantResponse] is built (#902).
 *
 * There were three copies, in `ConversationController` (create and list) and `GroupController`
 * (add members), and each wrote `phoneNumber = user?.phoneNumber` for every participant. The
 * number is now carried only when [phoneVisible] says so — the caller gets that answer from
 * `ResolvePhoneVisibilityUseCase` — so a fourth copy cannot reintroduce the leak by forgetting.
 */
internal fun participantResponseOf(
    userId: UUID,
    user: User?,
    role: MemberRole,
    isOnline: Boolean,
    phoneVisible: Boolean
): ParticipantResponse = ParticipantResponse(
    userId = userId.toString(),
    displayName = user?.displayName,
    phoneNumber = user?.phoneNumber?.takeIf { phoneVisible },
    avatarUrl = user?.avatarUrl,
    role = role,
    isOnline = isOnline
)
