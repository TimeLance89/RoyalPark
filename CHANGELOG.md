# Changelog

## 0.4.0 · 28. Oktober 2026
- Original-Funktionsparität erweitert: optionale Bewegungserkennung beim Aussteigen (Activity Recognition Transition API)
- Bluetooth- und Bewegungserkennung teilen einen deduplizierten Speicherungspfad und die lokale Parkplatzhistorie
- Drei Android-Widgets wie in der Referenz: aktueller Parkplatz, Schnell-Speichern und Parkuhr
- Fotoverwaltung: Foto betrachten, ersetzen und Verknüpfung entfernen
- Aktivitätserkennung mit eigener Android-Berechtigung und explizitem Start/Stop; Google Play-Dienste erforderlich
- Bewusster Schutz vor mehreren Erkennungen innerhalb von zwei Minuten
- Datenbankschema bleibt unverändert, bestehende Parkplatzdaten werden beibehalten

## 0.3.0 · 27. Oktober 2026
- Oberfläche umfassend an die Referenzansichten angeglichen: helle Kartenansicht statt dunklem Dashboard
- Karte als Hauptfläche mit schwebenden Einstellungen, Verlauf, Karteninfo und Standortaktualisierung
- Weißes Parkplatz-Bottom-Sheet mit kompakten Aktionskacheln, Notizen und Parkuhr
- Helle Fenster, Dialoge, Verlauf und Einstellungen; türkisfarbenes Interaktionsschema
- Keine Änderungen am SQLite-Schema oder der Paketkennung; bestehende Parkplatzdaten bleiben erhalten
- Layout stützt sich auf offizielle Store-Bilder und die Referenz-APK; visuelle Geräteprüfung steht noch aus

## 0.2.0 · 27. Oktober 2026
- Eigene Navigation mit Live-Distanz, Kompassrichtung, Kartenansicht und externer Fußwegroute
- Parkplatz durch langen Kartendruck manuell markieren und bestätigen
- Kameraaufnahme mit privatem FileProvider oder Fotogalerie
- Anpassbare Parkuhr (1 bis 1440 Minuten) zusätzlich zu Presets
- Widget mit Parkplatzstatus und Schnell-Speichern
- Domain-Unit-Tests für Distanzberechnung und Himmelsrichtungen
- SQLite-Datenbestand bleibt erhalten (kein Schemawechsel)

## 0.1.0 · 27. Oktober 2026
- Erste eigenständige Android-App mit Karte, GPS, Historie, Fotoauswahl, Parktimer und Bluetooth-Automatik
