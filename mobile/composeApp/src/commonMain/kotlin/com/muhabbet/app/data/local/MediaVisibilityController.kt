package com.muhabbet.app.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Media visibility as observable state, write-through to [TokenStorage] (#593).
 *
 * "Media visibility" is the user-facing framing — *do my chat photos show up in my gallery?* — and
 * one boolean answers it: when this is on, photos and videos that arrive in chats are copied into
 * the phone's shared media store, and the gallery app finds them like any other picture.
 *
 * A singleton, for the fifth time in this package and the same reason as every neighbour. The
 * switch lives in Settings and the reader is
 * [com.muhabbet.app.data.repository.ReceivedMediaAutoSaver], which runs from an app-wide collector
 * that is composed once at the root and never recomposed with Settings. A `remember` copy in the
 * Settings screen would leave the saver holding whatever the value was at process start: the switch
 * would move, and photos would keep not arriving in the gallery — the #377/#378/#380 shape, with
 * the disagreeing half invisible.
 *
 * The reader is a plain [StateFlow] read rather than a collector because the decision is made once
 * per received message, at the moment it arrives; there is nothing to keep in sync between reads.
 */
class MediaVisibilityController(private val tokenStorage: TokenStorage) {

    private val _saveToGallery = MutableStateFlow(tokenStorage.getSaveMediaToGallery())

    /** True when received photos and videos are copied to the device gallery. Off by default. */
    val saveToGallery: StateFlow<Boolean> = _saveToGallery.asStateFlow()

    fun setSaveToGallery(enabled: Boolean) {
        tokenStorage.setSaveMediaToGallery(enabled)
        _saveToGallery.value = enabled
    }
}
