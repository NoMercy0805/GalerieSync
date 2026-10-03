# GalerieSync

Android-App für Fotos und Videos auf einem NAS über SMB.

## Löschabgleich
- **Handy → NAS:** Der Zielordner spiegelt die ausgewählten Handy-Ordner. Dateien, die dort auf dem Handy fehlen, werden auf dem NAS gelöscht. Abgewählte Ordner bleiben erhalten.
- **Bidirektional:** Auf dem Handy gelöschte Dateien werden auf dem NAS gelöscht, wenn sie im vorherigen erfolgreichen Abgleich auf beiden Seiten vorhanden waren. Der erste Lauf erstellt diesen Vergleichsstand; ohne vorherigen Stand lässt sich eine Löschung nicht sicher von einer neuen NAS-Datei unterscheiden.
- **NAS → Handy:** Kopiert Dateien vom NAS. Löschungen auf dem NAS werden wegen der erforderlichen Android-Freigabe nicht automatisch auf das Handy übertragen.
- Löschungen werden erst nach erfolgreicher SMB-Löschung und Prüfung im Ergebnis gezählt. Der Hintergrund-Sync speichert Ergebnis und Zeit im Profilverlauf.
- Nur vollständiger Foto- und Videozugriff erlaubt den Abgleich. Bei eingeschränkter Berechtigung oder fehlgeschlagener Medienabfrage wird abgebrochen.

## Prüfung
`gradle testDebugUnitTest assembleDebug` führt die Regressionstests für den Löschplan aus und baut die APK gemeinsam. Die NAS-Verbindung selbst muss mit dem konkreten Gerät und SMB-Benutzer geprüft werden.
