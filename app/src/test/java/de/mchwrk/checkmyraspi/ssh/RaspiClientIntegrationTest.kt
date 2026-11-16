package de.mchwrk.checkmyraspi.ssh

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Läuft nur gegen den Test-Container aus docs/TESTPLAN.md:
 *   CMR_SSH_TEST=1 CMR_SSH_KEY=/pfad/id_test ./gradlew testDebugUnitTest
 */
class RaspiClientIntegrationTest {
    private val host = "127.0.0.1"
    private val port = 2222
    private val client = RaspiClient()

    @Before fun onlyWithContainer() {
        assumeTrue(System.getenv("CMR_SSH_TEST") == "1")
    }

    private fun pw(pw: String = "raspberry-test", fp: String? = null) =
        SshTarget(host, port, "pi", SshAuth.Password(pw), fp)

    @Test fun `password login reads status and learns host key`() = runBlocking {
        val r = client.query(pw(), acceptUnknownHostKey = true)
        assertThat(r.hostKey.fingerprint).startsWith("SHA256:")
        assertThat(r.status.hostname).isEqualTo("keller-pi")
        assertThat(r.status.throttledRaw).isEqualTo(0x50000L)
        assertThat(r.status.upgradable).isEqualTo(3)
        assertThat(r.status.rebootRequired).isTrue()
        assertThat(r.status.memTotalKb).isGreaterThan(0L)
        // Mit gemerktem Schlüssel klappt es ohne TOFU-Erlaubnis.
        val again = client.query(pw(fp = r.hostKey.fingerprint))
        assertThat(again.hostKey).isEqualTo(r.hostKey)
    }

    @Test fun `key login works`() = runBlocking {
        val keyFile = System.getenv("CMR_SSH_KEY")
        assumeTrue(keyFile != null)
        val r = client.query(SshTarget(host, port, "pi", SshAuth.Key(File(keyFile!!).readText(), null), null), acceptUnknownHostKey = true)
        assertThat(r.status.hostname).isEqualTo("keller-pi")
    }

    @Test(expected = SshFailure.AuthFailed::class)
    fun `wrong password is reported as auth failure`() {
        runBlocking { client.query(pw("falsch"), acceptUnknownHostKey = true) }
    }

    @Test fun `changed host key is detected`() = runBlocking {
        val e = runCatching { client.query(pw(fp = "SHA256:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")) }.exceptionOrNull()
        assertThat(e).isInstanceOf(SshFailure.HostKeyChanged::class.java)
        assertThat((e as SshFailure.HostKeyChanged).actual.fingerprint).startsWith("SHA256:")
    }

    @Test fun `unknown host key is refused without tofu`() = runBlocking {
        val e = runCatching { client.query(pw()) }.exceptionOrNull()
        assertThat(e).isInstanceOf(SshFailure::class.java)
        assertThat(e).isNotInstanceOf(SshFailure.AuthFailed::class.java)
    }

    @Test fun `closed port is unreachable`() = runBlocking {
        val e = runCatching { client.query(SshTarget(host, 2299, "pi", SshAuth.Password("x"), null), true) }.exceptionOrNull()
        assertThat(e).isInstanceOf(SshFailure.Unreachable::class.java)
    }
}
