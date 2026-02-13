package com.muhabbet.app.platform

import platform.UIKit.UIApplication
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class IosPushTokenProvider : PushTokenProvider {
    override suspend fun getToken(): String? {
        // Request notification permission first
        requestNotificationPermission()
        // Register for remote notifications
        UIApplication.sharedApplication.registerForRemoteNotifications()
        // The token will be delivered via AppDelegate.didRegisterForRemoteNotificationsWithDeviceToken
        // For now return the cached token if available
        return cachedToken
    }

    private suspend fun requestNotificationPermission(): Boolean = suspendCoroutine { cont ->
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionBadge or UNAuthorizationOptionSound
        ) { granted, _ ->
            cont.resume(granted)
        }
    }

    companion object {
        var cachedToken: String? = null
    }
}
