# RoyalPark · v1.0.0

Eigenständige Android-Parkplatz-App. Hauptfunktionen: Parkplatz speichern, lokale Historie, Parkhausangaben, Fotos, ZIP-Backup, Parkuhr, Bluetooth-/Bewegungserkennung und Online-/Offline-Karten.

## Offline-Regionen (v0.8.0)
- Öffne **Einstellungen → Offline-Karten → Deutschland-Region auswählen**. Enthalten: Bremen/Bremerhaven, Niedersachsen, Hamburg, Schleswig-Holstein, Berlin und Brandenburg.
- Karten werden als Vektor-Datei `.map` vom Hochschul-Mirror des Mapsforge-Downloads geladen. Die Daten stammen aus OpenStreetMap (ODbL) und werden mit Mapsforge (LGPLv3) direkt auf dem Gerät gerendert.
- Beim Download zeigt RoyalPark den Fortschritt. Maximale Größe je Region: 650 MiB. Aktuell wählt die App immer eine Region gleichzeitig als aktive Karte; installierte andere Regionspakete bleiben gespeichert.
- Für die Kartendarstellung wird die Mapsforge-Region nach dem Download sofort aktiviert. Sie funktioniert ohne mobile Daten. Die alten Rasterkarten (.mbtiles, .sqlite, .zip) bleiben ebenfalls unterstützt.
- Bei fehlendem Internet verwendet RoyalPark die gewählte installierte Karte. Keine Kacheln werden vom öffentlichen OSM-Tileserver auf Vorrat geladen.

## Wirklich offline Fußwegrouting (v0.8.0)
- RoyalPark kann sich per Android-AIDL mit der separaten, quelloffenen **BRouter-App** (`btools.routingapp`, MIT-Lizenz) verbinden. Die Route wird dann ohne Internetzugriff auf dem Telefon berechnet und innerhalb von RoyalPark angezeigt.
- **Wichtig:** Installiere BRouter (z. B. über F-Droid) und lade dort im Downloadmanager die für deinen Standort notwendigen **.rd5-Routingsegmente** herunter. Die Mapsforge-Kartenregionen sind **nicht** gleichzeitig Routingdaten.
- In RoyalPark: Parkplatz öffnen → Route → **Offline-Fußweg (BRouter)**. Offline-Route, Entfernung und geschätzte Gehzeit werden eingeblendet; keine Abbiegetexte im Offline-Modus, da BRouter über diese GPX-Schnittstelle nur die Strecke liefert.
- Android muss den BRouter-Dienst öffnen dürfen. Fehlende App oder Segmente werden mit einer Fehlermeldung angezeigt; der Luftlinien-Kompass bleibt als Fallback nutzbar.
- Online-Routen über FOSSGIS/Valhalla bleiben als separate, erst nach Zustimmung genutzte Funktion erhalten.

## Limitierungen
- **Offlinekarte in RoyalPark und Routendaten in BRouter erfordern getrennte Downloads.** Das ist noch keine vollständig integrierte Ein-Klick-Lösung ohne zusätzliche App.
- Offline-Routen und Kartenimport müssen auf einem realen Android-/HyperOS-Gerät getestet werden. Der GitHub-Build allein beweist nicht, dass die BRouter-Schnittstelle auf jedem Gerät funktioniert.
- Ohne heruntergeladene .rd5-Daten rechnet BRouter nicht. Für längere Strecken können außerdem benachbarte Regionen notwendig sein.
- Regionale `.map`-Dateien sind freie OSM-Daten unter ODbL. Quelle: https://download.mapsforge.org/ . Mirror: https://ftp-stud.hs-esslingen.de/Mirrors/download.mapsforge.org/maps/v5/europe/germany/ .
- RoyalPark speichert private Parkplatzdaten lokal. Onlinekarten und optionale Online-Routen nutzen externe Dienstanbieter.

## Entwicklungsumgebung
Java 17, Gradle, Android SDK 35, osmdroid und osmdroid-mapsforge 6.1.20, Google Play Services für Aktivitätserkennung. GitHub Actions baut eine Debug-APK. Dauerhafte Release-Signierung und umfangreiche Geräterealtests sind noch offen.


## 0.9.0: Smart Map / Parkplatz im Fokus
- Karte öffnet bei aktivem Parkplatz direkt mit Nahzoom 19 statt 16.8.
- Schwebende Befehle **Ich**, **Auto** und **Beide**: Der Standort-Button ist ein Fadenkreuz; Auto zoomt nah an den aktuellen Parkplatz; Beide passt die Ansicht an beide Marker an.
- Im "Beide"-Modus passt sich der Kartenausschnitt an die tatsächliche Entfernung an; aus kurzer Distanz bleibt die Karte in einem sinnvollen Nahzoom statt wegzuzoomen.
- Beim Fokussieren des eigenen Standorts variiert der Zoom nach GPS-Genauigkeit, damit eine ungenaue Netzposition nicht fälschlich punktgenau erscheint.
- Die Parkplatzkarte zeigt neben der Speicherzeit eine relative Parkdauer und die aktuelle Entfernung zum Auto als **Luftlinie**. Der Entfernungshinweis ist anklickbar und öffnet die gemeinsame Ansicht.
- Bei manuell gespeichertem Standort mit mehr als 80 Metern Ungenauigkeit erscheint eine Bestätigung mit erneuter Ortung oder bewusstem Speichern. Für präzisere Garagenangaben ist weiterhin die lange Kartenberührung verfügbar.
- Die Funktion arbeitet bei installierten Offlinekarten genauso wie online. Die reine Kartenfokussierung fordert keine Routingabfrage an.
- Es wurde keine Datenbankmigration benötigt; bestehende Parkplätze bleiben unverändert.
- UI- und GPS-Gerätetests stehen weiterhin aus; GitHub-CI bestätigt nur Kompilierung und Unit-Tests.

## Version 1.0.0 – unterschriebene Distribution
- Neue Release-Pipeline unter `.github/workflows/release.yml` ohne Debug-Eigenschaften.
- Dauerhafter Release-Key, Signaturschemata APK v1/v2/v3, Signaturprüfung.
- Vier geschützte Actions-Secrets statt Schlüsseldaten im öffentlichen Repository.
- Alte Debug-Versionen können **nicht** über die neue Release-Version aktualisiert werden (Backup/Neuinstallation nötig).
- Anleitung: [`docs/RELEASE_SIGNING.md`](docs/RELEASE_SIGNING.md).
