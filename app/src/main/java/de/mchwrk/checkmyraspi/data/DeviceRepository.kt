package de.mchwrk.checkmyraspi.data

import de.mchwrk.checkmyraspi.ssh.RaspiStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

enum class Reachability { UNKNOWN, ONLINE, OFFLINE, ERROR }

/** Letzter bekannter Zustand eines Geräts – damit die Übersicht auch ohne Netz etwas zeigt. */
@Serializable
data class Snapshot(
    val reachability: Reachability = Reachability.UNKNOWN,
    val message: String? = null,
    val checkedAt: Long = 0,
    val lastSeenAt: Long = 0,
    val status: RaspiStatus? = null,
)

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; prettyPrint = false }

private fun atomicWrite(file: File, text: String) {
    file.parentFile?.mkdirs()
    val tmp = File(file.parentFile, file.name + ".tmp")
    tmp.writeText(text)
    if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
}

/** Geräteliste als JSON-Datei (ohne Geheimnisse). */
class DeviceRepository(private val file: File) {
    private val _devices = MutableStateFlow(load())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    private fun load(): List<Device> = if (file.exists()) {
        runCatching { json.decodeFromString<List<Device>>(file.readText()) }.getOrDefault(emptyList())
    } else emptyList()

    fun get(id: String): Device? = _devices.value.firstOrNull { it.id == id }

    @Synchronized
    fun upsert(device: Device) {
        val list = _devices.value.filterNot { it.id == device.id } + device
        atomicWrite(file, json.encodeToString(list))
        _devices.value = list
    }

    @Synchronized
    fun delete(id: String) {
        val list = _devices.value.filterNot { it.id == id }
        atomicWrite(file, json.encodeToString(list))
        _devices.value = list
    }
}

/** Zuletzt abgefragte Zustände je Gerät. */
class SnapshotStore(private val file: File) {
    private val _snapshots = MutableStateFlow(load())
    val snapshots: StateFlow<Map<String, Snapshot>> = _snapshots.asStateFlow()

    private fun load(): Map<String, Snapshot> = if (file.exists()) {
        runCatching { json.decodeFromString<Map<String, Snapshot>>(file.readText()) }.getOrDefault(emptyMap())
    } else emptyMap()

    @Synchronized
    fun update(id: String, transform: (Snapshot) -> Snapshot) {
        val map = _snapshots.value.toMutableMap()
        map[id] = transform(map[id] ?: Snapshot())
        _snapshots.value = map
        atomicWrite(file, json.encodeToString(map.toMap()))
    }

    @Synchronized
    fun remove(id: String) {
        val map = _snapshots.value - id
        _snapshots.value = map
        atomicWrite(file, json.encodeToString(map))
    }
}
