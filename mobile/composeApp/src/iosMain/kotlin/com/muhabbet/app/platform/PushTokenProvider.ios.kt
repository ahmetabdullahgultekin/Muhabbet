package com.muhabbet.app.platform

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSLog
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIApplication
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class IosPushTokenProvider : PushTokenProvider {
    override suspend fun getToken(): String? {
        // Return cached token if already available
        cachedToken?.let { return it }

        // Also check persisted token from NSUserDefaults
        val persisted = NSUserDefaults.standardUserDefaults.stringForKey(PUSH_TOKEN_KEY)
        if (persisted != null) {
            cachedToken = persisted
            return persisted
        }

        // Request notification permission
        val granted = requestNotificationPermission()
        if (!granted) {
            NSLog("IosPushTokenProvider: notification permission denied")
            return null
        }

        // Register for remote notifications — token delivered via AppDelegate callback
        UIApplication.sharedApplication.registerForRemoteNotifications()

        // Wait briefly for token delivery (AppDelegate sets cachedToken)
        return suspendCancellableCoroutine { cont ->
            val checkInterval = 100L // ms
            val maxWait = 5000L // 5 seconds
            var elapsed = 0L

            fun check() {
                if (cachedToken != null) {
                    cont.resume(cachedToken)
                } else if (elapsed >= maxWait) {
                    NSLog("IosPushTokenProvider: token delivery timeout")
                    cont.resume(null)
                } else {
                    elapsed += checkInterval
                    platform.darwin.dispatch_after(
                        platform.darwin.dispatch_time(
                            platform.darwin.DISPATCH_TIME_NOW,
                            (checkInterval * 1_000_000) // ns
                        ),
                        platform.darwin.dispatch_get_main_queue()
                    ) { check() }
                }
            }
            check()
        }
    }

    private suspend fun requestNotificationPermission(): Boolean = suspendCoroutine { cont ->
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionBadge or UNAuthorizationOptionSound
        ) { granted, _ ->
            cont.resume(granted)
        }
    }

    companion object {
        private const val PUSH_TOKEN_KEY = "muhabbet_push_token"

        var cachedToken: String? = null
            set(value) {
                field = value
                // Persist token to survive app restarts
                value?.let {
                    NSUserDefaults.standardUserDefaults.setObject(it, forKey = PUSH_TOKEN_KEY)
                }
            }

        /**
         * Called from iOS AppDelegate.didRegisterForRemoteNotificationsWithDeviceToken.
         * The AppDelegate should convert deviceToken NSData to hex string and call this.
         */
        fun onTokenReceived(token: String) {
            NSLog("IosPushTokenProvider: token received")
            cachedToken = token
        }
    }
}
