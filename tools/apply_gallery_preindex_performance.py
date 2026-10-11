from pathlib import Path

repo_path=Path('app/src/main/java/de/galeriesync/gallery/GalleryRepository.kt')
s=repo_path.read_text(encoding='utf-8')

old='''    private val indexFile=File(cacheRoot,"index.json")\n    private val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)\n'''
new='''    private val indexFile=File(cacheRoot,"index.json")\n    private val folderIndexFile=File(cacheRoot,"folder_index.json")\n    private val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)\n'''
if old not in s: raise SystemExit('repository index anchor not found')
s=s.replace(old,new,1)

anchor='''    fun refreshIndex(profiles:List<NasSettings>):GalleryIndexResult{\n'''
if anchor not in s: raise SystemExit('refreshIndex anchor not found')
insert='''    private fun saveFolderPreviewIndex(items:List<GalleryMedia>)=synchronized(lock){\n        val folders=linkedMapOf<String,MutableList<GalleryMedia>>()\n        items.asSequence()\n            .sortedWith(compareBy<GalleryMedia>({it.video},{it.path.lowercase()}))\n            .forEach{item->\n                val relative=item.relativePath().trim('/')\n                val directory=relative.substringBeforeLast('/',"")\n                if(directory.isBlank())return@forEach\n                var current=""\n                directory.split('/').filter{it.isNotBlank()}.forEach{part->\n                    current=if(current.isBlank())part else "$current/$part"\n                    val previews=folders.getOrPut(current){mutableListOf()}\n                    if(previews.size<4)previews+=item\n                }\n            }\n        val root=JSONObject()\n        folders.forEach{(path,previews)->\n            root.put(path,JSONArray().apply{\n                previews.forEach{m->put(JSONObject().apply{\n                    put("sourceId",m.sourceId)\n                    put("sourceTitle",m.sourceTitle)\n                    put("remoteRoot",m.remoteRoot)\n                    put("path",m.path)\n                    put("size",m.size)\n                    put("video",m.video)\n                })}\n            })\n        }\n        val tmp=File(cacheRoot,"folder_index.json.tmp")\n        tmp.writeText(root.toString())\n        if(folderIndexFile.exists())folderIndexFile.delete()\n        if(!tmp.renameTo(folderIndexFile)){tmp.copyTo(folderIndexFile,true);tmp.delete()}\n    }\n\n    private fun loadFolderPreviewIndex():Map<String,List<GalleryMedia>> = synchronized(lock){\n        if(!folderIndexFile.exists())return@synchronized emptyMap()\n        runCatching{\n            val root=JSONObject(folderIndexFile.readText())\n            buildMap{\n                val keys=root.keys()\n                while(keys.hasNext()){\n                    val folder=keys.next()\n                    val array=root.optJSONArray(folder)?:continue\n                    val previews=(0 until array.length()).mapNotNull{i->\n                        val o=array.optJSONObject(i)?:return@mapNotNull null\n                        GalleryMedia(\n                            sourceId=o.optString("sourceId"),\n                            sourceTitle=o.optString("sourceTitle"),\n                            remoteRoot=o.optString("remoteRoot"),\n                            path=o.optString("path"),\n                            size=o.optLong("size",0L),\n                            video=o.optBoolean("video",false)\n                        ).takeIf{it.sourceId.isNotBlank()&&it.path.isNotBlank()}\n                    }\n                    if(previews.isNotEmpty())put(folder,previews)\n                }\n            }\n        }.getOrDefault(emptyMap())\n    }\n\n    fun loadFolderPreviews(currentFolder:String):Map<String,List<GalleryMedia>>{\n        val clean=currentFolder.trim('/')\n        return loadFolderPreviewIndex().filterKeys{path->path.substringBeforeLast('/',"")==clean}\n    }\n\n    private fun folderPreviewKeys():Set<String> = loadFolderPreviewIndex().values\n        .asSequence().flatten().map{it.sourceId+"|"+it.path}.toSet()\n\n'''
s=s.replace(anchor,insert+anchor,1)

old='''            saveIndex(found)\n            pruneObsoleteOfflineData(found)\n'''
new='''            saveIndex(found)\n            saveFolderPreviewIndex(found)\n            pruneObsoleteOfflineData(found)\n'''
if old not in s: raise SystemExit('saveIndex refresh anchor not found')
s=s.replace(old,new,1)

old='''        val eligible=items.filter{it.sourceId in allowedSources}\n        val missingImages=eligible.filter{!it.video&&!previewFile(it).exists()}\n        val missingVideos=eligible.filter{it.video&&!offlineVideoFile(it).exists()}\n'''
new='''        val eligible=items.filter{it.sourceId in allowedSources}\n        val coverKeys=folderPreviewKeys()\n        val missingImages=eligible\n            .filter{!it.video&&!previewFile(it).exists()}\n            .sortedByDescending{(it.sourceId+"|"+it.path) in coverKeys}\n        val missingVideos=eligible.filter{it.video&&!offlineVideoFile(it).exists()}\n'''
if old not in s: raise SystemExit('cache ordering anchor not found')
s=s.replace(old,new,1)
repo_path.write_text(s,encoding='utf-8')

screen_path=Path('app/src/main/java/de/galeriesync/gallery/GalleryScreen.kt')
s=screen_path.read_text(encoding='utf-8')
old='''    var selectedKey by rememberSaveable{mutableStateOf<String?>(null)}\n    var status by remember{mutableStateOf("")}\n'''
new='''    var selectedKey by rememberSaveable{mutableStateOf<String?>(null)}\n    var previewGeneration by remember{mutableIntStateOf(0)}\n    var status by remember{mutableStateOf("")}\n'''
if old not in s: raise SystemExit('previewGeneration anchor not found')
s=s.replace(old,new,1)

old='''    fun reloadLocal(){\n        media=repo.loadIndex()\n        val progress=repo.previewProgress(media)\n        progressDone=progress.first\n        progressTotal=progress.second\n    }\n'''
new='''    fun reloadLocal(){\n        media=repo.loadIndex()\n        val progress=repo.previewProgress(media)\n        progressDone=progress.first\n        progressTotal=progress.second\n        previewGeneration++\n    }\n'''
if old not in s: raise SystemExit('reloadLocal anchor not found')
s=s.replace(old,new,1)

start=s.index('    val folderPreviews=remember(media,folder,contents.first){')
end=s.index('    val openCount=',start)
s=s[:start]+'''    val folderPreviews=remember(folder,previewGeneration){repo.loadFolderPreviews(folder)}\n'''+s[end:]
screen_path.write_text(s,encoding='utf-8')
