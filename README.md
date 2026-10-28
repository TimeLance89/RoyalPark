# RoyalPark – unabhängige Android-Parkplatz-App (v0.7.0)

RoyalPark ist eine eigenständige Android-App mit eigenem Quellcode. Die Funktionen orientieren sich an der von dir bereitgestellten Parkplatz-App, aber wir verwenden weder deren Programmcode noch geschützte App-Ressourcen.

## Funktionen
- Parkplatz per GPS speichern, auf der Karte markieren, lokale Historie, Notizen und Fotos.
- Parkuhr mit Benachrichtigung sowie drei Startbildschirm-Widgets.
- Automatische Parkerkennung per gekoppeltem Auto-Bluetooth und optionaler Android-Bewegungserkennung (nur während eingeschalteter Überwachung).
- In-App-Navigation mit Live-GPS, Richtungspfeil und Luftlinie.
- **Neu in 0.5.0:** Echte Fußwegrouten über Valhalla (FOSSGIS-Demoserver), türkisfarbene Route auf der Karte, Distanz-/Zeitübersicht und deutschsprachige Abbiegehinweise.
- **Optional:** deutsche Sprachausgabe über Android Text-to-Speech, standardmäßig aus.
- Externer Karten-Button als Alternative.

## Datenschutz und Einschränkungen beim Online-Routing
1. Die App sendet **erst nach Klick und ausdrücklicher Bestätigung** die aktuelle Position und die Zielkoordinaten an `https://valhalla1.openstreetmap.de/route`.
2. Valhalla ist ein öffentliches Demoangebot mit fair-use Limits und ohne Verfügbarkeitsgarantie. Die App wartet zwischen manuellen Anfragen mindestens 30 Sekunden. Keine automatischen Hintergrundabfragen.
3. Ohne Verbindung oder bei Servicefehler bleibt die lokale Luftlinien-/Kompassanzeige verfügbar.
4. Die Fußwegroute ist auf maximal 20 km Luftlinie begrenzt. Abweichungen werden angezeigt; bei Bedarf bewusst neu berechnen.
5. Der Dienst kann Positionen protokollieren; im RoyalPark-Projekt gibt es keine eigenen Server, Konten oder Tracking-Analysen.
6. Kartenkacheln stammen von OpenStreetMap. Kein Massen-Download oder vorgefertigtes Offlinepaket.
7. Die Demo-API muss für eine öffentliche/verbreitete App durch ein eigenes Hosting oder einen vertraglichen Anbieter ersetzt werden. FOSSGIS bittet Entwickler veröffentlichter Apps um Kontaktaufnahme und `X-Client-Id`-Kennzeichnung.

## Entwicklungsstatus und offene Punkte
- Unit-Tests und CI-Build werden über GitHub Actions ausgeführt; echte HyperOS-/Android-Gerätetests stehen aus.
- Die TTS-Ansagen orientieren sich an der nächsten Routenanweisung, ersetzen aber keine vollwertige Automotive-Navigation.
- Noch offen: vollständiger Offline-Routingmodus, Wear OS, erweiterte Parkhausunterstützung, visuelle Feinabstimmung und zuverlässige Release-Signatur.
- Android Debug-APKs von GitHub Actions werden möglicherweise mit unterschiedlichen Debug-Zertifikaten signiert. Vor einer Deinstallation Daten sichern; sonst können Parkeinträge verloren gehen.

## Quellcode/Build
Java 17 · compileSdk 35 · Android Gradle Plugin · OSMdroid · Google Play Services Activity Recognition.

CI: `.github/workflows/android.yml` führt `:app:testDebugUnitTest` und `:app:assembleDebug` aus und veröffentlicht ein APK-Artefakt.

Version 0.5.0 ist ein schrittweiser Nachbau der Funktionen und keine pixelgenaue oder vollständige Kopie.


## 0.6.0: Parkhausdaten und portable ZIP-Sicherung
Unter **Einstellungen → Datensicherung** können Parkplätze über den Android-Dateidialog in ein ZIP-Archiv exportiert und später wieder importiert werden. Es wird keine Cloud automatisch angesprochen. Das Archiv enthält genaue Koordinaten und ist **nicht verschlüsselt**.

- Export: Koordinaten, Zeitpunkt, Quelle, Notizen, Ebene/Stellplatz und zugängliche Bilder. Nicht mehr zugängliche Bilder werden beim Export gemeldet.
- Import: Sichere Prüfung der Formate, Pfade und Größen. Bestehende Parkplätze bleiben erhalten; Duplikate anhand Zeitstempel/Koordinaten werden übersprungen.
- Importierte Parkuhren werden nicht reaktiviert. Fotos werden in den app-spezifischen Bilderordner kopiert.
- Limits: maximal 5000 Parkplätze, 8 MiB pro Foto, 120 MiB unkomprimierte ZIP-Daten.
- SQLite-Datenbankmigration v1 → v2 für `garage_floor` und `garage_spot` ohne Verlust bestehender Datensätze.
- Keine allgemeinen Speicherrechte nötig (Android-Dokumentenauswahl).
- **Achtung:** Die GitHub-Debug-Signatur ist noch nicht dauerhaft festgelegt. Sichere die Daten vor einer Deinstallation, um Datenverlust zu vermeiden.
- Geräte-Ende-zu-Ende-Tests für Backups, Bildformate und Android-Migration stehen noch aus.


## 0.7.0: Schnell-Ortung und Offlinekarten
- Standorttaste mit echtem GPS-Fadenkreuz-Icon und Suchstatus.
- Systemposition mit max. 120 Sekunden Alter und max. 250 m Genauigkeit wird sofort zentriert, danach folgt ein frischer GPS-/Netzwerk-Fix.
- Netzwerk-Standort wird schon nach 1,2 Sekunden statt acht Sekunden ergänzend angefragt; Timeout 12 statt 20 Sekunden.
- Für das dauerhafte Speichern eines Parkplatzes wird weiterhin ein neuer Standort abgefragt.
- Unter Einstellungen -> Offline-Karten kannst du lizenzierte Rasterkarten (.mbtiles, .sqlite, .zip) über den Android-Dateidialog importieren (max. 1 GB pro Archiv).
- Offline-Modus nutzt ausschließlich die importierte Karte und lädt keine Netzwerkkacheln. Bei fehlendem Internet erfolgt ein automatischer Wechsel auf das importierte Kartenarchiv.
- Ohne Offline-Archiv sind eventuell zuvor gesehene Kacheln aus dem Cache sichtbar, aber kein vollständiges Kartenmaterial.
- Kartendarstellung, gespeicherte Parkplätze und Kompass funktionieren ohne Internet. Eine neue Valhalla-Fußwegroute benötigt weiterhin Internet.
- Es werden keine Kartenkacheln auf Vorrat von tile.openstreetmap.org heruntergeladen; der Betreiber untersagt dieses Offline-Prefetching.
- Importierte Rasterkarten zeigen nur Regionen und Zoomstufen, die tatsächlich enthalten sind.
- Offene Tests: HyperOS-Standortzeiten, Dateiformate und Navigation auf einem echten Android-Gerät.
