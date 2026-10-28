# RoyalPark – Parität zur Referenz-App

## Fertig im Quellcode
- Aktuelle Position speichern; manuelle Pin-Wahl auf Karte
- Verlauf / Notizen / Fotos (Kamera und Galerie) / Teilen
- In-App-Kompass & Karte mit Luftlinienführung; externe Fußweg-Routen
- Parkuhr (Presets und benutzerdefiniert)
- Bluetooth-Automatik (Opt-in); aktiver Parkplatz im Widget
- Lokale Datenbank und automatischer GitHub-APK-Build

## Noch nicht originalgleich / nächster Ausbau
- Internes Offline-Routing und detailliertere Sprachnavigation (Online-Fußwegrouten und einfache Ansagen in 0.5.0)
- Geräterealtests der neuen Bewegungs-/Aktivitätserkennung und Kombinationslogik
- Originalgetreue Einstellungsseiten/Onboarding (Referenzscreenshots erforderlich)
- Download lizenzierter Offline-Kartenpakete und echtes Offline-Fußwegrouting (lokaler Import ab 0.7.0)
- Widget-Größen optimieren, Wear OS, erweiterter Parkhausmodus (Grundfunktion 0.6.0)
- Geräterealtests auf HyperOS/Android 15, Akkuoptimierung, Berechtigungen und Rotation
- Langfristig: Automatisierte UI-Tests, mehrere Fahrzeuge, Kartenbibliothekswechsel

Kein Fremdquellcode, keine fremden Grafiken, kein Lizenz-/Aboumgehungscode.

## Neu in 0.6.0
- Ebene/Stellplatz, SQLite-Migration v1→v2, ZIP-Backup und Merge-Restore inklusive zugänglicher Bilder
- Offen: Geräte-Ende-zu-Ende-Test von Datensicherung und Bildimport
- Offen: stabile Release-Signierung für Updates ohne Deinstallation

## 0.7.0
- OSMdroid OfflineTileProvider für importierte Offline-Rasterkarten, ohne Massendownload vom OSM-Server
- Standorttaste zeigt sofort einen frischen vorhandenen Fix und ergänzt ihn asynchron
- Offen: Geräterealtests verschiedener MBTiles/SQLite/ZIP-Archive und HyperOS-Standort-Performance
- Offen: Offline-Fußwegroutenberechnung

## 0.8.0
- Implementiert: vorgegebene DE-Regionen direkt als Mapsforge-Vektordatei herunterladen
- Implementiert: BRouter-Serviceanbindung und lokale GPX-Routenanzeige (BRouter-App mit .rd5-Daten separat erforderlich)
- Offen: Ein-Klick-Download derselben Regionen für Vektorkarte **und** BRouter-Segmente innerhalb von RoyalPark
- Offen: eigene native Offline-Routing-Engine ohne externe installierte App
- Offen: echte Offline-Wegbeschreibungen mit Sprachausgabe, Device-Tests und dauerhafte APK-Signierung

## 0.9.0 – umgesetzt
- Intelligente Kameraposition für Auto, eigenen Standort und beide zusammen
- Start direkt am geparkten Fahrzeug mit Nahzoom
- Parkdauer, Luftlinienentfernung und Warnung bei ungenauem GPS
- Noch offen: Realgerättests für Kamera, einheitliche Buttongrößen, andere Displayauflösungen

## Weiterentwicklung – priorisiert
1. Verlässliche Release-Signatur und Integrations-/Migrationstests, damit Daten bei Updates nicht gefährdet werden.
2. Mehrere Fahrzeuge inklusive frei benannter Profile und individuellem Bluetooth-Pairing.
3. Präzisere Ausstiegserkennung: Übergang Autofahrt → Fußweg und eine Bestätigungsphase, bevor die aktive Position ersetzt wird.
4. Parkzonen/Wiederkehrende Orte, Parkscheinablauf, Parkverbots- und Straßenreinigungserinnerungen (vom Nutzer konfiguriert, keine erfundenen örtlichen Vorschriften).
5. Parkhaus-Flow: Zufahrt, Ebene, Bereich, Foto, Offline-Speicherung, Standort-Genauigkeit sichtbar.
6. Stabilere Offlinekarte einschließlich Download-Integritätsprüfung, Segmentverwaltung und besserer lokaler Routingintegration.
7. Sichere Datenportabilität inklusive verschlüsselter Backups, optionaler selbst gehosteter Synchronisation.
8. Barrierefreiheit und UI-Tests, Energiesparverhalten, reproduzierbare Release-Builds.

## 1.2.0 – UX und Korrektheit
- Einstellungsseiten-Version dynamisch aus PackageManager statt fest kodiert.
- Schlanke Kartenelemente und ein ein-/ausklappbarer sekundärer Aktionsbereich.
- Offline-Regionenauswahl auf Xiaomi repariert (setMessage verdeckte setItems).
- Entfernung mit GPS-Unsicherheit und natürliche Parkdauer in einer getesteten Präsentationslogik.
- Offen: UI-Tests auf kleinen Displays, Bildschirmrotation und größere Schrift; Betrieb ohne Netzwerk.

## 1.3.0 – Kartenleistung
- Erledigt: MapView/Offline-Provider über Tabwechsel und Activity-Resume weiterverwenden.
- Erledigt: Kachelcache RAM 9→64, Overshoot=1, Animationen verkürzt, unnötige GPS-Zoomwechsel vermeiden.
- Erledigt: Kompassberechnung gedrosselt, Magnetausgleich wiederverwenden.
- Offen: Frame-Metriken auf realen Telefonen, große Offline-Vektorkarten benchmarken, gezielter Kartenrenderer-Vergleich.
- Offen: eventuell performantere Vektor-Engine evaluieren. Kein Wechsel ohne Android-Gerätevergleich.
