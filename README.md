# RoyalPark v1.1.0 — moderner erster Start

RoyalPark ist eine unabhängige Android-App für Parkplatzspeicherung, Parkuhr, Karten und Online-/Offline-Fußwegnavigation. Keine Registrierung, kein Konto und keine eigenen Tracking-Server.

## Neu: Erststart und visuelle Neugestaltung
- Vier kurze Einführungsschritte: Willkommen, Standortberechtigung, manuelle/automatische Speicherung, Offline-/Sicherungsfunktionen.
- Alles kann jederzeit übersprungen werden; keine automatisch gestarteten Überwachungsdienste.
- Standortzugriff wird erst nach einer ausdrücklich betätigten Schaltfläche angefragt, nicht unmittelbar beim ersten Start.
- Die Einführung ist danach unter Einstellungen → Erste Schritte erneut verfügbar.
- Erster Start wird lokal gespeichert. Bestehende Parkplatzdaten bleiben erhalten; kein Datenbankschema wurde verändert.
- Kartenansicht mit einer ruhigen Top-Leiste, kompakten Kartensteuerungen, modernisiertem Bottom Sheet und einem zentralen Route-Button.
- Neues Verlaufsdesign: zusammengefasste Datensätze mit Route- und Aktionen-Taste statt endloser Knopflisten.
- Verbesserte Typografie, Farben, Flächen und Abstände in den Einstellungen.
- Ungefähre Parkpositionen werden nach dem Speichern unaufdringlich gekennzeichnet; kein blockierendes GPS-Warnfenster.
- Nach dem Speichern wird kein Notizdialog automatisch geöffnet.

## Was bleibt unverändert
Parkplätze, Fotos, Parkzeit, Parkhausangaben, Export/Import, Kartenpakete, Widgets und Bluetooth-/Bewegungserkennung bleiben im eigenen Code vorhanden.

## Hinweis zu Berechtigungen
Standortgenauigkeit hängt von der Android-Freigabe ab. Ungefähre Freigabe erlaubt keine präzise Parkerkennung. Die App zeigt das an und bietet freiwillig den Weg zu den Android-App-Einstellungen.

## Offline
Die Kartendarstellung nutzt importierte Rasterkarten bzw. Mapsforge-Regionsdateien. Für offline berechnete Fußwege ist weiterhin BRouter mit eigenen geladenen .rd5-Daten nötig; Online-Routen nutzen nach Zustimmung FOSSGIS/Valhalla.

## Build und Signatur
GitHub Actions erstellt Debug- und nicht debuggable Release-Builds. VersionCode 12. Die Release-APK wird mit demselben privaten Zertifikat signiert wie RoyalPark 1.0.x, nicht mit einem Debug-Zertifikat.

Die reale Kontrolle auf Android/HyperOS, inklusive kleiner Displays, gedrehter Ausrichtung und Schriftgrößen, steht noch aus.
