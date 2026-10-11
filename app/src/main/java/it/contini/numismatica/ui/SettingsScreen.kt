package it.contini.numismatica.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
fun SettingsScreen(vm: AppViewModel, onGoSearch: () -> Unit) {
    val requests by vm.requests.collectAsState()
    val owned by vm.owned.collectAsState()
    var confirmImport by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refreshKeyState() }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u ->
        if (u != null) vm.exportTo(u)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) vm.importFrom(u)
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Impostazioni", style = MaterialTheme.typography.headlineSmall)

        ApiKeyCard(vm, onGoSearch)

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
                Text("Catalogo Excel", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Crea un file Excel con tutta la collezione: un foglio con ogni pezzo (dati di catalogo, anno, " +
                        "tiratura, conservazione, provenienza, note, link a Numista) e un foglio di statistiche.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = { vm.exportExcel() }, enabled = owned.isNotEmpty()) { Text("Esporta in Excel") }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Backup della collezione", style = MaterialTheme.typography.titleMedium)
                Text(
                    "La collezione (${owned.size} tipi) è salvata solo su questo telefono. " +
                        "Fai ogni tanto un backup, per esempio su Google Drive. " +
                        "Il backup serve per ripristinare l'app; per consultare la collezione usa il catalogo Excel.",
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

@Composable
private fun ApiKeyCard(vm: AppViewModel, onGoSearch: () -> Unit) {
    val savedKey by vm.apiKey.collectAsState()
    val state by vm.keyState.collectAsState()
    var key by remember { mutableStateOf(savedKey) }
    var visible by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    val checking = state is AppViewModel.KeyState.Checking
    val green = if (isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF2E7D32)
    val orange = if (isSystemInDarkTheme()) Color(0xFFFFB74D) else Color(0xFFB26A00)

    fun save() {
        focus.clearFocus()
        vm.verifyAndSaveKey(key)
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Chiave API Numista", style = MaterialTheme.typography.titleMedium)
            Text(
                "Il catalogo arriva da Numista e serve una chiave gratuita. Apri la pagina API di Numista, " +
                    "accedi, tocca \"Activate API access\" (piano gratuito) e copia la chiave che compare.",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = { uri.openUri("https://it.numista.com/api/") }) { Text("Apri la pagina API di Numista") }

            OutlinedTextField(
                value = key,
                onValueChange = {
                    key = it
                    vm.keyEdited()
                },
                label = { Text("Chiave API") },
                singleLine = true,
                enabled = !checking,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { save() }),
                trailingIcon = {
                    Row {
                        IconButton(onClick = {
                            clipboard.getText()?.text?.let {
                                key = it.trim()
                                vm.keyEdited()
                            }
                        }) { Icon(Icons.Filled.ContentPaste, "Incolla") }
                        IconButton(onClick = { visible = !visible }) {
                            Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (visible) "Nascondi" else "Mostra")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(onClick = { save() }, enabled = !checking && key.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(if (checking) "Verifica in corso…" else "Salva chiave")
            }

            when (val st = state) {
                is AppViewModel.KeyState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Verifico la chiave con Numista…")
                }
                is AppViewModel.KeyState.Valid -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, null, tint = green, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Chiave riconosciuta", color = green, style = MaterialTheme.typography.titleMedium)
                    }
                    st.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    OutlinedButton(onClick = onGoSearch, modifier = Modifier.fillMaxWidth()) { Text("Inizia a cercare") }
                }
                is AppViewModel.KeyState.Invalid -> Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(st.message, color = MaterialTheme.colorScheme.error)
                }
                is AppViewModel.KeyState.Unverified -> Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Warning, null, tint = orange, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(st.message)
                }
                AppViewModel.KeyState.Empty -> Unit
            }

            if (savedKey.isNotBlank() && !checking) {
                TextButton(onClick = { key = ""; vm.removeKey() }) {
                    Text("Rimuovi chiave", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
