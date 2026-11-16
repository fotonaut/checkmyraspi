package de.mchwrk.checkmyraspi.ssh

/**
 * Alle Abfragen in einem POSIX-sh-Skript, das per `sh -s` über **eine** SSH-Sitzung läuft.
 * rpicheck öffnete dafür rund 15 Sitzungen nacheinander; hier ist es ein Roundtrip.
 *
 * Jeder Abschnitt beginnt mit einer Markerzeile `@@CMR:<name>`. Alles vor dem ersten Marker
 * (z. B. Ausgaben aus `~/.ssh/rc` oder einem MOTD, rpicheck-Issue #228) wird ignoriert.
 */
object StatusScript {
    const val MARKER = "@@CMR:"

    val script: String = """
        exec 2>/dev/null
        export LC_ALL=C
        m() { echo; echo "$MARKER${'$'}1"; }
        m model; tr -d '\000' < /sys/firmware/devicetree/base/model
        m hostname; hostname
        m os; grep '^PRETTY_NAME=' /etc/os-release
        m kernel; uname -r
        m arch; uname -m
        m serial; grep -m1 '^Serial' /proc/cpuinfo || tr -d '\000' < /sys/firmware/devicetree/base/serial-number
        m uptime; cat /proc/uptime
        m loadavg; cat /proc/loadavg
        m nproc; nproc
        m meminfo; grep -E '^(MemTotal|MemAvailable|SwapTotal|SwapFree):' /proc/meminfo
        m temp; cat /sys/class/thermal/thermal_zone0/temp
        V=${'$'}(command -v vcgencmd || ls /usr/bin/vcgencmd /opt/vc/bin/vcgencmd | head -n1)
        if [ -n "${'$'}V" ]; then
          m throttled; "${'$'}V" get_throttled
          m volts; "${'$'}V" measure_volts core
          m armfreq; "${'$'}V" measure_clock arm
          m firmware; "${'$'}V" version | head -n1
        fi
        m fan; cat /sys/devices/platform/cooling_fan/hwmon/*/fan1_input
        m df; df -P -k -x tmpfs -x devtmpfs -x squashfs -x overlay -x efivarfs
        m net; for i in /sys/class/net/*; do n=${'$'}{i##*/}; [ "${'$'}n" = lo ] && continue; echo "${'$'}n ${'$'}(cat "${'$'}i/operstate") ${'$'}(cat "${'$'}i/address")"; done
        m ip; ip -o addr show
        m wifi; cat /proc/net/wireless
        if command -v apt >/dev/null; then m updates; apt list --upgradable 2>/dev/null | grep -c 'upgradable from'; fi
        m reboot; [ -f /var/run/reboot-required ] && echo yes
        m date; date '+%Y-%m-%d %H:%M:%S %Z'
        m end
    """.trimIndent() + "\n"

    /** Zerlegt die Skriptausgabe in Abschnitte (Name → Zeilen ohne Leerzeilen). */
    fun sections(output: String): Map<String, List<String>> {
        val result = linkedMapOf<String, MutableList<String>>()
        var current: MutableList<String>? = null
        for (raw in output.lineSequence()) {
            val line = raw.trimEnd('\r')
            if (line.startsWith(MARKER)) {
                current = mutableListOf<String>().also { result[line.removePrefix(MARKER).trim()] = it }
            } else if (current != null && line.isNotBlank()) {
                current += line
            }
        }
        return result
    }

    fun parse(output: String): RaspiStatus {
        val s = sections(output)
        fun first(name: String) = s[name]?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

        val load = first("loadavg")?.split(Regex("\\s+"))
        val mem = parseMeminfo(s["meminfo"].orEmpty())
        return RaspiStatus(
            model = first("model"),
            hostname = first("hostname"),
            os = first("os")?.substringAfter('=')?.trim('"', ' '),
            kernel = first("kernel"),
            arch = first("arch"),
            serial = first("serial")?.let { if (':' in it) it.substringAfter(':').trim() else it },
            uptimeSeconds = first("uptime")?.substringBefore(' ')?.toDoubleOrNull()?.toLong(),
            load1 = load?.getOrNull(0)?.toDoubleOrNull(),
            load5 = load?.getOrNull(1)?.toDoubleOrNull(),
            load15 = load?.getOrNull(2)?.toDoubleOrNull(),
            cpuCount = first("nproc")?.toIntOrNull(),
            memTotalKb = mem["MemTotal"],
            memAvailableKb = mem["MemAvailable"],
            swapTotalKb = mem["SwapTotal"],
            swapFreeKb = mem["SwapFree"],
            cpuTempC = first("temp")?.toLongOrNull()?.let { it / 1000.0 },
            throttledRaw = first("throttled")?.substringAfter('=')?.let(::parseHex),
            coreVolts = first("volts")?.let { Regex("([0-9.]+)").find(it.substringAfter('='))?.value?.toDoubleOrNull() },
            armFreqHz = first("armfreq")?.substringAfter('=')?.trim()?.toLongOrNull()?.takeIf { it > 0 },
            firmware = first("firmware"),
            fanRpm = first("fan")?.toIntOrNull(),
            disks = parseDf(s["df"].orEmpty()),
            interfaces = parseInterfaces(s["net"].orEmpty(), s["ip"].orEmpty(), s["wifi"].orEmpty()),
            upgradable = first("updates")?.toIntOrNull(),
            rebootRequired = first("reboot") == "yes",
            systemTime = first("date"),
        )
    }

