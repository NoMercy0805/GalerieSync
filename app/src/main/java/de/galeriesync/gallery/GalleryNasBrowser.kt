package de.galeriesync.gallery

import de.galeriesync.data.NasSettings
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import java.util.Properties

data class GalleryRemoteFolder(val name:String,val path:String)

class GalleryNasBrowser(private val settings:NasSettings){
    private val context:CIFSContext by lazy{
        val props=Properties().apply{
            setProperty("jcifs.smb.client.enableSMB2","true")
            setProperty("jcifs.smb.client.disableSMB1","true")
        }
        BaseContext(PropertyConfiguration(props)).withCredentials(
            NtlmPasswordAuthenticator(settings.domain.ifBlank{null},settings.username,settings.password)
        )
    }

    private fun url(path:String="")="smb://"+settings.host.trim('/')+"/"+settings.share.trim('/')+"/"+path.trimStart('/')

    fun listDirectories(path:String=""):List<GalleryRemoteFolder>{
        val clean=path.replace('\\','/').trim('/')
        val dir=SmbFile(url(clean).trimEnd('/')+"/",context)
        dir.connect()
        if(!dir.exists())throw java.io.IOException("NAS-Ordner nicht gefunden")
        return dir.listFiles()
            .filter{it.isDirectory}
            .mapNotNull{f->
                val name=f.name.trimEnd('/')
                name.takeIf{it.isNotBlank()}?.let{GalleryRemoteFolder(it,if(clean.isBlank())it else "$clean/$it")}
            }
            .sortedBy{it.name.lowercase()}
    }
}
