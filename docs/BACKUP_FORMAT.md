# RoyalPark-Sicherungsformat v1

Endung: ZIP. Die Datei wird ausschließlich über den Android-Dateidialog ausgewählt.

## Einträge
- parking.json (UTF-8, maximal 2 MiB)
- optional photos/<id>.(jpg|png|webp) (max. 8 MiB je Foto)

Beispiel für parking.json:

    {
      "format": "royalpark-parking-backup",
      "version": 1,
      "exportedAt": 1793160000000,
      "records": [
        {
          "id": 1,
          "latitude": 53.545,
          "longitude": 8.582,
          "parkedAt": 1793160000000,
          "source": "MANUAL",
          "note": "Parkhaus am Bahnhof",
          "floor": "-2",
          "space": "B-17",
          "active": true,
          "photo": "photos/1.jpg"
        }
      ]
    }

Die Koordinaten sind fiktiv. photo ist optional.

## Regeln
- Prüft Formatversion, Koordinaten, ID-Eindeutigkeit, Anzahl, Textlängen, Dateipfade und entpackte Größe.
- Importiert atomar per SQLite-Transaktion; bestehende Daten werden nicht gelöscht.
- Gleiches Datum plus gleiche Koordinaten gilt als Duplikat.
- Importierte Timer werden nie neu aktiviert.
- Fotos werden im app-spezifischen Speicher gesichert und mit Android Content-URI verknüpft.
- Das Archiv enthält genaue Standorte und ist **nicht verschlüsselt**; bitte privat aufbewahren.
