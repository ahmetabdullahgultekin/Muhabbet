package com.muhabbet.messaging.adapter.out.external

import com.muhabbet.media.domain.port.`in`.ManageMediaObjectUseCase
import com.muhabbet.messaging.domain.port.out.MediaBytes
import com.muhabbet.messaging.domain.port.out.MediaObjectPort
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Bridges messaging's [MediaObjectPort] to the media module, mirroring [ModerationBlockPolicyAdapter]
 * and [AuthUserDirectoryAdapter].
 *
 * Depends on media's **in-port**, not its `MediaFileRepository` or `MediaStoragePort`: a use-case
 * interface is the published face of a module, a repository is its private plumbing. Reaching for
 * the storage port directly would let messaging delete objects by key — which is precisely the
 * primitive #541 exists to keep behind an ownership check.
 */
@Component
class MediaObjectAdapter(
    private val manageMediaObjectUseCase: ManageMediaObjectUseCase
) : MediaObjectPort {

    override fun findUploaderId(mediaId: UUID): UUID? = manageMediaObjectUseCase.findUploaderId(mediaId)

    override fun takeAndDestroy(mediaId: UUID): MediaBytes? =
        manageMediaObjectUseCase.takeAndDestroy(mediaId)?.let { MediaBytes(it.bytes, it.contentType) }
}
