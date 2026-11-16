# Changelog

## 1.2.0 – 16. November 2026

- **Befehle:** Schnellbefehle zum Nachsehen (fehlerhafte Dienste, Fehler im Journal, Kernel-Meldungen, Speicherplatz, Prozesse, Stromversorgung, Netzwerk, Dienst-Status/-Log) und Eingreifen (Dienst neu starten, Paketlisten aktualisieren, Paket-Cache leeren, Journal kürzen) – Ausgabe live in der App, abbrechbar, kopierbar.
- Freie Befehlseingabe (optional mit sudo) und gemerkte eigene Befehle; `{}` fragt beim Ausführen einen Wert ab (shell-sicher gequotet). Eigene Befehle wandern mit in den Export.
- **SSH-App:** Gerät mit einem Tipp in einer installierten SSH-App (ConnectBot, JuiceSSH …) öffnen.
- Hängt der SSH-Dienst eines Geräts (Port offen, aber keine Antwort), meldet die App das nach 30 s im Klartext.

## 1.1.0 – 16. November 2026

- Export/Import der Geräteliste (inkl. Standort, Notizen, Fotos); Zugangsdaten optional, mit Passphrase verschlüsselt.
- „Zugangsdaten fehlen – bitte bearbeiten“ für Geräte ohne Passwort/Schlüssel.

## 1.0.1 – 16. November 2026

- Verständliche Fehlermeldungen bei nicht erreichbaren Geräten (mit IP und wahrscheinlichem Grund).
- Hinweis „OS ohne Sicherheitsupdates“ für ausgelaufene Debian/Raspbian-Versionen.
- „1 Kern“ statt „1 Kerne“.

## 1.0.0 – 16. November 2026

Erste Veröffentlichung als Neubau von RasPi Check: Übersicht nach Standort, Standortfoto, Erkennung über Modell/Seriennummer/MAC, Zustand inkl. Unterspannung und apt-Updates, verschlüsselte Zugangsdaten, Host-Key-Prüfung, Neustart/Herunterfahren.
