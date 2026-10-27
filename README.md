# RoyalPark – unabhängige Android-Parkplatz-App (v0.3.0)

Eigenständige, deutschsprachige Neuentwicklung anhand der Funktionsanalyse von „Mein Auto finden“ 1.1.15. Fremde APK-Ressourcen oder dekompilierter Programmcode wurden nicht übernommen.

## Funktionsstand
- GPS-Parkplatzspeicherung mit Genauigkeitsanzeige, aktiver Parkplatz und lokalem Verlauf
- **Manueller Karten-Pin:** lange auf die OSM-Karte drücken und Position bestätigen
- **In-App-Kompassnavigation** mit Live-Standort, Luftlinienentfernung, Richtungspfeil und Kartenanzeige
- Für tatsächliche Fußweg-Routenführung: Schaltfläche zur externen Navigations-App
- Parkplatznotiz, Foto **aufnehmen oder auswählen**, Parkplatz-Link teilen
- Parktimer mit Presets **und eigener Dauer** (1–1440 Minuten), Benachrichtigung
- Opt-in Bluetooth-Parkerkennung bei Trennung eines ausgewählten gekoppelten Geräts
- **Startbildschirm-Widget** mit aktuellem Parkplatz und Schnell-Speichern
- Eigene lokale SQLite-Datenbank, keine Cloud-Konten, Werbung oder Analytics
- CI mit Gradle-Tests, Debug-APK und JUnit-Domain-Tests

## Technische Grenzen
- Die interne Richtung ist **Luftlinie, keine berechnete Straßenroute**. Karten-App für vollständige Abbiegeanweisungen öffnen.
- Kein Bewegungsmodus der automatischen Parkerkennung, keine Wear-OS-Anbindung, keine vollumfänglichen Offlinekartenpakete.
- Die sichtbaren OSM-Kartenkacheln werden nach Bedarf geladen. Keine unzulässigen Offline-Prefetch-Abfragen.
- Android/Xiaomi kann Bluetooth-Hintergrundbetrieb und Timer verzögern. Der Timer verwendet bewusst einen ungenauen Alarm.
- Die erste Installation und die Funktionen müssen noch auf einem echten Gerät geprüft werden.
- Ein visuell pixelgenauer Nachbau der Original-App ist ohne Referenzscreenshots aus Version 1.1.15 nicht bestätigt.

## APK bauen
Push nach main oder feature/* startet GitHub Actions. Artefakt \`RoyalPark-v0.1-debug\` enthält derzeit die Debug-APK; GitHub Actions müssen erfolgreich sein. Eine vorhandene Debug-APK kann mit derselben Signatur als Update installiert werden; wechselt der Signaturschlüssel, muss vorher gesichert und neu installiert werden.

## Projekt
\`MainActivity\`: Karte, Verlauf, Einstellungen; \`NavigateActivity\`: integrierte Live-Richtungsanzeige; \`ParkingStore\`: lokale SQLite-Daten; \`AutoParkService\`: Bluetooth-Überwachung; \`ParkingWidget\`: Widget; \`NavigationMath\`: reine Mathematik mit Tests.

Android Studio: JDK 17, Gradle 8.11.1, compileSdk 35, targetSdk 35. Der Gradle-Wrapper ist noch nicht enthalten.

## UI 0.3.0
Die Oberfläche folgt nun der originalen hellen, kartenbasierten Navigation aus den offiziellen Store-Bildern: Fullscreen-OSM-Karte, schwebende Schaltflächen und weißes Parkplatz-Bottom-Sheet. Noch nicht pixelgenau auf einem realen Testgerät verifiziert.
