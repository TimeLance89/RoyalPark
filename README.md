# RoyalPark v1.2.0 — moderner erster Start

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


## Version 1.2.0 – smartere Bedienung
- **Einstellungen:** Die angezeigte Version wird live aus den installierten Paketinformationen gelesen. Keine veraltete fest codierte Nummer, keine Entwicklungsbezeichnung im UI.
- **Karte:** Ich/Auto/Beide sind eine platzsparende horizontale Steuerung. Ohne gespeichertes Auto erscheint nur der GPS-Knopf.
- **Parkplatzkarte:** Zeigt standardmäßig nur Parkstatus, Entfernung und Route. Parkuhr/Notiz/Foto/weitere Aktionen werden bewusst durch „Weitere Aktionen“ aufgeklappt, ohne die Karte oder aktuelle Position neu zu laden.
- **Standortdaten:** Eine GPS-Genauigkeit von beispielsweise ±71 Metern darf nicht als exakt „0 m zum Auto“ verstanden werden. Die App zeigt dann einen Unsicherheitshinweis; mit guter Ortung gibt es die tatsächliche Luftlinienentfernung.
- **Parkzeit:** „Gerade geparkt“ statt „Vor 0 Minuten“.
- **Offline-Regionen:** Die Regionsliste erscheint wieder vollständig: Der Android-AlertDialog darf hierfür nicht gleichzeitig setMessage() und setItems() verwenden.
- **Kompatibilität:** Kein neues Datenbankschema, VersionCode 13. Die reguläre signierte Release-Linie bleibt bestehen.
- Android-/HyperOS-UI-Gerätetests und Download-Tests für konkrete Mapsforge-Regionen stehen noch aus.
