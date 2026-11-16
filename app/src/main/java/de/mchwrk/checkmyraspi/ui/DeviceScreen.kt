package de.mchwrk.checkmyraspi.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import de.mchwrk.checkmyraspi.AppController
import de.mchwrk.checkmyraspi.data.AuthType
import de.mchwrk.checkmyraspi.data.Health
import de.mchwrk.checkmyraspi.data.Reachability
import de.mchwrk.checkmyraspi.design.Mchwrk
import de.mchwrk.checkmyraspi.ssh.PowerAction
import de.mchwrk.checkmyraspi.ssh.RaspiStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DeviceScreen(c: AppController, id: String, onEdit: () -> Unit, onBack: () -> Unit) {
    val devices by c.devices.devices.collectAsState()
    val snapshots by c.snapshots.snapshots.collectAsState()
    val refreshing by c.refreshing.collectAsState()
    val keyChanges by c.hostKeyChanges.collectAsState()
    val device = devices.firstOrNull { it.id == id }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var menu by remember { mutableStateOf(false) }
    var confirmPower by remember { mutableStateOf<PowerAction?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmTrust by remember { mutableStateOf(false) }
    var photoFull by remember { mutableStateOf(false) }

    LaunchedEffect(id) { c.refresh(id) }

    if (device == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val snap = snapshots[id]
    val status = snap?.status
    val reach = snap?.reachability ?: Reachability.UNKNOWN
    val busy = id in refreshing
    val newKey = keyChanges[id]

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(device.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück") } },
                actions = {
                    if (busy) CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                    else IconButton(onClick = { c.refresh(id) }) { Icon(Icons.Outlined.Refresh, "Aktualisieren") }
                    IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "Bearbeiten") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Mehr") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Neu starten") },
                                leadingIcon = { Icon(Icons.Outlined.RestartAlt, null) },
                                onClick = { menu = false; confirmPower = PowerAction.REBOOT },
                            )
                            DropdownMenuItem(
                                text = { Text("Herunterfahren") },
                                leadingIcon = { Icon(Icons.Outlined.PowerSettingsNew, null) },
                                onClick = { menu = false; confirmPower = PowerAction.SHUTDOWN },
                            )
                            DropdownMenuItem(
                                text = { Text("Löschen") },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                onClick = { menu = false; confirmDelete = true },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(reach)
                    Spacer(Modifier.width(8.dp))
                    Text(statusLine(snap, System.currentTimeMillis()), style = MaterialTheme.typography.bodyMedium, color = reachColor(reach))
                }

                if (newKey != null) {
                    SectionCard(null) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Outlined.WarningAmber, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Host-Schlüssel hat sich geändert", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                                Text(
                                    "Das passiert nach einer Neuinstallation – oder wenn sich jemand dazwischenschaltet. " +
                                        "Nur vertrauen, wenn du das Gerät neu aufgesetzt hast.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                KeyValue("Bisher", device.hostKeyFingerprint, mono = true)
                                KeyValue("Jetzt", newKey.fingerprint, mono = true)
                                TextButton(onClick = { confirmTrust = true }) { Text("Neuem Schlüssel vertrauen …") }
                            }
                        }
                    }
                }

                // Standort – das Wichtigste zum Wiederfinden
                SectionCard("Standort") {
                    if (device.location.isBlank() && device.locationNote.isBlank() && device.photo == null) {
                        Text("Kein Standort eingetragen.", color = Mchwrk.colors.muted)
                        TextButton(onClick = onEdit) { Text("Standort ergänzen") }
                    }
                    if (device.location.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Place, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(device.location, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    if (device.locationNote.isNotBlank()) Text(device.locationNote)
                    device.photo?.let { p ->
                        AsyncImage(
                            model = c.photos.file(p),
                            contentDescription = "Standortfoto",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(MaterialTheme.shapes.small)
                                .clickable { photoFull = true },
                        )
                    }
                    if (device.description.isNotBlank()) {
                        Text(device.description, color = Mchwrk.colors.muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (status != null && reach != Reachability.ONLINE) {
                    Text(
                        "Werte vom letzten erfolgreichen Abruf (${Format.ago(snap.lastSeenAt, System.currentTimeMillis())})",
                        style = MaterialTheme.typography.bodySmall,
                        color = Mchwrk.colors.muted,
                    )
                }
                status?.let { s ->
                    val issues = Health.issues(s)
                    if (issues.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            issues.forEach { Badge(it.text, severityColor(it.severity)) }
                        }
                    }
                    VitalsCard(s)
                    MemoryCard(s)
                    NetworkCard(s)
                    SystemCard(s)
                }

                SectionCard("Zugang & Erkennung") {
                    KeyValue("Host", "${device.user}@${device.host}:${device.port}", mono = true)
                    KeyValue("Anmeldung", if (device.authType == AuthType.KEY) "SSH-Schlüssel" else "Passwort")
                    KeyValue("Modell", device.model)
                    KeyValue("Seriennummer", device.serial, mono = true)
                    KeyValue("MAC", device.mac, mono = true)
                    KeyValue("Host-Key", device.hostKeyFingerprint?.let { "${device.hostKeyType ?: ""}\n$it".trim() }, mono = true)
                }
            }
        }
    }

    confirmPower?.let { action ->
        val verb = if (action == PowerAction.REBOOT) "neu starten" else "herunterfahren"
        AlertDialog(
            onDismissRequest = { confirmPower = null },
            title = { Text("„${device.name}“ $verb?") },
            text = {
                Text(
                    if (action == PowerAction.SHUTDOWN) "Danach ist er nur noch per Strom aus/ein wieder zu starten – Standort: ${device.location.ifBlank { "unbekannt" }}."
                    else "Er ist für etwa eine Minute nicht erreichbar.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmPower = null
                        scope.launch {
                            val msg = try {
                                c.power(id, action)
                                if (action == PowerAction.REBOOT) "Neustart ausgelöst" else "Wird heruntergefahren"
                            } catch (e: Exception) {
                                e.message ?: "Fehlgeschlagen"
                            }
                            snackbar.showSnackbar(msg)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(verb.replaceFirstChar { it.uppercase() }) }
            },
            dismissButton = { TextButton(onClick = { confirmPower = null }) { Text("Abbrechen") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("„${device.name}“ löschen?") },
            text = { Text("Eintrag, Zugangsdaten und Standortfoto werden aus der App entfernt. Am Gerät selbst ändert sich nichts.") },
            confirmButton = {
                Button(
                    onClick = { confirmDelete = false; c.delete(id); onBack() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } },
        )
    }

    if (confirmTrust && newKey != null) {
        AlertDialog(
            onDismissRequest = { confirmTrust = false },
            title = { Text("Neuem Host-Schlüssel vertrauen?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Vergleiche den Fingerprint mit der Ausgabe von\nssh-keygen -lf /etc/ssh/ssh_host_${newKey.type.substringAfter("ssh-").substringBefore("-")}_key.pub\nauf dem Gerät.")
                    Text(newKey.fingerprint, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(onClick = { confirmTrust = false; c.trustHostKey(id, newKey) }) { Text("Vertrauen") }
            },
            dismissButton = { TextButton(onClick = { confirmTrust = false }) { Text("Abbrechen") } },
        )
    }

    if (photoFull && device.photo != null) {
        Dialog(onDismissRequest = { photoFull = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            AsyncImage(
                model = c.photos.file(device.photo),
                contentDescription = "Standortfoto",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().clickable { photoFull = false },
            )
        }
    }
}

@Composable
private fun VitalsCard(s: RaspiStatus) {
    SectionCard("Zustand") {
        KeyValue("Temperatur", s.cpuTempC?.let(Format::temp))
        KeyValue("Last", listOfNotNull(s.load1, s.load5, s.load15).takeIf { it.isNotEmpty() }
            ?.joinToString(" · ") { Format.load(it) }?.plus(s.cpuCount?.let { "  ($it ${if (it == 1) "Kern" else "Kerne"})" } ?: ""))
        KeyValue("CPU-Takt", s.armFreqHz?.let(Format::mhz))
        KeyValue("Kernspannung", s.coreVolts?.let(Format::volts))
        KeyValue("Lüfter", s.fanRpm?.let { "$it U/min" })
        KeyValue("Läuft seit", s.uptimeSeconds?.let(Format::uptime))
        s.throttle?.let { t ->
            val text = if (t.ok) "Alles in Ordnung" else buildList {
                if (t.underVoltageNow) add("Unterspannung jetzt")
                if (t.throttledNow) add("gedrosselt jetzt")
                if (t.freqCappedNow) add("Takt begrenzt jetzt")
                if (t.softTempLimitNow) add("Temperaturlimit jetzt")
                if (t.underVoltageOccurred && !t.underVoltageNow) add("Unterspannung seit Boot")
                if (t.throttledOccurred && !t.throttledNow) add("gedrosselt seit Boot")
                if (t.freqCappedOccurred && !t.freqCappedNow) add("Takt begrenzt seit Boot")
                if (t.softTempLimitOccurred && !t.softTempLimitNow) add("Temperaturlimit seit Boot")
            }.joinToString(", ") + "  (0x${t.raw.toString(16)})"
            KeyValue("Stromversorgung", text)
            if (t.underVoltageNow || t.underVoltageOccurred) {
                Text(
                    "Tipp: Unterspannung kommt fast immer vom Netzteil oder Kabel – offizielles Netzteil verwenden.",
                    style = MaterialTheme.typography.bodySmall, color = Mchwrk.colors.muted,
                )
            }
        }
    }
}

@Composable
private fun MemoryCard(s: RaspiStatus) {
    SectionCard("Speicher") {
        val total = s.memTotalKb
        val avail = s.memAvailableKb
        if (total != null && avail != null) {
            UsageBar("Arbeitsspeicher", s.memUsedPercent ?: 0, "${Format.kb(total - avail)} von ${Format.kb(total)} belegt")
        }
        val swapT = s.swapTotalKb
        val swapF = s.swapFreeKb
        if (swapT != null && swapF != null && swapT > 0) {
            UsageBar("Swap", (((swapT - swapF) * 100.0) / swapT).toInt(), "${Format.kb(swapT - swapF)} von ${Format.kb(swapT)}")
        }
        s.disks.forEach { d ->
            UsageBar(d.mount, d.usedPercent, "${Format.kb(d.usedKb)} von ${Format.kb(d.sizeKb)} · ${d.filesystem}")
        }
    }
}

@Composable
private fun NetworkCard(s: RaspiStatus) {
    if (s.interfaces.isEmpty()) return
    SectionCard("Netzwerk") {
        s.interfaces.forEach { n ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(if (n.isUp) Reachability.ONLINE else Reachability.UNKNOWN)
                    Spacer(Modifier.width(8.dp))
                    Text(n.name, style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace)
                    n.wifiSignalDbm?.let {
                        Spacer(Modifier.width(8.dp))
                        Text("WLAN $it dBm", style = MaterialTheme.typography.bodySmall, color = Mchwrk.colors.muted)
                    }
                }
                (n.ipv4 + n.ipv6).forEach { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                n.mac?.let { Text("MAC $it", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = Mchwrk.colors.muted) }
            }
        }
    }
}

@Composable
private fun SystemCard(s: RaspiStatus) {
    SectionCard("System") {
        KeyValue("Modell", s.model)
        KeyValue("Hostname", s.hostname, mono = true)
        KeyValue("Betriebssystem", s.os)
        Health.endOfLife(s.os)?.let {
            Text(
                "„${it.replaceFirstChar { c -> c.uppercase() }}“ bekommt keine Sicherheitsupdates mehr – ein Upgrade oder eine Neuinstallation mit aktuellem Raspberry Pi OS lohnt sich.",
                style = MaterialTheme.typography.bodySmall, color = Mchwrk.colors.warn,
            )
        }
        KeyValue("Kernel", s.kernel, mono = true)
        KeyValue("Architektur", s.arch, mono = true)
        KeyValue("Firmware", s.firmware)
        KeyValue("Updates", s.upgradable?.let { if (it == 0) "Alles aktuell" else "$it Pakete aktualisierbar" })
        if (s.rebootRequired) KeyValue("Neustart", "nötig (nach Updates)")
        KeyValue("Systemzeit", s.systemTime)
    }
}
