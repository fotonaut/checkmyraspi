package de.mchwrk.checkmyraspi.ssh

import kotlinx.serialization.Serializable

/** Momentaufnahme eines Geräts, aus einem einzigen SSH-Aufruf (siehe [StatusScript]). */
@Serializable
data class RaspiStatus(
    val model: String? = null,
    val hostname: String? = null,
    val os: String? = null,
    val kernel: String? = null,
    val arch: String? = null,
    val serial: String? = null,
    val uptimeSeconds: Long? = null,
    val load1: Double? = null,
    val load5: Double? = null,
    val load15: Double? = null,
    val cpuCount: Int? = null,
    val memTotalKb: Long? = null,
    val memAvailableKb: Long? = null,
    val swapTotalKb: Long? = null,
    val swapFreeKb: Long? = null,
    val cpuTempC: Double? = null,
    /** Rohwert von `vcgencmd get_throttled`, null wenn vcgencmd fehlt. */
    val throttledRaw: Long? = null,
    val coreVolts: Double? = null,
    val armFreqHz: Long? = null,
    val firmware: String? = null,
    val fanRpm: Int? = null,
    val disks: List<Disk> = emptyList(),
    val interfaces: List<NetInterface> = emptyList(),
    /** Anzahl aktualisierbarer apt-Pakete, null wenn kein apt. */
    val upgradable: Int? = null,
    val rebootRequired: Boolean = false,
    val systemTime: String? = null,
) {
    val throttle: Throttle? get() = throttledRaw?.let(::Throttle)

    val isRaspberryPi: Boolean get() = model?.contains("Raspberry Pi", ignoreCase = true) == true

    /** Erste MAC-Adresse eines physischen Interfaces (eth vor wlan) – zum Wiedererkennen. */
    val primaryMac: String?
        get() = interfaces.sortedBy { if (it.name.startsWith("e")) 0 else 1 }
            .firstOrNull { it.mac != null && it.mac != "00:00:00:00:00:00" }?.mac

    val memUsedPercent: Int?
        get() {
            val total = memTotalKb ?: return null
            val avail = memAvailableKb ?: return null
            if (total <= 0) return null
            return (((total - avail) * 100.0) / total).toInt()
        }

    /** Wurzeldateisystem, sonst das größte. */
    val rootDisk: Disk? get() = disks.firstOrNull { it.mount == "/" } ?: disks.maxByOrNull { it.sizeKb }
}

@Serializable
data class Disk(
    val filesystem: String,
    val mount: String,
    val sizeKb: Long,
    val usedKb: Long,
    val availKb: Long,
) {
    val usedPercent: Int get() = if (sizeKb <= 0) 0 else ((usedKb * 100.0) / sizeKb).toInt()
}

@Serializable
data class NetInterface(
    val name: String,
    val state: String? = null,
    val mac: String? = null,
    val ipv4: List<String> = emptyList(),
    val ipv6: List<String> = emptyList(),
    /** WLAN-Signal in dBm (aus /proc/net/wireless). */
    val wifiSignalDbm: Int? = null,
    /** WLAN-Link-Qualität 0–70. */
    val wifiQuality: Int? = null,
) {
    val isUp: Boolean get() = state == "up"
}

/**
 * Bits von `vcgencmd get_throttled`:
 * 0 Unterspannung, 1 Takt begrenzt, 2 gedrosselt, 3 Temperatur-Softlimit (jeweils jetzt);
 * 16–19 dieselben Ereignisse seit dem Booten.
 */
data class Throttle(val raw: Long) {
    private fun bit(n: Int) = raw and (1L shl n) != 0L

    val underVoltageNow get() = bit(0)
    val freqCappedNow get() = bit(1)
    val throttledNow get() = bit(2)
    val softTempLimitNow get() = bit(3)
    val underVoltageOccurred get() = bit(16)
    val freqCappedOccurred get() = bit(17)
    val throttledOccurred get() = bit(18)
    val softTempLimitOccurred get() = bit(19)

    val anyNow get() = raw and 0xF != 0L
    val anyOccurred get() = raw and 0xF0000 != 0L
    val ok get() = raw == 0L
}
