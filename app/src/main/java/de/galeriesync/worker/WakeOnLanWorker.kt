package de.galeriesync.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.galeriesync.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class WakeOnLanWorker(appContext:Context,params:WorkerParameters):CoroutineWorker(appContext,params){
 override suspend fun doWork():Result=withContext(Dispatchers.IO){
  val store=SettingsStore(applicationContext)
  val active=store.loadProfiles().filter{it.enabled}
  val targets=store.loadConnections().filter{nas->
   nas.wolEnabled&&nas.wolMac.isNotBlank()&&active.any{p->p.host==nas.host&&p.share==nas.share}
  }
  if(targets.isEmpty())return@withContext Result.success()
  var sent=0
  targets.forEach{nas->if(runCatching{sendMagicPacket(nas.wolMac)}.isSuccess)sent++}
  if(sent>0)Result.success() else Result.retry()
 }
 private fun sendMagicPacket(macText:String){
  val clean=macText.replace(":","").replace("-","").trim()
  require(clean.length==12&&clean.all{it.isDigit()||it.uppercaseChar() in 'A'..'F'}){"Ungültige MAC-Adresse"}
  val mac=ByteArray(6){i->clean.substring(i*2,i*2+2).toInt(16).toByte()}
  val data=ByteArray(6+16*6)
  for(i in 0 until 6)data[i]=0xFF.toByte()
  for(i in 0 until 16)System.arraycopy(mac,0,data,6+i*6,6)
  DatagramSocket().use{socket->
   socket.broadcast=true
   val packet=DatagramPacket(data,data.size,InetAddress.getByName("255.255.255.255"),9)
   repeat(3){socket.send(packet)}
  }
 }
}
