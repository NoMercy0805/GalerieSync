package de.galeriesync.sync

import java.util.Locale

internal fun canonicalMediaPath(path:String):String = path.replace('\\','/').trim('/').lowercase(Locale.ROOT)
internal fun inSourceFolders(path:String,folders:Set<String>):Boolean = folders.isEmpty() || folders.any {
    canonicalMediaPath(path).startsWith(canonicalMediaPath(it)+"/")
}

// Return the actual NAS spelling; matching uses one canonical key everywhere.
internal fun nasDeletionPlan(local:Set<String>,remote:Set<String>,previous:Set<String>,folders:Set<String>,mirror:Boolean):Set<String> {
    val localKeys=local.map(::canonicalMediaPath).toSet()
    val previousKeys=previous.map(::canonicalMediaPath).toSet()
    return remote.filter { path ->
        inSourceFolders(path,folders) && !path.endsWith(".galeriesync-part") &&
            canonicalMediaPath(path) !in localKeys && (mirror || canonicalMediaPath(path) in previousKeys)
    }.toSet()
}
