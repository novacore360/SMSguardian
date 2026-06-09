package com.secureguardian.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.*
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class SecureGuardianApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()

        // Logging (only in debug builds)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Schedule background workers
        scheduleWorkers()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private fun scheduleWorkers() {
        val workManager = WorkManager.getInstance(this)

        // Daily cleanup of expired messages
        workManager.enqueueUniquePeriodicWork(
            "cleanup_messages",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<com.secureguardian.app.worker.CleanupWorker>(
                24, TimeUnit.HOURS
            ).setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            ).build()
        )

        // Sync community flagged domains every 6 hours
        workManager.enqueueUniquePeriodicWork(
            "sync_community_domains",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<com.secureguardian.app.worker.SyncWorker>(
                6, TimeUnit.HOURS
            ).setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            ).build()
        )
    }
}