    private fun parseHex(v: String): Long? = v.trim().removePrefix("0x").removePrefix("0X").toLongOrNull(16)

    internal fun parseMeminfo(lines: List<String>): Map<String, Long> = lines.mapNotNull { line ->
        val key = line.substringBefore(':').trim()
        val value = line.substringAfter(':').trim().split(Regex("\\s+")).firstOrNull()?.toLongOrNull()
        if (key.isEmpty() || value == null) null else key to value
    }.toMap()

    internal fun parseDf(lines: List<String>): List<Disk> = lines
        .filterNot { it.startsWith("Filesystem") }
        .mapNotNull { line ->
            val t = line.trim().split(Regex("\\s+"))
            if (t.size < 6) return@mapNotNull null
            val size = t[1].toLongOrNull() ?: return@mapNotNull null
            val used = t[2].toLongOrNull() ?: return@mapNotNull null
            val avail = t[3].toLongOrNull() ?: return@mapNotNull null
            // Einhängepunkt darf Leerzeichen enthalten.
            Disk(filesystem = t[0], mount = t.drop(5).joinToString(" "), sizeKb = size, usedKb = used, availKb = avail)
        }
        .filter { it.sizeKb > 0 && !it.mount.startsWith("/run") && !it.mount.startsWith("/sys") && !it.mount.startsWith("/dev") }

    internal fun parseInterfaces(net: List<String>, ip: List<String>, wifi: List<String>): List<NetInterface> {
        val v4 = mutableMapOf<String, MutableList<String>>()
        val v6 = mutableMapOf<String, MutableList<String>>()
        for (line in ip) {
            // "2: eth0    inet 192.168.1.5/24 brd 192.168.1.255 scope global dynamic eth0\ ..."
            val t = line.trim().split(Regex("\\s+"))
            if (t.size < 4) continue
            val name = t[1].removeSuffix(":").substringBefore('@')
            when (t[2]) {
                "inet" -> v4.getOrPut(name) { mutableListOf() } += t[3].substringBefore('/')
                "inet6" -> if ("scope global" in line) v6.getOrPut(name) { mutableListOf() } += t[3].substringBefore('/')
            }
        }
        val wifiInfo = mutableMapOf<String, Pair<Int?, Int?>>()
        for (line in wifi) {
            // " wlan0: 0000   70.  -40.  -256        0      0      0      0      0        0"
            if (!line.contains(':') || line.trimStart().startsWith("Inter") || line.trimStart().startsWith("face")) continue
            val name = line.substringBefore(':').trim()
            val t = line.substringAfter(':').trim().split(Regex("\\s+"))
            val quality = t.getOrNull(1)?.trimEnd('.')?.toDoubleOrNull()?.toInt()
            val level = t.getOrNull(2)?.trimEnd('.')?.toDoubleOrNull()?.toInt()
            wifiInfo[name] = quality to level
        }
        val names = net.mapNotNull { it.trim().split(Regex("\\s+")).firstOrNull() }
        return net.mapNotNull { line ->
            val t = line.trim().split(Regex("\\s+"))
            val name = t.firstOrNull() ?: return@mapNotNull null
            // Virtuelle Docker-/Bridge-Interfaces ohne Adresse blenden wir aus.
            if ((name.startsWith("veth") || name.startsWith("br-")) && v4[name].isNullOrEmpty()) return@mapNotNull null
            NetInterface(
                name = name,
                state = t.getOrNull(1),
                mac = t.getOrNull(2)?.takeIf { it.count { c -> c == ':' } == 5 },
                ipv4 = v4[name].orEmpty(),
                ipv6 = v6[name].orEmpty(),
                wifiQuality = wifiInfo[name]?.first,
                wifiSignalDbm = wifiInfo[name]?.second,
            )
        }.ifEmpty {
            // Ohne /sys/class/net: wenigstens die IP-Adressen anzeigen.
            (v4.keys + v6.keys).filter { it != "lo" && it !in names }.distinct()
                .map { NetInterface(name = it, ipv4 = v4[it].orEmpty(), ipv6 = v6[it].orEmpty()) }
        }
    }
}
