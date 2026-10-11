package it.contini.numismatica

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.contini.numismatica.data.CatalogueExport
import it.contini.numismatica.data.Issue
import it.contini.numismatica.data.Issuer
import it.contini.numismatica.data.NumistaApi
import it.contini.numismatica.data.OwnedItem
import it.contini.numismatica.data.Repository
import it.contini.numismatica.data.TypeDetail
import it.contini.numismatica.data.TypeSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

enum class OwnedFilter { ALL, OWNED, MISSING }

/** What Dad fills in when adding or editing a piece. */
data class OwnedForm(
    val quantity: Int = 1,
    val grade: String = "",
    val issueYear: String = "",
    val mintage: Long? = null,
    val provenance: String = "",
    val notes: String = "",
) {
    companion object {
        fun of(item: OwnedItem) = OwnedForm(item.quantity, item.grade, item.issueYear, item.mintage, item.provenance, item.notes)
    }
}

data class SearchFilters(
    val query: String = "",
    val issuer: Issuer? = null,
    val category: String? = null,
    val year: String = "",
    val owned: OwnedFilter = OwnedFilter.ALL,
) {
    val hasCriteria get() = query.isNotBlank() || issuer != null || year.trim().toIntOrNull() != null
}

data class SearchState(
    val filters: SearchFilters = SearchFilters(),
    val results: List<TypeSummary> = emptyList(),
    val total: Int = 0,
    val page: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
    val searched: Boolean = false,
) {
    val canLoadMore get() = results.size < total && !loading
}

enum class StatusFilter(val label: String) { ALL("Tutte"), ACTIVE("In corso"), DEMONETIZED("Fuori corso"), UNKNOWN("Non noto") }

