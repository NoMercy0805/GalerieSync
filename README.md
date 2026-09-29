# GalerieSync 0.1.0 – erste Testversion

Android-App für die Synchronisation von Fotos und Videos mit einem NAS über SMB.

## Enthalten
- Handy → NAS
- NAS → Handy
- bidirektional (konservativ: keine automatischen Löschungen)
- SMB/Windows-Shares, z. B. UGREEN NAS
- lokale Medien aus MediaStore
- Zielordner auf dem NAS
- WLAN-only Option
- sichere Speicherung des Passworts via Android Keystore/EncryptedSharedPreferences
- manueller Sync
- Hintergrund-Sync über WorkManager
- GitHub Actions baut automatisch `app-debug.apk`

## Test
1. APK installieren.
2. Medienberechtigung erlauben.
3. NAS-Host/IP, Share, Benutzer und Passwort eintragen.
4. Zielordner festlegen, z. B. `GalerieSync`.
5. Verbindung testen und anschließend synchronisieren.

Die erste Version spiegelt absichtlich keine Löschungen.
