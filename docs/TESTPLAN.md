# Testplan

## Automatisch
- `./gradlew testDebugUnitTest` – Parser (inkl. echtem `sh`-Lauf des Statusskripts), Standorte, Speicher,
  verschlüsselter SecretStore, Hinweise, Formatierung.
- Mit Test-Container (`tools/fakepi/run.sh`):
  `CMR_SSH_TEST=1 CMR_SSH_KEY=tools/fakepi/id_test ./gradlew testDebugUnitTest` – Passwort- und
  Schlüssel-Login, falsches Passwort, geänderter/unbekannter Host-Key, geschlossener Port.
- `./gradlew lintDebug assembleRelease` (R8!).

## Emulator (Release-APK) – zuletzt 2026-11-16 ✔
1. Leere Übersicht → „Raspi hinzufügen“.
2. Host `10.0.2.2`, Port `2222`, `pi` / `raspberry-test` → „Verbindung testen“: Modell/Hostname/OS +
   Fingerprint erscheinen; Fingerprint = `docker exec cmr-fakepi ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub`.
3. Standort „Keller / Rack“ (wird zu „Keller › Rack“), Notiz → Speichern → Karte unter „KELLER“ in der Übersicht,
   Badges „Unterspannung seit Boot“, „Neustart nötig“, „3 Updates“.
4. Detail: Zustand, Speicher, Netzwerk, System, Zugang & Erkennung.
5. ⋮ → Neu starten: ohne `shutdown` im Container → „Befehl fehlgeschlagen …“; mit Fake-`shutdown` läuft es als root.
6. Host-Keys im Container neu erzeugen + Neustart → Aktualisieren → rote Warnkarte, Verbindung blockiert;
   „Neuem Schlüssel vertrauen …“ → Dialog → Vertrauen → wieder online.

## Offen – echtes Gerät
- [ ] Echter Raspberry Pi (Pi OS Bookworm): Werte plausibel? Datenträger/Throttle/Lüfter (Pi 5)?
- [ ] Schlüssel-Login mit echter ed25519- und RSA-Datei aus dem Dateimanager.
- [ ] Standortfoto per Kamera-App und Galerie.
- [ ] Neustart/Herunterfahren mit sudo-Passwort und mit NOPASSWD.
- [ ] Dunkles Design, Tablet-Layout (mehrspaltige Übersicht).

## Befehle (Stufe 1)
- Gerät → „Befehle“: „Größte Verzeichnisse in /var“ (sudo) liefert Ausgabe; sudo-Passwort = Login-Passwort, wenn kein eigenes.
- Freier Befehl `echo a; ls /nicht-da` → Ausgabe inkl. Fehlermeldung, „Exit-Code 1“.
- „Dienst neu starten …“ mit Wert `sshd'x` → Bestätigung, Wert gequotet; im Alpine-Container „systemctl: not found“, Exit 127.
- „Neuer Befehl“ → speichern → erscheint unter „Gemerkte Befehle“ und läuft.
- „SSH-App“ ohne installierte App → Snackbar „Keine SSH-App gefunden …“.
- Abbrechen: in RaspiClientIntegrationTest (`sleep 30` per Timeout) abgedeckt.
