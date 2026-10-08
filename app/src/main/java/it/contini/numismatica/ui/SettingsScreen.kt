package it.contini.numismatica.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import it.contini.numismatica.AppViewModel
import java.time.LocalDate

private const val FREE_QUOTA = 2000

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val savedKey by vm.apiKey.collectAsState()
    val requests by vm.requests.collectAsState()
    val owned by vm.owned.collectAsState()
    var key by remember(savedKey) { mutableStateOf(savedKey) }
    var visible by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u ->
        if (u != null) vm.exportTo(u)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) vm.importFrom(u)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Impostazioni", style = MaterialTheme.typography.headlineSmall)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Chiave API Numista", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Il catalogo arriva da Numista. Serve una chiave gratuita: accedi a Numista, " +
                        "vai su Il mio account → API, crea un'applicazione e copia qui la chiave.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { uri.openUri("https://it.numista.com/api/") }) { Text("Apri la pagina API di Numista") }
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it.trim() },
                    label = { Text("Chiave API") },
                    singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Mostra")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = { vm.setApiKey(key) }, enabled = key != savedKey, modifier = Modifier.fillMaxWidth()) {
                    Text("Salva chiave")
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Richieste di questo mese", style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(
                    progress = { (requests.toFloat() / FREE_QUOTA).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                val month = LocalDate.now().month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ITALIAN)
                Text(
                    "$requests su $FREE_QUOTA gratuite ($month). Le ricerche e le schede già aperte sono salvate sul telefono " +
                        "e non consumano altre richieste. Il conteggio riparte il primo del mese.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Backup della collezione", style = MaterialTheme.typography.titleMedium)
                Text(
                    "La collezione (${owned.size} tipi) è salvata solo su questo telefono. " +
                        "Fai ogni tanto un backup, per esempio su Google Drive.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { exportLauncher.launch("collezione-numismatica-${LocalDate.now()}.json") }) {
                        Text("Esporta")
                    }
                    OutlinedButton(onClick = { confirmImport = true }) { Text("Ripristina") }
                }
            }
        }

        NumistaCredit()
    }

    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text("Ripristinare un backup?") },
            text = { Text("La collezione attuale verrà sostituita da quella del file scelto.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmImport = false
                    importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }) { Text("Scegli file") }
            },
            dismissButton = { TextButton(onClick = { confirmImport = false }) { Text("Annulla") } },
        )
    }
}
