package com.muhabbet.app.platform

import android.content.Context
import java.security.SecureRandom

/**
 * A random value, generated once per install, that this app puts on every intent it builds to
 * open a conversation (notification taps and conversation shortcuts).
 *
 * `MainActivity` trusts an intent's extras only when they carry this value; see
 * [ChatIntentResolver]. It is kept in its own private preferences file, which other apps cannot
 * read and which `data_extraction_rules.xml` keeps off backups and device transfers. Persisted
 * rather than held in memory because a notification or a pinned shortcut outlives the process
 * that created it.
 */
internal object NotificationLaunchToken {

    const val EXTRA_LAUNCH_TOKEN = "com.muhabbet.app.extra.LAUNCH_TOKEN"

    private const val PREFS_NAME = "muhabbet_launch_token"
    private const val KEY_TOKEN = "token"
    private const val TOKEN_BYTES = 32

    @Volatile
    private var cached: String? = null

    fun of(context: Context): String {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: loadOrCreate(context).also { cached = it }
        }
    }

    private fun loadOrCreate(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }?.let { return it }

        val bytes = ByteArray(TOKEN_BYTES).also { SecureRandom().nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        prefs.edit().putString(KEY_TOKEN, token).commit()
        return token
    }
}
