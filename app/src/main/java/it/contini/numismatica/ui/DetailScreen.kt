package it.contini.numismatica.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import it.contini.numismatica.AppViewModel
import it.contini.numismatica.data.Categories
import it.contini.numismatica.data.GRADES
import it.contini.numismatica.data.OwnedItem
import it.contini.numismatica.data.TypeDetail
import it.contini.numismatica.data.yearsLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(vm: AppViewModel, id: Long, onBack: () -> Unit) {
    val owned by vm.owned.collectAsState()
    val ownedItem = owned.firstOrNull { it.typeId == id }
    var detail by remember { mutableStateOf<TypeDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var confirmRemove by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        loading = true
        vm.loadType(id)
            .onSuccess { detail = it }
            .onFailure { error = it.message }
        loading = false
    }

    val title = detail?.summary?.title ?: ownedItem?.title ?: ""

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title, maxLines = 1) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") }
            },
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            if (loading) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            error?.let { InfoCard(it, isError = true) }

            detail?.let { d -> DetailBody(d) }

            Spacer(Modifier.padding(8.dp))
            HorizontalDivider()
            Spacer(Modifier.padding(4.dp))

            // ---- My collection ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ce l'ho", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Switch(
                    checked = ownedItem != null,
                    enabled = ownedItem != null || detail != null,
                    onCheckedChange = { checked ->
                        if (checked) {
                            detail?.let { vm.addFromDetail(it) }
                        } else {
                            confirmRemove = true
                        }
                    },
                )
            }
            if (ownedItem != null) {
                OwnedEditor(ownedItem, onSave = { vm.save(it); vm.message.value = "Salvato" })
            } else {
                Text(
                    "Attiva per aggiungerlo alla collezione e annotare quantità, conservazione e note.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.padding(16.dp))
            NumistaCredit()
            Spacer(Modifier.padding(24.dp))
        }
    }

    if (confirmRemove) {
        ConfirmRemoveDialog(title, onConfirm = { vm.remove(id) }, onDismiss = { confirmRemove = false })
    }
}

@Composable
private fun DetailBody(d: TypeDetail) {
    val uri = LocalUriHandler.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        listOf("Dritto" to (d.obverseImage ?: d.summary.obverseThumb), "Rovescio" to (d.reverseImage ?: d.summary.reverseThumb))
            .forEach { (label, url) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (url != null) {
                        AsyncImage(
                            model = url,
                            contentDescription = label,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        )
                    } else {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                            Text("Nessuna foto", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(label, style = MaterialTheme.typography.labelMedium)
                }
            }
    }
    Spacer(Modifier.padding(6.dp))
    Text(d.summary.title, style = MaterialTheme.typography.titleLarge)
    Text(
        listOf(d.summary.issuerName, yearsLabel(d.summary.minYear, d.summary.maxYear), Categories.label(d.summary.category))
            .filter { it.isNotBlank() }.joinToString(" · "),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.padding(6.dp))

    val status = when (d.demonetized) {
        true -> "Fuori corso" + (d.demonetizationDate?.let { " dal $it" } ?: "")
        false -> "In corso legale"
        null -> null
    }
    val facts = listOf(
        "Valore" to d.valueText,
        "Valuta" to d.currency,
        "Stato" to status,
        "Materiale" to d.composition,
        "Peso" to d.weight?.let { "$it g" },
        "Diametro / misure" to d.size?.let { "$it mm" },
        "Spessore" to d.thickness?.let { "$it mm" },
        "Forma" to d.shape,
        "Tecnica" to d.technique,
        "Autorità" to d.rulers.joinToString(", ").ifBlank { null },
        "Zecca" to d.mints.joinToString(", ").ifBlank { null },
        "Bordo" to d.edge,
        "Dritto" to listOfNotNull(d.obverseDescription, d.obverseLettering?.let { "Legenda: $it" }).joinToString("\n").ifBlank { null },
        "Rovescio" to listOfNotNull(d.reverseDescription, d.reverseLettering?.let { "Legenda: $it" }).joinToString("\n").ifBlank { null },
        "Note" to d.comments,
        "Numista N°" to d.summary.id.toString(),
    ).filter { it.second != null }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            facts.forEachIndexed { i, (k, v) ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Row {
                    Text(k, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(120.dp))
                    Text(v!!, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
        }
    }
    d.url?.let { link ->
        OutlinedButton(onClick = { uri.openUri(link) }, modifier = Modifier.padding(top = 8.dp)) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
            Spacer(Modifier.width(8.dp))
            Text("Apri su Numista")
        }
    }
}

@Composable
private fun OwnedEditor(item: OwnedItem, onSave: (OwnedItem) -> Unit) {
    var quantity by remember(item.typeId) { mutableStateOf(item.quantity) }
    var grade by remember(item.typeId) { mutableStateOf(item.grade) }
    var year by remember(item.typeId) { mutableStateOf(item.issueYear) }
    var notes by remember(item.typeId) { mutableStateOf(item.notes) }
    var price by remember(item.typeId) {
        mutableStateOf(item.purchasePrice?.let { String.format(java.util.Locale.ITALIAN, "%.2f", it) } ?: "")
    }
    var purchaseDate by remember(item.typeId) { mutableStateOf(item.purchaseDate) }
    var provenance by remember(item.typeId) { mutableStateOf(item.provenance) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Quantità", modifier = Modifier.weight(1f))
            IconButton(onClick = { if (quantity > 1) quantity-- }) { Icon(Icons.Filled.Remove, "Meno") }
            Text("$quantity", style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { quantity++ }) { Icon(Icons.Filled.Add, "Più") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Conservazione", modifier = Modifier.weight(1f))
            DropdownChip(
                label = grade.ifBlank { "Non indicata" },
                selected = grade.isNotBlank(),
                options = GRADES.map { it to it.ifBlank { "Non indicata" } },
                onSelect = { grade = it },
            )
        }
        OutlinedTextField(
            value = year,
            onValueChange = { year = it.take(20) },
            label = { Text("Anno / millesimo del mio esemplare") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Note") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Acquisto", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = price,
                onValueChange = { v -> price = v.filter { it.isDigit() || it == ',' || it == '.' }.take(12) },
                label = { Text("Prezzo (€)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = purchaseDate,
                onValueChange = { purchaseDate = it.take(20) },
                label = { Text("Data") },
                placeholder = { Text("gg/mm/aaaa") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = provenance,
            onValueChange = { provenance = it.take(200) },
            label = { Text("Provenienza (negozio, asta, eredità…)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                onSave(
                    item.copy(
                        quantity = quantity,
                        grade = grade,
                        issueYear = year.trim(),
                        notes = notes.trim(),
                        purchasePrice = parsePrice(price),
                        purchaseDate = purchaseDate.trim(),
                        provenance = provenance.trim(),
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Salva") }
    }
}

/** Accepts "12,50", "12.50" and "1.234,50". */
private fun parsePrice(s: String): Double? {
    val t = s.trim()
    if (t.isEmpty()) return null
    val normalized = if (t.contains(',')) t.replace(".", "").replace(',', '.') else t
    return normalized.toDoubleOrNull()
}
