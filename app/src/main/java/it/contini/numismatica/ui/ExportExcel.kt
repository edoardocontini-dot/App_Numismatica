package it.contini.numismatica.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import it.contini.numismatica.AppViewModel

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

/** Progress dialog while the catalogue is built, then a choice: share it or save it. */
@Composable
fun ExportExcelDialogs(vm: AppViewModel) {
    val progress by vm.exportProgress.collectAsState()
    val file by vm.exportedFile.collectAsState()
    val context = LocalContext.current

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX_MIME)) { uri ->
        val f = vm.exportedFile.value
        if (uri != null && f != null) vm.saveExportTo(uri, f)
        vm.exportedFile.value = null
    }

    progress?.let { (done, total) ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Preparo il catalogo Excel…") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LinearProgressIndicator(
                        progress = { if (total == 0) 0f else done.toFloat() / total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("$done di $total pezzi. Le schede già aperte non usano richieste Numista.")
                }
            },
            confirmButton = {},
        )
    }

    val ready = file
    if (ready != null && progress == null) {
        AlertDialog(
            onDismissRequest = { vm.exportedFile.value = null },
            title = { Text("Catalogo Excel pronto") },
            text = { Text("${ready.name}\n\nPuoi inviarlo (email, WhatsApp, Drive…) o salvarlo sul telefono.") },
            confirmButton = {
                TextButton(onClick = {
                    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", ready)
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = XLSX_MIME
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "Catalogo collezione numismatica")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try {
                        context.startActivity(Intent.createChooser(send, "Invia catalogo"))
                    } catch (e: ActivityNotFoundException) {
                        vm.message.value = "Nessuna app disponibile per inviare il file"
                    }
                    vm.exportedFile.value = null
                }) { Text("Invia / Condividi") }
            },
            dismissButton = {
                TextButton(onClick = { saveLauncher.launch(ready.name) }) { Text("Salva sul telefono") }
            },
        )
    }
}
