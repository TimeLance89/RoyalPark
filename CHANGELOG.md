# Changelog

## 1.2.1 · 28. Oktober 2026
- Kartenquellenangabe von OpenStreetMap bleibt sichtbar und führt zur Lizenzseite, überdeckt aber nicht mehr den Text der Parkplatzkarte.
- Quellenangabe folgt dynamisch der tatsächlichen Höhe des Parkplatzfensters (eingeklappt, aufgeklappt, bei anderer Schriftgröße).
- Schrift und Hintergrund des Quellenhinweises reduziert, damit er weniger von der Karte ablenkt.
- Auf sehr kleinen Displays wird die Angabe in den oberen freien Kartenbereich verschoben.
- Keine Datenbankänderung; versionCode 14 und weiterhin dasselbe Release-Signaturzertifikat.

## 1.2.0 · 28. Oktober 2026
- Einstellungsseite zeigt dynamisch die tatsächlich installierte Versionsnummer statt eines veralteten statischen Textes.
- Der Zusatz „Eigenständiger Neubau“ wurde aus der Oberfläche entfernt.
- Behoben: Offline-Kartenregionenauswahl zeigt wieder alle Regionen statt eines leeren Dialogs (Android setMessage() verdeckte setItems()).
- Drei große vertikale Kartenbuttons durch eine kompakte horizontale Kartensteuerung ersetzt; bei fehlendem Parkplatz erscheint nur der Standortbutton.
- Der Verlaufsknopf ist kleiner und gibt mehr Kartenfläche frei.
- Parkplatzkarte startet eingeklappt mit den wichtigsten Angaben und Route; weitere Aktionen sind aufklappbar, ohne die Karte neu zu laden.
- Unpräzise GPS-Ortung wird nicht mehr als scheinbar exakte „0 m zum Auto“ dargestellt.
- Natürlichere Parkdauer („Gerade geparkt“, „Seit 5 Min. geparkt“).
- JUnit-Tests für Entfernungshinweise und Parkdauer.

## 1.1.0 · 28. Oktober 2026
- Erststart-Onboarding mit vier eigenständigen, überspringbaren Seiten
- Standortberechtigung nur auf Benutzeraktion, keine automatische Aktivierung der Fahrzeugüberwachung
- Karte mit moderner Top-Leiste und kompakter Steuergruppe (Ich/Auto/Beide)
- Klarere Parkplatzansicht mit prominenter Route und reduzierten Schnellaktionen
- Verlauf auf kompakte Karte mit Aktionen umgestellt
- Einführung über Einstellungen wiederholbar
- GPS-Warndialog und erzwungene Notiz nach Speichern entfernt
- Keine Datenbankänderung; versionCode 12

## 1.0.1 · 28. Oktober 2026
- Kein automatisch aufspringender Genauigkeits-Dialog beim Parkplatzspeichern mehr.
- Kein erzwungener Notiz-Dialog nach dem Speichern, auch nicht nach manueller Kartenmarkierung.
- Ungenaue gespeicherte Parkplatzpositionen werden nachvollziehbar mit MANUAL_APPROX gekennzeichnet.
- Nicht blockierender Hinweis auf der Parkplatzkarte, inklusive freiwilligem Link zur Android-Standortfreigabe.
- GPS-Kennzeichnung verbessert: "Ungefährer Standort" bei nur grober Android-Berechtigung.
- Keine Datenbankmigration; VersionCode 11 für Update derselben signierten Release-App.
- Unit-Tests für die Standort-Genauigkeitsklassifizierung.

## 1.0.0 · 28. Oktober 2026
- Erste reguläre Release-Linie mit eigenem dauerhaften Android-Signaturschlüssel
- Sicherer GitHub-Actions-Workflow für signierte APKs, inkl. Signaturprüfung
- Geheimnisse werden nicht ins öffentliche Repository geschrieben
- Tag-Releases erfordern zwingend bereitgestellte Signing-Secrets
- Änderung des Update-Zertifikats gegenüber den bisherigen Debug-APKs (Daten vorher exportieren)

## 0.9.0 · 28. Oktober 2026
- Smart Map: Startzoom 19 am aktuellen Parkplatz, eigene Aktionen für Ich/Auto/Beide
- Zoom der eigenen Position an GPS-Genauigkeit angepasst
- Intelligente Kartenbegrenzung für gleichzeitige Anzeige von Fahrer und Auto
- Parkplatzkarte mit relativer Parkdauer und Luftlinienentfernung zum Fahrzeug
- Warnung vor dem manuellen Speichern einer ungenauen GPS-Position (über 80 m)
- MapFraming als eigenständig getestete Kameralogik, keine Datenbankschemaänderung
- Datenschutztext auf tatsächliche Online-/Offline-Kartenfunktion angepasst

## 0.8.0 · 28. Oktober 2026
- Direkter Download ausgewählter deutscher Offline-Vektorkarten aus dem Mapsforge-Hochschulmirror
- Fortschrittsanzeige, Größenbegrenzung, Kartenheaderprüfung und lokale Speicherung
- Darstellung offline über osmdroid-mapsforge, unabhängig vom OSM-Tileserver
- BRouter als lokaler Android-Routingdienst via AIDL; Fußweg wird offline berechnet, GPX-Koordinaten in RoyalPark dargestellt
- Separate Buttons für Online-/Offline-Fußwege, verständliche Voraussetzungen bei fehlenden .rd5-Segmenten
- Bestehende Rasterkarte, Parkplätze und Kompass bleiben erhalten

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
