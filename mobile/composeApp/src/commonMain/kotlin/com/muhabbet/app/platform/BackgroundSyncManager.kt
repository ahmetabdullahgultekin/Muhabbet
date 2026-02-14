package com.muhabbet.app.platform

/**
 * Platform-specific background sync manager.
 *
 * Android: Uses WorkManager for periodic background sync.
 * iOS: Uses BGAppRefreshTask for background fetch.
 *
 * Schedules periodic sync that fetches messages missed while app was inactive.
 */
expect class BackgroundSyncManager {
    fun schedulePeriodicSync()
    fun cancelPeriodicSync()
}
