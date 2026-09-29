package de.example.nasphotosync.data

enum class SyncDirection {
    PHONE_TO_NAS,
    NAS_TO_PHONE,
    BIDIRECTIONAL
}

enum class ConnectionType {
    SMB,
    WEBDAV,
    SFTP,
    UGREEN_LINK
}
