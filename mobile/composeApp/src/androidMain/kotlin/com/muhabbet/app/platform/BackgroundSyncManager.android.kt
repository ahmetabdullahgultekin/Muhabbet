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
            val messages = messageRepository.syncMessagesSince(lastSync)
            // Update last sync timestamp to now
            tokenStorage.setLastSyncTimestamp(Clock.System.now().toString())
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
