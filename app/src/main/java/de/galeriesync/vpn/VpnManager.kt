package de.galeriesync.vpn

import android.content.Context
import android.net.VpnService
import android.net.wifi.WifiManager
import de.galeriesync.data.SettingsStore
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object VpnManager {
 private var backend:GoBackend?=null
 private val tunnel=object:Tunnel{
  override fun getName()="GalerieSync"
  override fun onStateChange(newState:Tunnel.State){}
 }
 fun currentWifi(context:Context):String? {
  val wm=context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager?:return null
  return runCatching{wm.connectionInfo?.ssid?.trim('"')}.getOrNull()?.takeIf{it.isNotBlank()&&it!="<unknown ssid>"}
 }
 fun needsVpn(context:Context):Boolean {
  val ssid=currentWifi(context)
  return ssid==null || ssid !in SettingsStore(context).loadTrustedWifi()
 }
 fun permissionIntent(context:Context)=VpnService.prepare(context)
 suspend fun connectIfNeeded(context:Context,onStatus:(String)->Unit={}):Boolean=withContext(Dispatchers.IO){
  if(!needsVpn(context)){onStatus("Heim-WLAN · VPN nicht nötig");return@withContext false}
  val raw=SettingsStore(context).loadVpnConfig()
  require(raw.isNotBlank()){"Außerhalb des Heim-WLANs ist VPN nötig. Bitte zuerst die FRITZ!Box-WireGuard-Datei importieren."}
  require(VpnService.prepare(context)==null){"VPN-Freigabe fehlt. Bitte unter WLAN & VPN einmal VPN erlauben."}
  val cfg=Config.parse(raw.byteInputStream())
  val b=backend?:GoBackend(context.applicationContext).also{backend=it}
  onStatus("Externes Netzwerk · VPN wird verbunden …")
  b.setState(tunnel,Tunnel.State.UP,cfg)
  repeat(20){if(b.getState(tunnel)==Tunnel.State.UP){onStatus("SMB über FRITZ!Box-VPN");return@withContext true};delay(250)}
  error("VPN-Tunnel konnte nicht gestartet werden.")
 }
 suspend fun disconnect(onStatus:(String)->Unit={}){
  withContext(Dispatchers.IO){backend?.let{runCatching{it.setState(tunnel,Tunnel.State.DOWN,null)}}}
  onStatus("VPN getrennt")
 }
 fun isUp():Boolean=runCatching{backend?.getState(tunnel)==Tunnel.State.UP}.getOrDefault(false)
}
