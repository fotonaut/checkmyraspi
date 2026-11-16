package de.mchwrk.checkmyraspi.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.mchwrk.checkmyraspi.AppController
import de.mchwrk.checkmyraspi.CommandRun
import de.mchwrk.checkmyraspi.data.BuiltinCommands
import de.mchwrk.checkmyraspi.data.CommandGroup
import de.mchwrk.checkmyraspi.data.Device
import de.mchwrk.checkmyraspi.data.QuickCommand
import de.mchwrk.checkmyraspi.design.Mchwrk
import kotlinx.coroutines.launch
import java.util.UUID

/** Öffnet das Gerät in einer installierten SSH-App (ConnectBot, JuiceSSH …). Passwörter werden nicht übergeben. */
fun openInSshApp(context: Context, d: Device): Boolean {
    val host = if (d.host.contains(':') && !d.host.startsWith("[")) "[${d.host}]" else d.host
    val uri = Uri.parse("ssh://${Uri.encode(d.user)}@$host:${d.port}/#${Uri.encode(d.name)}")
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

const val NO_SSH_APP = "Keine SSH-App gefunden – z. B. ConnectBot installieren."

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandsScreen(c: AppController, id: String, onBack: () -> Unit) {
    val devices by c.devices.devices.collectAsState()
    val runs by c.runs.collectAsState()
    val custom by c.commands.commands.collectAsState()
    val device = devices.firstOrNull { it.id == id }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listScroll = rememberScrollState()

    var ask by remember { mutableStateOf<QuickCommand?>(null) }
    var editing by remember { mutableStateOf<QuickCommand?>(null) }
    var free by rememberSaveable { mutableStateOf("") }
    var freeSudo by rememberSaveable { mutableStateOf(false) }

    if (device == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val run = runs[id]
    val hasCreds = c.hasCredentials(device)

    fun toast(m: String) { scope.launch { snackbar.showSnackbar(m) } }
    fun start(cmd: QuickCommand, value: String? = null) {
        if (!c.runCommand(id, cmd, value)) toast("Es läuft noch ein Befehl.")
        else scope.launch { listScroll.animateScrollTo(0) }
    }
    fun pick(cmd: QuickCommand) {
        if (cmd.needsValue || cmd.confirm) ask = cmd else start(cmd)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Befehle", maxLines = 1)
                        Text(device.name, style = MaterialTheme.typography.bodySmall, color = Mchwrk.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück") } },
                actions = {
                    IconButton(onClick = { if (!openInSshApp(context, device)) toast(NO_SSH_APP) }) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, "In SSH-App öffnen")
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
                Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(listScroll).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (!hasCreds) {
                    Text("Zugangsdaten fehlen – bitte zuerst das Gerät bearbeiten.", color = MaterialTheme.colorScheme.error)
                }
                run?.let { OutputCard(it, onCancel = { c.cancelCommand(id) }, onClose = { c.clearRun(id) }, onCopy = {
                    val cm = context.getSystemService(ClipboardManager::class.java)
                    cm.setPrimaryClip(ClipData.newPlainText("Ausgabe", it.output))
                    toast("Ausgabe kopiert.")
                }) }

                SectionCard("Eigener Befehl") {
                    OutlinedTextField(
                        value = free, onValueChange = { free = it },
                        placeholder = { Text("z. B. uptime") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        keyboardOptions = KeyboardOptions(autoCorrect = false, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            if (free.isNotBlank() && hasCreds) start(QuickCommand("free", free.trim(), free.trim(), sudo = freeSudo))
                        }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.clickable { freeSudo = !freeSudo }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = freeSudo, onCheckedChange = { freeSudo = it })
                            Text("mit sudo")
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            enabled = free.isNotBlank(),
                            onClick = { editing = QuickCommand(UUID.randomUUID().toString(), "", free.trim(), sudo = freeSudo) },
                        ) { Text("Merken") }
                        Button(
                            enabled = free.isNotBlank() && hasCreds && run?.running != true,
                            onClick = { start(QuickCommand("free", free.trim(), free.trim(), sudo = freeSudo)) },
                        ) { Text("Ausführen") }
                    }
                }

                CommandList(CommandGroup.DIAGNOSE.title, BuiltinCommands.diagnose, hasCreds, ::pick)
                CommandList(CommandGroup.WARTUNG.title, BuiltinCommands.wartung, hasCreds, ::pick)
                CommandList(
                    "Gemerkte Befehle", custom, hasCreds, ::pick,
                    onEdit = { editing = it },
                    empty = "Noch keine – oben einen Befehl eingeben und „Merken“ tippen.",
                    trailing = {
                        TextButton(onClick = { editing = QuickCommand(UUID.randomUUID().toString(), "", "") }) {
                            Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Neuer Befehl")
                        }
                    },
                )
                Text(
                    "Befehle laufen ohne Terminal: interaktive Programme (nano, htop, apt upgrade mit Rückfragen) gehen hier nicht – " +
                        "dafür oben rechts in einer SSH-App öffnen.",
                    style = MaterialTheme.typography.bodySmall, color = Mchwrk.colors.muted,
                )
            }
        }
    }

    ask?.let { cmd ->
        var value by remember(cmd) { mutableStateOf("") }
        val focus = remember { FocusRequester() }
        if (cmd.needsValue) LaunchedEffect(cmd) { runCatching { focus.requestFocus() } }
        val ok = !cmd.needsValue || value.isNotBlank()
        AlertDialog(
            onDismissRequest = { ask = null },
            title = { Text(cmd.label.removeSuffix(" …") + if (cmd.needsValue) "" else "?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(cmd.command, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = Mchwrk.colors.muted)
                    if (cmd.needsValue) {
                        OutlinedTextField(
                            value = value, onValueChange = { value = it },
                            label = { Text(cmd.param ?: "Wert") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(autoCorrect = false, imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { if (ok) { ask = null; start(cmd, value) } }),
                            modifier = Modifier.focusRequester(focus),
                        )
                    }
                    if (cmd.confirm) Text("Das ändert etwas auf „${device.name}“.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    enabled = ok,
                    onClick = { ask = null; start(cmd, value) },
                    colors = if (cmd.confirm) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
                ) { Text("Ausführen") }
            },
            dismissButton = { TextButton(onClick = { ask = null }) { Text("Abbrechen") } },
        )
    }

    editing?.let { cmd ->
        CommandEditDialog(
            cmd, isNew = custom.none { it.id == cmd.id },
            onSave = { c.commands.upsert(it); editing = null; toast("Gemerkt.") },
            onDelete = { c.commands.delete(cmd.id); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun CommandList(
    title: String,
    cmds: List<QuickCommand>,
    enabled: Boolean,
    onPick: (QuickCommand) -> Unit,
    onEdit: ((QuickCommand) -> Unit)? = null,
    empty: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    SectionCard(title) {
        if (cmds.isEmpty() && empty != null) Text(empty, color = Mchwrk.colors.muted, style = MaterialTheme.typography.bodySmall)
        cmds.forEachIndexed { i, cmd ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(
                Modifier.fillMaxWidth().clickable(enabled = enabled) { onPick(cmd) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.PlayArrow, null, tint = if (enabled) MaterialTheme.colorScheme.primary else Mchwrk.colors.muted)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(cmd.label, style = MaterialTheme.typography.bodyLarge)
                        if (cmd.sudo) { Spacer(Modifier.width(6.dp)); Badge("sudo", Mchwrk.colors.warn) }
                    }
                    Text(
                        cmd.command, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                        color = Mchwrk.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (onEdit != null) IconButton(onClick = { onEdit(cmd) }) { Icon(Icons.Outlined.Edit, "Bearbeiten") }
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun OutputCard(run: CommandRun, onCancel: () -> Unit, onClose: () -> Unit, onCopy: () -> Unit) {
    val scroll = rememberScrollState()
    LaunchedEffect(run.output.length) { if (run.running) scroll.scrollTo(scroll.maxValue) }
    val (stateText, stateColor) = when {
        run.running -> "läuft …" to MaterialTheme.colorScheme.primary
        run.error != null -> run.error to MaterialTheme.colorScheme.error
        run.exitCode == 0 -> "fertig" to Mchwrk.colors.good
        run.exitCode == null -> "beendet" to Mchwrk.colors.muted
        else -> "Exit-Code ${run.exitCode}" to MaterialTheme.colorScheme.error
    }
    SectionCard(null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(run.label, style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (run.running) {
                        CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    val secs = ((if (run.running) System.currentTimeMillis() else run.finishedAt) - run.startedAt) / 1000
                    Text("$stateText${if (!run.running) " · ${secs} s" else ""}", style = MaterialTheme.typography.bodySmall, color = stateColor)
                }
            }
            if (run.running) {
                TextButton(onClick = onCancel) { Icon(Icons.Outlined.Stop, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Abbrechen") }
            } else {
                IconButton(onClick = onCopy, enabled = run.output.isNotEmpty()) { Icon(Icons.Outlined.ContentCopy, "Kopieren") }
                TextButton(onClick = onClose) { Text("Schließen") }
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(Modifier.heightIn(min = 48.dp, max = 420.dp).verticalScroll(scroll).horizontalScroll(rememberScrollState()).padding(10.dp)) {
                val text = run.output.ifEmpty { if (run.running) "" else "(keine Ausgabe)" } +
                    if (run.truncated) "\n… gekürzt" else ""
                Text(text, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp, softWrap = false)
            }
        }
        if (!run.running && run.sudo && run.output.contains("a password is required")) {
            Text(
                "sudo braucht ein Passwort – beim Gerät unter „Bearbeiten“ ein sudo-Passwort eintragen.",
                style = MaterialTheme.typography.bodySmall, color = Mchwrk.colors.warn,
            )
        }
    }
}

@Composable
private fun CommandEditDialog(
    cmd: QuickCommand,
    isNew: Boolean,
    onSave: (QuickCommand) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember(cmd) { mutableStateOf(cmd.label) }
    var command by remember(cmd) { mutableStateOf(cmd.command) }
    var sudo by remember(cmd) { mutableStateOf(cmd.sudo) }
    var confirm by remember(cmd) { mutableStateOf(cmd.confirm) }
    val ok = command.isNotBlank()
    val focus = remember { FocusRequester() }
    LaunchedEffect(cmd.id) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Befehl merken" else "Befehl bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    label, { label = it }, label = { Text("Name") }, placeholder = { Text("leer = Befehl") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                OutlinedTextField(
                    command, { command = it }, label = { Text("Befehl") },
                    supportingText = { Text("{} wird beim Ausführen abgefragt") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(autoCorrect = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.clickable { sudo = !sudo }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(sudo, { sudo = it }); Text("mit sudo")
                }
                Row(Modifier.clickable { confirm = !confirm }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(confirm, { confirm = it }); Text("vorher nachfragen")
                }
                if (!isNew) TextButton(onClick = onDelete) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = ok, onClick = {
                onSave(cmd.copy(label = label.trim().ifEmpty { command.trim() }, command = command.trim(), sudo = sudo, confirm = confirm))
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
