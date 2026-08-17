package com.muhabbet.app.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * How long the app may sit in the background before App Lock (#378) demands re-authentication —
 * the grace period.
 *
 * A grace period is a product decision, not a technical one: re-locking on every glance away is
 * unusable (switching to check a notification, answering a phone call), and never re-locking is
 * theatre — a lock that only ever fires once, at cold start, protects nothing on a phone someone
 * picks up mid-session. This file is the one place that decision lives, as three named, storable
 * options rather than a free-form duration, so the choice stays exhaustive and each value has a
 * name a user can read in Settings (`app_lock_immediately` / `_1_minute` / `_30_minutes`).
 *
 * [DEFAULT] is [ONE_MINUTE], not [IMMEDIATELY]: a lock that reopens the biometric prompt every
 * single time the screen turns off — which includes an incoming call, switching to type a reply in
 * another app, or the OS just dimming the display — trains a user to dismiss it without looking,
 * which is worse than no lock. [ONE_MINUTE] is what enabling App Lock persists on first use;
 * `AppLockScreen` lets the user tighten it to [IMMEDIATELY] or loosen it to [THIRTY_MINUTES]
 * afterwards.
 */
object AppLockTimeout {
    /** Re-lock the instant the app leaves the foreground. The strictest option. */
    const val IMMEDIATELY = "immediately"

    /** Re-lock only if the app was backgrounded for at least a minute. */
    const val ONE_MINUTE = "1m"

    /** Re-lock only if the app was backgrounded for at least half an hour. */
    const val THIRTY_MINUTES = "30m"

    /** The option persisted the first time App Lock is turned on — see the class doc above. */
    const val DEFAULT = ONE_MINUTE

    /**
     * The grace period a stored option key maps to. An unrecognised or missing key (a value that
     * predates this constant, or [TokenStorage.getAppLockTimeout] never having been set) falls back
     * to [IMMEDIATELY] — the strict end, not the lenient one, because a corrupted or absent setting
     * for a security feature must fail closed.
     */
    fun graceFor(option: String?): Duration = when (option) {
        ONE_MINUTE -> 1.minutes
        THIRTY_MINUTES -> 30.minutes
        else -> 0.seconds
    }
}
