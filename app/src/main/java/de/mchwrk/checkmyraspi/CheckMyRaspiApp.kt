package de.mchwrk.checkmyraspi

import android.app.Application
import de.mchwrk.checkmyraspi.data.CommandStore
import de.mchwrk.checkmyraspi.data.DeviceRepository
import de.mchwrk.checkmyraspi.data.KeystoreCipher
import de.mchwrk.checkmyraspi.data.Photos
import de.mchwrk.checkmyraspi.data.SecretStore
import de.mchwrk.checkmyraspi.data.SnapshotStore
import de.mchwrk.checkmyraspi.ssh.RaspiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.File
import java.security.Security

class CheckMyRaspiApp : Application() {
    lateinit var controller: AppController
        private set

    override fun onCreate() {
        super.onCreate()
        // Android bringt ein abgespecktes „BC“ mit; sshj braucht das vollständige BouncyCastle.
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.insertProviderAt(BouncyCastleProvider(), 1)

        controller = AppController(
            devices = DeviceRepository(File(filesDir, "devices.json")),
            snapshots = SnapshotStore(File(noBackupFilesDir, "snapshots.json")),
            secrets = SecretStore(File(noBackupFilesDir, "secrets.bin"), KeystoreCipher()),
            photos = Photos(this),
            commands = CommandStore(File(filesDir, "commands.json")),
            client = RaspiClient(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }
}
