# CheckMyRaspi

Android-App, die deine Raspberry Pis (und andere Linux-Rechner) per SSH abfragt und in einer
Übersicht zeigt – **mit Standort**, damit du jeden Pi auch wiederfindest.

Ein MCHWRK-Projekt. Neubau auf Basis der Idee und der SSH-Abfragen von
[RasPi Check](https://github.com/eidottermihi/rpicheck) von Michael Prankl (MIT-Lizenz; seit 2023 archiviert).
Der ursprüngliche Code ist in der Git-Historie erhalten (Tag `upstream-final`).

## Funktionen

- **Übersicht** aller Geräte als Karten, gruppiert und filterbar nach Standort, mit Suche
  (Name, Standort, IP, Seriennummer, MAC …). Ein neu eingetragener Pi erscheint sofort dort.
- **Standort**: hierarchisch („Keller › Rack › oben“) mit Vorschlägen, Freitext „So findest du ihn“
  und Standortfoto (Kamera oder Galerie).
- **Erkennung**: Modell, Seriennummer und MAC werden beim Einrichten gemerkt – der Pi bleibt
  identifizierbar, auch wenn sich die IP ändert.
- **Zustand**: Temperatur, Last, Takt, Kernspannung, Lüfter, Uptime, **Unterspannung/Drosselung**
  (`vcgencmd get_throttled`), RAM/Swap/Datenträger, Netzwerk inkl. WLAN-Signal, OS/Kernel/Firmware,
  ausstehende **apt-Updates** und „Neustart nötig“.
- **Hinweise** als Badges (Unterspannung, heiß, Platte voll, Updates …).
- **Neustart / Herunterfahren** über sudo – das Passwort geht über stdin, nie über die Kommandozeile.

## Sicherheit (gegenüber RasPi Check)

| | RasPi Check | CheckMyRaspi |
|---|---|---|
| Passwörter/Schlüssel | Klartext in SQLite, im Backup | AES-GCM mit Android-Keystore-Schlüssel, nicht im Backup |
| Host-Key | jeder akzeptiert (`PromiscuousVerifier`) | Trust-on-first-use, Wechsel wird blockiert und angezeigt |
| Schlüsseldatei | Dateipfad (bricht mit Scoped Storage) | Inhalt importiert (Datei oder Zwischenablage) |
| SSH-Sitzungen pro Abfrage | ~15 | 1 (ein Skript, siehe `StatusScript.kt`) |

## Bauen

```bash
export JAVA_HOME=/home/andi/dev-tools/jdk-21
./gradlew testDebugUnitTest lintDebug assembleRelease
```

Test-„Raspi“ per Docker: `tools/fakepi/run.sh`, danach
`CMR_SSH_TEST=1 CMR_SSH_KEY=tools/fakepi/id_test ./gradlew testDebugUnitTest` für die SSH-Integrationstests.
Details in `docs/TESTPLAN.md`.

## Lizenz

MIT – siehe `LICENSE`.
