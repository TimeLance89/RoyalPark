# RoyalPark – unabhängige Android-Parkplatz-App (v0.5.0)

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
