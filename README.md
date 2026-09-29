# NAS Photo Sync

Android-Projekt für eine Foto-Synchronisations-App mit Kotlin, Gradle und Jetpack Compose.

## Ziel

- Handy → NAS
- NAS → Handy
- Bidirektional
- SMB / WebDAV / SFTP als austauschbare NAS-Provider
- UGREENlink als separater Remote-Provider
- Room für Sync-Zustände
- WorkManager für Hintergrund-Synchronisation

## Aktueller Stand

Version 0.1.0 enthält:
- Android/Gradle-Grundprojekt
- Compose-Startscreen
- Sync-Domain-Modelle
- Room-Datenmodell
- Provider-/Sync-Engine-Abstraktion

Noch nicht enthalten:
- SMB-Implementierung
- Fotoauswahl / MediaStore
- sichere Credential-Speicherung
- WorkManager Worker
- UGREENlink-Integration
- Konfliktauflösung
