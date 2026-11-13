package com.muhabbet.app.platform

import android.content.Context
import java.util.UUID

/**
 * Proves that an intent asking `MainActivity` to open a conversation came from one of this app's own
 * notifications (#815).
 *
 * `MainActivity` is exported — it is the launcher — so any installed app can start it with any
 * extras it likes. Before this, a stranger's intent carrying `conversationId` (and a display name of
 * its choosing) opened that conversation under that title. The notification intents now carry this
 * token, and an intent without it is treated as a plain launch.
 *
 * Stored in app-private preferences, which no other app can read, and kept across process death so a
 * notification posted before the process was killed still opens its chat when tapped.
 */
internal object NotificationLaunchToken {

    private const val PREFS = "muhabbet_notification_launch"
    private const val KEY = "token"

    fun current(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY, it).apply()
        }
    }

    fun matches(context: Context, candidate: String?): Boolean =
        candidate != null && candidate == current(context)
}
