# Dauerhafte RoyalPark Release-Signatur

RoyalPark verwendet ab Version 1.0.0 **einen eigenen, langlebigen Release-Schlüssel** statt wechselnder CI-Debug-Keys. Er wird niemals in Git committed.

## GitHub Actions
Im Repository unter **Settings → Secrets and variables → Actions → New repository secret** vier Werte hinterlegen:
- `ROYALPARK_KEYSTORE_BASE64`: Base64 der JKS-Datei ohne Zeilenumbrüche.
- `ROYALPARK_KEYSTORE_PASSWORD`: Passwort der JKS-Datei.
- `ROYALPARK_KEY_ALIAS`: Schlüsselalias (für diesen Release: `royalpark-release`).
- `ROYALPARK_KEY_PASSWORD`: Passwort des Schlüsselalias.

In Windows PowerShell:
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("RoyalPark-release.jks")) | Set-Clipboard
```
Kopierten Inhalt als `ROYALPARK_KEYSTORE_BASE64` eintragen. Passwortwerte separat aus der privat aufbewahrten Schlüsselnotiz entnehmen. Wer `gh` installiert hat, kann alternativ `gh secret set` benutzen.

`.github/workflows/release.yml` führt Unit-Tests aus, erstellt ein nicht-debuggbares Release-APK, signiert es über die vier GitHub-Secrets und verifiziert das APK mit Android `apksigner`. Fehlen Secrets, gibt es auf `main` **nur ein deutlich als UNSIGNED markiertes Build-Artefakt** zum lokalen Signieren. Ein `v*`-Tag ohne Secrets führt absichtlich zum Fehler. Secrets niemals in Issues, Logs, Commits oder Release-Notizen eintragen.

## Eigentümerschaft und Schlüsselarchiv
- Die `.jks` und das Passwort sind **das Eigentum des App-Herausgebers**. Beides getrennt, sicher und möglichst auf zwei Offline-Datenträgern archivieren. Passwortverlust bedeutet für normale APK-Updates Schlüsselverlust.
- Der Schlüssel ist **kein** Server- oder Account-Passwort. Bei Google Play App Signing können App-Signaturschlüssel anders verwaltet werden.
- Ein neuer Signaturschlüssel ist nicht kompatibel mit den früheren **Debug-APKs**. Vor Deinstallation auf der Debug-App unter *Einstellungen → Datensicherung* ein ZIP sichern und Datei prüfen, anschließend Debug deinstallieren, Release installieren, Backup importieren.
- Nach Wechsel zu einer Release-App mit gleicher Paket-ID und **identischer Signatur** können spätere Versionen mit höherem `versionCode` normalerweise direkt aktualisiert werden.
- Diese Signatur beweist Herausgeberkontinuität, keine Prüfung durch den Play Store. Gerätetests und Datenschutz-/Veröffentlichungsprüfungen bleiben erforderlich.

## Lokales APK-Signieren
Android SDK `apksigner`:
```bash
apksigner sign --ks RoyalPark-release.jks --ks-key-alias royalpark-release \
  --ks-pass env:RP_STOREPASS --key-pass env:RP_KEYPASS \
  --out RoyalPark-v1.0.0-release.apk app-release-unsigned.apk
apksigner verify --verbose --print-certs RoyalPark-v1.0.0-release.apk
```
Wenn das Paket aus `assembleRelease` stammt, wird es vom Android-Gradle-Plugin bereits korrekt ausgerichtet. Eine nachträgliche Änderung an einer signierten APK zerstört deren Signatur.
