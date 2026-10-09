from pathlib import Path

g = Path('app/src/main/java/de/galeriesync/gallery/GalleryScreen.kt')
s = g.read_text()
old = '''        Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){
                Text("Galerie",fontSize=25.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onBackground)
                Text("Verkleinerte Offline-Medien · ohne Speicherlimit",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            if(selection==null){
                TextButton(onClick={pickerOpen=true},enabled=profiles.isNotEmpty()){Text("Ordner wählen")}
            }else{
                Box{
                    IconButton(onClick={galleryMenuOpen=true}){Text("⋮",fontSize=25.sp,color=MaterialTheme.colorScheme.onSurface)}
                    DropdownMenu(expanded=galleryMenuOpen,onDismissRequest={galleryMenuOpen=false}){
                        DropdownMenuItem(
                            text={Text(if(working)"Aktualisierung läuft" else "Aktualisieren")},
                            onClick={galleryMenuOpen=false;startRefresh()},
                            enabled=!working
                        )
                        DropdownMenuItem(
                            text={Text("Ordner ändern")},
                            onClick={galleryMenuOpen=false;pickerOpen=true},
                            enabled=profiles.isNotEmpty()
                        )
                        if(progressDone>0){
                            DropdownMenuItem(
                                text={Text("Offline-Daten löschen",color=MaterialTheme.colorScheme.error)},
                                onClick={galleryMenuOpen=false;deleteOfflineConfirm=true},
                                enabled=!working
                            )
                        }
                    }
                }
            }
        }
'''
new = '''        Row(Modifier.fillMaxWidth().padding(start=6.dp,end=18.dp,top=0.dp,bottom=2.dp),verticalAlignment=Alignment.CenterVertically){
            if(selection!=null){
                Box{
                    IconButton(onClick={galleryMenuOpen=true},modifier=Modifier.size(40.dp)){Text("⋮",fontSize=25.sp,color=MaterialTheme.colorScheme.onSurface)}
                    DropdownMenu(expanded=galleryMenuOpen,onDismissRequest={galleryMenuOpen=false}){
                        DropdownMenuItem(text={Text(if(working)"Aktualisierung läuft" else "Aktualisieren")},onClick={galleryMenuOpen=false;startRefresh()},enabled=!working)
                        DropdownMenuItem(text={Text("Ordner ändern")},onClick={galleryMenuOpen=false;pickerOpen=true},enabled=profiles.isNotEmpty())
                        if(progressDone>0)DropdownMenuItem(text={Text("Offline-Daten löschen",color=MaterialTheme.colorScheme.error)},onClick={galleryMenuOpen=false;deleteOfflineConfirm=true},enabled=!working)
                    }
                }
            }
            Column(Modifier.weight(1f)){
                Text("Galerie",fontSize=25.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onBackground)
                Text("Verkleinerte Offline-Medien · ohne Speicherlimit",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            if(selection==null)TextButton(onClick={pickerOpen=true},enabled=profiles.isNotEmpty()){Text("Ordner wählen")}
        }
'''
if old not in s:
    raise SystemExit('gallery header block not found')
s = s.replace(old, new, 1)

old_call = 'items(contents.first,key={"folder:"+it.path}){f->FolderTile(f){folder=f.path}}'
new_call = 'items(contents.first,key={"folder:"+it.path}){f->FolderTile(f,media.firstOrNull{m->(m.path==f.path||m.path.startsWith(f.path+"/"))&&repo.previewFile(m).exists()},repo){folder=f.path}}'
if old_call not in s:
    raise SystemExit('folder call not found')
s = s.replace(old_call, new_call, 1)

old_tile = '''@Composable
private fun FolderTile(folder:GalleryFolder,onClick:()->Unit){
    Card(Modifier.aspectRatio(1f).clickable{onClick()},shape=RoundedCornerShape(14.dp)){
        Column(Modifier.fillMaxSize().padding(10.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
            Text("📁",fontSize=34.sp)
            Spacer(Modifier.height(5.dp))
            Text(folder.name,fontSize=12.sp,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
            Text("${folder.count}",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
'''
new_tile = '''@Composable
private fun FolderTile(folder:GalleryFolder,previewItem:GalleryMedia?,repo:GalleryRepository,onClick:()->Unit){
    val preview=previewItem?.let{repo.previewFile(it)}
    val hasPreview=preview?.exists()==true
    Card(Modifier.aspectRatio(1f).clickable{onClick()},shape=RoundedCornerShape(14.dp)){
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)){
            if(hasPreview){
                AsyncImage(model=preview,contentDescription=folder.name,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.30f)))
            }
            Column(Modifier.fillMaxSize().padding(10.dp),verticalArrangement=Arrangement.Bottom,horizontalAlignment=Alignment.Start){
                if(!hasPreview)Text("📁",fontSize=30.sp)
                Text(folder.name,fontSize=12.sp,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis,color=if(hasPreview)Color.White else MaterialTheme.colorScheme.onSurface)
                Text("${folder.count}",fontSize=10.sp,color=if(hasPreview)Color.White.copy(alpha=0.82f) else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
'''
if old_tile not in s:
    raise SystemExit('folder tile not found')
s = s.replace(old_tile, new_tile, 1)
g.write_text(s)

m = Path('app/src/main/java/de/galeriesync/MainActivity.kt')
t = m.read_text()
old_top = 'Scaffold(containerColor=Color.Transparent,topBar={Row(Modifier.fillMaxWidth().padding(top=10.dp,start=28.dp,end=28.dp,bottom=10.dp),horizontalArrangement=Arrangement.End){IconButton(onClick={settingsPage="ROOT"},modifier=Modifier.offset(x=(-4).dp)){Text("⚙",fontSize=24.sp,color=MaterialTheme.colorScheme.onSurface)}}}'
new_top = 'Scaffold(containerColor=Color.Transparent,topBar={Row(Modifier.fillMaxWidth().padding(top=0.dp,start=28.dp,end=28.dp,bottom=0.dp),horizontalArrangement=Arrangement.End){IconButton(onClick={settingsPage="ROOT"},modifier=Modifier.size(38.dp).offset(x=(-4).dp)){Text("⚙",fontSize=24.sp,color=MaterialTheme.colorScheme.onSurface)}}}'
if old_top not in t:
    raise SystemExit('top bar not found')
t = t.replace(old_top, new_top, 1)
old_header = '@Composable private fun Header(t:String,s:String?=null){Column(Modifier.fillMaxWidth().padding(20.dp,18.dp,20.dp,10.dp)){Text(t,fontSize=25.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onBackground);s?.let{Text(it,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp)}}}'
new_header = '@Composable private fun Header(t:String,s:String?=null){Column(Modifier.fillMaxWidth().padding(start=20.dp,top=2.dp,end=20.dp,bottom=8.dp)){Text(t,fontSize=25.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onBackground);s?.let{Text(it,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp)}}}'
if old_header not in t:
    raise SystemExit('header helper not found')
t = t.replace(old_header, new_header, 1)
m.write_text(t)

Path('.github/build-trigger').write_text('GalerieSync 0.4.8: compact headers and folder previews\n')
