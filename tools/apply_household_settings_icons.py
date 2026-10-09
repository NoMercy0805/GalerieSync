from pathlib import Path
p=Path('app/src/main/java/de/galeriesync/MainActivity.kt')
s=p.read_text()

imports='''import androidx.compose.material.icons.rounded.Storage\n'''
replacement='''import androidx.compose.material.icons.rounded.Storage\nimport androidx.compose.material.icons.outlined.Settings\nimport androidx.compose.material.icons.outlined.Palette\nimport androidx.compose.material.icons.outlined.Wifi\nimport androidx.compose.material.icons.outlined.Schedule\nimport androidx.compose.material.icons.outlined.Storage as OutlinedStorage\nimport androidx.compose.material.icons.outlined.Info\nimport androidx.compose.ui.graphics.vector.ImageVector\n'''
if imports not in s: raise SystemExit('icon import anchor not found')
s=s.replace(imports,replacement,1)

old='''Scaffold(containerColor=Color.Transparent,topBar={Row(Modifier.fillMaxWidth().padding(top=0.dp,start=28.dp,end=28.dp,bottom=0.dp),horizontalArrangement=Arrangement.End){IconButton(onClick={settingsPage="ROOT"},modifier=Modifier.size(38.dp).offset(x=(-4).dp)){Text("⚙",fontSize=24.sp,color=MaterialTheme.colorScheme.onSurface)}}},bottomBar='''
new='''Scaffold(containerColor=Color.Transparent,topBar={Row(Modifier.fillMaxWidth().padding(top=0.dp,start=28.dp,end=28.dp,bottom=0.dp),horizontalArrangement=Arrangement.End){Surface(modifier=Modifier.size(38.dp).offset(x=(-4).dp),shape=RoundedCornerShape(50),color=MaterialTheme.colorScheme.surface,border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outline.copy(alpha=0.35f)),tonalElevation=0.dp,shadowElevation=0.dp){IconButton(onClick={settingsPage="ROOT"},modifier=Modifier.fillMaxSize()){Icon(Icons.Outlined.Settings,contentDescription="Einstellungen",modifier=Modifier.size(20.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)}}}},bottomBar='''
if old not in s: raise SystemExit('top settings button anchor not found')
s=s.replace(old,new,1)

old_rows='''    SettingsRow("⚙","Allgemein","Design, Benachrichtigungen, Sprache"){open("Allgemein")}\n    SettingsRow("◉","WLAN & VPN","Vertrauenswürdige WLANs · VPN-Fallback"){open("WLAN-Einstellungen")}\n    SettingsRow("◷","Automatische Synchronisation","Zeitplan · Hintergrund"){open("Automatische Synchronisation")}\n    SettingsRow("▣","Daten & Speicher","Speicherorte, Cache, Datenbank"){open("Daten & Speicher")}\n    SettingsRow("ⓘ","Über die App","GalerieSync · Version 0.4.0"){open("Über die App")}'''
new_rows='''    SettingsRow(Icons.Outlined.Palette,"Allgemein","Design, Benachrichtigungen, Sprache"){open("Allgemein")}\n    SettingsRow(Icons.Outlined.Wifi,"WLAN & VPN","Vertrauenswürdige WLANs · VPN-Fallback"){open("WLAN-Einstellungen")}\n    SettingsRow(Icons.Outlined.Schedule,"Automatische Synchronisation","Zeitplan · Hintergrund"){open("Automatische Synchronisation")}\n    SettingsRow(Icons.Outlined.OutlinedStorage,"Daten & Speicher","Speicherorte, Cache, Datenbank"){open("Daten & Speicher")}\n    SettingsRow(Icons.Outlined.Info,"Über die App","GalerieSync · Version 0.4.0"){open("Über die App")}'''
if old_rows not in s: raise SystemExit('settings rows anchor not found')
s=s.replace(old_rows,new_rows,1)

old_fun='''@Composable private fun SettingsRow(icon:String,title:String,subtitle:String,onClick:()->Unit){Card(Modifier.fillMaxWidth().clickable{onClick()},shape=RoundedCornerShape(14.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Text(icon,fontSize=22.sp,color=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(title,color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold,fontSize=16.sp);Text(subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)};Text("›",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=26.sp)}}}'''
new_fun='''@Composable private fun SettingsRow(icon:ImageVector,title:String,subtitle:String,onClick:()->Unit){Card(Modifier.fillMaxWidth().clickable{onClick()},shape=RoundedCornerShape(14.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Surface(modifier=Modifier.size(38.dp),shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.primaryContainer.copy(alpha=0.72f),tonalElevation=0.dp){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Icon(icon,contentDescription=null,modifier=Modifier.size(21.dp),tint=MaterialTheme.colorScheme.primary)}};Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(title,color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold,fontSize=16.sp);Text(subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)};Text("›",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=26.sp)}}}'''
if old_fun not in s: raise SystemExit('SettingsRow function anchor not found')
s=s.replace(old_fun,new_fun,1)

p.write_text(s)
