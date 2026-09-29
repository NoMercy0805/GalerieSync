package com.schiwy.galeriesync.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingWorkPolicy
import com.schiwy.galeriesync.data.SettingsRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Fuehrt einen SyncEngine-Durchlauf im Hintergrund aus. Wird periodisch
 * (alle 15 Minuten, WorkManager-Minimum) UND bei Bedarf sofort (manueller
 * "Jetzt synchronisieren"-Knopf) ausgefuehrt. Die WLAN-Einschraenkung wird
 * ueber die Constraints gesteuert, nicht im Worker selbst.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val engine = SyncEngine(applicationContext)
        return when (val result = engine.runSync()) {
            is SyncResult.Success -> Result.success()
            is SyncResult.NotConfigured -> Result.success() // nichts zu tun, kein Fehler
            is SyncResult.Failed -> Result.retry()
        }
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "galeriesync_periodic"
        private const val ONE_TIME_WORK_NAME = "galeriesync_manual"

        /** Registriert den periodischen Hintergrund-Sync passend zur WLAN-Einstellung. Bei jedem App-Start bzw. Einstellungsaenderung neu aufrufen. */
        suspend fun schedulePeriodic(context: Context) {
            val settings = SettingsRepository(context)
            val wifiOnly = settings.wifiOnly.first()

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        /** Stoesst sofort einen einmaligen Sync an (z.B. "Jetzt synchronisieren"-Knopf). */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
