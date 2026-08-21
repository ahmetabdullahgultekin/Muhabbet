package com.muhabbet.media.domain.service

import com.muhabbet.media.domain.port.`in`.ResolveMediaOwnerUseCase
import com.muhabbet.media.domain.port.out.MediaFileRepository
import com.muhabbet.media.domain.port.out.MediaStoragePort
import java.util.UUID

/**
 * Answers "who uploaded this?" for a media URL.
 *
 * A class of its own rather than a fourth interface on [MediaService], which already carries three.
 * It also has a different shape of dependency: it never touches storage contents, only the naming
 * scheme, so it stays cheap and side-effect free.
 *
 * The URL is reversed by [MediaStoragePort], not here — the adapter that *built* the URL is the
 * only thing that can be trusted to take it apart, and the domain has no business knowing that an
 * object key lives after the bucket name in a path.
 */
open class MediaOwnershipService(
    private val mediaStoragePort: MediaStoragePort,
    private val mediaFileRepository: MediaFileRepository
) : ResolveMediaOwnerUseCase {

    override fun findUploaderByUrl(mediaUrl: String): UUID? {
        val key = mediaStoragePort.resolveObjectKey(mediaUrl) ?: return null
        return mediaFileRepository.findByObjectKey(key)?.uploaderId
    }
}
