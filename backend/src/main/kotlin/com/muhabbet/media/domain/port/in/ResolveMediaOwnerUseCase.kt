package com.muhabbet.media.domain.port.`in`

import java.util.UUID

/**
 * Who uploaded the blob a URL names.
 *
 * Its own in-port rather than a fourth method on [GetMediaUrlUseCase], because the two questions
 * have no caller in common: minting a URL is what the owner of a file does, and asking who owns a
 * file is what another module does before trusting a string a client sent it.
 *
 * This is the published face of the media module for that question. Callers outside `media` reach
 * it through a port of their own — never through `MediaFileRepository`, which is media's private
 * plumbing.
 */
interface ResolveMediaOwnerUseCase {

    /**
     * The uploader of the blob [mediaUrl] addresses, or null when the URL does not address a blob
     * in our own store at all — a different host, a path outside our bucket, or a key with no row
     * behind it. Null is therefore "we know nothing about this address", not "nobody owns it".
     */
    fun findUploaderByUrl(mediaUrl: String): UUID?
}
