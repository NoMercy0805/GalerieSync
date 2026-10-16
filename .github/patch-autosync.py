from pathlib import Path

path = Path("app/src/main/java/de/galeriesync/MainActivity.kt")
text = path.read_text(encoding="utf-8")

replacements = [
    (
        'LaunchedEffect(Unit){val p=if(Build.VERSION.SDK_INT>=33)',
        'LaunchedEffect(Unit){de.galeriesync.worker.AutoSyncScheduler.migrateIfNeeded(context);val p=if(Build.VERSION.SDK_INT>=33)'
    ),
    (
        'var startMinute by remember{mutableIntStateOf(store.loadAutoSyncStartMinute())}',
        'var startMinute by remember{mutableIntStateOf(store.loadAutoSyncStartMinute())};var nextPlanned by remember{mutableStateOf(nextAutoSyncLabel(context))}'
    ),
    (
        'schedule(context,minutes);saved="Automatische Synchronisation aktiviert"',
        'schedule(context,minutes);nextPlanned=nextAutoSyncLabel(context);saved="Automatische Synchronisation aktiviert"'
    ),
    (
        'WorkManager.getInstance(context).cancelUniqueWork("GalerieSync");saved="Automatische Synchronisation deaktiviert"',
        'WorkManager.getInstance(context).cancelUniqueWork("GalerieSync");nextPlanned="Deaktiviert";saved="Automatische Synchronisation deaktiviert"'
    ),
    (
        'schedule(context,minutes);saved="Automatik gespeichert · Start %02d:%02d ·".format(startHour,startMinute)',
        'schedule(context,minutes);nextPlanned=nextAutoSyncLabel(context);saved="Automatik gespeichert · Start %02d:%02d ·".format(startHour,startMinute)'
    ),
    (
        'Text("Android führt Hintergrundarbeit nicht sekundengenau aus. Das kleinste unterstützte Intervall sind 15 Minuten.",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=11.sp)\n  Button',
        'Text("Android führt Hintergrundarbeit nicht sekundengenau aus. Das kleinste unterstützte Intervall sind 15 Minuten.",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=11.sp)\n  Text("Nächster geplanter Start: $nextPlanned",color=MaterialTheme.colorScheme.primary,fontSize=12.sp,fontWeight=FontWeight.SemiBold)\n  Button'
    ),
]

for old, new in replacements:
    if old not in text:
        raise SystemExit(f"Expected source fragment not found: {old[:100]}")
    text = text.replace(old, new, 1)

old_schedule = 'fun schedule(context:android.content.Context,minutes:Long){val store=SettingsStore(context);val network=if(store.loadAutoSyncNetwork()=="ANY")NetworkType.CONNECTED else NetworkType.UNMETERED;val c=Constraints.Builder().setRequiredNetworkType(network).setRequiresCharging(store.loadAutoSyncCharging()).build();val now=java.time.ZonedDateTime.now();var first=now.withHour(store.loadAutoSyncStartHour()).withMinute(store.loadAutoSyncStartMinute()).withSecond(0).withNano(0);val interval=java.time.Duration.ofMinutes(minutes.coerceAtLeast(15));while(!first.isAfter(now))first=first.plus(interval);val delay=java.time.Duration.between(now,first).toMillis();val r=PeriodicWorkRequestBuilder<SyncWorker>(minutes.coerceAtLeast(15),TimeUnit.MINUTES).setInitialDelay(delay,TimeUnit.MILLISECONDS).setConstraints(c).build();WorkManager.getInstance(context).enqueueUniquePeriodicWork("GalerieSync",ExistingPeriodicWorkPolicy.UPDATE,r)}'
new_schedule = '''private fun nextAutoSyncLabel(context:android.content.Context):String=de.galeriesync.worker.AutoSyncScheduler.nextLabel(context)
fun schedule(context:android.content.Context,minutes:Long){de.galeriesync.worker.AutoSyncScheduler.scheduleNext(context,false)}'''

if old_schedule not in text:
    raise SystemExit("Expected schedule() implementation not found")
text = text.replace(old_schedule, new_schedule, 1)
path.write_text(text, encoding="utf-8")
print("DST-safe auto-sync scheduling patch applied")
