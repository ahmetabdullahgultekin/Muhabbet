package com.muhabbet.app.platform

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.LocusIdCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.muhabbet.app.R
import com.muhabbet.app.data.repository.MessageRepository
import com.muhabbet.app.data.repository.PushTokenRegistrar
import com.muhabbet.app.di.androidPlatformModule
import com.muhabbet.app.di.bootstrapOrReuseKoin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MuhabbetFirebaseMessagingService : FirebaseMessagingService() {

    // Not viewModelScope/lifecycleScope — a FirebaseMessagingService has neither. Cancelled in
    // onDestroy so a registration in flight cannot outlive the service.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New token: ${token.take(10)}...")
        // Used to be a comment ("Token will be registered when the app next connects via
        // App.kt") with no code behind it, so a rotated token was never re-sent and the row on
        // the server just went stale (#398). The system can deliver this callback before
        // MainActivity has ever run in the process, so Koin may not exist yet — bootstrapOrReuseKoin
        // starts it here if needed, the same way App.kt does.
        serviceScope.launch {
            bootstrapOrReuseKoin(androidPlatformModule(applicationContext))
                .get<PushTokenRegistrar>()
                .registerIfLoggedIn(token)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        // Localized against the language chosen in the app, not only the device's — see
        // MuhabbetNotifications.localized.
        val context = MuhabbetNotifications.localized(this)

        val title = message.notification?.title
            ?: message.data["senderName"]
            ?: context.getString(R.string.notification_app_name)
        val body = message.notification?.body
            ?: message.data["body"]
            ?: context.getString(R.string.notification_new_message)
        val conversationId = message.data[MuhabbetNotifications.EXTRA_CONVERSATION_ID]
        val senderName = message.data[MuhabbetNotifications.EXTRA_SENDER_NAME] ?: title
        val senderId = message.data[MuhabbetNotifications.EXTRA_SENDER_ID]
        val conversationType = message.data[MuhabbetNotifications.EXTRA_CONVERSATION_TYPE]
            ?: MuhabbetNotifications.CONVERSATION_TYPE_DIRECT
        val groupName = message.data[MuhabbetNotifications.EXTRA_GROUP_NAME]?.takeIf { it.isNotBlank() }
        // Falls back to arrival time for a message from a build that predates #595's payload change
        // — not exact for one that queued, but no worse than the single-line notification it replaces.
        val sentAtMillis = message.data[MuhabbetNotifications.EXTRA_SENT_AT]?.toLongOrNull()
            ?: System.currentTimeMillis()
        val messageId = message.data[MuhabbetNotifications.EXTRA_MESSAGE_ID]

        // #596: this is the one case where delivery is certain — the notification is about to be
        // posted below — and, before this, the one case that never told the sender so. No socket to
        // reach for here (that is why FCM was involved), so this is the REST ack #596 added
        // (`POST /messages/{id}/delivered`), same shape as onNewToken's bootstrapOrReuseKoin below:
        // this callback can run before MainActivity ever has in this process.
        if (messageId != null) {
            serviceScope.launch {
                bootstrapOrReuseKoin(androidPlatformModule(applicationContext))
                    .get<MessageRepository>()
                    .markDelivered(messageId)
            }
        }

        createNotificationChannels()

        val channelId = MuhabbetNotifications.channelId(conversationType)
        val notificationId = MuhabbetNotifications.notificationId(conversationId)
        val accentColor = MuhabbetNotifications.accentColor(context)
        val openPendingIntent =
            MuhabbetNotifications.openConversationIntent(context, conversationId, senderName)
        val sender = MuhabbetNotifications.senderPerson(senderName, senderId)

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // The history that makes #623's second message stack instead of replace. A MessagingStyle is
        // built from the messages added to it, and this service can be started fresh for every push
        // — there is no in-process buffer that would survive that. What does survive a fresh process
        // is the notification already sitting in the shade: read it back and extend its style rather
        // than starting a new one, so three pushes across three separate process lifetimes still add
        // up to three lines instead of one.
        val existingStyle = manager.activeNotifications
            .firstOrNull { it.id == notificationId }
            ?.notification
            ?.let { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it) }

        // Never actually shown: only a message THIS device sent would render under this identity,
        // and everything reaching this method was received, not sent. Required anyway —
        // MessagingStyle's constructor needs a "me" to compare senders against.
        val me = Person.Builder().setName(context.getString(R.string.notification_app_name)).build()

        val messagingStyle = (existingStyle ?: NotificationCompat.MessagingStyle(me))
            .setGroupConversation(conversationType == MuhabbetNotifications.CONVERSATION_TYPE_GROUP)
            .addMessage(body, sentAtMillis, sender)

        if (conversationType == MuhabbetNotifications.CONVERSATION_TYPE_GROUP && groupName != null) {
            messagingStyle.conversationTitle = groupName
        }

        // setGroup + setGroupSummary is gone (#623). It bundled this conversation's one tray entry
        // with a second, static "New messages" notification under a group key unique to that same
        // conversation — but a group of one real notification has nothing to summarize, which is
        // exactly why posting again only ever replaced the real entry instead of stacking. The
        // MessagingStyle history above says what a generic summary line could not. Seeing several
        // DIFFERENT conversations at once is a separate concern Android's own per-app bundling
        // already covers without any setGroup call here.
        if (!conversationId.isNullOrBlank()) {
            // Must run before the notification below: setShortcutId only promotes a notification
            // into the Conversations section when the shortcut it names already exists.
            MuhabbetNotifications.pushConversationShortcut(
                context = context,
                conversationId = conversationId,
                label = groupName ?: senderName,
                sender = sender
            )
        }

        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(accentColor)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(messagingStyle)
            .setWhen(sentAtMillis)
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .addAction(
                MuhabbetNotifications.replyAction(
                    context = context,
                    conversationId = conversationId,
                    senderName = senderName,
                    conversationType = conversationType
                )
            )

        if (!conversationId.isNullOrBlank()) {
            notificationBuilder
                .setShortcutId(conversationId)
                .setLocusId(LocusIdCompat(conversationId))
        }

        manager.notify(notificationId, notificationBuilder.build())
    }

    /**
     * Channel names and descriptions stay literal here, unlike the rest of this file's text.
     * Android records a channel's name when the channel is first created and ignores it on every
     * later call, so moving these into resources would change nothing on any device the app is
     * already installed on. Translating them needs new channel ids and a migration, not a string.
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            // Channel group
            val channelGroup = NotificationChannelGroup(
                MuhabbetNotifications.CHANNEL_GROUP_ID,
                "Mesajlar"
            )
            manager.createNotificationChannelGroup(channelGroup)

            // DM channel
            val dmChannel = NotificationChannel(
                MuhabbetNotifications.CHANNEL_ID_DM,
                "Bireysel mesajlar",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Bireysel sohbet bildirimleri"
                group = MuhabbetNotifications.CHANNEL_GROUP_ID
            }

            // Group messages channel
            val groupChannel = NotificationChannel(
                MuhabbetNotifications.CHANNEL_ID_GROUP,
                "Grup mesajları",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Grup sohbet bildirimleri"
                group = MuhabbetNotifications.CHANNEL_GROUP_ID
            }

            manager.createNotificationChannels(listOf(dmChannel, groupChannel))
        }
    }

    companion object {
        private const val TAG = "MuhabbetFCM"
    }
}
