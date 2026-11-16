package de.mchwrk.checkmyraspi.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Ein Schnellbefehl. `{}` im Befehl wird durch einen abgefragten Wert ersetzt (shell-sicher gequotet).
 * @param confirm vor dem Ausführen nachfragen (ändert etwas am Gerät)
 */
@Serializable
data class QuickCommand(
    val id: String,
    val label: String,
    val command: String,
    val sudo: Boolean = false,
    val confirm: Boolean = false,
    /** Bezeichnung des Werts für `{}`, z. B. „Dienst“. */
    val param: String? = null,
) {
    val needsValue get() = command.contains(CommandLine.PLACEHOLDER)
}

enum class CommandGroup(val title: String) { DIAGNOSE("Nachsehen"), WARTUNG("Eingreifen") }

object BuiltinCommands {
    val diagnose = listOf(
        QuickCommand("b-failed", "Fehlerhafte Dienste", "systemctl --failed --no-pager"),
        QuickCommand("b-errors", "Fehler seit dem Start", "journalctl -p err -b -n 50 --no-pager", sudo = true),
        QuickCommand("b-dmesg", "Kernel-Meldungen", "dmesg | tail -n 40", sudo = true),
        QuickCommand("b-df", "Speicherplatz", "df -h -x tmpfs -x devtmpfs"),
        QuickCommand("b-du", "Größte Verzeichnisse in /var", "du -xh -d 2 /var 2>/dev/null | sort -rh | head -n 15", sudo = true),
        QuickCommand("b-top-cpu", "Prozesse nach CPU", "ps -eo pid,user,pcpu,pmem,comm --sort=-pcpu | head -n 15"),
        QuickCommand("b-top-mem", "Prozesse nach Speicher", "ps -eo pid,user,pcpu,pmem,comm --sort=-pmem | head -n 15"),
        QuickCommand("b-power", "Temperatur & Stromversorgung", "vcgencmd measure_temp; vcgencmd measure_volts; vcgencmd get_throttled"),
        QuickCommand("b-net", "Netzwerk", "ip -br addr; echo; ip route"),
        QuickCommand("b-status", "Status eines Dienstes …", "systemctl --no-pager -n 20 status {}", param = "Dienst"),
        QuickCommand("b-unitlog", "Log eines Dienstes …", "journalctl -u {} -n 50 --no-pager", sudo = true, param = "Dienst"),
    )
    val wartung = listOf(
        QuickCommand("b-restart", "Dienst neu starten …", "systemctl restart {} && systemctl --no-pager -n 10 status {}", sudo = true, confirm = true, param = "Dienst"),
        QuickCommand("b-apt-update", "Paketlisten aktualisieren", "apt-get update -q && apt list --upgradable 2>/dev/null", sudo = true),
        QuickCommand("b-apt-clean", "Paket-Cache leeren", "apt-get clean && df -h /", sudo = true, confirm = true),
        QuickCommand("b-vacuum", "Journal auf 100 MB kürzen", "journalctl --vacuum-size=100M", sudo = true, confirm = true),
    )
    val all = diagnose + wartung
}

object CommandLine {
    const val PLACEHOLDER = "{}"
    /** Obergrenze der angezeigten Ausgabe in Zeichen. */
    const val MAX_OUTPUT = 200_000

    /** POSIX-sicheres Quoting in einfachen Anführungszeichen. */
    fun quote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    /** Setzt den Wert für `{}` ein – immer gequotet, damit kein Shell-Code eingeschleust wird. */
    fun fill(command: String, value: String?): String {
        if (!command.contains(PLACEHOLDER)) return command
        require(!value.isNullOrBlank()) { "Wert fehlt" }
        return command.replace(PLACEHOLDER, quote(value.trim()))
    }

    /**
     * Baut die Zeile, die per SSH ausgeführt wird. stderr landet mit in der Ausgabe.
     * Bei sudo liest `sudo -S` das Passwort aus stdin; der Befehl selbst bekommt /dev/null,
     * damit er das Passwort nie zu sehen bekommt (z. B. bei NOPASSWD-sudo).
     * sudos eigene Meldungen (Belehrung, „password is required“) bleiben auf stderr –
     * der Client zeigt sie nur, wenn der Befehl fehlschlägt.
     */
    fun wrap(command: String, sudo: Boolean, withPassword: Boolean): String {
        if (!sudo) return "{ $command\n} 2>&1"
        val inner = quote("exec </dev/null 2>&1\n$command")
        return if (withPassword) "sudo -S -p '' sh -c $inner" else "sudo -n sh -c $inner"
    }
}

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

/** Eigene Befehle (gelten für alle Geräte). */
class CommandStore(private val file: File) {
    private val _commands = MutableStateFlow(load())
    val commands: StateFlow<List<QuickCommand>> = _commands.asStateFlow()

    private fun load(): List<QuickCommand> = if (file.exists()) {
        runCatching { json.decodeFromString<List<QuickCommand>>(file.readText()) }.getOrDefault(emptyList())
    } else emptyList()

    @Synchronized
    fun upsert(cmd: QuickCommand) {
        val list = _commands.value.toMutableList()
        val i = list.indexOfFirst { it.id == cmd.id }
        if (i >= 0) list[i] = cmd else list += cmd
        save(list)
    }

    @Synchronized
    fun delete(id: String) = save(_commands.value.filterNot { it.id == id })

    /** Import: gleiche ID wird ersetzt, neue angehängt. */
    @Synchronized
    fun merge(cmds: List<QuickCommand>) {
        val byId = _commands.value.associateBy { it.id }.toMutableMap()
        cmds.forEach { byId[it.id] = it }
        save(byId.values.toList())
    }

    private fun save(list: List<QuickCommand>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(list))
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        _commands.value = list
    }
}
