package com.muhabbet.app.data.local

/**
 * The compression profile applied to photos sent in a chat.
 *
 * The dimension and quality live here rather than at the upload site so that "HD" means one thing
 * in one place. Before this existed the setting stored a bare string that nothing read, and every
 * upload used a hardcoded 1280/80 regardless of what the user had chosen.
 *
 * Profile photos and video thumbnails deliberately do **not** consult this — they are avatars and
 * previews with fixed budgets, and letting HD inflate them would cost bandwidth with nothing on
 * screen to show for it.
 */
enum class MediaQuality(
    val storageKey: String,
    val maxDimension: Int,
    val jpegQuality: Int
) {
    Standard(storageKey = "standard", maxDimension = 1280, jpegQuality = 80),
    Hd(storageKey = "hd", maxDimension = 1920, jpegQuality = 92);

    companion object {
        /** What an account gets before it ever opens the picker. */
        val Default = Standard

        fun fromStorageKey(key: String?): MediaQuality =
            entries.firstOrNull { it.storageKey == key } ?: Default
    }
}
