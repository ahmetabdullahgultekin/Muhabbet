package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.ContentType
import java.util.Locale

/**
 * Every user-visible word the server puts in a push, looked up by locale.
 *
 * Push text is the one place the backend writes for a human to read, so the project's "no
 * hardcoded strings" rule applies to it exactly as it does to the app — #469 shipped a Turkish
 * literal to an English-locale device. Messaging declares the lookup as its own out-port and the
 * adapter behind it owns the resource bundles.
 *
 * The [Locale] is a parameter rather than ambient state because the right locale is the
 * *recipient's*, not the server's and not the sender's. Today no device row carries one, so every
 * caller passes null and gets the fallback; when the column lands, only the call sites change.
 */
interface NotificationTextPort {

    /** "📷 Fotoğraf", "🎤 Sesli mesaj", … — what a non-text message reads as in the tray. */
    fun contentSummary(contentType: ContentType, locale: Locale): String

    /** Joins a sender and a group into one title, e.g. "Ayşe · Aile". */
    fun groupTitle(senderName: String, groupName: String, locale: Locale): String

    /** Stands in for a sender whose profile carries no display name. */
    fun unknownSender(locale: Locale): String
}
