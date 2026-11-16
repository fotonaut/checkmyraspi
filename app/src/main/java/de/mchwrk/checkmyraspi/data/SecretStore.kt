package de.mchwrk.checkmyraspi.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Ver-/Entschlüsselung; auf dem Gerät per Android-Keystore, in Tests austauschbar. */
interface SecretCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(data: ByteArray): ByteArray
}

/**
 * Alle Geheimnisse als ein verschlüsselter Blob in noBackupFilesDir: Weder Klartext auf der
 * Platte noch im Android-Backup (rpicheck speicherte Passwörter im Klartext in SQLite).
 */
class SecretStore(private val file: File, private val cipher: SecretCipher) {
    private val json = Json { ignoreUnknownKeys = true }
    private var cache: MutableMap<String, DeviceSecrets>? = null

    @Synchronized
    private fun all(): MutableMap<String, DeviceSecrets> {
        cache?.let { return it }
        val loaded: MutableMap<String, DeviceSecrets> = if (file.exists()) {
            runCatching {
                json.decodeFromString<Map<String, DeviceSecrets>>(cipher.decrypt(file.readBytes()).decodeToString())
            }.getOrDefault(emptyMap()).toMutableMap()
        } else mutableMapOf()
        cache = loaded
        return loaded
    }

    @Synchronized
    fun get(id: String): DeviceSecrets = all()[id] ?: DeviceSecrets()

    @Synchronized
    fun put(id: String, secrets: DeviceSecrets) {
        val map = all()
        map[id] = secrets
        persist(map)
    }

    @Synchronized
    fun remove(id: String) {
        val map = all()
        if (map.remove(id) != null) persist(map)
    }

    private fun persist(map: Map<String, DeviceSecrets>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(cipher.encrypt(json.encodeToString(map).encodeToByteArray()))
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }
}
