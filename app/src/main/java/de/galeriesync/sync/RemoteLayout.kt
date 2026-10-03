package de.galeriesync.sync

internal fun legacyMediaPath(path:String):String {
 val clean=path.replace('\\','/').trim('/')
 val parts=clean.split('/')
 val content=if(parts.firstOrNull()?.lowercase(java.util.Locale.ROOT) in setOf("dcim","pictures","movies"))parts.drop(1).joinToString("/") else clean
 return "Bilder & Videos/$content"
}

// Translate only unambiguous legacy folders. Keep the actual path for SMB operations.
internal fun phonePathForRemote(remote:String,phoneFolders:Set<String>):String {
 if(!canonicalMediaPath(remote).startsWith("bilder & videos/"))return remote
 val candidates=phoneFolders.mapNotNull{folder->
  val old=legacyMediaPath(folder).trim('/')
  if(canonicalMediaPath(remote).startsWith(canonicalMediaPath(old)+"/"))folder.trim('/')+"/"+remote.substring(old.length+1) else null
 }.distinctBy(::canonicalMediaPath)
 return if(candidates.size==1)candidates.single() else remote
}
