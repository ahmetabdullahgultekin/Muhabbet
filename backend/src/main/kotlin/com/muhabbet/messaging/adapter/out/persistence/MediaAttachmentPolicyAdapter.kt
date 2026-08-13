package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.messaging.adapter.out.persistence.repository.MediaAttachmentQueryRepository
import com.muhabbet.messaging.domain.port.out.MediaAttachmentPolicy
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class MediaAttachmentPolicyAdapter(
    private val mediaAttachmentQueryRepository: MediaAttachmentQueryRepository
) : MediaAttachmentPolicy {

    override fun canAttach(senderId: UUID, mediaUrl: String): Boolean =
        mediaAttachmentQueryRepository.ownsMedia(senderId, mediaUrl) ||
            mediaAttachmentQueryRepository.canAlreadyReachMedia(senderId, mediaUrl)
}
