# Roadmap – RoyalPark

## M0: Funktionaler eigener Kern (implementiert, Build/Device-Test offen)
- GPS-Parken, Verlauf, eigener Name und Design
- OSM-Karte, Notiz, Bild, Link und Fußweg-Navigation
- Benachrichtigungs-Parkuhr und Bluetooth-Foreground-Service

## M1: Gerätetest und Qualität (nächste Priorität)
- Android-Build in CI ausführen, Compiler-/Lintfehler beheben
- Installation auf Android 15/Xiaomi 12T; Standortberechtigungen testen
- Gegenprüfen: Bluetooth-Auto trennt, GPS-Fix, Hintergrundbetrieb/Doze
- Timer-Zuverlässigkeit, Foto-URI nach Neustart, Rotation, Accessibility
- Datenmigrationen und Export/Import-Backup

## M2: Eigene Navigation & moderne Karten
- MapLibre statt archiviertem osmdroid
- Routendarstellung & Wegzeiten, Kompass und Distanz
- Offline-Maps nur von rechtlich/vertraglich geeigneten Quellen
- Parkhausmodus mit Etage/Foto und manueller Pin-Korrektur

## M3: Intelligente Autopark-Erkennung
- Bluetooth + Activity Recognition kombinieren, Fehlalarme reduzieren
- Optionale Verzögerung/Konfidenz und Standortqualität anzeigen
- Android-Boot- und OEM-Energiesparverhalten absichern
- Widgets/Quick-Settings/optional Wear OS

## M4: RoyalPark als langfristiges eigenes Produkt
- Vollständiges Designsystem, Tests und CI/CD
- Backup/Sync optional und Ende-zu-Ende-verschlüsselt
- Mehrere Fahrzeuge, Erinnerungen, intelligente Orte, eigene Features

Nicht vorgesehen: fremden Code oder Grafiken kopieren, Zahl-/Lizenzprüfungen umgehen.
