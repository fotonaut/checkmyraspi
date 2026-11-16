package de.mchwrk.checkmyraspi.data

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

class CommandsTest {
    @get:Rule val tmp = TemporaryFolder()

    /** Führt eine Zeile in einer echten sh aus – prüft, dass das Quoting wirklich hält. */
    private fun sh(line: String): String {
        val p = ProcessBuilder("sh", "-c", line).redirectErrorStream(true).start()
        p.outputStream.close()
        p.waitFor(10, TimeUnit.SECONDS)
        return p.inputStream.bufferedReader().readText()
    }

    @Test fun `quote survives quotes and shell metacharacters`() {
        val nasty = "it's \$(echo pwned); `id` && rm -rf / | \"x\""
        assertThat(sh("printf %s " + CommandLine.quote(nasty))).isEqualTo(nasty)
    }

    @Test fun `fill inserts the value quoted at every placeholder`() {
        val line = CommandLine.fill("echo {} {}", "a; echo injiziert")
        assertThat(sh(line).trim()).isEqualTo("a; echo injiziert a; echo injiziert")
        assertThat(CommandLine.fill("uptime", null)).isEqualTo("uptime")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fill without value is refused`() { CommandLine.fill("systemctl status {}", " ") }

    @Test fun `wrap merges stderr and keeps the exit code`() {
        val p = ProcessBuilder("sh", "-c", CommandLine.wrap("echo out; echo err >&2; exit 3", sudo = false, withPassword = false))
            .start()
        p.waitFor(10, TimeUnit.SECONDS)
        assertThat(p.inputStream.bufferedReader().readText()).isEqualTo("out\nerr\n")
        assertThat(p.errorStream.bufferedReader().readText()).isEmpty()
        assertThat(p.exitValue()).isEqualTo(3)
    }

    @Test fun `sudo wrap hides stdin from the command`() {
        val line = CommandLine.wrap("cat; echo ok", sudo = true, withPassword = true)
        assertThat(line).startsWith("sudo -S -p '' sh -c ")
        // Ohne sudo nachgestellt: der innere Teil darf das „Passwort“ auf stdin nicht lesen.
        val inner = line.removePrefix("sudo -S -p '' ")
        assertThat(inner).isNotEqualTo(line)
        val p = ProcessBuilder("sh", "-c", inner).start()
        p.outputStream.write("geheim\n".toByteArray()); p.outputStream.close()
        p.waitFor(10, TimeUnit.SECONDS)
        assertThat(p.inputStream.bufferedReader().readText()).isEqualTo("ok\n")
        assertThat(CommandLine.wrap("x", sudo = true, withPassword = false)).startsWith("sudo -n ")
    }

    @Test fun `builtin ids are unique and placeholders have a label`() {
        val all = BuiltinCommands.all
        assertThat(all.map { it.id }).containsNoDuplicates()
        all.filter { it.needsValue }.forEach { assertThat(it.param).isNotNull() }
        // Was etwas verändert, muss nachfragen.
        assertThat(all.filter { it.id in setOf("b-restart", "b-apt-clean", "b-vacuum") }.all { it.confirm }).isTrue()
    }

    @Test fun `store persists upserts deletes and merges`() {
        val f = File(tmp.root, "commands.json")
        val s = CommandStore(f)
        s.upsert(QuickCommand("1", "Uptime", "uptime"))
        s.upsert(QuickCommand("2", "Pihole", "pihole status", sudo = true))
        s.upsert(QuickCommand("1", "Laufzeit", "uptime -p"))
        assertThat(CommandStore(f).commands.value.map { it.label }).containsExactly("Laufzeit", "Pihole").inOrder()
        s.delete("2")
        s.merge(listOf(QuickCommand("1", "Import", "uptime"), QuickCommand("3", "Neu", "df")))
        assertThat(CommandStore(f).commands.value.map { it.label }).containsExactly("Import", "Neu")
    }
}
