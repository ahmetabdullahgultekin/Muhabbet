package com.muhabbet.media.adapter.out.persistence

import com.muhabbet.media.adapter.out.persistence.repository.MediaAccessQueryRepository
import com.muhabbet.media.domain.port.out.MediaAccessPolicy
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class MediaAccessPolicyAdapter(
    private val mediaAccessQueryRepository: MediaAccessQueryRepository
) : MediaAccessPolicy {

    override fun isMemberOfConversationContainingMedia(userId: UUID, fileKey: String): Boolean =
        mediaAccessQueryRepository.isMemberOfConversationContainingMedia(userId, fileKey)
}
