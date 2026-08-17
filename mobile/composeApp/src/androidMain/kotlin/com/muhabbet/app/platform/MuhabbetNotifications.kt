package com.muhabbet.app.platform

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.muhabbet.app.MainActivity
import com.muhabbet.app.R
import java.util.Locale

/**
 * The pieces of a message notification that more than one class has to agree on.
 *
 * Two do: [MuhabbetFirebaseMessagingService] posts the notification, and
 * [NotificationReplyReceiver] re-posts it once it knows whether the inline reply actually went.
 * When the reply action, the channel and the tray-entry id were built inline in both, the receiver
 * quietly disagreed with the service — it re-posted every reply on the direct-message channel, so a
 * group reply landed on the wrong channel and outside its own bundle (#510).
 */
internal object MuhabbetNotifications {

    const val CHANNEL_GROUP_ID = "muhabbet_messages_group"
    const val CHANNEL_ID_DM = "muhabbet_dm_messages"
    const val CHANNEL_ID_GROUP = "muhabbet_group_messages"

    /** Values of the `conversationType` push-data key. See `PushNotificationComposer` on the server. */
    const val CONVERSATION_TYPE_GROUP = "GROUP"
    const val CONVERSATION_TYPE_DIRECT = "DIRECT"

    const val KEY_REPLY_TEXT = "key_reply_text"

    /** Must stay in step with the `<receiver>` intent filter in AndroidManifest.xml. */
    const val ACTION_REPLY = "com.muhabbet.app.ACTION_REPLY"

    const val EXTRA_CONVERSATION_ID = "conversationId"
    const val EXTRA_SENDER_NAME = "senderName"
    const val EXTRA_CONVERSATION_TYPE = "conversationType"

    /** Matches `PushNotificationComposer.KEY_SENDER_ID` on the server; unused before #595/#623. */
    const val EXTRA_SENDER_ID = "senderId"

    /** Matches `PushNotificationComposer.KEY_SENT_AT` — epoch millis of the message, not delivery. */
    const val EXTRA_SENT_AT = "sentAt"

    /** Matches `PushNotificationComposer.KEY_GROUP_NAME` — the raw name, not the composed title. */
    const val EXTRA_GROUP_NAME = "groupName"

    private const val TAG = "MuhabbetNotifications"

    fun channelId(conversationType: String?): String =
        if (conversationType == CONVERSATION_TYPE_GROUP) CHANNEL_ID_GROUP else CHANNEL_ID_DM

    /**
     * One tray entry per conversation. Null hashes to 0, which is stable and therefore still
     * addressable — a notification posted without a conversation id can still be replaced.
     */
    fun notificationId(conversationId: String?): Int = conversationId.hashCode()

    fun accentColor(context: Context): Int =
        ContextCompat.getColor(context, R.color.muhabbet_notification_accent)

    /** The Activity-launching intent shared by a direct tap and a Conversations-section shortcut. */
    private fun conversationTargetIntent(
        context: Context,
        conversationId: String?,
        senderName: String?
    ): Intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        if (conversationId != null) {
            putExtra(EXTRA_CONVERSATION_ID, conversationId)
            putExtra(EXTRA_SENDER_NAME, senderName)
        }
    }

    /** Opens the app on the named conversation. */
    fun openConversationIntent(
        context: Context,
        conversationId: String?,
        senderName: String?
    ): PendingIntent {
        val intent = conversationTargetIntent(context, conversationId, senderName)
        return PendingIntent.getActivity(
            context,
            notificationId(conversationId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** One [Person] per sender, reused for both a `MessagingStyle` message and its [Person] on the shortcut. */
    fun senderPerson(senderName: String, senderId: String?): Person =
        Person.Builder()
            .setName(senderName)
            .apply { if (!senderId.isNullOrBlank()) setKey(senderId) }
            .build()

    /**
     * Registers (or refreshes) a long-lived dynamic shortcut for one conversation.
     *
     * `NotificationCompat.Builder.setShortcutId` only promotes a notification into Android 11+'s
     * *Conversations* section when a matching **long-lived** shortcut already exists for that id —
     * an id with nothing behind it is silently ignored, so this has to run before that notification
     * is built. `pushDynamicShortcut` (not `addDynamicShortcuts`) is the one meant to be called on
     * every incoming message: it is exempt from the rate limit `updateShortcuts` hits, and it is
     * also how an existing shortcut's rank gets refreshed, since Android evicts the
     * least-recently-pushed shortcut once a device's per-activity cap is reached.
     *
     * Never allowed to fail loudly: a shortcut is what upgrades the notification, not what the
     * notification depends on existing to be posted at all.
     */
    fun pushConversationShortcut(
        context: Context,
        conversationId: String,
        label: String,
        sender: Person
    ) {
        runCatching {
            val intent = conversationTargetIntent(context, conversationId, sender.name?.toString())
                .apply { action = Intent.ACTION_VIEW }

            val shortcut = ShortcutInfoCompat.Builder(context, conversationId)
                .setShortLabel(label)
                .setLongLived(true)
                .setIntent(intent)
                .setPerson(sender)
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .build()

            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        }.onFailure { e ->
            Log.w(TAG, "Could not push conversation shortcut: ${e::class.simpleName}: ${e.message}")
        }
    }

    /**
     * The "Reply" action, with the text field the system draws inside the notification.
     *
     * `FLAG_MUTABLE` is required and not an oversight: the system writes the typed text into this
     * intent, which an immutable one forbids.
     */
    fun replyAction(
        context: Context,
        conversationId: String?,
        senderName: String?,
        conversationType: String?
    ): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(KEY_REPLY_TEXT)
            .setLabel(context.getString(R.string.notification_reply_hint))
            .build()

        val replyIntent = Intent(context, NotificationReplyReceiver::class.java).apply {
            action = ACTION_REPLY
            putExtra(EXTRA_CONVERSATION_ID, conversationId)
            putExtra(EXTRA_SENDER_NAME, senderName)
            putExtra(EXTRA_CONVERSATION_TYPE, conversationType)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId(conversationId),
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            context.getString(R.string.notification_reply_action),
            pendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()
    }

    /**
     * The same context with the language the user picked inside the app applied.
     *
     * A service and a receiver are handed the base application context, whose locale is the
     * device's. `MainActivity` overrides it per Activity for the UI, which reaches nothing outside
     * the Activity — so without this a user running the app in English on a Turkish phone would get
     * a Turkish notification. Returns the receiver unchanged when no preference has been set, which
     * is the common case and correctly means "follow the device".
     */
    fun localized(context: Context): Context {
        val language = context
            .getSharedPreferences("muhabbet_prefs", Context.MODE_PRIVATE)
            .getString("app_language", null)
            ?.takeIf { it.isNotBlank() }
            ?: return context
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(Locale(language))
        return context.createConfigurationContext(configuration)
    }
}
