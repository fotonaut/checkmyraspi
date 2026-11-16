# Handoff – CheckMyRaspi (Stand 2026-11-16, 1.0.1 live)

## Erledigt (0.1.0)
- Fork `fotonaut/checkmyraspi` auf GitHub (Parent eidottermihi/rpicheck, archiviert). Lokal: Branch `main` =
  Neubau, `master` = Original, Tag `upstream-final`. Remote `upstream` zeigt auf das Original.
- Kotlin/Compose-Neubau: Übersicht nach Standort, Anlegen/Bearbeiten mit Verbindungstest, Detailansicht,
  Neustart/Herunterfahren, TOFU-Host-Keys, verschlüsselte Zugangsdaten, Standortfoto.
- 21 JVM-Tests (+ 6 SSH-Integrationstests gegen Docker-Fake-Pi), Lint ohne Fehler, Release-APK (R8) im Emulator durchgetestet.

## Release 1.0.0 (2026-11-16)
- `scripts/release.sh` → `dist/checkmyraspi-1.0.0.apk`, SHA-256 c26fc9b5…561b, gleiches Zertifikat wie Timer/Kompass/Enshū
  (keystore.properties → heartmonitor.jks, git-ignored).
- LIVE: https://checkmyraspi.mchwrk.de (`scripts/deploy-website.sh` → `web/mchwrk.de/checkmyraspi/`), Prüfsumme live verifiziert.
- Auf Andreas' OPPO (CPH2305) per adb installiert, startet; Bedienung am Handy noch nicht getestet.
- Website-Screenshots aus dem Emulator mit drei Demo-Containern (Fake-Modell über /sys/firmware-Bind-Mount,
  dafür `--security-opt systempaths=unconfined --security-opt apparmor=unconfined`).

## 1.0.1 (2026-11-16) – nach erstem Test mit echten Pis
- Andreas' OPPO: hausrat (Pi 3 B, Büro) und vitopi (Pi Zero W, Raspbian Buster, Keller) laufen, alle Werte plausibel.
- Klartext-Fehler bei nicht erreichbaren Geräten (EHOSTUNREACH/ECONNREFUSED/Timeout, mit IP); Badge „OS ohne
  Sicherheitsupdates“ (Debian-Codename → EOL-Datum in `Health`); „1 Kern“.
- SHA-256 aca08232…1d1d, live + auf dem OPPO installiert.
- mchwrk.de-Projektseite https://mchwrk.de/projekte/checkmyraspi/ (WP-ID 45, menu_order 14, 🍓) via
  `../wordpress-theme/migration/add-project-checkmyraspi.php`.

## Noch nicht gemacht
- Neustart/Herunterfahren an einem echten Pi; Schlüssel-Login mit echter Datei; Standortfoto am Handy.

## Ideen für die nächsten Versionen
1. QR-Etikett zum Aufkleben → öffnet den Eintrag direkt.
2. Netzwerksuche (mDNS `*.local`, Port-22-Scan) zum schnellen Eintragen.
3. Eigene Befehle (wie in RasPi Check) + Vorlagen (`apt upgrade`, Dienst neu starten).
4. Schlüsselpaar in der App erzeugen, Public Key teilen.
5. Hintergrundprüfung (WorkManager) mit Benachrichtigung bei offline/heiß/Unterspannung; Verlauf als Diagramm.
6. Widget; Export/Import der Geräteliste (ohne Geheimnisse); Docker-Container-Liste; GPS für Außenstandorte.
