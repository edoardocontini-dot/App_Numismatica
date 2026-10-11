package it.contini.numismatica.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import it.contini.numismatica.AppViewModel
import it.contini.numismatica.CollectionFilters
import it.contini.numismatica.StatusFilter
import it.contini.numismatica.data.Categories
import it.contini.numismatica.data.Issuer
import it.contini.numismatica.data.OwnedItem

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CollectionScreen(vm: AppViewModel, openDetail: (Long) -> Unit) {
    val owned by vm.owned.collectAsState()
    val f by vm.collectionFilters.collectAsState()
    val shown = remember(owned, f) { vm.filteredOwned(owned, f) }
    val groups = remember(shown) { shown.groupBy { it.issuerName.ifBlank { "Senza paese" } } }
    val countries = remember(owned) { owned.map { it.issuerName }.filter { it.isNotBlank() }.distinct().sortedBy { it.lowercase() } }
    val materials = remember(owned) { owned.mapNotNull { it.composition }.distinct().sortedBy { it.lowercase() } }
    val missingDetails = remember(owned) { owned.count { it.composition == null || it.demonetized == null } }

    var showCountry by remember { mutableStateOf(false) }
    var showYears by remember { mutableStateOf(false) }
    var toRemove by remember { mutableStateOf<OwnedItem?>(null) }

    fun set(transform: (CollectionFilters) -> CollectionFilters) {
        vm.collectionFilters.value = transform(vm.collectionFilters.value)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "La mia collezione",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { vm.exportExcel() }, enabled = owned.isNotEmpty()) {
                Icon(Icons.Filled.TableView, null)
                Spacer(Modifier.width(6.dp))
                Text("Excel")
            }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("Pezzi", owned.sumOf { it.quantity }.toString(), Modifier.weight(1f))
            Stat("Tipi", owned.size.toString(), Modifier.weight(1f))
            Stat("Paesi", countries.size.toString(), Modifier.weight(1f))
        }

        OutlinedTextField(
            value = f.text,
            onValueChange = { v -> set { it.copy(text = v) } },
            placeholder = { Text("Cerca nella collezione o nelle note") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (f.text.isNotEmpty()) IconButton(onClick = { set { it.copy(text = "") } }) { Icon(Icons.Filled.Clear, "Cancella") }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )

        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = f.issuerName != null,
                onClick = { showCountry = true },
                label = { Text(f.issuerName ?: "Paese") },
            )
            DropdownChip(
                label = f.category?.let { Categories.label(it) } ?: "Tipo",
                selected = f.category != null,
                options = listOf(
                    null to "Tutti i tipi",
                    Categories.COIN to "Monete",
                    Categories.BANKNOTE to "Banconote",
                    Categories.EXONUMIA to "Esonumia",
                ),
                onSelect = { c -> set { it.copy(category = c) } },
            )
            DropdownChip(
                label = f.material ?: "Materiale",
                selected = f.material != null,
                options = listOf<Pair<String?, String>>(null to "Tutti i materiali") + materials.map { it to it },
                onSelect = { m -> set { it.copy(material = m) } },
            )
            DropdownChip(
                label = if (f.status == StatusFilter.ALL) "In corso / fuori corso" else f.status.label,
                selected = f.status != StatusFilter.ALL,
                options = StatusFilter.entries.map { it to it.label },
                onSelect = { s -> set { it.copy(status = s) } },
            )
            FilterChip(
                selected = f.yearFrom.isNotBlank() || f.yearTo.isNotBlank(),
                onClick = { showYears = true },
                label = {
                    Text(
                        if (f.yearFrom.isBlank() && f.yearTo.isBlank()) "Anni"
                        else "${f.yearFrom.ifBlank { "…" }}–${f.yearTo.ifBlank { "…" }}"
                    )
                },
            )
            if (f.active) {
                AssistChip(onClick = { vm.collectionFilters.value = CollectionFilters() }, label = { Text("Azzera filtri") })
            }
        }

        if (missingDetails > 0) {
            Row(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "$missingDetails pezzi senza materiale o stato.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(top = 12.dp),
                )
                TextButton(onClick = { vm.completeMissingDetails() }) { Text("Completa") }
            }
        }
        HorizontalDivider()

        if (owned.isEmpty()) {
            InfoCard("La collezione è vuota. Vai su Cerca e spunta \"Ce l'ho\" sui pezzi che possiedi.")
        } else if (shown.isEmpty()) {
            InfoCard("Nessun pezzo corrisponde ai filtri.")
        }

        LazyColumn(Modifier.fillMaxSize()) {
            groups.forEach { (country, list) ->
                stickyHeader(key = "h_$country") {
                    Text(
                        "$country  (${list.sumOf { it.quantity }})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(list, key = { it.typeId }) { o ->
                    val extra = listOfNotNull(
                        if (o.quantity > 1) "×${o.quantity}" else null,
                        o.grade.ifBlank { null },
                        o.issueYear.ifBlank { null },
                        o.composition,
                        when (o.demonetized) { true -> "fuori corso"; false -> "in corso"; null -> null },
                    ).joinToString(" · ")
                    ItemRow(
                        title = o.title,
                        issuer = "",
                        category = o.category,
                        minYear = o.minYear,
                        maxYear = o.maxYear,
                        thumb = o.thumb,
                        extra = extra,
                        checked = true,
                        onCheck = { toRemove = o },
                        onClick = { openDetail(o.typeId) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showCountry) {
        IssuerPickerDialog(
            issuers = countries.map { Issuer(it, it) },
            error = if (countries.isEmpty()) "Nessun paese in collezione." else null,
            onPick = { iss -> set { it.copy(issuerName = iss?.name) }; showCountry = false },
            onDismiss = { showCountry = false },
        )
    }

    if (showYears) {
        var from by remember { mutableStateOf(f.yearFrom) }
        var to by remember { mutableStateOf(f.yearTo) }
        AlertDialog(
            onDismissRequest = { showYears = false },
            title = { Text("Periodo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Anni a.C. con il segno meno (es. -300).")
                    OutlinedTextField(
                        value = from, onValueChange = { v -> from = v.filter { it.isDigit() || it == '-' }.take(5) },
                        label = { Text("Dal") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = to, onValueChange = { v -> to = v.filter { it.isDigit() || it == '-' }.take(5) },
                        label = { Text("Al") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { set { it.copy(yearFrom = from, yearTo = to) }; showYears = false }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { set { it.copy(yearFrom = "", yearTo = "") }; showYears = false }) { Text("Tutti") }
            },
        )
    }

    toRemove?.let { o ->
        ConfirmRemoveDialog(o.title, onConfirm = { vm.remove(o.typeId) }, onDismiss = { toRemove = null })
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
