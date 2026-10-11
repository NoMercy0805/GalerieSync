from pathlib import Path
p=Path('app/src/main/java/de/galeriesync/gallery/GalleryCacheWorker.kt')
s=p.read_text(encoding='utf-8')
if 'import de.galeriesync.vpn.VpnManager' not in s:
    s=s.replace('import de.galeriesync.data.SettingsStore\n','import de.galeriesync.data.SettingsStore\nimport de.galeriesync.vpn.VpnManager\n',1)
old='''        if(profiles.none{it.id==selection.sourceId})\n            return@withContext Result.success(workDataOf(KEY_MESSAGE to "Die ausgewählte NAS-Quelle ist nicht mehr vorhanden"))\n\n        setForeground(foreground("Galerie wird vorbereitet …"))\n        var lastNotificationAt=0L\n\n        try{\n            val result=runCatching{\n                val scanMessage="NAS-Ordner wird im Hintergrund eingelesen …"\n'''
new='''        if(profiles.none{it.id==selection.sourceId})\n            return@withContext Result.success(workDataOf(KEY_MESSAGE to "Die ausgewählte NAS-Quelle ist nicht mehr vorhanden"))\n        val activeProfile=profiles.first{it.id==selection.sourceId}\n\n        setForeground(foreground("Galerie wird vorbereitet …"))\n        var lastNotificationAt=0L\n        var startedVpn=false\n\n        try{\n            val result=runCatching{\n                startedVpn=VpnManager.connectIfNeeded(applicationContext){message->\n                    setProgressAsync(workDataOf(KEY_MESSAGE to message))\n                    updateForegroundNotification(message)\n                }\n                if(startedVpn){\n                    VpnManager.waitForSmb(activeProfile.host){message->\n                        setProgressAsync(workDataOf(KEY_MESSAGE to message))\n                        updateForegroundNotification(message)\n                    }\n                }\n                val scanMessage="NAS-Ordner wird im Hintergrund eingelesen …"\n'''
if old not in s:
    raise SystemExit('VPN insertion anchor not found')
s=s.replace(old,new,1)
old2='''            result\n        }finally{\n            nm.cancel(NOTIFICATION_RUNNING)\n        }\n'''
new2='''            result\n        }finally{\n            if(startedVpn)runCatching{VpnManager.disconnect()}\n            nm.cancel(NOTIFICATION_RUNNING)\n        }\n'''
if old2 not in s:
    raise SystemExit('finally anchor not found')
s=s.replace(old2,new2,1)
p.write_text(s,encoding='utf-8')
