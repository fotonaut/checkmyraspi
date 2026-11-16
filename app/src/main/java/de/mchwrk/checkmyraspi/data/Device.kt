package de.mchwrk.checkmyraspi.data

import kotlinx.serialization.Serializable

enum class AuthType { PASSWORD, KEY }

/**
 * Ein eingetragenes Gerät. Enthält bewusst **keine** Geheimnisse – Passwörter und Schlüssel
 * liegen verschlüsselt in [SecretStore].
 */
@Serializable
data class Device(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = 22,
    val user: String,
    val authType: AuthType = AuthType.PASSWORD,
    /** Standort, hierarchisch mit „›“ oder „/“ gegliedert, z. B. „Keller › Rack › oben“. */
    val location: String = "",
    /** Freitext, wie man ihn findet: „hinter dem Router, blaues Gehäuse“. */
    val locationNote: String = "",
    val description: String = "",
    /** Dateiname des Standortfotos in filesDir/photos. */
    val photo: String? = null,
    val hostKeyType: String? = null,
    val hostKeyFingerprint: String? = null,
    /** Beim Einrichten erkannt – zum Wiedererkennen, auch wenn sich die IP ändert. */
    val model: String? = null,
    val serial: String? = null,
    val mac: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

/** Geheimnisse eines Geräts (verschlüsselt gespeichert, nicht im Backup). */
@Serializable
data class DeviceSecrets(
    val password: String? = null,
    val privateKey: String? = null,
    val passphrase: String? = null,
    val sudoPassword: String? = null,
)
