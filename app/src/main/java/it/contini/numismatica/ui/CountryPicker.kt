package it.contini.numismatica.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.contini.numismatica.data.Country
import it.contini.numismatica.data.CountryIndex
import it.contini.numismatica.data.Issuer
import java.text.Normalizer

/** Lowercase without accents, so "aland" finds "Åland" and "eta" finds "Età". */
private fun fold(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

/**
 * Two-step picker for the search: first the country (Italian names, one entry per country),
 * then optionally a specific issuer inside it (e.g. "Italian States").
 * Choosing the whole country searches all its issuers at once.
 */
@Composable
fun CountryPickerDialog(
    index: CountryIndex?,
    error: String?,
    onPick: (Issuer?) -> Unit,
    onDismiss: () -> Unit,
) {
    var country by remember { mutableStateOf<Country?>(null) }
    var filter by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (country != null) {
                    IconButton(onClick = { country = null; filter = "" }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro")
                    }
                }
                Text(country?.name ?: "Paese")
            }
        },
        text = {
            Column {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    placeholder = { Text(if (country == null) "Cerca paese…" else "Cerca emittente…") },
                    singleLine = true,
                    trailingIcon = {
                        if (filter.isNotEmpty()) IconButton(onClick = { filter = "" }) { Icon(Icons.Filled.Clear, "Cancella") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                when {
                    error != null && index == null -> Text(error, color = MaterialTheme.colorScheme.error)
                    index == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    country == null -> CountryList(index, filter, onAll = { onPick(null) }) { c ->
                        if (c.issuerCount <= 1) onPick(Issuer(c.rootCodes.first(), c.name)) else { country = c; filter = "" }
                    }
                    else -> IssuerList(index, country!!, filter, onPick)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
    )
}

@Composable
private fun CountryList(index: CountryIndex, filter: String, onAll: () -> Unit, onOpen: (Country) -> Unit) {
    val q = fold(filter.trim())
    val shown = remember(index, q) { if (q.isEmpty()) index.countries else index.countries.filter { fold(it.name).contains(q) } }
    LazyColumn(Modifier.heightIn(max = 440.dp)) {
        if (q.isEmpty()) {
            item {
                Text(
                    "Tutti i paesi",
                    Modifier.fillMaxWidth().clickable(onClick = onAll).padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
                HorizontalDivider()
            }
        }
        items(shown, key = { it.name }) { c ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(c) }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(c.name, Modifier.weight(1f))
                if (c.issuerCount > 1) {
                    Text(
                        "${c.issuerCount - 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun IssuerList(index: CountryIndex, country: Country, filter: String, onPick: (Issuer?) -> Unit) {
    val all = remember(index, country) { index.issuersOf(country) }
    val q = fold(filter.trim())
    val shown = remember(all, q) { if (q.isEmpty()) all else all.filter { fold(it.name).contains(q) } }
    LazyColumn(Modifier.heightIn(max = 440.dp)) {
        item {
            Text(
                "Tutto: ${country.name}",
                Modifier.fillMaxWidth().clickable { onPick(Issuer(country.rootCodes.first(), country.name)) }.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Oppure scegli un emittente specifico (${all.size}):",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.padding(top = 6.dp))
        }
        items(shown, key = { it.code }) { iss ->
            val indent = if (q.isEmpty()) ((iss.level - 2).coerceIn(0, 4) * 14).dp else 0.dp
            Text(
                iss.name,
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(Issuer(iss.code, "${country.name} · ${iss.name}")) }
                    .padding(start = indent, top = 10.dp, bottom = 10.dp),
                style = if (iss.level <= 2) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
