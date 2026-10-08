package it.contini.numismatica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import it.contini.numismatica.data.Categories
import it.contini.numismatica.data.Issuer
import it.contini.numismatica.data.yearsLabel

@Composable
fun CoinThumb(url: String?, size: Int = 56) {
    val mod = Modifier
        .size(size.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.surfaceVariant)
    if (url.isNullOrBlank()) {
        Box(mod, contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Payments, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = mod,
            error = {
                Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Payments, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }
}

/** One coin/banknote row, used in search results and in the collection. */
@Composable
fun ItemRow(
    title: String,
    issuer: String,
    category: String,
    minYear: Int?,
    maxYear: Int?,
    thumb: String?,
    extra: String?,
    checked: Boolean,
    onCheck: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoinThumb(thumb)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val line = listOf(issuer, yearsLabel(minYear, maxYear), Categories.label(category))
                .filter { it.isNotBlank() }.joinToString(" · ")
            Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!extra.isNullOrBlank()) {
                Text(extra, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Checkbox(checked = checked, onCheckedChange = { onCheck() })
            Text(
                if (checked) "Ce l'ho" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** A chip that opens a dropdown of options. */
@Composable
fun <T> DropdownChip(
    label: String,
    selected: Boolean,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected,
            onClick = { open = true },
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(value); open = false })
            }
        }
    }
}

/** Searchable list of countries / issuers. */
@Composable
fun IssuerPickerDialog(
    issuers: List<Issuer>,
    error: String?,
    onPick: (Issuer?) -> Unit,
    onDismiss: () -> Unit,
) {
    var filter by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paese / Emittente") },
        text = {
            Column {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    placeholder = { Text("Cerca paese…") },
                    singleLine = true,
                    trailingIcon = {
                        if (filter.isNotEmpty()) IconButton(onClick = { filter = "" }) { Icon(Icons.Filled.Clear, "Cancella") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                when {
                    error != null -> Text(error, color = MaterialTheme.colorScheme.error)
                    issuers.isEmpty() -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    else -> {
                        val q = filter.trim().lowercase()
                        val shown = if (q.isEmpty()) issuers else issuers.filter { it.name.lowercase().contains(q) }
                        LazyColumn(Modifier.heightIn(max = 420.dp)) {
                            item {
                                Text(
                                    "Tutti i paesi",
                                    Modifier.fillMaxWidth().clickable { onPick(null) }.padding(vertical = 12.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                HorizontalDivider()
                            }
                            items(shown, key = { it.code }) { iss ->
                                Text(
                                    iss.name,
                                    Modifier.fillMaxWidth().clickable { onPick(iss) }.padding(vertical = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
    )
}

@Composable
fun ConfirmRemoveDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Togliere dalla collezione?") },
        text = { Text("\"$title\" verrà rimosso insieme a note, quantità e conservazione.") },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text("Rimuovi") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

@Composable
fun NumistaCredit(modifier: Modifier = Modifier) {
    Text(
        "Dati forniti da Numista",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
fun SectionGap() = Spacer(Modifier.height(12.dp))
