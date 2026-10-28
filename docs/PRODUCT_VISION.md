# RoyalPark – Produktvision nach Version 0.8

Ziel: Ein persönlicher Parkassistent mit möglichst wenig Bedienaufwand. "Wo steht mein Auto?" muss ohne Netz, ohne Nachdenken und ohne einen erneuten Ortswechsel funktionieren. Die ursprüngliche APK ist eine Funktionsreferenz; RoyalPark bleibt eine eigenständige Implementierung.

## Bestehender Bestand (geprüfter Quellcode)
- Aktiver Parkplatz, manuelles Setzen, lokale Historie und Notizen
- Fotoaufnahme/-verwaltung, Parkhaus-Ebene und Stellplatz
- Opt-in-Automatik für Auto-Bluetooth und Aktivitätswechsel
- Parkuhr, lokale Benachrichtigungen, drei Widgets
- Luftlinienkompass, Online-Routen und BRouter-Offline-Fußwege (separate App + .rd5 nötig)
- Mapsforge-Regionskarten, lokale Rasterkarten, ZIP-Backups und Zusammenführung

## Hochwertigste Weiterentwicklungen

### A. Orientierung und Nähe (0.9)
- Karte startet am gespeicherten Wagen statt mit einer unpassend weiten Deutschlandansicht.
- Ich / Auto / Beide, mit kontrolliertem GPS-Genauigkeitszoom.
- Echte Entfernung zum Auto als **Luftlinie** und relative Parkdauer in der Übersicht.
- Warnung bei schwacher Position, bevor eine versehentlich falsche Parkposition geschrieben wird.

### B. Nie wieder ein verlorenes Update (0.10)
- Dauerhaft sichere Signierung mit geheimem Keystore in GitHub-Actions-Secrets, niemals im Repository.
- Testfälle für Migration, Export-/Import und Foto-Berechtigungen.
- Gezielte Backups vor Update, dokumentierte Wiederherstellung.

### C. Weniger Fehlauslösungen (0.11)
- Optionale Nutzerbestätigung bei Automatik-Erkennung.
- Multi-Signal-Erkennung: Bluetooth plus Aktivitätswechsel mit realer GPS-Genauigkeit.
- Keine automatischen Standortüberschreibungen bei offensichtlich falschen Positionen.

### D. Individuelle Autos (0.12)
- Fahrzeuge mit Name/Farbe/zugehörigem BT-Gerät.
- Abstellhistorie je Fahrzeug, getrennte Widgets, klare Wahl aktives Auto.

### E. Parkhaus und Parkzone (0.13)
- Unterirdische GPS-Unschärfe auffangen: Einfahrt/Notiz/Foto/Stellplatz gesondert.
- Parkzeit-/Parkgebührhinweise ausschließlich nach Nutzereingaben, keine behaupteten gesetzlichen Regelungen.
- Wiederkehrende Parkplätze und Favoriten als Schnellwahl.

### F. Offline-Freiheit (später)
- Kartenregionen + Routingdaten aus zulässigen Quellen mit Lizenzhinweisen.
- Daten sparsam lokal, ohne automatische Standortübermittlung.
- Offline-Diagnose: aktuelle Kartenabdeckung, Segmentstatus, Speicherbedarf.

## Qualitätsprinzipien
- Nutzerbestätigung vor externem Standortversand und vor Überschreiben falscher Positionen.
- Offline-Funktion darf nicht stillschweigend ein Netz brauchen.
- Kein Kopieren geschützter Original-App-Bestandteile.
- Release-Gates: Unit-Tests, Android-Build, anschließend reale Geräteprüfung.
