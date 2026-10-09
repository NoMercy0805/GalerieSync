from pathlib import Path
p=Path('app/src/main/java/de/galeriesync/MainActivity.kt')
s=p.read_text()
old='Surface(modifier=Modifier.fillMaxWidth().padding(horizontal=6.dp,vertical=6.dp),shape=RoundedCornerShape(26.dp),color=MaterialTheme.colorScheme.surface,tonalElevation=0.dp,shadowElevation=8.dp,border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outline.copy(alpha=0.45f))){Row(Modifier.fillMaxWidth().padding(6.dp),horizontalArrangement=Arrangement.SpaceEvenly){Tab.entries.forEach{t->val selected=tab==t;Column(Modifier.weight(1f).clickable{tab=t}.padding(vertical=12.dp,horizontal=4.dp)'
new='Surface(modifier=Modifier.fillMaxWidth().padding(horizontal=6.dp,vertical=3.dp),shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surface,tonalElevation=0.dp,shadowElevation=8.dp,border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outline.copy(alpha=0.45f))){Row(Modifier.fillMaxWidth().padding(horizontal=6.dp,vertical=2.dp),horizontalArrangement=Arrangement.SpaceEvenly){Tab.entries.forEach{t->val selected=tab==t;Column(Modifier.weight(1f).clickable{tab=t}.padding(vertical=7.dp,horizontal=4.dp)'
if old not in s: raise SystemExit('tabbar block not found')
p.write_text(s.replace(old,new,1))
