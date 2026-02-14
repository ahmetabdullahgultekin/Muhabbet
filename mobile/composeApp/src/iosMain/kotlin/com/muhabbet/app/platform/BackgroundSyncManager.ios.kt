package com.muhabbet.app.platform

import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.data.repository.MessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.minutes

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
        // BGTaskScheduler.shared.register() must be called from Swift/ObjC app delegate.
        // This is a placeholder — iOS background task scheduling requires native code.
        // The sync logic is available via performSync() for the native task handler.
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
