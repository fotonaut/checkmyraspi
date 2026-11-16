package de.mchwrk.checkmyraspi.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import de.mchwrk.checkmyraspi.AppController
import de.mchwrk.checkmyraspi.data.AuthType
import de.mchwrk.checkmyraspi.data.Device
import de.mchwrk.checkmyraspi.data.DeviceSecrets
import de.mchwrk.checkmyraspi.data.Locations
import de.mchwrk.checkmyraspi.design.Mchwrk
import de.mchwrk.checkmyraspi.ssh.QueryResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditScreen(c: AppController, deviceId: String?, onDone: (String) -> Unit, onBack: () -> Unit) {
    val existing = remember(deviceId) { deviceId?.let { c.devices.get(it) } }
    val existingSecrets = remember(deviceId) { deviceId?.let { c.secretsFor(it) } ?: DeviceSecrets() }
    val allDevices by c.devices.devices.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val id = rememberSaveable { existing?.id ?: UUID.randomUUID().toString() }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var host by rememberSaveable { mutableStateOf(existing?.host ?: "") }
    var port by rememberSaveable { mutableStateOf((existing?.port ?: 22).toString()) }
    var user by rememberSaveable { mutableStateOf(existing?.user ?: "pi") }
    var authType by rememberSaveable { mutableStateOf(existing?.authType ?: AuthType.PASSWORD) }
    var password by rememberSaveable { mutableStateOf(existingSecrets.password ?: "") }
    var privateKey by rememberSaveable { mutableStateOf(existingSecrets.privateKey ?: "") }
    var passphrase by rememberSaveable { mutableStateOf(existingSecrets.passphrase ?: "") }
    var sudoPassword by rememberSaveable { mutableStateOf(existingSecrets.sudoPassword ?: "") }
    var location by rememberSaveable { mutableStateOf(existing?.location ?: "") }
    var locationNote by rememberSaveable { mutableStateOf(existing?.locationNote ?: "") }
    var description by rememberSaveable { mutableStateOf(existing?.description ?: "") }
    var photo by rememberSaveable { mutableStateOf(existing?.photo) }

    var probing by remember { mutableStateOf(false) }
    var probe by remember { mutableStateOf<QueryResult?>(null) }
    var probeError by remember { mutableStateOf<String?>(null) }
    var keyError by remember { mutableStateOf<String?>(null) }
    var showErrors by remember { mutableStateOf(false) }
    var captureUri by rememberSaveable { mutableStateOf<String?>(null) }

    val portInt = port.toIntOrNull()
    val hostChanged = existing != null && (existing.host != host.trim() || existing.port != portInt)
    val credentialsOk = when (authType) {
        AuthType.PASSWORD -> password.isNotEmpty()
        AuthType.KEY -> privateKey.contains("PRIVATE KEY")
    }
    val valid = host.isNotBlank() && user.isNotBlank() && portInt != null && portInt in 1..65535 && credentialsOk

    fun buildDevice(): Device {
        val keepKey = existing != null && !hostChanged
        val p = probe
        return Device(
            id = id,
            name = name.trim().ifEmpty { p?.status?.hostname ?: host.trim() },
            host = host.trim(),
            port = portInt ?: 22,
            user = user.trim(),
            authType = authType,
            location = Locations.normalize(location),
            locationNote = locationNote.trim(),
            description = description.trim(),
            photo = photo,
            // Neuer Host/Port → neuer Rechner → Host-Key neu lernen.
            hostKeyType = p?.hostKey?.type ?: existing?.hostKeyType?.takeIf { keepKey },
            hostKeyFingerprint = p?.hostKey?.fingerprint ?: existing?.hostKeyFingerprint?.takeIf { keepKey },
            model = p?.status?.model ?: existing?.model,
            serial = p?.status?.serial ?: existing?.serial,
            mac = p?.status?.primaryMac ?: existing?.mac,
            createdAt = existing?.createdAt ?: 0,
        )
    }

    fun buildSecrets() = DeviceSecrets(
        password = password.takeIf { authType == AuthType.PASSWORD && it.isNotEmpty() },
        privateKey = privateKey.takeIf { authType == AuthType.KEY && it.isNotEmpty() },
        passphrase = passphrase.takeIf { authType == AuthType.KEY && it.isNotEmpty() },
        sudoPassword = sudoPassword.takeIf { it.isNotEmpty() },
    )

    val keyPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
                }
                if (text != null && text.contains("PRIVATE KEY")) {
                    privateKey = text.trim() + "\n"; keyError = null
                } else {
                    keyError = "Das ist kein privater SSH-Schlüssel (erwartet: -----BEGIN … PRIVATE KEY-----)."
                }
            }
        }
    }
    fun importPhoto(uri: Uri) {
        scope.launch {
            val newName = withContext(Dispatchers.IO) { c.photos.import(uri) }
            if (newName != null) {
                // Ein in dieser Sitzung neu aufgenommenes, noch nicht gespeichertes Foto verwerfen.
                if (photo != existing?.photo) c.photos.delete(photo)
                photo = newName
            }
        }
    }
    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) importPhoto(uri)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = captureUri
        if (ok && u != null) importPhoto(Uri.parse(u))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Raspi hinzufügen" else "Bearbeiten") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück") }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (!valid) { showErrors = true; return@TextButton }
                            val d = buildDevice()
                            if (existing?.photo != null && existing.photo != photo) c.photos.delete(existing.photo)
                            c.save(d, buildSecrets(), probe)
                            if (probe == null) c.refresh(d.id)
                            onDone(d.id)
                        },
                    ) { Text("Speichern") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SectionCard("Verbindung") {
                    OutlinedTextField(
                        value = host, onValueChange = { host = it; probe = null },
                        label = { Text("Host oder IP") },
                        placeholder = { Text("raspberrypi.local") },
                        singleLine = true,
                        isError = showErrors && host.isBlank(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = user, onValueChange = { user = it; probe = null },
                            label = { Text("Benutzer") },
                            singleLine = true,
                            isError = showErrors && user.isBlank(),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = port, onValueChange = { port = it.filter(Char::isDigit).take(5); probe = null },
                            label = { Text("Port") },
                            singleLine = true,
                            isError = showErrors && (portInt == null || portInt !in 1..65535),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(96.dp),
                        )
                    }
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = authType == AuthType.PASSWORD,
                            onClick = { authType = AuthType.PASSWORD; probe = null },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                        ) { Text("Passwort") }
                        SegmentedButton(
                            selected = authType == AuthType.KEY,
                            onClick = { authType = AuthType.KEY; probe = null },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                        ) { Text("SSH-Schlüssel") }
                    }
                    if (authType == AuthType.PASSWORD) {
                        SecretField(password, { password = it; probe = null }, "Passwort")
                        if (showErrors && password.isEmpty()) {
                            Text("Passwort fehlt.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { keyPicker.launch(arrayOf("*/*")) }) {
                                Icon(Icons.Outlined.FileOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Datei …")
                            }
                            OutlinedButton(onClick = {
                                val t = clipboard.getText()?.text.orEmpty()
                                if (t.contains("PRIVATE KEY")) { privateKey = t.trim() + "\n"; keyError = null }
                                else keyError = "In der Zwischenablage ist kein privater Schlüssel."
                            }) {
                                Icon(Icons.Outlined.ContentPaste, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Einfügen")
                            }
                        }
                        val keyLine = privateKey.lineSequence().firstOrNull { it.startsWith("-----BEGIN") }
                        Text(
                            keyError ?: keyLine?.let { "Geladen: ${it.trim('-').removePrefix("BEGIN ")}" } ?: "Noch kein Schlüssel geladen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                keyError != null || (showErrors && !credentialsOk) -> MaterialTheme.colorScheme.error
                                keyLine != null -> Mchwrk.colors.good
                                else -> Mchwrk.colors.muted
                            },
                        )
                        SecretField(passphrase, { passphrase = it; probe = null }, "Passphrase (falls vorhanden)")
                    }
                    SecretField(
                        sudoPassword, { sudoPassword = it }, "sudo-Passwort (optional)",
                        supporting = "Für Neustart/Herunterfahren. Leer = Login-Passwort bzw. sudo ohne Passwort.",
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            enabled = valid && !probing,
                            onClick = {
                                probing = true; probeError = null; probe = null
                                scope.launch {
                                    try {
                                        val base = buildDevice().let {
                                            if (existing == null || hostChanged) it.copy(hostKeyFingerprint = null, hostKeyType = null) else it
                                        }
                                        val r = c.probe(base, buildSecrets())
                                        probe = r
                                        if (name.isBlank()) r.status.hostname?.let { name = it }
                                    } catch (e: Exception) {
                                        probeError = e.message ?: e.javaClass.simpleName
                                    } finally {
                                        probing = false
                                    }
                                }
                            },
                        ) { Text("Verbindung testen") }
                        if (probing) {
                            Spacer(Modifier.width(12.dp))
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                    probe?.let { r ->
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Outlined.CheckCircle, null, tint = Mchwrk.colors.good, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    listOfNotNull(r.status.model ?: "Linux-Rechner", r.status.hostname).joinToString(" · "),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                r.status.os?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Mchwrk.colors.muted) }
                                Text(
                                    "Host-Schlüssel (${r.hostKey.type}) wird gemerkt:",
                                    style = MaterialTheme.typography.bodySmall, color = Mchwrk.colors.muted,
                                )
                                Text(r.hostKey.fingerprint, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                    probeError?.let {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                SectionCard("Standort") {
                    OutlinedTextField(
                        value = location, onValueChange = { location = it },
                        label = { Text("Wo steht er?") },
                        placeholder = { Text("Keller › Rack › oben") },
                        leadingIcon = { Icon(Icons.Outlined.Place, null) },
                        singleLine = true,
                        supportingText = { Text("Ebenen mit / oder › trennen – gruppiert wird nach der ersten.") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val suggestions = Locations.suggestions(allDevices.filter { it.id != id }, location)
                    if (suggestions.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            suggestions.forEach { s -> SuggestionChip(onClick = { location = s }, label = { Text(s) }) }
                        }
                    }
                    OutlinedTextField(
                        value = locationNote, onValueChange = { locationNote = it },
                        label = { Text("So findest du ihn") },
                        placeholder = { Text("hinter dem Router, schwarzes Gehäuse") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    photo?.let { p ->
                        Box {
                            AsyncImage(
                                model = c.photos.file(p),
                                contentDescription = "Standortfoto",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(MaterialTheme.shapes.small),
                            )
                            IconButton(
                                onClick = { if (photo != existing?.photo) c.photos.delete(photo); photo = null },
                                modifier = Modifier.align(Alignment.TopEnd),
                            ) { Icon(Icons.Outlined.Delete, "Foto entfernen", tint = androidx.compose.ui.graphics.Color.White) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val f = c.photos.newCaptureFile()
                            val uri = FileProvider.getUriForFile(context, context.packageName + ".files", f)
                            captureUri = uri.toString()
                            runCatching { camera.launch(uri) }
                        }) {
                            Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Foto")
                        }
                        OutlinedButton(onClick = {
                            galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) {
                            Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Galerie")
                        }
                    }
                }

                SectionCard("Name & Notizen") {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("Name") },
                        placeholder = { Text("wird sonst aus dem Hostnamen übernommen") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = description, onValueChange = { description = it },
                        label = { Text("Wofür? (optional)") },
                        placeholder = { Text("Pi-hole, Home Assistant, Druckserver …") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.size(32.dp))
            }
        }
    }
}
