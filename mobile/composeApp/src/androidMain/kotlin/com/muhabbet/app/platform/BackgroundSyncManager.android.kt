package com.muhabbet.app.platform

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.data.repository.MessageRepository
import com.muhabbet.app.util.Log
import kotlin.time.Clock
import kotlinx.datetime.Instant
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes

actual class BackgroundSyncManager(private val context: Context) {

    actual fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<MessageSyncWorker>(
            15, TimeUnit.MINUTES
        ).setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }

    actual fun cancelPeriodicSync() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    companion object {
        private const val WORK_NAME = "muhabbet_message_sync"
    }
}

/**
 * WorkManager worker that syncs missed messages in the background.
 * Fetches messages since last sync timestamp and caches them locally.
 */
class MessageSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val messageRepository: MessageRepository by inject()
    private val tokenStorage: TokenStorage by inject()

    override suspend fun doWork(): Result {
        val userId = tokenStorage.getUserId() ?: return Result.failure()

        val lastSync = tokenStorage.getLastSyncTimestamp()
            ?: Clock.System.now().minus(60.minutes).toString()

        return try {
            messageRepository.syncMessagesSince(lastSync)
            // Update last sync timestamp to now
            tokenStorage.setLastSyncTimestamp(Clock.System.now().toString())
            Result.success()
        } catch (e: Exception) {
            // No user to tell — this runs with the app closed — but never silent. It used to be:
            // a failed sync now retries WITHOUT advancing the timestamp (previously an error
            // decoded to an empty page, the worker "succeeded", and the timestamp moved past
            // messages it had never fetched), and a persistent 401 would otherwise be an invisible
            // 15-minute retry loop.
            Log.w(TAG, "Background message sync failed, will retry: $e")
            Result.retry()
        }
    }
}

private const val TAG = "MessageSyncWorker"
