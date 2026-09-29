package de.example.nasphotosync.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.example.nasphotosync.data.SyncDatabase
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = SyncDatabase.getInstance(applicationContext)
        val engine = SyncEngine(applicationContext)
        val profiles = db.syncProfileDao().observeAll().first().filter { it.isActive }

        var anyFailed = false
        for (profile in profiles) {
            val result = engine.runSync(profile)
            if (result is SyncResult.Failed) anyFailed = true
        }
        return if (anyFailed) Result.retry() else Result.success()
    }

    companion object {
        private const val PERIODIC_NAME = "nasphotosync_periodic"
        private const val ONE_TIME_NAME = "nasphotosync_manual"

        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .build()
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME, ExistingPeriodicWorkPolicy.UPDATE, request
            )
        }

        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_NAME, ExistingWorkPolicy.REPLACE, request
            )
        }
    }
}
