package de.mchwrk.checkmyraspi.data

import com.google.common.truth.Truth.assertThat
import de.mchwrk.checkmyraspi.ssh.Disk
import de.mchwrk.checkmyraspi.ssh.RaspiStatus
import de.mchwrk.checkmyraspi.ui.Format
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun dev(id: String, name: String, loc: String) = Device(id = id, name = name, host = "$name.local", user = "pi", location = loc)

    @Test fun `normalize unifies separators and whitespace`() {
        assertThat(Locations.normalize(" Keller / Rack>  oben ")).isEqualTo("Keller › Rack › oben")
        assertThat(Locations.normalize("   ")).isEmpty()
        assertThat(Locations.topLevel("Keller › Rack")).isEqualTo("Keller")
        assertThat(Locations.topLevel("")).isEqualTo(Locations.NONE)
    }

    @Test fun `group sorts by location with unknown last and names inside`() {
        val g = Locations.group(listOf(
            dev("1", "zeta", "Keller › Rack"), dev("2", "alpha", ""), dev("3", "beta", "Büro"), dev("4", "alpha", "Keller"),
        ))
        assertThat(g.map { it.first }).containsExactly("Büro", "Keller", Locations.NONE).inOrder()
        assertThat(g[1].second.map { it.name }).containsExactly("alpha", "zeta").inOrder()
    }

    @Test fun `suggestions include parents ranked by frequency`() {
        val devices = listOf(dev("1", "a", "Keller › Rack"), dev("2", "b", "Keller › Regal"), dev("3", "c", "Garage"))
        assertThat(Locations.suggestions(devices, "").first()).isEqualTo("Keller")
        assertThat(Locations.suggestions(devices, "ra")).containsExactly("Garage", "Keller › Rack")
        assertThat(Locations.suggestions(devices, "Keller")).doesNotContain("Keller")
    }

    @Test fun `search matches location note and serial`() {
        val d = dev("1", "pi", "Keller").copy(locationNote = "hinter dem Router", serial = "10000000abcd")
        assertThat(Locations.matches(d, "router")).isTrue()
        assertThat(Locations.matches(d, "ABCD")).isTrue()
        assertThat(Locations.matches(d, "garage")).isFalse()
    }

    @Test fun `repository persists and reloads devices`() {
        val f = tmp.newFile("devices.json").also { it.delete() }
        val repo = DeviceRepository(f)
        repo.upsert(dev("1", "pi", "Keller"))
        repo.upsert(dev("1", "pi2", "Keller"))
        repo.upsert(dev("2", "nas", ""))
        val reloaded = DeviceRepository(f)
        assertThat(reloaded.devices.value.map { it.name }).containsExactly("pi2", "nas")
        reloaded.delete("1")
        assertThat(DeviceRepository(f).devices.value.map { it.id }).containsExactly("2")
    }

    @Test fun `secret store never writes plaintext`() {
        val xor = object : SecretCipher {
            override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
            override fun decrypt(data: ByteArray) = encrypt(data)
        }
        val f = tmp.newFile("secrets.bin").also { it.delete() }
        SecretStore(f, xor).put("1", DeviceSecrets(password = "geheim123"))
        assertThat(f.readText()).doesNotContain("geheim123")
        assertThat(SecretStore(f, xor).get("1").password).isEqualTo("geheim123")
        SecretStore(f, xor).remove("1")
        assertThat(SecretStore(f, xor).get("1").password).isNull()
    }

    @Test fun `snapshot store keeps last status`() {
        val f = tmp.newFile("snap.json").also { it.delete() }
        val store = SnapshotStore(f)
        store.update("1") { it.copy(reachability = Reachability.ONLINE, lastSeenAt = 5, status = RaspiStatus(hostname = "pi")) }
        store.update("1") { it.copy(reachability = Reachability.OFFLINE, checkedAt = 9) }
        val s = SnapshotStore(f).snapshots.value["1"]!!
        assertThat(s.reachability).isEqualTo(Reachability.OFFLINE)
        assertThat(s.lastSeenAt).isEqualTo(5)
        assertThat(s.status!!.hostname).isEqualTo("pi")
    }

    @Test fun `health flags undervoltage heat full disks and updates`() {
        val s = RaspiStatus(
            throttledRaw = 0x50005, cpuTempC = 82.0, upgradable = 3, rebootRequired = true,
            disks = listOf(Disk("/dev/root", "/", 100, 95, 5)),
        )
        val issues = Health.issues(s)
        assertThat(issues.map { it.text }).containsExactly("Unterspannung", "Gedrosselt", "82 °C", "/ 95 % voll", "Neustart nötig", "3 Updates").inOrder()
        assertThat(Health.worst(issues)).isEqualTo(Severity.CRITICAL)
        assertThat(Health.issues(RaspiStatus(throttledRaw = 0, cpuTempC = 45.0))).isEmpty()
        assertThat(Health.issues(RaspiStatus(throttledRaw = 0x10000)).single().text).isEqualTo("Unterspannung seit Boot")
    }

    @Test fun `format helpers`() {
        assertThat(Format.uptime(93784)).isEqualTo("1 T 2 h")
        assertThat(Format.uptime(300)).isEqualTo("5 min")
        assertThat(Format.kb(3884152)).isEqualTo("3,7 GB")
        assertThat(Format.kb(512)).isEqualTo("512 KB")
        assertThat(Format.ago(0, 10)).isEqualTo("nie")
        assertThat(Format.ago(1_000, 1_000 + 7 * 60_000)).isEqualTo("vor 7 min")
    }
}
