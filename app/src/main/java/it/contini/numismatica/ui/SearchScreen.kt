package it.contini.numismatica.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import it.contini.numismatica.AppViewModel
import it.contini.numismatica.OwnedFilter
import it.contini.numismatica.data.Categories
import it.contini.numismatica.data.TypeSummary

@Composable
fun SearchScreen(vm: AppViewModel, openDetail: (Long) -> Unit) {
    val state by vm.search.collectAsState()
    val owned by vm.owned.collectAsState()
    val apiKey by vm.apiKey.collectAsState()
    val issuers by vm.issuers.collectAsState()
    val issuersError by vm.issuersError.collectAsState()
    val ownedIds = remember(owned) { owned.map { it.typeId }.toSet() }
    val focus = LocalFocusManager.current
    val f = state.filters

    var showIssuers by remember { mutableStateOf(false) }
    var showYear by remember { mutableStateOf(false) }
    var toRemove by remember { mutableStateOf<TypeSummary?>(null) }

    val shown = when (f.owned) {
        OwnedFilter.ALL -> state.results
        OwnedFilter.OWNED -> state.results.filter { it.id in ownedIds }
        OwnedFilter.MISSING -> state.results.filter { it.id !in ownedIds }
    }

    fun search() {
        focus.clearFocus()
        vm.runSearch()
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Cerca nel catalogo",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        )
        OutlinedTextField(
            value = f.query,
            onValueChange = { v -> vm.updateFilters { it.copy(query = v) } },
            placeholder = { Text("Es. 500 lire, sesterzio, marengo…") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (f.query.isNotEmpty()) {
                    IconButton(onClick = { vm.updateFilters { it.copy(query = "") } }) { Icon(Icons.Filled.Clear, "Cancella") }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )

        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = f.issuer != null,
                onClick = { vm.loadIssuers(); showIssuers = true },
                label = { Text(f.issuer?.name ?: "Paese") },
            )
            DropdownChip(
                label = f.category?.let { Categories.label(it) } ?: "Tipo",
                selected = f.category != null,
                options = listOf(
                    null to "Tutti i tipi",
                    Categories.COIN to "Monete",
                    Categories.BANKNOTE to "Banconote",
                    Categories.EXONUMIA to "Esonumia (medaglie, gettoni…)",
                ),
                onSelect = { c -> vm.updateFilters { it.copy(category = c) } },
            )
            FilterChip(
                selected = f.year.isNotBlank(),
                onClick = { showYear = true },
                label = { Text(if (f.year.isBlank()) "Anno" else "Anno ${f.year}") },
            )
            DropdownChip(
                label = when (f.owned) {
                    OwnedFilter.ALL -> "Possedute e no"
                    OwnedFilter.OWNED -> "Solo possedute"
                    OwnedFilter.MISSING -> "Solo mancanti"
                },
                selected = f.owned != OwnedFilter.ALL,
                options = listOf(
                    OwnedFilter.ALL to "Tutte",
                    OwnedFilter.OWNED to "Solo quelle che ho",
                    OwnedFilter.MISSING to "Solo quelle che mancano",
                ),
                onSelect = { o -> vm.updateFilters { it.copy(owned = o) } },
            )
        }

        Button(
            onClick = { search() },
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) { Text("Cerca") }

        when {
            apiKey.isBlank() -> InfoCard(
                "Per cercare nel catalogo serve la chiave API gratuita di Numista. Inseriscila in Impostazioni."
            )
            state.error != null -> InfoCard(state.error!!, isError = true)
            !state.searched -> InfoCard(
                "Cerca per nome, paese e anno. Spunta \"Ce l'ho\" per aggiungere un pezzo alla collezione. " +
                    "Ogni ricerca usa una richiesta Numista (2.000 al mese gratis); le ricerche già fatte restano in memoria."
            )
        }

        if (state.searched && state.error == null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (state.loading && state.results.isEmpty()) "Ricerca in corso…"
                    else "${state.total} risultati" + if (f.owned != OwnedFilter.ALL) " · ${shown.size} mostrati" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                NumistaCredit()
            }
        }
        HorizontalDivider()

        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.id }) { t ->
                val isOwned = t.id in ownedIds
                ItemRow(
                    title = t.title,
                    issuer = t.issuerName,
                    category = t.category,
                    minYear = t.minYear,
                    maxYear = t.maxYear,
                    thumb = t.obverseThumb,
                    extra = null,
                    checked = isOwned,
                    onCheck = {
                        if (isOwned) {
                            toRemove = t
                        } else {
                            vm.addFromSummary(t)
                        }
                    },
                    onClick = { openDetail(t.id) },
                )
                HorizontalDivider()
            }
            if (state.loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (state.canLoadMore) {
                item {
                    OutlinedButton(
                        onClick = { vm.loadMore() },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) { Text("Carica altri (${state.results.size} di ${state.total})") }
                }
            }
        }
    }

    if (showIssuers) {
        IssuerPickerDialog(
            issuers = issuers,
            error = issuersError,
            onPick = { iss -> vm.updateFilters { it.copy(issuer = iss) }; showIssuers = false },
            onDismiss = { showIssuers = false },
        )
    }

    if (showYear) {
        var text by remember { mutableStateOf(f.year) }
        AlertDialog(
            onDismissRequest = { showYear = false },
            title = { Text("Anno") },
            text = {
                Column {
                    Text("Anno di emissione. Per gli anni avanti Cristo usa il segno meno (es. -44).")
                    OutlinedTextField(
                        value = text,
                        onValueChange = { v -> text = v.filter { it.isDigit() || it == '-' }.take(5) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.updateFilters { it.copy(year = text) }; showYear = false }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { vm.updateFilters { it.copy(year = "") }; showYear = false }) { Text("Tutti gli anni") }
            },
        )
    }

    toRemove?.let { t ->
        ConfirmRemoveDialog(t.title, onConfirm = { vm.remove(t.id) }, onDismiss = { toRemove = null })
    }
}

@Composable
fun InfoCard(text: String, isError: Boolean = false) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Text(text, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
