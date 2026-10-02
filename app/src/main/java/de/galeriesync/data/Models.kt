package de.galeriesync.data

enum class SyncDirection { PHONE_TO_NAS, NAS_TO_PHONE, BIDIRECTIONAL }

data class NasSettings(
    val id:String = java.util.UUID.randomUUID().toString(),
    val name:String = "",
    val host:String = "",
    val share:String = "",
    val username:String = "",
    val password:String = "",
    val domain:String = "",
    val remoteFolder:String = "GalerieSync",
    val wifiOnly:Boolean = true,
    val direction:SyncDirection = SyncDirection.PHONE_TO_NAS,
    val sourceFolders:Set<String> = emptySet(),
    val profileName:String = "",
    val enabled:Boolean = true,
    val wolEnabled:Boolean = false,
    val wolMac:String = ""
)
