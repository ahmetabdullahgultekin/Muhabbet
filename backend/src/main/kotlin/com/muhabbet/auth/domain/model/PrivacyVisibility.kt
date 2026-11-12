package com.muhabbet.auth.domain.model

import java.util.UUID

/**
 * The everyone / contacts / nobody vocabulary of `onlineStatusVisibility` and `aboutVisibility`,
 * decided in one place so that every screen showing the same fact applies the same rule.
 *
 * "Contacts" of an owner are the people the owner has reached out to (see
 * `ConversationRepository.findAllContactUserIds`). Being put in a conversation by someone does not
 * make that someone a contact. The owner always sees their own value, and an unknown setting is
 * treated as the most private one.
 */
object PrivacyVisibility {
    const val EVERYONE = "everyone"
    const val CONTACTS = "contacts"
    const val NOBODY = "nobody"

    /**
     * Whether [viewerId] may see a value [ownerId] has restricted with [setting].
     * [viewerIsContact] is only evaluated for the "contacts" case, so a caller can defer its query.
     */
    fun allows(setting: String, ownerId: UUID, viewerId: UUID, viewerIsContact: () -> Boolean): Boolean {
        if (viewerId == ownerId) return true
        return when (setting.lowercase()) {
            EVERYONE -> true
            CONTACTS -> viewerIsContact()
            else -> false
        }
    }
}
