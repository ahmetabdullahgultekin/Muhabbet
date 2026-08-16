package com.muhabbet.messaging.domain.model

/**
 * The finished text of a single push, composed once and handed to every device of one recipient.
 *
 * Before #469 the broadcasters built this inline at the call site: a constant `"Yeni mesaj"` title
 * and `content.take(100)` as the body, so a photo pushed an empty line and two people writing at
 * once produced two notifications that read identically. Making it a value object gives the
 * composition one home and one set of tests.
 */
data class PushNotification(
    /** The sender's name for a 1:1, `"Sender · Group"` for a group. Never a fixed label. */
    val title: String,
    val body: String,
    /**
     * Groups every push for one conversation under a single entry.
     *
     * Two mechanisms hang off this one value and both are needed: FCM's `collapseKey` replaces
     * messages still *queued* for a device that is offline, while `AndroidNotification.tag`
     * replaces one already *shown* in the tray. Set only the first and three messages sent to a
     * live device still stack three notifications deep.
     */
    val collapseKey: String,
    val data: Map<String, String>
)
