# RoyalPark – Parität zur Referenz-App

## Fertig im Quellcode
- Aktuelle Position speichern; manuelle Pin-Wahl auf Karte
- Verlauf / Notizen / Fotos (Kamera und Galerie) / Teilen
- In-App-Kompass & Karte mit Luftlinienführung; externe Fußweg-Routen
- Parkuhr (Presets und benutzerdefiniert)
- Bluetooth-Automatik (Opt-in); aktiver Parkplatz im Widget
- Lokale Datenbank und automatischer GitHub-APK-Build

## Noch nicht originalgleich / nächster Ausbau
- Internes Offline-Routing und detailliertere Sprachnavigation (Online-Fußwegrouten und einfache Ansagen in 0.5.0)
- Geräterealtests der neuen Bewegungs-/Aktivitätserkennung und Kombinationslogik
- Originalgetreue Einstellungsseiten/Onboarding (Referenzscreenshots erforderlich)
- Download lizenzierter Offline-Kartenpakete und echtes Offline-Fußwegrouting (lokaler Import ab 0.7.0)
- Widget-Größen optimieren, Wear OS, erweiterter Parkhausmodus (Grundfunktion 0.6.0)
- Geräterealtests auf HyperOS/Android 15, Akkuoptimierung, Berechtigungen und Rotation
- Langfristig: Automatisierte UI-Tests, mehrere Fahrzeuge, Kartenbibliothekswechsel

Kein Fremdquellcode, keine fremden Grafiken, kein Lizenz-/Aboumgehungscode.

## Neu in 0.6.0
- Ebene/Stellplatz, SQLite-Migration v1→v2, ZIP-Backup und Merge-Restore inklusive zugänglicher Bilder
- Offen: Geräte-Ende-zu-Ende-Test von Datensicherung und Bildimport
- Offen: stabile Release-Signierung für Updates ohne Deinstallation

## 0.7.0
- OSMdroid OfflineTileProvider für importierte Offline-Rasterkarten, ohne Massendownload vom OSM-Server
- Standorttaste zeigt sofort einen frischen vorhandenen Fix und ergänzt ihn asynchron
- Offen: Geräterealtests verschiedener MBTiles/SQLite/ZIP-Archive und HyperOS-Standort-Performance
- Offen: Offline-Fußwegroutenberechnung
