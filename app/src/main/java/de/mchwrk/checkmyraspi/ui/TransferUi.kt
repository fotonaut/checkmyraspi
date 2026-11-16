package de.mchwrk.checkmyraspi.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import de.mchwrk.checkmyraspi.AppController
import de.mchwrk.checkmyraspi.data.ImportPreview
import de.mchwrk.checkmyraspi.data.Transfer
import de.mchwrk.checkmyraspi.design.Mchwrk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Export/Import der Geräteliste: Dialoge und Dateiauswahl. */
class TransferState(val startExport: () -> Unit, val startImport: () -> Unit)

@Composable
fun rememberTransfer(c: AppController, snackbar: SnackbarHostState): Pair<TransferState, @Composable () -> Unit> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var exportDialog by remember { mutableStateOf(false) }
    var withSecrets by remember { mutableStateOf(false) }
    var pass1 by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var pendingExport by remember { mutableStateOf<String?>(null) }

    var preview by remember { mutableStateOf<ImportPreview?>(null) }
    var importPass by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }

    fun toast(msg: String) { scope.launch { snackbar.showSnackbar(msg) } }

    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        val data = pendingExport
        pendingExport = null
        if (uri == null || data == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(data.toByteArray()) } != null }.getOrDefault(false)
            }
            toast(if (ok) "Exportiert." else "Datei konnte nicht geschrieben werden.")
        }
    }

    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            }
            try {
                preview = c.previewImport(text ?: throw IllegalStateException())
                importPass = ""; importError = null
            } catch (e: Exception) {
                toast(e.message ?: "Datei konnte nicht gelesen werden.")
            }
        }
    }

    val state = remember {
        TransferState(
            startExport = { withSecrets = false; pass1 = ""; pass2 = ""; exportDialog = true },
            startImport = { openDoc.launch(arrayOf("*/*")) },
        )
    }

    val dialogs: @Composable () -> Unit = {
        if (exportDialog) {
            val passOk = !withSecrets || (pass1.length >= Transfer.MIN_PASSPHRASE && pass1 == pass2)
            AlertDialog(
                onDismissRequest = { exportDialog = false },
                title = { Text("Geräte exportieren") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Alle Geräte mit Standort, Notizen und Fotos als Datei – zum Umzug auf ein anderes Gerät oder als Sicherung.")
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { withSecrets = !withSecrets }) {
                            Checkbox(checked = withSecrets, onCheckedChange = { withSecrets = it })
                            Text("Zugangsdaten mitnehmen")
                        }
                        if (withSecrets) {
                            Text(
                                "Passwörter und Schlüssel werden mit einer Passphrase verschlüsselt, die du beim Import wieder brauchst.",
                                style = MaterialTheme.typography.bodySmall, color = Mchwrk.colors.muted,
                            )
                            SecretField(
                                pass1, { pass1 = it }, "Passphrase",
                                supporting = "mindestens ${Transfer.MIN_PASSPHRASE} Zeichen",
                                imeAction = ImeAction.Next,
                            )
                            SecretField(pass2, { pass2 = it }, "Passphrase wiederholen", imeAction = ImeAction.Done)
                            if (pass2.isNotEmpty() && pass1 != pass2) {
                                Text("Stimmt nicht überein.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            Text(
                                "Ohne Zugangsdaten musst du Passwörter bzw. Schlüssel nach dem Import neu eingeben.",
                                style = MaterialTheme.typography.bodySmall, color = Mchwrk.colors.muted,
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(enabled = passOk, onClick = {
                        exportDialog = false
                        val pw = pass1.takeIf { withSecrets }
                        scope.launch {
                            pendingExport = withContext(Dispatchers.Default) { c.exportJson(pw) }
                            createDoc.launch("checkmyraspi-${LocalDate.now()}.json")
                        }
                    }) { Text("Exportieren …") }
                },
                dismissButton = { TextButton(onClick = { exportDialog = false }) { Text("Abbrechen") } },
            )
        }

        preview?.let { p ->
            fun finish(pass: String?) {
                scope.launch {
                    try {
                        val r = withContext(Dispatchers.Default) { c.applyImport(p, pass) }
                        preview = null
                        val parts = listOfNotNull(
                            r.added.takeIf { it > 0 }?.let { "$it neu" },
                            r.updated.takeIf { it > 0 }?.let { "$it aktualisiert" },
                        ).joinToString(", ")
                        toast("Importiert: $parts" + if (r.withSecrets) "." else " – Zugangsdaten bitte nachtragen.")
                    } catch (e: Exception) {
                        importError = e.message ?: "Import fehlgeschlagen."
                    }
                }
            }
            AlertDialog(
                onDismissRequest = { preview = null },
                title = { Text("Geräte importieren") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${p.deviceCount} ${if (p.deviceCount == 1) "Gerät" else "Geräte"} gefunden: " +
                                p.file.devices.joinToString(", ") { it.name } + ". Gleiche Geräte werden aktualisiert.",
                        )
                        if (p.hasSecrets) {
                            Text("Die Datei enthält verschlüsselte Zugangsdaten.", style = MaterialTheme.typography.bodySmall, color = Mchwrk.colors.muted)
                            SecretField(importPass, { importPass = it; importError = null }, "Passphrase", imeAction = ImeAction.Done)
                            TextButton(onClick = { finish(null) }) { Text("Ohne Zugangsdaten importieren") }
                        }
                        importError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                },
                confirmButton = {
                    Button(
                        enabled = !p.hasSecrets || importPass.isNotEmpty(),
                        onClick = { finish(importPass.takeIf { p.hasSecrets }) },
                    ) { Text("Importieren") }
                },
                dismissButton = { TextButton(onClick = { preview = null }) { Text("Abbrechen") } },
            )
        }
    }
    return state to dialogs
}
