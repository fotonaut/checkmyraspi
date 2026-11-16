package de.mchwrk.checkmyraspi.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.mchwrk.checkmyraspi.AppController
import de.mchwrk.checkmyraspi.data.Device
import de.mchwrk.checkmyraspi.data.Health
import de.mchwrk.checkmyraspi.data.Locations
import de.mchwrk.checkmyraspi.data.Reachability
import de.mchwrk.checkmyraspi.data.Snapshot
import de.mchwrk.checkmyraspi.design.Mchwrk
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(c: AppController, onOpen: (String) -> Unit, onAdd: () -> Unit) {
    val devices by c.devices.devices.collectAsState()
    val snapshots by c.snapshots.snapshots.collectAsState()
    val refreshing by c.refreshing.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }

    // Uhr für „vor x min“
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        c.refreshAllIfStale()
        while (true) { delay(30_000); now = System.currentTimeMillis() }
    }

    val groups = Locations.group(devices.filter { Locations.matches(it, query) })
    val shown = groups.filter { filter == null || it.first == filter }
    val online = devices.count { snapshots[it.id]?.reachability == Reachability.ONLINE }
    val allGroups = Locations.group(devices).map { it.first }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("CheckMyRaspi", style = MaterialTheme.typography.titleLarge)
                        if (devices.isNotEmpty()) {
                            Text(
                                "${devices.size} ${if (devices.size == 1) "Gerät" else "Geräte"} · $online online",
                                style = MaterialTheme.typography.bodySmall,
                                color = Mchwrk.colors.muted,
                            )
                        }
                    }
                },
                actions = {
                    if (devices.isNotEmpty()) {
                        if (refreshing.isNotEmpty()) {
                            CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = { c.refreshAll() }) {
                                Icon(Icons.Outlined.Refresh, contentDescription = "Alle aktualisieren")
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            if (devices.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Icon(Icons.Outlined.Add, null) },
                    text = { Text("Raspi hinzufügen") },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (devices.isEmpty()) {
            EmptyState(Modifier.padding(padding), onAdd)
            return@Scaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 340.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            if (devices.size > 3) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Suche: Name, Standort, IP, Seriennr. …") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (allGroups.size > 1) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("Alle") }) }
                        items(allGroups) { g ->
                            FilterChip(
                                selected = filter == g,
                                onClick = { filter = if (filter == g) null else g },
                                label = { Text(g) },
                                leadingIcon = { Icon(Icons.Outlined.Place, null, Modifier.size(16.dp)) },
                            )
                        }
                    }
                }
            }
            shown.forEach { (location, list) ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "h-$location") {
                    Text(
                        "${location.uppercase()} · ${list.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Mchwrk.colors.muted,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(list, key = { it.id }) { d ->
                    DeviceCard(
                        c = c,
                        device = d,
                        snapshot = snapshots[d.id],
                        busy = d.id in refreshing,
                        now = now,
                        onClick = { onOpen(d.id) },
                    )
                }
            }
            if (shown.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Nichts gefunden.", color = Mchwrk.colors.muted, modifier = Modifier.padding(24.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceCard(c: AppController, device: Device, snapshot: Snapshot?, busy: Boolean, now: Long, onClick: () -> Unit) {
    val reach = snapshot?.reachability ?: Reachability.UNKNOWN
    val status = snapshot?.status
    val issues = if (reach == Reachability.ONLINE && status != null) Health.issues(status) else emptyList()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, Mchwrk.colors.line, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (device.photo != null) {
            AsyncImage(
                model = c.photos.file(device.photo),
                contentDescription = "Standortfoto",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.small),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 1.5.dp)
                } else {
                    StatusDot(reach)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    device.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                status?.cpuTempC?.takeIf { reach == Reachability.ONLINE }?.let {
                    Icon(Icons.Outlined.Thermostat, null, Modifier.size(16.dp), tint = Mchwrk.colors.muted)
                    Text(Format.temp(it), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                listOfNotNull(device.host, status?.model?.removePrefix("Raspberry ")).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = Mchwrk.colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (device.location.isNotBlank() || device.locationNote.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Place, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        listOf(device.location, device.locationNote).filter { it.isNotBlank() }.joinToString(" – "),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (issues.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    issues.forEach { Badge(it.text, severityColor(it.severity)) }
                }
            }
            Text(
                statusLine(snapshot, now),
                style = MaterialTheme.typography.bodySmall,
                color = if (reach == Reachability.ONLINE) Mchwrk.colors.muted else reachColor(reach),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

fun statusLine(s: Snapshot?, now: Long): String = when (s?.reachability ?: Reachability.UNKNOWN) {
    Reachability.UNKNOWN -> "Noch nicht abgefragt"
    Reachability.ONLINE -> "Online · " + Format.ago(s!!.checkedAt, now) +
        (s.status?.uptimeSeconds?.let { " · läuft seit ${Format.uptime(it)}" } ?: "")
    Reachability.OFFLINE -> (s!!.message ?: "Offline") + " · zuletzt gesehen " + Format.ago(s.lastSeenAt, now)
    Reachability.ERROR -> s!!.message ?: "Fehler"
}

@Composable
private fun EmptyState(modifier: Modifier, onAdd: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(88.dp).clip(MaterialTheme.shapes.large)
                .border(1.dp, Mchwrk.colors.line, MaterialTheme.shapes.large),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.DeveloperBoard, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(20.dp))
        Text("Noch keine Raspis", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Trag deinen ersten Raspberry Pi (oder jeden anderen Linux-Rechner mit SSH) ein – samt Standort, damit du ihn wiederfindest.",
            textAlign = TextAlign.Center,
            color = Mchwrk.colors.muted,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAdd) {
            Icon(Icons.Outlined.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Raspi hinzufügen")
        }
    }
}
