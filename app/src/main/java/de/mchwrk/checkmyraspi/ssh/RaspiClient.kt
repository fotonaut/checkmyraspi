package de.mchwrk.checkmyraspi.ssh

import de.mchwrk.checkmyraspi.data.CommandLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import net.schmizz.sshj.AndroidConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import net.schmizz.keepalive.KeepAliveProvider
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.connection.ConnectionException
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.TransportException
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.userauth.method.AuthKeyboardInteractive
import net.schmizz.sshj.userauth.method.AuthPassword
import net.schmizz.sshj.userauth.method.PasswordResponseProvider
import net.schmizz.sshj.userauth.password.PasswordUtils
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64
import java.util.concurrent.TimeUnit

sealed interface SshAuth {
    data class Password(val password: String) : SshAuth
    data class Key(val privateKey: String, val passphrase: String?) : SshAuth
}

data class SshTarget(
    val host: String,
    val port: Int,
    val user: String,
    val auth: SshAuth,
    /** Gemerkter Host-Key-Fingerprint; null = noch keiner (nur beim Einrichten erlaubt). */
    val knownFingerprint: String?,
)

data class HostKey(val type: String, val fingerprint: String)

data class QueryResult(val hostKey: HostKey, val status: RaspiStatus)

/** Ergebnis eines Befehls; exitCode null = unbekannt (Verbindung vorher beendet). */
data class ExecResult(val exitCode: Int?, val truncated: Boolean)

enum class PowerAction(val command: String) {
    REBOOT("shutdown -r now"),
    SHUTDOWN("shutdown -h now"),
}

/** Fehler mit deutscher Klartext-Meldung für die Oberfläche. */
sealed class SshFailure(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class UnknownHost(host: String) : SshFailure("Host „$host“ nicht gefunden.")
    class Unreachable(cause: Throwable, port: Int = 22) : SshFailure(unreachableMessage(cause, port), cause)
    class AuthFailed : SshFailure("Anmeldung abgelehnt – Benutzer, Passwort oder Schlüssel prüfen.")
    class BadKey(cause: Throwable) : SshFailure("Privater Schlüssel konnte nicht gelesen werden (Passphrase?).", cause)
    class HostKeyChanged(val expected: String, val actual: HostKey) :
        SshFailure("Host-Schlüssel hat sich geändert! Erwartet $expected, erhalten ${actual.fingerprint}.")
    class SudoFailed(detail: String) : SshFailure("Befehl fehlgeschlagen: $detail")
    class Transport(cause: Throwable) : SshFailure(transportMessage(cause), cause)
}

/** Verbindung steht, aber SSH antwortet nicht mehr (z. B. hängender sshd) → Klartext statt „Read timed out“. */
internal fun transportMessage(cause: Throwable): String {
    val timedOut = generateSequence(cause) { it.cause }.any { it is SocketTimeoutException || it.message?.contains("timed out", true) == true }
    return if (timedOut) "SSH antwortet nicht (Zeitüberschreitung) – Gerät überlastet oder SSH-Dienst hängt? Ggf. Strom aus/ein."
    else "Verbindungsfehler: ${cause.message ?: cause.javaClass.simpleName}"
}

/** Übersetzt Socket-Fehler in Klartext; die aufgelöste IP hilft beim Wiederfinden. */
internal fun unreachableMessage(cause: Throwable, port: Int): String {
    val raw = cause.message.orEmpty()
    val ip = Regex("""/(\d{1,3}(?:\.\d{1,3}){3}|[0-9a-fA-F:]{3,})\b""").find(raw)?.groupValues?.get(1)
    val at = ip?.let { " ($it)" } ?: ""
    return when {
        raw.contains("EHOSTUNREACH") || raw.contains("No route", true) || raw.contains("Keine Route", true) ->
            "Gerät antwortet nicht$at – ausgeschaltet oder in einem anderen Netz?"
        raw.contains("ECONNREFUSED") || raw.contains("refused", true) ->
            "Gerät erreichbar$at, aber SSH auf Port $port ist nicht aktiv."
        raw.contains("ENETUNREACH") || raw.contains("Network is unreachable", true) ->
            "Kein passendes Netz – WLAN oder VPN prüfen."
        cause is java.net.SocketTimeoutException || raw.contains("timed out", true) || raw.contains("after ") ->
            "Keine Antwort$at – Zeitüberschreitung. Ausgeschaltet oder Firewall?"
        else -> "Nicht erreichbar$at (${raw.ifEmpty { cause.javaClass.simpleName }})."
    }
}

