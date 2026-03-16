package com.muhabbet.app.platform

import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.data.repository.MessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.time.Clock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.minutes
import platform.Foundation.NSLog

/**
 * iOS background sync manager.
 *
 * Uses BGAppRefreshTask for periodic background fetch.
 * The actual BGTaskScheduler registration must happen in the iOS app delegate.
 * This class provides the sync logic that the task handler should call.
 */
actual class BackgroundSyncManager : KoinComponent {

    private val messageRepository: MessageRepository by inject()
    private val tokenStorage: TokenStorage by inject()

    actual fun schedulePeriodicSync() {
        // BGTaskScheduler.shared.register() must be called from the Swift/ObjC app delegate.
        // This Kotlin method is intentionally a no-op: iOS background task scheduling
        // requires native bridge code that calls BGTaskScheduler from Swift.
        // To enable background sync on iOS, register BGAppRefreshTask in AppDelegate.swift
        // and call performSync() from the task handler.
        NSLog(
            "BackgroundSyncManager: schedulePeriodicSync() has no effect on iOS. " +
            "Register BGAppRefreshTask in AppDelegate.swift and call performSync() from the task handler."
        )
    }

    actual fun cancelPeriodicSync() {
        // BGTaskScheduler.shared.cancel(taskRequestWithIdentifier:) in Swift.
    }

    /**
     * Performs the actual sync. Called from iOS BGAppRefreshTask handler.
     */
    fun performSync(onComplete: (Boolean) -> Unit) {
        val userId = tokenStorage.getUserId()
        if (userId == null) {
            onComplete(false)
            return
        }

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val lastSync = tokenStorage.getLastSyncTimestamp()
                    ?: Clock.System.now().minus(60.minutes).toString()
                messageRepository.syncMessagesSince(lastSync)
                tokenStorage.setLastSyncTimestamp(Clock.System.now().toString())
                onComplete(true)
            } catch (_: Exception) {
                onComplete(false)
            }
        }
    }
}
