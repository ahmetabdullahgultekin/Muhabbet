package com.muhabbet.app.data.repository

import com.muhabbet.app.data.local.MediaVisibilityController
import com.muhabbet.app.platform.GallerySaveResult
import com.muhabbet.app.platform.MediaGallerySaver
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import com.muhabbet.shared.model.ContentType
import com.muhabbet.shared.protocol.WsMessage

private const val TAG = "ReceivedMediaAutoSaver"

/**
 * Copies media that **arrives** into the phone's gallery, when the user has asked for that (#593).
 *
 * The decision half is [shouldAutoSave] and is deliberately a pure function of the frame: it is the
 * part with rules worth being sure about, and it is the part that is tested. The doing half —
 * download, name, write — is [onMessageReceived].
 *
 * ### Where this runs, and what it therefore cannot see
 *
 * It is driven by the app-wide `WsClient.incoming` collector in `App.kt`, so it saves media that
 * arrives **while the app is running**. Media that landed while the process was dead is not
 * backfilled: the background sync path fetches messages, not blobs, and walking history to
 * retro-save would mean re-downloading every photo a person has ever received the first time they
 * turn the setting on. Saving from the moment you ask is the honest, bounded behaviour — and it is
 * what the switch's subtitle says.
 *
 * Because each frame is delivered once, there is no de-duplication bookkeeping and no risk of
 * re-saving a photo when the chat is reopened. That is the main reason the hook is here rather than
 * in `ChatScreen`, where "which of these have I already written" would have to be persisted, and
 * where a photo the user deleted from their gallery would come back.
 */
class ReceivedMediaAutoSaver(
    private val mediaRepository: MediaRepository,
    private val mediaVisibility: MediaVisibilityController
) {

    /**
     * Whether this frame should be copied to the gallery, given the current setting.
     *
     * The rules are in [shouldAutoSaveMedia]; this reads the switch and delegates.
     */
    fun shouldAutoSave(message: WsMessage.NewMessage, currentUserId: String): Boolean =
        shouldAutoSaveMedia(message, currentUserId, enabled = mediaVisibility.saveToGallery.value)

    /**
     * Download and write one received photo or video, if [shouldAutoSave] says so.
     *
     * Never throws. A failure here must not take down the collector it shares a coroutine lineage
     * with — a dropped connection mid-download would otherwise stop auto-save for the rest of the
     * session with nothing on screen to say so. Cancellation still propagates, via
     * [runCatchingCancellable].
     */
    suspend fun onMessageReceived(
        message: WsMessage.NewMessage,
        currentUserId: String,
        saver: MediaGallerySaver
    ) {
        if (!shouldAutoSave(message, currentUserId)) return
        if (!saver.isSupported()) return
        val url = message.mediaUrl ?: return

        runCatchingCancellable {
            // No key material: media E2E is flag-OFF (E2EConfig.MEDIA_ENABLED), and the frame does
            // not carry any. When it does, this call is where it goes — downloadMedia already takes
            // it and fails closed on a bad blob rather than writing ciphertext to the camera roll.
            val bytes = mediaRepository.downloadMedia(url)
            val mimeType = mimeTypeFor(message.contentType, url)
            val result = saver.save(bytes, fileNameFor(message.messageId, mimeType), mimeType)
            if (result != GallerySaveResult.SAVED) {
                Log.w(TAG, "Gallery save for ${message.messageId} returned $result")
            }
        }.onFailure { Log.e(TAG, "Auto-save failed for ${message.messageId}", it) }
    }
}

/**
 * Which received frames are eligible for the gallery. A pure function of the frame and the switch —
 * the part with rules worth being sure about, and therefore the part that is tested.
 *
 * The exclusions, in the order they matter:
 *
 * - **View-once media is never saved.** Since #541 the server destroys the object as it is
 *   revealed, precisely so a "view once" photo cannot outlive its viewing. Writing it into a
 *   permanent, cross-app album at arrival would defeat the entire feature — and would do it
 *   silently, before the recipient has even opened the seal. This is the one rule here that is a
 *   correctness requirement rather than a preference.
 * - **Disappearing messages are never saved**, for the same reason one step weaker: a message with
 *   an expiry is one the sender asked to be temporary, and a copy in the camera roll is not
 *   temporary. The app already honours [WsMessage.NewMessage.expiresAt] by removing the bubble;
 *   leaving the photo behind in the gallery would make that theatre.
 * - **Own messages are never saved.** Anything this device sent came from its own gallery or
 *   camera; a second copy is a duplicate, not a feature.
 * - Only [ContentType.IMAGE] and [ContentType.VIDEO]. Stickers and GIFs are other people's reaction
 *   images, documents are files with their own share sheet, and voice notes are not gallery
 *   material.
 *
 * Media from **unknown senders** is deliberately *not* excluded. The filter for people you do not
 * want to hear from is the block list, which is enforced server-side — a blocked user's message
 * never reaches here. Gating on the address book instead would mean that anyone who declined
 * contacts permission (a supported, first-class choice since #691) would have a switch that saves
 * nothing, with no way to tell why.
 */
internal fun shouldAutoSaveMedia(
    message: WsMessage.NewMessage,
    currentUserId: String,
    enabled: Boolean
): Boolean {
    if (!enabled) return false
    if (message.senderId == currentUserId) return false
    if (message.viewOnce) return false
    if (message.expiresAt != null) return false
    if (message.contentType != ContentType.IMAGE && message.contentType != ContentType.VIDEO) return false
    return !message.mediaUrl.isNullOrBlank()
}

/**
 * A stable, collision-proof display name.
 *
 * The message id is a UUIDv7, so names sort by arrival in a file browser and a second write of the
 * same message is recognisable as such instead of landing as "photo (2)".
 */
internal fun fileNameFor(messageId: String, mimeType: String): String =
    "muhabbet-$messageId.${extensionFor(mimeType)}"

/**
 * Best-effort MIME type. The frame does not carry one, so it is inferred from the content type and
 * the URL's extension — the media service names objects with the extension it stored them under.
 *
 * Falls back to `image/jpeg` / `video/mp4`, which is what the upload pipeline produces
 * (`MediaUploadHelper` re-encodes every image to JPEG). A wrong-but-plausible type costs a
 * mislabelled gallery entry; refusing to save because the URL had no extension would cost the
 * feature.
 */
internal fun mimeTypeFor(contentType: ContentType, url: String): String {
    val extension = url.substringBefore('?').substringAfterLast('.', "").lowercase()
    return when (extension) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "heic" -> "image/heic"
        "gif" -> "image/gif"
        "mp4" -> "video/mp4"
        "mov" -> "video/quicktime"
        "webm" -> "video/webm"
        else -> if (contentType == ContentType.VIDEO) "video/mp4" else "image/jpeg"
    }
}

private fun extensionFor(mimeType: String): String = when (mimeType) {
    "image/jpeg" -> "jpg"
    "image/png" -> "png"
    "image/webp" -> "webp"
    "image/heic" -> "heic"
    "image/gif" -> "gif"
    "video/mp4" -> "mp4"
    "video/quicktime" -> "mov"
    "video/webm" -> "webm"
    else -> "bin"
}
