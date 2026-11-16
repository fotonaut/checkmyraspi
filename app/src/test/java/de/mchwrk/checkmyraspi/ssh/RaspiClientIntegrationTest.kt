package de.mchwrk.checkmyraspi.ssh

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
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

    private suspend fun known() = pw(fp = client.query(pw(), acceptUnknownHostKey = true).hostKey.fingerprint)

    @Test fun `exec streams output with stderr and exit code`() = runBlocking {
        val out = StringBuilder()
        val r = client.exec(known(), "echo hallo; echo fehler >&2; exit 4", sudo = false, sudoPassword = null) { out.append(it) }
        assertThat(out.toString()).isEqualTo("hallo\nfehler\n")
        assertThat(r.exitCode).isEqualTo(4)
    }

    @Test fun `exec with sudo uses password from stdin`() = runBlocking {
        val out = StringBuilder()
        val r = client.exec(known(), "id -u; cat", sudo = true, sudoPassword = "raspberry-test") { out.append(it) }
        assertThat(out.toString()).isEqualTo("0\n")
        assertThat(r.exitCode).isEqualTo(0)
        val wrong = StringBuilder()
        val r2 = client.exec(known(), "id -u", sudo = true, sudoPassword = null) { wrong.append(it) }
        assertThat(r2.exitCode).isNotEqualTo(0)
        assertThat(wrong.toString()).contains("password")
    }

    @Test fun `exec output is truncated but command finishes`() = runBlocking {
        val out = StringBuilder()
        val r = client.exec(known(), "seq 1 100000", sudo = false, sudoPassword = null, maxOutput = 1000) { out.append(it) }
        assertThat(out.length).isEqualTo(1000)
        assertThat(r.truncated).isTrue()
        assertThat(r.exitCode).isEqualTo(0)
    }

    @Test fun `silent command longer than the socket timeout survives via keepalive`() = runBlocking {
        val short = RaspiClient(commandTimeoutSec = 3)
        val t = known()
        val out = StringBuilder()
        val r = short.exec(t, "sleep 8; echo durch", sudo = false, sudoPassword = null) { out.append(it) }
        assertThat(out.toString()).isEqualTo("durch\n")
        assertThat(r.exitCode).isEqualTo(0)
    }

    @Test fun `hanging sshd times out instead of waiting forever`() = runBlocking {
        // Port, der Verbindungen annimmt, aber nie ein SSH-Banner schickt (wie hausrat am 16.11.).
        java.net.ServerSocket(0).use { silent ->
            val accepted = mutableListOf<java.net.Socket>()
            val th = Thread { runCatching { while (true) accepted += silent.accept() } }.apply { isDaemon = true; start() }
            val start = System.currentTimeMillis()
            val e = runCatching {
                RaspiClient(commandTimeoutSec = 3).exec(SshTarget(host, silent.localPort, "pi", SshAuth.Password("x"), "SHA256:x"), "true", false, null) {}
            }.exceptionOrNull()
            assertThat(e).isInstanceOf(SshFailure::class.java)
            assertThat(System.currentTimeMillis() - start).isLessThan(10_000L)
            th.interrupt(); accepted.forEach { it.close() }
        }
    }

    @Test fun `exec can be cancelled`() = runBlocking {
        val t = known()
        val start = System.currentTimeMillis()
        val r = withTimeoutOrNull(1500) { client.exec(t, "sleep 30", sudo = false, sudoPassword = null) {} }
        assertThat(r).isNull()
        assertThat(System.currentTimeMillis() - start).isLessThan(5000L)
    }

    @Test fun `closed port is unreachable`() = runBlocking {
        val e = runCatching { client.query(SshTarget(host, 2299, "pi", SshAuth.Password("x"), null), true) }.exceptionOrNull()
        assertThat(e).isInstanceOf(SshFailure.Unreachable::class.java)
    }
}