data class CollectionFilters(
    val text: String = "",
    val issuerName: String? = null,
    val category: String? = null,
    val material: String? = null,
    val status: StatusFilter = StatusFilter.ALL,
    val yearFrom: String = "",
    val yearTo: String = "",
) {
    val active get() = this != CollectionFilters()
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)

    val search = MutableStateFlow(SearchState())
    val owned = MutableStateFlow<List<OwnedItem>>(emptyList())
    val collectionFilters = MutableStateFlow(CollectionFilters())
    val issuers = MutableStateFlow<List<Issuer>>(emptyList())
    val issuersError = MutableStateFlow<String?>(null)
    val apiKey = MutableStateFlow(repo.apiKey)
    val requests = MutableStateFlow(repo.requestsThisMonth())
    val message = MutableStateFlow<String?>(null)

    init {
        refreshOwned()
    }

    private fun refreshOwned() = viewModelScope.launch { owned.value = repo.owned() }

    private fun refreshCounter() {
        requests.value = repo.requestsThisMonth()
    }

    // ---------- settings ----------

    fun setApiKey(key: String) {
        repo.apiKey = key
        apiKey.value = repo.apiKey
        message.value = if (key.isBlank()) "Chiave rimossa" else "Chiave salvata"
    }

    // ---------- search ----------

    fun updateFilters(transform: (SearchFilters) -> SearchFilters) {
        search.update { it.copy(filters = transform(it.filters)) }
    }

    fun runSearch() {
        val f = search.value.filters
        if (!f.hasCriteria) {
            search.update { it.copy(error = "Scrivi qualcosa, scegli un paese o un anno.") }
            return
        }
        search.update { it.copy(loading = true, error = null, results = emptyList(), total = 0, page = 0, searched = true) }
        loadPage(1)
    }

    fun loadMore() {
        val s = search.value
        if (!s.canLoadMore) return
        search.update { it.copy(loading = true, error = null) }
        loadPage(s.page + 1)
    }

    private fun loadPage(page: Int) = viewModelScope.launch {
        val f = search.value.filters
        try {
            val year = f.year.trim().toIntOrNull()?.toString()
            val result = repo.search(f.query, f.issuer?.code, f.category, year, page)
            search.update {
                it.copy(
                    results = (it.results + result.items).distinctBy { t -> t.id },
                    total = result.total,
                    page = page,
                    loading = false,
                )
            }
        } catch (e: Exception) {
            search.update { it.copy(loading = false, error = e.message ?: "Errore sconosciuto") }
        }
        refreshCounter()
    }

    fun loadIssuers() {
        if (issuers.value.isNotEmpty()) return
        viewModelScope.launch {
            issuersError.value = null
            try {
                issuers.value = repo.issuers()
            } catch (e: Exception) {
                issuersError.value = e.message
            }
            refreshCounter()
        }
    }

    // ---------- details ----------

    suspend fun loadType(id: Long): Result<TypeDetail> {
        val r = runCatching { repo.type(id) }
        refreshCounter()
        return r
    }

    // ---------- collection ----------

    /** Years / mint marks of a type, for the year menu. */
    suspend fun loadIssues(typeId: Long): Result<List<Issue>> {
        val r = runCatching { repo.issues(typeId) }
        refreshCounter()
        return r
    }

    /**
     * Adds a piece with what Dad entered in the "Aggiungi" form. If the catalogue details are not
     * known yet they are fetched afterwards (material, circulation status).
     */
    fun addToCollection(t: TypeSummary, form: OwnedForm, detail: TypeDetail?) = viewModelScope.launch {
        val item = OwnedItem(
            typeId = t.id,
            title = t.title,
            category = t.category,
            issuerCode = t.issuerCode,
            issuerName = t.issuerName,
            minYear = t.minYear,
            maxYear = t.maxYear,
            thumb = t.obverseThumb,
            composition = detail?.composition,
            demonetized = detail?.demonetized,
            quantity = form.quantity,
            grade = form.grade,
            notes = form.notes.trim(),
            issueYear = form.issueYear.trim(),
            addedAt = System.currentTimeMillis(),
            provenance = form.provenance.trim(),
            mintage = form.mintage,
        )
        repo.saveOwned(item)
        refreshOwned().join()
        message.value = "Aggiunto alla collezione"
        if (detail == null) {
            val d = runCatching { repo.type(t.id) }.getOrNull()
            refreshCounter()
            if (d != null) {
                val current = owned.value.firstOrNull { it.typeId == t.id } ?: return@launch
                repo.saveOwned(current.copy(composition = d.composition, demonetized = d.demonetized))
                refreshOwned()
            }
        }
    }

    fun update(item: OwnedItem, form: OwnedForm) = viewModelScope.launch {
        repo.saveOwned(
            item.copy(
                quantity = form.quantity,
                grade = form.grade,
                issueYear = form.issueYear.trim(),
                mintage = form.mintage,
                provenance = form.provenance.trim(),
                notes = form.notes.trim(),
            )
        )
        refreshOwned()
        message.value = "Salvato"
    }

    fun remove(typeId: Long) = viewModelScope.launch {
        repo.removeOwned(typeId)
        refreshOwned()
    }

    fun filteredOwned(items: List<OwnedItem>, f: CollectionFilters): List<OwnedItem> {
        val from = f.yearFrom.trim().toIntOrNull()
        val to = f.yearTo.trim().toIntOrNull()
        val text = f.text.trim().lowercase()
        return items.filter { o ->
            (text.isEmpty() || o.title.lowercase().contains(text) || o.notes.lowercase().contains(text) ||
                o.issuerName.lowercase().contains(text)) &&
                (f.issuerName == null || o.issuerName == f.issuerName) &&
                (f.category == null || o.category == f.category) &&
                (f.material == null || o.composition == f.material) &&
                statusMatches(o, f.status) &&
                (from == null || (o.maxYear ?: o.minYear ?: Int.MAX_VALUE) >= from) &&
                (to == null || (o.minYear ?: o.maxYear ?: Int.MIN_VALUE) <= to)
        }
    }

    private fun statusMatches(o: OwnedItem, s: StatusFilter): Boolean = when (s) {
        StatusFilter.ALL -> true
        StatusFilter.ACTIVE -> o.demonetized == false
        StatusFilter.DEMONETIZED -> o.demonetized == true
        StatusFilter.UNKNOWN -> o.demonetized == null
    }

    /** Fills in material / status for pieces added before their details were loaded. */
    fun completeMissingDetails() = viewModelScope.launch {
        val missing = owned.value.filter { it.composition == null || it.demonetized == null }
        var done = 0
        for (o in missing) {
            val d = runCatching { repo.type(o.typeId) }.getOrNull() ?: break
            repo.saveOwned(o.copy(composition = d.composition, demonetized = d.demonetized))
            done++
        }
        refreshCounter()
        refreshOwned()
        message.value = "Aggiornati $done pezzi su ${missing.size}"
    }

    // ---------- Excel export ----------

    /** (done, total) while the catalogue is being prepared, null otherwise. */
    val exportProgress = MutableStateFlow<Pair<Int, Int>?>(null)

    /** The last Excel file produced, waiting for the user to share or save it. */
    val exportedFile = MutableStateFlow<File?>(null)

    fun exportExcel() {
        if (exportProgress.value != null) return
        if (owned.value.isEmpty()) {
            message.value = "La collezione è vuota: niente da esportare."
            return
        }
        viewModelScope.launch {
            exportProgress.value = 0 to owned.value.size
            try {
                val entries = repo.exportEntries { done, total -> exportProgress.value = done to total }
                val file = withContext(Dispatchers.IO) {
                    val dir = File(getApplication<Application>().cacheDir, "exports").apply { mkdirs() }
                    dir.listFiles()?.forEach { it.delete() }
                    val f = File(dir, "Collezione_numismatica_${LocalDate.now()}.xlsx")
                    f.outputStream().buffered().use { CatalogueExport.write(it, entries) }
                    f
                }
                exportedFile.value = file
                val missing = entries.count { it.detail == null }
                if (missing > 0) {
                    message.value = "$missing pezzi esportati senza dettagli (Numista non raggiungibile)."
                }
            } catch (e: Exception) {
                message.value = "Esportazione non riuscita: ${e.message}"
            } finally {
                exportProgress.value = null
                refreshCounter()
            }
        }
    }

    fun saveExportTo(uri: Uri, file: File) = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                } ?: error("impossibile scrivere il file")
            }
            message.value = "File Excel salvato"
        } catch (e: Exception) {
            message.value = "Salvataggio non riuscito: ${e.message}"
        }
    }

    // ---------- backup ----------

    fun exportTo(uri: Uri) = viewModelScope.launch {
        try {
            val json = repo.exportJson()
            withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                    it.write(json.toByteArray(Charsets.UTF_8))
                } ?: error("impossibile scrivere il file")
            }
            message.value = "Backup salvato (${owned.value.size} pezzi)"
        } catch (e: Exception) {
            message.value = "Backup non riuscito: ${e.message}"
        }
    }

    fun importFrom(uri: Uri) = viewModelScope.launch {
        try {
            val json = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: error("impossibile leggere il file")
            }
            val n = repo.importJson(json)
            refreshOwned()
            message.value = "Ripristinati $n pezzi"
        } catch (e: Exception) {
            message.value = "Ripristino non riuscito: ${e.message}"
        }
    }

    companion object {
        const val PAGE_SIZE = NumistaApi.PAGE_SIZE
    }
}
