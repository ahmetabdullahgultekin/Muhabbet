package com.muhabbet.shared

import com.muhabbet.messaging.domain.port.out.MediaAttachmentPolicyPort
import com.muhabbet.messaging.domain.port.out.ResolvedAttachment
import java.util.UUID

/**
 * The media rule (#679) for tests that are not about media, and a fixture for the ones that are.
 *
 * Every send now asks this port where a URL points, so a suite that only cares about delivery still
 * has to answer. Deliberately not a relaxed mock: a relaxed mock answers `false` to a boolean, which
 * would make every media send in every suite fail for a reason the test never mentions. The default
 * host is the `https://cdn.example` the existing fixtures already sign their URLs with.
 *
 * [owned] is what makes the fresh-upload path testable: a media id maps to the user who uploaded it
 * and the URLs this server would mint for it, which is exactly what the real adapter gets back from
 * `GetMediaUrlUseCase`.
 */
class TestMediaAttachmentPolicy(
    private val ownHost: String = "https://cdn.example",
    private val owned: Map<UUID, OwnedMedia> = emptyMap()
) : MediaAttachmentPolicyPort {

    data class OwnedMedia(val uploaderId: UUID, val attachment: ResolvedAttachment)

    override fun resolveOwnUpload(mediaId: UUID, senderId: UUID): ResolvedAttachment? =
        owned[mediaId]?.takeIf { it.uploaderId == senderId }?.attachment

    override fun isAllowedOrigin(url: String): Boolean = url.startsWith("$ownHost/")
}