object Fingerprints {
    /** OpenSSH-Format: `SHA256:<base64 ohne Padding>`. */
    fun sha256(key: PublicKey): String {
        val blob = Buffer.PlainBuffer().putPublicKey(key).compactData
        return sha256(blob)
    }

    fun sha256(blob: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(blob)
        return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
    }
}

/**
 * Trust-on-first-use: Ohne gemerkten Fingerprint wird der Schlüssel nur beim Einrichten akzeptiert
 * (und anschließend gespeichert), danach muss er exakt passen. rpicheck akzeptierte jeden Schlüssel.
 */
private class TofuVerifier(private val expected: String?, private val acceptUnknown: Boolean) : HostKeyVerifier {
    var presented: HostKey? = null

    override fun verify(hostname: String?, port: Int, key: PublicKey): Boolean {
        val hk = HostKey(KeyType.fromKey(key).toString(), Fingerprints.sha256(key))
        presented = hk
        return if (expected == null) acceptUnknown else expected == hk.fingerprint
    }

    override fun findExistingAlgorithms(hostname: String?, port: Int): List<String> = emptyList()
}

class RaspiClient(
    private val connectTimeoutMs: Int = 8_000,
    private val commandTimeoutSec: Long = 30,
) {
    private companion object { const val KEEPALIVE_SEC = 10 }

    /**
     * Verbindet, liest den Status und trennt wieder.
     * @param acceptUnknownHostKey nur beim Einrichten true – sonst muss der Fingerprint bekannt sein.
     */
    suspend fun query(target: SshTarget, acceptUnknownHostKey: Boolean = false): QueryResult =
        withContext(Dispatchers.IO) {
            connected(target, acceptUnknownHostKey) { client, hostKey ->
                val output = client.startSession().use { session -> runScript(session, StatusScript.script) }
                QueryResult(hostKey, StatusScript.parse(output))
            }
        }

    /** Neustart/Herunterfahren per sudo. Das sudo-Passwort geht über stdin, nie über die Kommandozeile. */
    suspend fun power(target: SshTarget, action: PowerAction, sudoPassword: String?) =
        withContext(Dispatchers.IO) {
            connected(target, acceptUnknownHostKey = false) { client, _ ->
                val session = client.startSession()
                try {
                    val command = if (sudoPassword.isNullOrEmpty()) {
                        "sudo -n ${action.command}"
                    } else {
                        "sudo -S -p '' ${action.command}"
                    }
                    val cmd = session.exec(command)
                    if (!sudoPassword.isNullOrEmpty()) {
                        cmd.outputStream.write((sudoPassword + "\n").toByteArray())
                        cmd.outputStream.flush()
                    }
                    cmd.join(15, TimeUnit.SECONDS)
                    val status = cmd.exitStatus
                    if (status != null && status != 0) {
                        val err = cmd.errorStream.bufferedReader().readText().trim()
                        throw SshFailure.SudoFailed(err.ifEmpty { "Exit-Code $status" }.lineSequence().last())
                    }
                } catch (e: ConnectionException) {
                    // Der Pi hat die Verbindung beim Herunterfahren gekappt – das ist Erfolg.
                } catch (e: TransportException) {
                    // dito
                } finally {
                    runCatching { session.close() }
                }
            }
        }

    /**
     * Führt einen Befehl aus und liefert die Ausgabe (stdout + stderr) stückweise an [onOutput].
     * Abbrechen der Coroutine trennt die Verbindung sofort.
     * @param sudo mit `sudo`; das Passwort geht über stdin ([sudoPassword] null → `sudo -n`).
     */
    suspend fun exec(
        target: SshTarget,
        command: String,
        sudo: Boolean,
        sudoPassword: String?,
        maxOutput: Int = CommandLine.MAX_OUTPUT,
        onOutput: (String) -> Unit,
    ): ExecResult = try {
        execBlocking(target, command, sudo, sudoPassword, maxOutput, onOutput)
    } catch (e: SshFailure) {
        currentCoroutineContext().ensureActive() // abgebrochen → CancellationException statt Verbindungsfehler
        throw e
    }

    private suspend fun execBlocking(
        target: SshTarget,
        command: String,
        sudo: Boolean,
        sudoPassword: String?,
        maxOutput: Int,
        onOutput: (String) -> Unit,
    ): ExecResult = runInterruptible(Dispatchers.IO) {
        connected(target, acceptUnknownHostKey = false, longRunning = true) { client, _ ->
            client.startSession().use { session ->
                val withPw = sudo && !sudoPassword.isNullOrEmpty()
                val cmd = session.exec(CommandLine.wrap(command, sudo, withPw))
                if (withPw) cmd.outputStream.write((sudoPassword + "\n").toByteArray())
                cmd.outputStream.close()
                val reader = cmd.inputStream.reader(Charsets.UTF_8)
                val buf = CharArray(4096)
                var total = 0
                var truncated = false
                while (true) {
                    val n = reader.read(buf)
                    if (n < 0) break
                    if (total >= maxOutput) { truncated = true; continue } // weiterlesen, damit der Befehl nicht hängt
                    val take = minOf(n, maxOutput - total)
                    total += take
                    onOutput(String(buf, 0, take))
                    if (take < n) truncated = true
                }
                val err = cmd.errorStream.bufferedReader().readText()
                cmd.join(5, TimeUnit.SECONDS)
                val status = cmd.exitStatus
                if (status != 0 && err.isNotBlank()) onOutput(err.trim().lineSequence().last() + "\n")
                ExecResult(status, truncated)
            }
        }
    }

    private fun runScript(session: Session, script: String): String {
        val cmd = session.exec("sh -s")
        cmd.outputStream.write(script.toByteArray())
        cmd.outputStream.close() // EOF → sh beendet sich nach dem Skript
        val out = cmd.inputStream.bufferedReader().readText()
        cmd.join(commandTimeoutSec, TimeUnit.SECONDS)
        return out
    }

    private fun <T> connected(
        target: SshTarget,
        acceptUnknownHostKey: Boolean,
        longRunning: Boolean = false,
        block: (SSHClient, HostKey) -> T,
    ): T {
        val verifier = TofuVerifier(target.knownFingerprint, acceptUnknownHostKey)
        val config = AndroidConfig()
        // Befehle dürfen lange still sein (apt-get update): keepalive@openssh.com wird vom Server beantwortet,
        // dadurch kommen regelmäßig Daten und der Socket-Timeout greift nur bei wirklich toter Verbindung.
        // Verbindungsaufbau und Anmeldung behalten den normalen Timeout (hängender sshd → Fehler statt ewig warten).
        if (longRunning) config.keepAliveProvider = KeepAliveProvider.KEEP_ALIVE
        val client = SSHClient(config)
        client.connectTimeout = connectTimeoutMs
        client.timeout = (commandTimeoutSec * 1000).toInt()
        if (longRunning) client.connection.keepAlive.keepAliveInterval = KEEPALIVE_SEC
        client.addHostKeyVerifier(verifier)
        try {
            try {
                client.connect(target.host, target.port)
            } catch (e: TransportException) {
                val presented = verifier.presented
                if (presented != null && target.knownFingerprint != null && presented.fingerprint != target.knownFingerprint) {
                    throw SshFailure.HostKeyChanged(target.knownFingerprint, presented)
                }
                throw SshFailure.Transport(e)
            } catch (e: UnknownHostException) {
                throw SshFailure.UnknownHost(target.host)
            } catch (e: SocketTimeoutException) {
                throw SshFailure.Unreachable(e, target.port)
            } catch (e: ConnectException) {
                throw SshFailure.Unreachable(e, target.port)
            } catch (e: NoRouteToHostException) {
                throw SshFailure.Unreachable(e, target.port)
            }
            authenticate(client, target)
            val hostKey = verifier.presented ?: throw SshFailure.Transport(IOException("Kein Host-Schlüssel"))
            return block(client, hostKey)
        } catch (e: SshFailure) {
            throw e
        } catch (e: IOException) {
            throw SshFailure.Transport(e)
        } finally {
            runCatching { client.disconnect() }
        }
    }

    private fun authenticate(client: SSHClient, target: SshTarget) {
        try {
            when (val auth = target.auth) {
                is SshAuth.Password -> {
                    val finder = PasswordUtils.createOneOff(auth.password.toCharArray())
                    val finder2 = PasswordUtils.createOneOff(auth.password.toCharArray())
                    // Manche sshd bieten nur keyboard-interactive an.
                    client.auth(
                        target.user,
                        AuthPassword(finder),
                        AuthKeyboardInteractive(PasswordResponseProvider(finder2)),
                    )
                }
                is SshAuth.Key -> {
                    val keys = try {
                        client.loadKeys(
                            auth.privateKey,
                            null,
                            auth.passphrase?.takeIf { it.isNotEmpty() }?.let { PasswordUtils.createOneOff(it.toCharArray()) },
                        ).also { it.public } // erzwingt das Parsen
                    } catch (e: Exception) {
                        throw SshFailure.BadKey(e)
                    }
                    client.authPublickey(target.user, keys)
                }
            }
        } catch (e: UserAuthException) {
            throw SshFailure.AuthFailed()
        }
        if (!client.isAuthenticated) throw SshFailure.AuthFailed()
    }
}
