package de.galeriesync.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import de.galeriesync.data.SettingsStore
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

object AutoSyncScheduler {
    private const val UNIQUE_WORK = "GalerieSync"
    private const val MIGRATION_PREFS = "autosync_scheduler"
    private const val MIGRATION_V2 = "local_time_scheduler_v2"

    private fun nextTime(store: SettingsStore, now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime {
        val raw = store.loadAutoSyncValue().toLong().coerceAtLeast(1L)
        val unit = store.loadAutoSyncUnit()
        var next = now
            .withHour(store.loadAutoSyncStartHour())
            .withMinute(store.loadAutoSyncStartMinute())
            .withSecond(0)
            .withNano(0)

        fun step(value: ZonedDateTime): ZonedDateTime = when (unit) {
            "HOURS" -> value.plusHours(raw)
            "DAYS" -> value.plusDays(raw)
            "WEEKS" -> value.plusWeeks(raw)
            else -> value.plusMinutes(raw.coerceAtLeast(15L))
        }

        while (!next.isAfter(now)) next = step(next)
        return next
    }

    fun nextLabel(context: Context): String {
        val store = SettingsStore(context)
        if (!store.loadAutoSyncEnabled()) return "Deaktiviert"
        return nextTime(store).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm z"))
    }

    fun scheduleNext(context: Context, appendToRunning: Boolean = false) {
        val store = SettingsStore(context)
        val workManager = WorkManager.getInstance(context)
        if (!store.loadAutoSyncEnabled()) {
            workManager.cancelUniqueWork(UNIQUE_WORK)
            return
        }

        val network = if (store.loadAutoSyncNetwork() == "ANY") NetworkType.CONNECTED else NetworkType.UNMETERED
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(network)
            .setRequiresCharging(store.loadAutoSyncCharging())
            .build()

        val now = ZonedDateTime.now()
        val next = nextTime(store, now)
        val delayMs = Duration.between(now, next).toMillis().coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .setInputData(workDataOf("auto_sync" to true))
            .build()

        val policy = if (appendToRunning) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE
        workManager.enqueueUniqueWork(UNIQUE_WORK, policy, request)
    }

    fun migrateIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(MIGRATION_V2, false)) return
        if (SettingsStore(context).loadAutoSyncEnabled()) scheduleNext(context, false)
        prefs.edit().putBoolean(MIGRATION_V2, true).apply()
    }
}
