package de.mchwrk.checkmyraspi.data

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TransferTest {
    @get:Rule val tmp = TemporaryFolder()

    private val d1 = Device(id = "1", name = "hausrat", host = "hausrat", user = "pi", location = "Büro › Regal", photo = "loc-a.jpg")
    private val d2 = Device(id = "2", name = "vitopi", host = "vitopi", user = "pi", authType = AuthType.KEY, location = "Keller")
    private val secrets = mapOf(
        "1" to DeviceSecrets(password = "geheim-1", sudoPassword = "sudo-1"),
        "2" to DeviceSecrets(privateKey = "-----BEGIN OPENSSH PRIVATE KEY-----\nabc\n", passphrase = "pp"),
    )

    private fun export(pass: String?): String {
        val dir = tmp.newFolder("photos")
        java.io.File(dir, "loc-a.jpg").writeBytes(byteArrayOf(1, 2, 3, 4))
        return Transfer.export(listOf(d1, d2), { secrets.getValue(it) }, dir, pass, "test", now = 42)
    }

    @Test fun `export without passphrase contains devices and photos but no secrets`() {
        val text = export(null)
        assertThat(text).doesNotContain("geheim-1")
        assertThat(text).doesNotContain("PRIVATE KEY")
        val p = Transfer.parse(text)
        assertThat(p.deviceCount).isEqualTo(2)
        assertThat(p.hasSecrets).isFalse()
        assertThat(p.file.photos.keys).containsExactly("loc-a.jpg")
    }

    @Test fun `encrypted secrets round trip and are not readable in the file`() {
        val text = export("lange-passphrase")
        assertThat(text).doesNotContain("geheim-1")
        assertThat(text).doesNotContain("sudo-1")
        val p = Transfer.parse(text)
        assertThat(p.hasSecrets).isTrue()
        assertThat(Transfer.decryptSecrets(p.file.secrets!!, "lange-passphrase")).isEqualTo(secrets)
    }

    @Test(expected = TransferException::class)
    fun `wrong passphrase is rejected`() {
        val p = Transfer.parse(export("lange-passphrase"))
        Transfer.decryptSecrets(p.file.secrets!!, "falsche-passphrase")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `short passphrase is refused on export`() { export("kurz") }

    @Test fun `apply adds new devices updates existing and restores photos`() {
        val p = Transfer.parse(export("lange-passphrase"))
        val store = mutableMapOf("2" to d2.copy(name = "alt"))
        val stored = mutableMapOf<String, DeviceSecrets?>()
        val photos = mutableListOf<ByteArray>()
        val r = Transfer.apply(
            p, Transfer.decryptSecrets(p.file.secrets!!, "lange-passphrase"),
            existing = { store[it] },
            writePhoto = { photos += it; "neu.jpg" },
            upsert = { d, s -> store[d.id] = d; stored[d.id] = s },
        )
        assertThat(r).isEqualTo(ImportResult(added = 1, updated = 1, withSecrets = true))
        assertThat(store["1"]!!.photo).isEqualTo("neu.jpg")
        assertThat(store["2"]!!.name).isEqualTo("vitopi")
        assertThat(photos.single().toList()).containsExactly(1.toByte(), 2.toByte(), 3.toByte(), 4.toByte()).inOrder()
        assertThat(stored["1"]!!.password).isEqualTo("geheim-1")
    }

    @Test fun `apply without secrets keeps credentials untouched`() {
        val p = Transfer.parse(export(null))
        val stored = mutableMapOf<String, DeviceSecrets?>()
        val r = Transfer.apply(p, null, { null }, { "x.jpg" }, { d, s -> stored[d.id] = s })
        assertThat(r.withSecrets).isFalse()
        assertThat(stored.values.filterNotNull()).isEmpty()
    }

    @Test fun `custom commands travel with the export`() {
        val dir = tmp.newFolder("p2")
        val cmd = QuickCommand("c1", "Pihole", "pihole status", sudo = true)
        val p = Transfer.parse(Transfer.export(listOf(d2), { secrets.getValue(it) }, dir, null, "test", 1, listOf(cmd)))
        assertThat(p.file.commands).containsExactly(cmd)
        // Alte Dateien ohne Befehle bleiben lesbar.
        assertThat(Transfer.parse(export(null)).file.commands).isEmpty()
    }

    @Test fun `foreign files are rejected`() {
        assertThat(runCatching { Transfer.parse("{\"hello\":1}") }.exceptionOrNull()).isInstanceOf(TransferException::class.java)
        assertThat(runCatching { Transfer.parse("kein json") }.exceptionOrNull()).isInstanceOf(TransferException::class.java)
        val newer = export(null).replace("\"version\":1", "\"version\":2")
        assertThat(runCatching { Transfer.parse(newer) }.exceptionOrNull()!!.message).contains("neueren")
    }
}
