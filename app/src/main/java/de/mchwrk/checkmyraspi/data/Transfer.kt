package de.mchwrk.checkmyraspi.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Export/Import der Geräteliste als JSON-Datei – zum Umzug auf ein anderes Gerät oder als Sicherung.
 *
 * Zugangsdaten sind auf dem Gerät mit einem Keystore-Schlüssel verschlüsselt, der das Gerät nie verlässt.
 * Für den Export werden sie deshalb nur auf Wunsch mitgenommen – dann mit einer Passphrase
 * (PBKDF2-HMAC-SHA256 → AES-256-GCM) neu verschlüsselt.
 */
@Serializable
data class ExportFile(
    val format: String = FORMAT,
    val version: Int = 1,
    val exportedAt: Long,
    val app: String,
    val devices: List<Device>,
    /** Dateiname → JPEG als Base64. */
    val photos: Map<String, String> = emptyMap(),
    val secrets: EncryptedSecrets? = null,
    /** Eigene Schnellbefehle (seit 1.2.0; ältere Dateien haben keine). */
    val commands: List<QuickCommand> = emptyList(),
) {
    companion object { const val FORMAT = "checkmyraspi-export" }
}

@Serializable
data class EncryptedSecrets(
    val kdf: String = "PBKDF2WithHmacSHA256",
    val iterations: Int,
    val salt: String,
    val iv: String,
    val data: String,
)

class TransferException(message: String) : Exception(message)

data class ImportPreview(val file: ExportFile) {
    val deviceCount get() = file.devices.size
    val hasSecrets get() = file.secrets != null
}

data class ImportResult(val added: Int, val updated: Int, val withSecrets: Boolean)

object Transfer {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private const val ITERATIONS = 120_000
    const val MIN_PASSPHRASE = 8

    fun export(
        devices: List<Device>,
        secrets: (String) -> DeviceSecrets,
        photoDir: File,
        passphrase: String?,
        app: String,
        now: Long = System.currentTimeMillis(),
        commands: List<QuickCommand> = emptyList(),
    ): String {
        val photos = devices.mapNotNull { d ->
            val name = d.photo ?: return@mapNotNull null
            val f = File(photoDir, name)
            if (f.isFile) name to Base64.getEncoder().encodeToString(f.readBytes()) else null
        }.toMap()
        val enc = passphrase?.let { pw ->
            require(pw.length >= MIN_PASSPHRASE) { "Passphrase zu kurz" }
            val map = devices.associate { it.id to secrets(it.id) }
            encrypt(json.encodeToString(map).encodeToByteArray(), pw)
        }
        return json.encodeToString(ExportFile(exportedAt = now, app = app, devices = devices, photos = photos, secrets = enc, commands = commands))
    }

    fun parse(text: String): ImportPreview {
        val file = try {
            json.decodeFromString<ExportFile>(text)
        } catch (e: Exception) {
            throw TransferException("Das ist keine CheckMyRaspi-Exportdatei.")
        }
        if (file.format != ExportFile.FORMAT) throw TransferException("Das ist keine CheckMyRaspi-Exportdatei.")
        if (file.version > 1) throw TransferException("Die Datei stammt aus einer neueren App-Version – bitte CheckMyRaspi aktualisieren.")
        return ImportPreview(file)
    }

    /** Entschlüsselt die Zugangsdaten; falsche Passphrase → [TransferException]. */
    fun decryptSecrets(enc: EncryptedSecrets, passphrase: String): Map<String, DeviceSecrets> {
        val plain = try {
            val key = deriveKey(passphrase, b64(enc.salt), enc.iterations)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, b64(enc.iv)))
            c.doFinal(b64(enc.data))
        } catch (e: Exception) {
            throw TransferException("Passphrase falsch.")
        }
        return json.decodeFromString(plain.decodeToString())
    }

    /**
     * Übernimmt die Geräte. Gleiche ID → wird aktualisiert (Zugangsdaten nur, wenn mitgeliefert).
     * Fotos werden unter neuem Namen abgelegt, damit nichts überschrieben wird.
     */
    fun apply(
        preview: ImportPreview,
        secrets: Map<String, DeviceSecrets>?,
        existing: (String) -> Device?,
        writePhoto: (ByteArray) -> String?,
        upsert: (Device, DeviceSecrets?) -> Unit,
    ): ImportResult {
        var added = 0
        var updated = 0
        for (d in preview.file.devices) {
            val photo = d.photo?.let { name -> preview.file.photos[name]?.let { writePhoto(b64(it)) } }
            val old = existing(d.id)
            if (old == null) added++ else updated++
            upsert(d.copy(photo = photo ?: old?.photo), secrets?.get(d.id))
        }
        return ImportResult(added, updated, secrets != null)
    }

    private fun encrypt(plain: ByteArray, passphrase: String): EncryptedSecrets {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also(rnd::nextBytes)
        val iv = ByteArray(12).also(rnd::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt, ITERATIONS), GCMParameterSpec(128, iv))
        val e = Base64.getEncoder()
        return EncryptedSecrets(iterations = ITERATIONS, salt = e.encodeToString(salt), iv = e.encodeToString(iv), data = e.encodeToString(c.doFinal(plain)))
    }

    private fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private fun b64(s: String) = Base64.getDecoder().decode(s)
}
