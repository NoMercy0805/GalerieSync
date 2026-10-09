from pathlib import Path
p=Path('app/src/main/java/de/galeriesync/MainActivity.kt')
s=p.read_text()
old='''@Composable private fun ModalWindow(title:String,onClose:()->Unit,onBack:(()->Unit)?=null,content:@Composable BoxScope.()->Unit){
 Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false,dismissOnClickOutside=false)){
  Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp),contentAlignment=Alignment.Center){
   Surface(Modifier.widthIn(max=600.dp).fillMaxWidth().fillMaxHeight(0.9f),shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surface,shadowElevation=14.dp){
    Column(Modifier.fillMaxSize()){
     Row(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
      if(onBack!=null)TextButton(onClick=onBack){Text("‹",fontSize=28.sp)} else Spacer(Modifier.width(48.dp))
      Text(title,Modifier.weight(1f),fontSize=20.sp,fontWeight=FontWeight.Bold,maxLines=1)
      IconButton(onClick=onClose){Text("✕",fontSize=20.sp)}
     }
     HorizontalDivider()
     Box(Modifier.fillMaxWidth().weight(1f),content=content)
    }
   }
  }
 }
}
'''
new='''@Composable private fun ModalWindow(title:String,onClose:()->Unit,onBack:(()->Unit)?=null,content:@Composable BoxScope.()->Unit){
 Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false,dismissOnClickOutside=true)){
  Box(
   Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.24f)).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal=18.dp,vertical=28.dp),
   contentAlignment=Alignment.Center
  ){
   Surface(
    Modifier.widthIn(max=520.dp).fillMaxWidth().fillMaxHeight(0.78f),
    shape=RoundedCornerShape(26.dp),
    color=MaterialTheme.colorScheme.surface,
    tonalElevation=0.dp,
    shadowElevation=22.dp,
    border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outline.copy(alpha=0.28f))
   ){
    Column(Modifier.fillMaxSize()){
     Box(Modifier.fillMaxWidth().padding(top=9.dp),contentAlignment=Alignment.Center){
      Surface(Modifier.width(38.dp).height(4.dp),shape=RoundedCornerShape(50),color=MaterialTheme.colorScheme.outline.copy(alpha=0.45f)){}
     }
     Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically){
      if(onBack!=null){
       IconButton(onClick=onBack,modifier=Modifier.size(40.dp)){Text("‹",fontSize=30.sp,color=MaterialTheme.colorScheme.primary)}
      }else Spacer(Modifier.width(40.dp))
      Text(title,Modifier.weight(1f),fontSize=20.sp,fontWeight=FontWeight.Bold,maxLines=1,color=MaterialTheme.colorScheme.onSurface)
      IconButton(onClick=onClose,modifier=Modifier.size(40.dp)){Text("✕",fontSize=18.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
     }
     HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=0.22f))
     Box(Modifier.fillMaxWidth().weight(1f),content=content)
    }
   }
  }
 }
}
'''
if old not in s:
    raise SystemExit('ModalWindow block not found')
s=s.replace(old,new,1)
p.write_text(s)
