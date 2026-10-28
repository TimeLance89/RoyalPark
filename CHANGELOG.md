# Changelog

## 0.7.0 · 28. Oktober 2026
- Schnellere Positionierung mit letzter ausreichend frischer Systemposition, danach Aktualisierung mit frischem Fix
- Netzwerk-Fallback 1,2 s statt 8 s, Timeout 12 statt 20 s
- Echtes GPS-Fadenkreuz und visuelle Statusanzeige, eigene Standortmarkierung
- Import legaler Offline-Rasterkarten (.mbtiles/.sqlite/.zip) in privaten App-Speicher
- Reiner Offline-Kartenmodus ohne Kachel-Netzwerkzugriff; automatische Offlinenutzung bei Verbindungsverlust
- Einstellungen zum Wechsel und Löschen der Offline-Karte
- Keine Vorabdownloads von OSM-Kacheln; Online-Fußwegrouting bleibt weiterhin Internet-abhängig

## 0.6.0 · 28. Oktober 2026
- Eigene Felder für Parkhaus-Ebene und Stellplatz, SQLite-Migration v1→v2
- ZIP-Export/-Import über Android-Dokumentenauswahl samt zugänglichen Parkplatzfotos
- Import als Zusammenführung mit Duplikatprüfung und Datenbanktransaktion; bestehende Einträge bleiben erhalten
- Größen- und Datenvalidierung bei Backup und Wiederherstellung
- Importierte Parkuhren werden nicht automatisch reaktiviert

## 0.5.0 · 28. Oktober 2026
- Echte interne Fußweg-Routenberechnung per Valhalla, erst nach ausdrücklicher Einwilligung pro Anfrage
- Anzeige einer berechneten Route als Linie auf der OSM-Karte mit Streckenlänge, Dauer und deutschsprachigen Anweisungen
- Freiwillige deutsche Text-to-Speech-Ansagen, standardmäßig deaktiviert
- Keine automatischen Routinganfragen; 30-Sekunden-Abstand, Netzwerktimeouts und Antwortgrößenlimit
- Offline-/Serverfehler-Fallback auf vorhandene Luftliniennavigation
- Polyline6-Geometrie-Decoder mit JUnit-Tests

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
