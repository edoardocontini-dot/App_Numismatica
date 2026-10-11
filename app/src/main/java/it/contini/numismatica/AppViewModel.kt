package it.contini.numismatica

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.contini.numismatica.data.CatalogueExport
import it.contini.numismatica.data.Issue
import it.contini.numismatica.data.CountryIndex
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
    /** Italian country name (see CountryIndex) */
    val country: String? = null,
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
    /** Numista issuers grouped into countries; null until loaded (one request, cached for months). */
    val countryIndex = MutableStateFlow<CountryIndex?>(null)
    val countryError = MutableStateFlow<String?>(null)
    val apiKey = MutableStateFlow(repo.apiKey)
    val requests = MutableStateFlow(repo.requestsThisMonth())
    val message = MutableStateFlow<String?>(null)

    init {
        refreshOwned()
        loadCountries()
    }

    private fun refreshOwned() = viewModelScope.launch { owned.value = repo.owned() }

    private fun refreshCounter() {
        requests.value = repo.requestsThisMonth()
    }

    // ---------- settings ----------

    /** State of the API key box in Impostazioni. */
    sealed interface KeyState {
        data object Empty : KeyState
        data object Checking : KeyState
        data class Valid(val note: String? = null) : KeyState
        data class Invalid(val message: String) : KeyState
        data class Unverified(val message: String) : KeyState
    }

    val keyState = MutableStateFlow(currentKeyState())

    private fun currentKeyState(): KeyState = when {
        repo.apiKey.isBlank() -> KeyState.Empty
        repo.keyVerified -> KeyState.Valid()
        else -> KeyState.Unverified("Chiave salvata ma non ancora verificata. Tocca \"Salva chiave\" per controllarla.")
    }

    /** Called when Impostazioni opens, so a key rejected during a search shows up here. */
    fun refreshKeyState() {
        if (keyState.value !is KeyState.Checking && keyState.value !is KeyState.Invalid) keyState.value = currentKeyState()
    }

    /** Typing in the key box clears the previous result. */
    fun keyEdited() {
        if (keyState.value !is KeyState.Checking) keyState.value = KeyState.Empty
    }

    /** Checks the key with Numista and saves it only if Numista accepts it (or if there is no connection). */
    fun verifyAndSaveKey(raw: String) {
        val key = raw.filterNot { it.isWhitespace() }
        if (key.isEmpty()) {
            keyState.value = KeyState.Invalid("Incolla prima la chiave nel campo qui sopra.")
            return
        }
        if (keyState.value is KeyState.Checking) return
        keyState.value = KeyState.Checking
        viewModelScope.launch {
            val (result, detail) = repo.checkKey(key)
            when (result) {
                Repository.KeyCheck.VALID, Repository.KeyCheck.VALID_BUT_LIMITED -> {
                    repo.apiKey = key
                    repo.keyVerified = true
                    apiKey.value = key
                    keyState.value = KeyState.Valid(
                        if (result == Repository.KeyCheck.VALID_BUT_LIMITED) {
                            "Hai però raggiunto il limite di richieste del mese: le ricerche riprenderanno più avanti."
                        } else null
                    )
                    loadCountries()
                }
                Repository.KeyCheck.INVALID -> keyState.value = KeyState.Invalid(
                    "Chiave non riconosciuta da Numista. Controlla di averla copiata tutta, senza parti mancanti."
                )
                Repository.KeyCheck.OFFLINE -> {
                    repo.apiKey = key
                    apiKey.value = key
                    keyState.value = KeyState.Unverified(
                        "Nessuna connessione: la chiave è salvata ma non ancora verificata. Riprova quando sei online."
                    )
                }
                Repository.KeyCheck.ERROR -> keyState.value = KeyState.Invalid(
                    "Numista non ha risposto correttamente (${detail ?: "errore"}). Riprova tra qualche minuto."
                )
            }
            refreshCounter()
        }
    }

    fun removeKey() {
        repo.apiKey = ""
        apiKey.value = ""
        keyState.value = KeyState.Empty
        message.value = "Chiave rimossa"
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

    fun loadCountries() {
        if (countryIndex.value != null || repo.apiKey.isBlank()) return
        viewModelScope.launch {
            countryError.value = null
            try {
                val list = repo.issuers()
                countryIndex.value = withContext(Dispatchers.Default) { CountryIndex(list).also { it.countries } }
            } catch (e: Exception) {
                countryError.value = e.message
            }
            refreshCounter()
        }
    }

    /** "Italia" or "Italia · Italian States"; falls back to Numista's issuer name until the countries are loaded. */
    fun place(issuerCode: String, issuerName: String): String =
        countryIndex.value?.placeLabel(issuerCode, issuerName) ?: issuerName

    fun countryOf(o: OwnedItem): String =
        countryIndex.value?.countryName(o.issuerCode) ?: o.issuerName.ifBlank { "Senza paese" }

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
                o.issuerName.lowercase().contains(text) || countryOf(o).lowercase().contains(text)) &&
                (f.country == null || countryOf(o) == f.country) &&
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
