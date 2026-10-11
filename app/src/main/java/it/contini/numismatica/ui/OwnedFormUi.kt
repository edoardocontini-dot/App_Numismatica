package it.contini.numismatica.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.contini.numismatica.AppViewModel
import it.contini.numismatica.OwnedForm
import it.contini.numismatica.data.GRADES
import it.contini.numismatica.data.Issue
import it.contini.numismatica.data.TypeDetail
import it.contini.numismatica.data.TypeSummary
import it.contini.numismatica.data.formatThousands
import it.contini.numismatica.data.yearsLabel

/** Largest year span offered as a plain list when Numista has no issue list for the type. */
private const val MAX_RANGE = 600

/** The fields Dad fills in for a piece: year (menu), grade, quantity, provenance, notes. */
@Composable
fun OwnedFormFields(
    vm: AppViewModel,
    typeId: Long,
    minYear: Int?,
    maxYear: Int?,
    form: OwnedForm,
    onChange: (OwnedForm) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        YearPicker(vm, typeId, minYear, maxYear, form.issueYear, form.mintage) { label, mintage ->
            onChange(form.copy(issueYear = label, mintage = mintage))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Conservazione", modifier = Modifier.weight(1f))
            DropdownChip(
                label = form.grade.ifBlank { "Non indicata" },
                selected = form.grade.isNotBlank(),
                options = GRADES.map { it to it.ifBlank { "Non indicata" } },
                onSelect = { onChange(form.copy(grade = it)) },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Quantità", modifier = Modifier.weight(1f))
            IconButton(onClick = { if (form.quantity > 1) onChange(form.copy(quantity = form.quantity - 1)) }) {
                Icon(Icons.Filled.Remove, "Meno")
            }
            Text("${form.quantity}", style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { onChange(form.copy(quantity = form.quantity + 1)) }) { Icon(Icons.Filled.Add, "Più") }
        }
        OutlinedTextField(
            value = form.provenance,
            onValueChange = { onChange(form.copy(provenance = it.take(200))) },
            label = { Text("Provenienza") },
            placeholder = { Text("Negozio, asta, eredità, resto…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.notes,
            onValueChange = { onChange(form.copy(notes = it)) },
            label = { Text("Note") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private data class YearOption(val label: String, val mintage: Long?, val detail: String?)

/**
 * Scrollable menu of the years of this type. Uses Numista's list of issues (year + mint mark +
 * mintage) when available, otherwise every year of the issue period. "Scrivi a mano" is always
 * available for anything not in the list.
 */
@Composable
private fun YearPicker(
    vm: AppViewModel,
    typeId: Long,
    minYear: Int?,
    maxYear: Int?,
    value: String,
    mintage: Long?,
    onPick: (String, Long?) -> Unit,
) {
    var issues by remember(typeId) { mutableStateOf<List<Issue>?>(null) }
    var open by remember { mutableStateOf(false) }

    LaunchedEffect(typeId) {
        issues = vm.loadIssues(typeId).getOrNull() ?: emptyList()
    }

    val options: List<YearOption> = remember(issues, minYear, maxYear) {
        val fromIssues = issues.orEmpty().map { iss ->
            YearOption(
                iss.label,
                iss.mintage,
                listOfNotNull(iss.mintage?.let { "Tiratura ${formatThousands(it)}" }, iss.comment).joinToString(" · ").ifBlank { null },
            )
        }
        when {
            fromIssues.isNotEmpty() -> fromIssues
            minYear != null && maxYear != null && maxYear >= minYear && maxYear - minYear <= MAX_RANGE ->
                (minYear..maxYear).filter { it != 0 }.map { YearOption(yearsLabel(it, it), null, null) }
            minYear != null && maxYear == null -> listOf(YearOption(yearsLabel(minYear, minYear), null, null))
            else -> emptyList()
        }
    }
    // Free text when the list has nothing to offer, or when the saved value is not in the list.
    var manual by remember(typeId) { mutableStateOf(false) }
    val loading = issues == null
    val useText = manual || (!loading && options.isEmpty()) ||
        (!loading && value.isNotBlank() && options.none { it.label == value })

    if (useText) {
        OutlinedTextField(
            value = value,
            onValueChange = { onPick(it.take(30), null) },
            label = { Text("Anno / millesimo") },
            placeholder = { Text("Es. 1958 R") },
            singleLine = true,
            trailingIcon = {
                if (options.isNotEmpty()) {
                    IconButton(onClick = { manual = false; onPick("", null) }) { Icon(Icons.AutoMirrored.Filled.List, "Scegli dall'elenco") }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    val supporting: (@Composable () -> Unit)? =
        if (mintage != null) { { Text("Tiratura ${formatThousands(mintage)}") } } else null

    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value.ifBlank { if (loading) "Carico gli anni…" else "Non indicato" },
            onValueChange = {},
            readOnly = true,
            label = { Text("Anno / millesimo") },
            supportingText = supporting,
            trailingIcon = {
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.ArrowDropDown, null)
            },
            modifier = Modifier.fillMaxWidth(),
        )
        // Transparent layer so a tap anywhere on the field opens the menu
        Box(Modifier.matchParentSize().clickable(enabled = !loading) { open = true })
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.heightIn(max = 380.dp),
        ) {
            DropdownMenuItem(text = { Text("Non indicato") }, onClick = { onPick("", null); open = false })
            HorizontalDivider()
            options.forEach { o ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(o.label, style = MaterialTheme.typography.bodyLarge)
                            if (o.detail != null) {
                                Text(
                                    o.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    onClick = { onPick(o.label, o.mintage); open = false },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Scrivi a mano…") },
                leadingIcon = { Icon(Icons.Filled.Edit, null) },
                onClick = { manual = true; open = false },
            )
        }
    }
}

/** Form shown when Dad ticks "Ce l'ho" on a piece he doesn't have yet. */
@Composable
fun AddToCollectionDialog(
    vm: AppViewModel,
    summary: TypeSummary,
    detail: TypeDetail?,
    onDismiss: () -> Unit,
) {
    var form by remember(summary.id) { mutableStateOf(OwnedForm()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aggiungi alla collezione") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CoinThumb(summary.obverseThumb, size = 48)
                    Column(Modifier.weight(1f)) {
                        Text(summary.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOf(summary.issuerName, yearsLabel(summary.minYear, summary.maxYear)).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                OwnedFormFields(vm, summary.id, summary.minYear, summary.maxYear, form) { form = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.addToCollection(summary, form, detail); onDismiss() }) { Text("Aggiungi") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}
