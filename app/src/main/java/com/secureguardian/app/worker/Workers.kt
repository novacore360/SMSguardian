package com.secureguardian.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.secureguardian.app.data.local.dao.MessageDao
import com.secureguardian.app.data.repository.AppRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Deletes messages older than 24 hours from local DB and triggers Supabase cleanup
 */
@HiltWorker
class CleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val messageDao: MessageDao
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24)
            messageDao.deleteOlderThan(cutoff)
            Timber.d("CleanupWorker: deleted messages older than 24h")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "CleanupWorker failed")
            Result.retry()
        }
    }
}

/**
 * Syncs community flagged domains from Supabase for offline checking
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: AppRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val result = repository.syncCommunityFlaggedDomains()
            result.fold(
                onSuccess = {
                    Timber.d("SyncWorker: synced $it community domains")
                    Result.success()
                },
                onFailure = {
                    Timber.e(it, "SyncWorker failed")
                    Result.retry()
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "SyncWorker exception")
            Result.retry()
        }
    }
}
