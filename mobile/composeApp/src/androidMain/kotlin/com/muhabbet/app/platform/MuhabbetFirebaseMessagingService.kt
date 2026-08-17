package com.muhabbet.app.platform

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
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
        val conversationType = message.data[MuhabbetNotifications.EXTRA_CONVERSATION_TYPE]
            ?: MuhabbetNotifications.CONVERSATION_TYPE_DIRECT
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
        val groupKey = MuhabbetNotifications.groupKey(conversationId)
        val accentColor = MuhabbetNotifications.accentColor(context)
        val openPendingIntent =
            MuhabbetNotifications.openConversationIntent(context, conversationId, senderName)

        // Build the individual message notification
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(accentColor)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .setGroup(groupKey)
            .addAction(
                MuhabbetNotifications.replyAction(
                    context = context,
                    conversationId = conversationId,
                    senderName = senderName,
                    conversationType = conversationType
                )
            )
            .build()

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)

        // Summary notification for grouping (required for grouped notifications on API < 24
        // and for the bundled notification on API 24+)
        val summaryNotification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(accentColor)
            .setContentTitle(senderName)
            .setContentText(context.getString(R.string.notification_new_messages))
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .build()

        manager.notify(MuhabbetNotifications.summaryNotificationId(conversationId), summaryNotification)
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
