package de.mchwrk.checkmyraspi

import de.mchwrk.checkmyraspi.data.AuthType
import de.mchwrk.checkmyraspi.data.Device
import de.mchwrk.checkmyraspi.data.DeviceRepository
import de.mchwrk.checkmyraspi.data.DeviceSecrets
import de.mchwrk.checkmyraspi.data.Photos
import de.mchwrk.checkmyraspi.data.Reachability
import de.mchwrk.checkmyraspi.data.SecretStore
import de.mchwrk.checkmyraspi.data.SnapshotStore
import de.mchwrk.checkmyraspi.ssh.HostKey
import de.mchwrk.checkmyraspi.ssh.PowerAction
import de.mchwrk.checkmyraspi.ssh.QueryResult
import de.mchwrk.checkmyraspi.ssh.RaspiClient
import de.mchwrk.checkmyraspi.ssh.SshAuth
import de.mchwrk.checkmyraspi.ssh.SshFailure
import de.mchwrk.checkmyraspi.ssh.SshTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Zentrale Logik: Geräte abfragen, speichern, Host-Keys verwalten. */
class AppController(
    val devices: DeviceRepository,
    val snapshots: SnapshotStore,
    private val secrets: SecretStore,
    val photos: Photos,
    private val client: RaspiClient,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _refreshing = MutableStateFlow<Set<String>>(emptySet())
    val refreshing: StateFlow<Set<String>> = _refreshing.asStateFlow()

    private val limiter = Semaphore(4)

    /** Geräte, deren Host-Key sich geändert hat – mit dem neu präsentierten Schlüssel. */
    private val _hostKeyChanges = MutableStateFlow<Map<String, HostKey>>(emptyMap())
    val hostKeyChanges: StateFlow<Map<String, HostKey>> = _hostKeyChanges.asStateFlow()

    private var lastAutoRefresh = 0L

    /** Beim Öffnen der Übersicht: höchstens einmal pro Minute alle abfragen. */
    fun refreshAllIfStale(maxAgeMs: Long = 60_000) {
        val t = now()
        if (t - lastAutoRefresh < maxAgeMs) return
        lastAutoRefresh = t
        refreshAll()
    }

    /** Löscht Fotos, die kein Gerät mehr referenziert (z. B. nach abgebrochenem Bearbeiten). */
    fun cleanupOrphanPhotos() {
        val used = devices.devices.value.mapNotNull { it.photo }.toSet()
        photos.dir.listFiles()?.filter { it.name !in used }?.forEach { it.delete() }
        photos.clearCaptures()
    }

    fun secretsFor(id: String): DeviceSecrets = secrets.get(id)

    fun target(device: Device, s: DeviceSecrets = secrets.get(device.id)) = SshTarget(
        host = device.host,
        port = device.port,
        user = device.user,
        auth = when (device.authType) {
            AuthType.PASSWORD -> SshAuth.Password(s.password.orEmpty())
            AuthType.KEY -> SshAuth.Key(s.privateKey.orEmpty(), s.passphrase)
        },
        knownFingerprint = device.hostKeyFingerprint,
    )

    fun refreshAll() {
        scope.launch {
            coroutineScope {
                devices.devices.value.map { d -> async { limiter.withPermit { refreshNow(d.id) } } }.awaitAll()
            }
        }
    }

    fun refresh(id: String) {
        scope.launch { refreshNow(id) }
    }

    suspend fun refreshNow(id: String) {
        val device = devices.get(id) ?: return
        if (id in _refreshing.value) return
        _refreshing.update { it + id }
        try {
            // Trust-on-first-use: Ohne gemerkten Schlüssel wird der erste präsentierte gespeichert.
            val result = client.query(target(device), acceptUnknownHostKey = device.hostKeyFingerprint == null)
            val t = now()
            _hostKeyChanges.update { it - id }
            snapshots.update(id) { it.copy(Reachability.ONLINE, null, t, t, result.status) }
            // Erkennungsmerkmale nachpflegen, falls sie sich geändert haben (z. B. neue SD-Karte).
            val s = result.status
            val updated = device.copy(
                hostKeyType = device.hostKeyType ?: result.hostKey.type,
                hostKeyFingerprint = device.hostKeyFingerprint ?: result.hostKey.fingerprint,
                model = s.model ?: device.model,
                serial = s.serial ?: device.serial,
                mac = s.primaryMac ?: device.mac,
            )
            if (updated != device) devices.upsert(updated)
        } catch (e: SshFailure) {
            if (e is SshFailure.HostKeyChanged) _hostKeyChanges.update { it + (id to e.actual) }
            val reach = if (e is SshFailure.Unreachable || e is SshFailure.UnknownHost) Reachability.OFFLINE else Reachability.ERROR
            val msg = if (e is SshFailure.HostKeyChanged) "Host-Schlüssel geändert – Verbindung blockiert" else e.message
            snapshots.update(id) { it.copy(reachability = reach, message = msg, checkedAt = now()) }
        } catch (e: Exception) {
            snapshots.update(id) { it.copy(reachability = Reachability.ERROR, message = e.message ?: e.javaClass.simpleName, checkedAt = now()) }
        } finally {
            _refreshing.update { it - id }
        }
    }

    /** Verbindungstest beim Einrichten: akzeptiert einen noch unbekannten Host-Key. */
    suspend fun probe(device: Device, s: DeviceSecrets): QueryResult =
        client.query(target(device, s), acceptUnknownHostKey = device.hostKeyFingerprint == null)

    fun save(device: Device, s: DeviceSecrets, probe: QueryResult?) {
        val t = now()
        val existing = devices.get(device.id)
        devices.upsert(device.copy(createdAt = existing?.createdAt ?: t, updatedAt = t))
        secrets.put(device.id, s)
        if (probe != null) {
            snapshots.update(device.id) { Snapshot(Reachability.ONLINE, null, t, t, probe.status) }
        }
    }

    fun delete(id: String) {
        val d = devices.get(id)
        devices.delete(id)
        secrets.remove(id)
        snapshots.remove(id)
        photos.delete(d?.photo)
    }

    /** Nur nach ausdrücklicher Bestätigung in der Oberfläche aufrufen. */
    fun trustHostKey(id: String, hostKey: HostKey) {
        val d = devices.get(id) ?: return
        devices.upsert(d.copy(hostKeyType = hostKey.type, hostKeyFingerprint = hostKey.fingerprint, updatedAt = now()))
        _hostKeyChanges.update { it - id }
        refresh(id)
    }

    suspend fun power(id: String, action: PowerAction) {
        val d = devices.get(id) ?: return
        val s = secrets.get(id)
        val sudo = s.sudoPassword?.takeIf { it.isNotEmpty() }
            ?: s.password.takeIf { d.authType == AuthType.PASSWORD }
        client.power(target(d, s), action, sudo)
        snapshots.update(id) { it.copy(reachability = Reachability.OFFLINE, message = when (action) {
            PowerAction.REBOOT -> "Neustart ausgelöst"
            PowerAction.SHUTDOWN -> "Heruntergefahren"
        }, checkedAt = now()) }
    }
}

private typealias Snapshot = de.mchwrk.checkmyraspi.data.Snapshot
