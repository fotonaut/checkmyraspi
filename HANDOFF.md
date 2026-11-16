# Handoff – CheckMyRaspi (Stand 2026-11-16, 1.1.0 live)

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

## 1.1.0 (2026-11-16) – Export/Import
- `data/Transfer.kt`: JSON-Exportdatei (Geräte, Fotos als Base64); Zugangsdaten optional mit Passphrase
  (PBKDF2-HMAC-SHA256 120k → AES-256-GCM). Import upsert per ID; ohne Zugangsdaten → „Zugangsdaten fehlen“.
- Menü ⋮ in der Übersicht + „Aus Exportdatei importieren“ im leeren Zustand. Import-Filter `*/*` (LineageOS-
  Dateiauswahl auf dem Nexus 7 bot die .json mit engem MIME-Filter nicht an), Inhalt wird geprüft.
- Real: OPPO → Export (ohne Zugangsdaten) → Nexus 7 (09edbf66, LineageOS 18.1) importiert hausrat/vitopi/ubuntu.
  Auf dem Nexus fehlen noch die Passwörter (bewusst nicht von mir eingegeben). Exportdatei liegt auf beiden in Download/.
- Emulator: Export mit Passphrase → `pm clear` → Import → Login klappt (neuer Keystore-Schlüssel); falsche Passphrase abgelehnt.
- Gotcha: DocumentsUI auf dem Nexus ignoriert `adb input tap` auf Listeneinträgen → per DPAD/ENTER steuern.

## Noch nicht gemacht
- Neustart/Herunterfahren an einem echten Pi; Schlüssel-Login mit echter Datei; Standortfoto am Handy.

## Ideen für die nächsten Versionen
1. QR-Etikett zum Aufkleben → öffnet den Eintrag direkt.
2. Netzwerksuche (mDNS `*.local`, Port-22-Scan) zum schnellen Eintragen.
3. ~~Eigene Befehle + Vorlagen~~ → erledigt als „Befehle“ (Stufe 1, ui/CommandsScreen.kt, data/Commands.kt). Stufe 2: echtes Terminal (Termux terminal-view + sshj-PTY) als eigenes Gradle-Modul `:terminal`; vorher Spike gegen tools/fakepi.
4. Schlüsselpaar in der App erzeugen, Public Key teilen.
5. Hintergrundprüfung (WorkManager) mit Benachrichtigung bei offline/heiß/Unterspannung; Verlauf als Diagramm.
6. Widget; Export/Import der Geräteliste (ohne Geheimnisse); Docker-Container-Liste; GPS für Außenstandorte.
