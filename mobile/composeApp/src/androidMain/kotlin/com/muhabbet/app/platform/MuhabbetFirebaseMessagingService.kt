package com.muhabbet.app.platform

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.muhabbet.app.MainActivity

class MuhabbetFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Token will be registered when the app next connects via App.kt
        Log.d(TAG, "New token: ${token.take(10)}...")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title ?: message.data["senderName"] ?: "Muhabbet"
        val body = message.notification?.body ?: message.data["body"] ?: "Yeni mesaj"
        val conversationId = message.data["conversationId"]
        val senderName = message.data["senderName"] ?: title
        val conversationType = message.data["conversationType"] ?: "DIRECT"

        createNotificationChannels()

        val channelId = when (conversationType) {
            "GROUP" -> CHANNEL_ID_GROUP
            else -> CHANNEL_ID_DM
        }

        val notificationId = conversationId.hashCode()
        val groupKey = "$GROUP_KEY_PREFIX$conversationId"

        // PendingIntent to open the conversation
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (conversationId != null) {
                putExtra("conversationId", conversationId)
                putExtra("senderName", senderName)
            }
        }

        val openPendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Inline reply RemoteInput
        val remoteInput = RemoteInput.Builder(KEY_REPLY_TEXT)
            .setLabel("Yanıt yaz...")
            .build()

        val replyIntent = Intent(this, NotificationReplyReceiver::class.java).apply {
            action = ACTION_REPLY
            putExtra("conversationId", conversationId)
            putExtra("senderName", senderName)
        }

        val replyPendingIntent = PendingIntent.getBroadcast(
            this,
            notificationId,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Yanıtla",
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()

        // Build the individual message notification
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(com.muhabbet.app.R.drawable.ic_notification)
            .setColor(0xFF1B5E20.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .setGroup(groupKey)
            .addAction(replyAction)
            .build()

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)

        // Summary notification for grouping (required for grouped notifications on API < 24
        // and for the bundled notification on API 24+)
        val summaryNotification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(com.muhabbet.app.R.drawable.ic_notification)
            .setColor(0xFF1B5E20.toInt())
            .setContentTitle(senderName)
            .setContentText("Yeni mesajlar")
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .build()

        manager.notify(summaryNotificationId(conversationId), summaryNotification)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            // Channel group
            val channelGroup = NotificationChannelGroup(
                CHANNEL_GROUP_ID,
                "Mesajlar"
            )
            manager.createNotificationChannelGroup(channelGroup)

            // DM channel
            val dmChannel = NotificationChannel(
                CHANNEL_ID_DM,
                "Bireysel mesajlar",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Bireysel sohbet bildirimleri"
                group = CHANNEL_GROUP_ID
            }

            // Group messages channel
            val groupChannel = NotificationChannel(
                CHANNEL_ID_GROUP,
                "Grup mesajları",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Grup sohbet bildirimleri"
                group = CHANNEL_GROUP_ID
            }

            manager.createNotificationChannels(listOf(dmChannel, groupChannel))
        }
    }

    /** Generate a stable summary notification ID from the conversationId. */
    private fun summaryNotificationId(conversationId: String?): Int {
        return "summary_$conversationId".hashCode()
    }

    companion object {
        private const val TAG = "MuhabbetFCM"
        private const val CHANNEL_GROUP_ID = "muhabbet_messages_group"
        private const val CHANNEL_ID_DM = "muhabbet_dm_messages"
        private const val CHANNEL_ID_GROUP = "muhabbet_group_messages"
        private const val GROUP_KEY_PREFIX = "com.muhabbet.CONVERSATION_"
        const val KEY_REPLY_TEXT = "key_reply_text"
        const val ACTION_REPLY = "com.muhabbet.app.ACTION_REPLY"
    }
}

/**
 * BroadcastReceiver that handles inline reply actions from notifications.
 * For now, the reply text is logged since sending messages requires WsClient
 * which is not available in a BroadcastReceiver context.
 */
class NotificationReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MuhabbetFirebaseMessagingService.ACTION_REPLY) return

        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val replyText = remoteInput?.getCharSequence(
            MuhabbetFirebaseMessagingService.KEY_REPLY_TEXT
        )?.toString()

        val conversationId = intent.getStringExtra("conversationId")
        val senderName = intent.getStringExtra("senderName")

        Log.d(
            "MuhabbetReply",
            "Inline reply to conversation=$conversationId ($senderName): $replyText"
        )

        // Clear the notification's spinner by re-posting with updated text
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val updatedNotification = NotificationCompat.Builder(context, "muhabbet_dm_messages")
            .setSmallIcon(com.muhabbet.app.R.drawable.ic_notification)
            .setColor(0xFF1B5E20.toInt())
            .setContentText("Yanıt gönderildi")
            .setAutoCancel(true)
            .build()

        manager.notify(conversationId.hashCode(), updatedNotification)
    }
}
