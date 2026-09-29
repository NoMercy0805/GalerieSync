package com.schiwy.galeriesync

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.schiwy.galeriesync.sync.SyncWorker

class GalerieSyncApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Periodischen Sync bei jedem App-Start (neu) registrieren, falls
        // schon konfiguriert - WorkManager haelt das dann selbststaendig am
        // Laufen, auch nach Geraete-Neustart.
        CoroutineScope(Dispatchers.IO).launch {
            SyncWorker.schedulePeriodic(applicationContext)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "galeriesync_status",
                "GalerieSync Status",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
