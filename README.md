# RoyalPark · Android-App (v0.1.0)

Ein unabhängiges Android-Projekt für das Wiederfinden des geparkten Autos.
Die App ist eine **eigene Neuentwicklung**, keine Kopie des Quellcodes oder der Grafiken von
„Mein Auto finden“ (`com.appcelerate.findmycar.android`). Das Fremdprodukt diente
nur dazu, eine Funktionsliste zu bestimmen. Diese Codebasis ist vollständig neu geschrieben.

## Bereits implementiert

- Selbst gestaltete, deutschsprachige Oberfläche mit Karte (OpenStreetMap via osmdroid)
- Aktuellen GPS-Standort auf ausdrücklichen Tastendruck speichern
- Lokale SQLite-Parkplatzhistorie, aktiven Parkplatz wechseln und Einträge löschen
- Parkplatznotizen, Bild aus Galerie/Dokumentauswahl (persistente URI), Standort teilen
- Fußweg-Navigation über externe Kartenanwendung (Google Maps URL / Browser)
- Parktimer mit 30/60/90/120/180 Minuten; inexact Android-Alarm und Benachrichtigung
- Opt-in Bluetooth-Automatik für **gekoppelte** Auto-Geräte mit sichtbar laufendem
  Location-Foreground-Service und Schutz gegen wiederholte Disconnect-Ereignisse
- Keine Registrierung, Werbung, Analytics, proprietäre Cloud-API oder In-App-Käufe
- GitHub Actions Workflow für JUnit-Tests und eine Android-Debug-APK

## Noch offen / Einschränkungen

- Dies ist **eine funktionale erste Version**, kein 1:1-Abbild aller Features der Referenz-App.
- Karten werden nur online geladen. Kein Massendownload: Die OSM-Tile-Richtlinie verbietet
  Offline-Prefetch über `tile.openstreetmap.org`. Gecachte sichtbare Kacheln sind möglich.
- Auf Xiaomi/HyperOS kann zusätzlich eine Freigabe unter „Akku“ → „Keine Einschränkungen“
  erforderlich sein, damit der sichtbare Vordergrunddienst stabil läuft.
- Bluetooth-Disconnect ist nicht in jedem Auto gleich zuverlässig; keine echte
  Bewegungserkennung, kein Android-Auto-Integrationstest, kein Wiederanlauf nach Neustart.
- GPS kann in Parkhäusern ungenau sein. Bei ausbleibender Ortung wird ausdrücklich **nichts** gespeichert.
- Der Timer ist bewusst ungenau; ohne Benachrichtigungsberechtigung keine sichtbare Erinnerung.
- Kein In-App-Fußweg-Routing, kein Offline-Kartenpaket, keine Widgets, kein Wear-OS und kein Cloud-Sync.
- Kein vollständiger Gerätetest und kein erfolgreicher Android-Build in der Erstellungsumgebung
  (hier fehlen Android SDK, Gradle und Maven-Downloads). CI ist vorbereitet, nicht ausgeführt.
- osmdroid ist archiviert. Der Kartenadapter sollte mittelfristig auf MapLibre umgestellt werden.

## APK erzeugen

**Methode A – GitHub:** Neues (idealerweise privates) Repo anlegen, den Inhalt dieses
Ordners auf `main` hochladen. Unter **Actions → RoyalPark Android → Run workflow**
wird `app-debug.apk` als Artefakt `RoyalPark-v0.1-debug` bereitgestellt, falls
Gradle-Build und Tests erfolgreich sind. GitHub Actions benötigt Internetzugriff.

**Methode B – Android Studio:** Ordner als Android-Projekt öffnen, Gradle 8.11.1
und JDK 17 auswählen und Build > Build APK(s) starten. Für einen reproduzierbaren
lokalen Kommandozeilen-Build kann auf einem Rechner mit Gradle 8.11.1
`gradle wrapper --gradle-version 8.11.1` ausgeführt werden; der Wrapper wird
anschließend eingecheckt. Dieses Archiv enthält noch keinen Wrapper-JAR.

**Installations-Hinweis:** Die Debug-APK ist nicht im Play Store erhältlich. Android
kann für manuelle Installationen die Freigabe „Aus dieser Quelle installieren“ verlangen.
Original-App und RoyalPark haben unterschiedliche Paketnamen und können nebeneinander bestehen.

## Code-Struktur

- `MainActivity.java`: unabhängiges UI, Berechtigungen und Nutzeraktionen
- `ParkingStore.java`, `ParkingSpot.java`: lokale Datenhaltung
- `LocationGateway.java`: frische Standortermittlung nach Erlaubnis
- `AutoParkService.java`: aktivierbarer Bluetooth-/GPS-Vordergrunddienst
- `AlarmScheduler.java`, `ParkingAlarmReceiver.java`: Parktimer
- `ParkingLogic.java`: überprüfbare fachliche Regeln
- `.github/workflows/android.yml`: Android-Build und Unit-Tests
- `docs/ROADMAP.md`: offene Features und Umsetzungsphasen

## Datenschutz & Berechtigungen

RoyalPark sendet **keine** gespeicherten Parkplatzkoordinaten an einen eigenen Server.
Der externe Tile-Server sieht notwendige Kartenausschnittanfragen und die IP-Adresse.
Bei externer Navigation gelangen Zielkoordinaten an den gewählten Kartenanbieter.
Bluetooth-Adressen und Notizen bleiben lokal. Für die aktive Automatik läuft eine
permanente Standort-Vordergrundmeldung, die jederzeit in Einstellungen beendet werden kann.

## Rechte und Lizenzen

Der eigene Quellcode ist zunächst für ein **privates Entwicklungsprojekt** vorgesehen;
eine Lizenz für spätere Veröffentlichung oder kommerzielle Nutzung wird noch festgelegt.
Kartenmaterial: © OpenStreetMap contributors, ODbL.
Verwendete Bibliothek: osmdroid (Apache-2.0), siehe deren Lizenzdateien.
Es wurden **keine** Original-APK-Ressourcen, Lizenzprüfungen, Signaturen oder privaten
Schnittstellen in das Projekt übernommen.
