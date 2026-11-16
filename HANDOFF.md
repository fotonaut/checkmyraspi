# Handoff – CheckMyRaspi (Stand 2026-11-16)

## Erledigt (0.1.0)
- Fork `fotonaut/checkmyraspi` auf GitHub (Parent eidottermihi/rpicheck, archiviert). Lokal: Branch `main` =
  Neubau, `master` = Original, Tag `upstream-final`. Remote `upstream` zeigt auf das Original.
- Kotlin/Compose-Neubau: Übersicht nach Standort, Anlegen/Bearbeiten mit Verbindungstest, Detailansicht,
  Neustart/Herunterfahren, TOFU-Host-Keys, verschlüsselte Zugangsdaten, Standortfoto.
- 21 JVM-Tests (+ 6 SSH-Integrationstests gegen Docker-Fake-Pi), Lint ohne Fehler, Release-APK (R8) im Emulator durchgetestet.

## Noch nicht gemacht
- Nichts gepusht – `main` liegt nur lokal.
- Kein Release-Signing/keine Website/kein mchwrk.de-Eintrag (Muster: `../enshu/scripts/`).
- Echter-Gerät-Test (docs/TESTPLAN.md).

## Ideen für die nächsten Versionen
1. QR-Etikett zum Aufkleben → öffnet den Eintrag direkt.
2. Netzwerksuche (mDNS `*.local`, Port-22-Scan) zum schnellen Eintragen.
3. Eigene Befehle (wie in RasPi Check) + Vorlagen (`apt upgrade`, Dienst neu starten).
4. Schlüsselpaar in der App erzeugen, Public Key teilen.
5. Hintergrundprüfung (WorkManager) mit Benachrichtigung bei offline/heiß/Unterspannung; Verlauf als Diagramm.
6. Widget; Export/Import der Geräteliste (ohne Geheimnisse); Docker-Container-Liste; GPS für Außenstandorte.
