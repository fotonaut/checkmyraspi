# CLAUDE.md – CheckMyRaspi

Native Android-App (Kotlin/Compose), Neubau von RasPi Check (eidottermihi/rpicheck, Fork: fotonaut/checkmyraspi).
Gleiche Toolchain wie `../enshu`, `../kompass`, `../timer` (AGP 8.5.2, Kotlin 1.9.24, Compose-BOM 2024.06.00,
compileSdk/targetSdk 34), hier **minSdk 26**. Kein Hilt/Room: `AppController` als manueller Container, JSON-Dateien.

- SSH: sshj **0.39.0** (nicht höher: ab 0.40 JDK-EdDSA, das Android erst ab API 33 hat). BouncyCastle wird in
  `CheckMyRaspiApp` als Provider 1 registriert. R8-Regeln in `app/proguard-rules.pro`.
- Alle Abfragen laufen als ein Skript (`ssh/StatusScript.kt`, Marker `@@CMR:`); Parser ist rein und getestet.
- Geheimnisse: `SecretStore` (noBackupFilesDir, AES-GCM via `KeystoreCipher`). `Device` enthält nie Geheimnisse.
- Host-Keys: TOFU in `RaspiClient`; geänderter Key → `SshFailure.HostKeyChanged`, nur per Nutzerbestätigung übernehmen.
- Build: `export JAVA_HOME=/home/andi/dev-tools/jdk-21`. Vor dem Emulator `./gradlew --stop`.
  Andreas' Handy hängt oft per USB → `ANDROID_SERIAL=emulator-5554` setzen.
