package com.muhabbet.app.util

import com.muhabbet.shared.dto.ViewOnceRevealResponse
import kotlin.io.encoding.Base64

/**
 * The photo a burn released, as bytes Coil can render — or null when this response carries none.
 *
 * Since #541 the server deletes the object in the same call that reveals it and returns the bytes
 * inline, because a presigned URL is a credential with a lifetime and "view once" cannot be
 * expressed as a duration. The one the message used to carry lasted seven days, which is how long a
 * "burned" photo stayed fetchable by anyone who had kept the string.
 *
 * A malformed payload degrades to null rather than throwing. The caller's fallback is to show the
 * failure snackbar; crashing the chat because one field would not decode is a worse answer, and the
 * message is spent either way.
 */
fun ViewOnceRevealResponse.decodedMedia(): ByteArray? {
    val encoded = mediaBase64 ?: return null
    return try {
        Base64.decode(encoded)
    } catch (e: IllegalArgumentException) {
        null
    }
}
