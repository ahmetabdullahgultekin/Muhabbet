package com.muhabbet.app.platform

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.muhabbet.app.R
import com.muhabbet.app.data.repository.MessageRepository
import com.muhabbet.app.di.androidPlatformModule
import com.muhabbet.app.di.bootstrapOrReuseKoin
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Sends the text typed into a notification's reply field, and then says what actually happened.
 *
 * Until #510 it did neither: it wrote the message body to logcat and replaced the notification with
 * the words "Yanıt gönderildi" — *Reply sent* — unconditionally. Nothing was ever sent. That is the
 * worst shape a stub can take, because the claim is made in the one place the user cannot check:
 * they reply from the lock screen, read the confirmation, and put the phone down.
 *
 * Two rules follow from that and neither is negotiable:
 *
 *  - **The outcome is reported, never assumed.** [R.string.notification_reply_sent] is posted only
 *    on the branch where the server answered 2xx. Every other branch — rejected, offline, out of
 *    time, no conversation id — posts the failure, with the typed text kept in the expanded view so
 *    it is not lost, and the reply action put back so the next tap is a retry rather than a retype.
 *  - **The message body is never logged.** It is the user's private text and `Log` here writes
 *    through `println`, which release builds do not strip (#354). Failures log the reason only.
 *
 * REST rather than the WebSocket, deliberately. A broadcast can be delivered into a process that
 * was started for it alone — the app may have been swiped away hours earlier — so there is usually
 * no socket to reach, and standing one up, authenticating it and tearing it down again inside the
 * ten seconds a receiver gets is a worse bet than one POST that either returns or does not.
 */
class NotificationReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MuhabbetNotifications.ACTION_REPLY) return

        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(MuhabbetNotifications.KEY_REPLY_TEXT)
            ?.toString()
            ?.trim()
            .orEmpty()
        val conversationId = intent.getStringExtra(MuhabbetNotifications.EXTRA_CONVERSATION_ID)
        val senderName = intent.getStringExtra(MuhabbetNotifications.EXTRA_SENDER_NAME)
        val conversationType = intent.getStringExtra(MuhabbetNotifications.EXTRA_CONVERSATION_TYPE)

        // Two contexts on purpose. Koin is given the plain application context, because that is
        // what it stores for the lifetime of the process and a locale-overridden wrapper has no
        // business outliving this broadcast. Everything user-visible is resolved against the
        // localized one — see MuhabbetNotifications.localized.
        val appContext = context.applicationContext
        val uiContext = MuhabbetNotifications.localized(appContext)

        if (replyText.isEmpty()) {
            // Nothing was typed, so there is nothing to send and nothing to claim. The system keeps
            // the entry showing a sending spinner until something replaces or removes it, so clear
            // it rather than leave a reply that appears to be in flight forever.
            notificationManager(uiContext).cancel(
                MuhabbetNotifications.notificationId(conversationId)
            )
            return
        }

        if (conversationId.isNullOrBlank()) {
            // A notification from a build that did not carry the id, or one the sender could not
            // resolve. The text cannot be delivered and must not be reported as delivered; no retry
            // is offered either, because a retry would fail for the same reason.
            Log.w(TAG, "Inline reply has no conversation id; cannot send")
            postOutcome(
                context = uiContext,
                sent = false,
                replyText = replyText,
                conversationId = null,
                senderName = senderName,
                conversationType = conversationType
            )
            return
        }

        // Keeps the process alive across the call. The system gives a receiver roughly ten seconds
        // in total; SEND_TIMEOUT_MS bounds the network call inside that so the notification is
        // still updated on a slow network rather than the whole receiver being killed mid-request.
        val pendingResult = goAsync()
        scope.launch {
            var sent = false
            try {
                sent = withTimeoutOrNull(SEND_TIMEOUT_MS) {
                    runCatchingCancellable {
                        bootstrapOrReuseKoin(androidPlatformModule(appContext))
                            .get<MessageRepository>()
                            .sendMessage(conversationId, replyText)
                    }.onFailure { e ->
                        // The reason, never the text. See the class comment.
                        Log.w(TAG, "Inline reply failed: ${e::class.simpleName}: ${e.message}")
                    }.isSuccess
                } ?: run {
                    Log.w(TAG, "Inline reply timed out after ${SEND_TIMEOUT_MS}ms")
                    false
                }
            } finally {
                postOutcome(
                    context = uiContext,
                    sent = sent,
                    replyText = replyText,
                    conversationId = conversationId,
                    senderName = senderName,
                    conversationType = conversationType
                )
                // Exactly once, and after the notification is posted: the process can be reclaimed
                // as soon as this returns.
                pendingResult.finish()
            }
        }
    }

    /**
     * Replaces the conversation's tray entry with what actually happened.
     *
     * On failure the typed text goes into the expanded view and the reply action is rebuilt, so the
     * user can see what they wrote and send it again without retyping it.
     */
    private fun postOutcome(
        context: Context,
        sent: Boolean,
        replyText: String,
        conversationId: String?,
        senderName: String?,
        conversationType: String?
    ) {
        val title = senderName?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.notification_app_name)
        val statusText = context.getString(
            if (sent) R.string.notification_reply_sent else R.string.notification_reply_failed
        )

        val builder = NotificationCompat.Builder(
            context,
            MuhabbetNotifications.channelId(conversationType)
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(MuhabbetNotifications.accentColor(context))
            .setContentTitle(title)
            .setContentText(statusText)
            .setGroup(MuhabbetNotifications.groupKey(conversationId))
            .setAutoCancel(true)
            .setContentIntent(
                MuhabbetNotifications.openConversationIntent(context, conversationId, senderName)
            )

        if (!sent) {
            builder
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(replyText)
                        .setSummaryText(statusText)
                )
                // Only worth offering where a retry could actually go somewhere.
                .apply {
                    if (!conversationId.isNullOrBlank()) {
                        addAction(
                            MuhabbetNotifications.replyAction(
                                context = context,
                                conversationId = conversationId,
                                senderName = senderName,
                                conversationType = conversationType
                            )
                        )
                    }
                }
        }

        notificationManager(context).notify(
            MuhabbetNotifications.notificationId(conversationId),
            builder.build()
        )
    }

    private fun notificationManager(context: Context): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private companion object {
        const val TAG = "MuhabbetReply"

        /**
         * Comfortably inside the ~10s a `BroadcastReceiver` is given, leaving room for Koin to
         * start, the token to be refreshed if it has expired, and the notification to be posted.
         */
        const val SEND_TIMEOUT_MS = 7_000L

        /**
         * Deliberately not tied to a receiver instance: the system throws the instance away as soon
         * as `onReceive` returns, and `goAsync` keeps only the *process* alive, not the object. A
         * scope owned by the class outlives both, which is what the coroutine needs.
         */
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
