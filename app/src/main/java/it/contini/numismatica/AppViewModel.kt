package it.contini.numismatica

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

enum class OwnedFilter { ALL, OWNED, MISSING }

data class SearchFilters(
    val query: String = "",
    val issuer: Issuer? = null,
    val category: String? = null,
    val year: String = "",
    val owned: OwnedFilter = OwnedFilter.ALL,
) {
    val hasCriteria get() = query.isNotBlank() || issuer != null || year.isNotBlank()
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
            val result = repo.search(f.query, f.issuer?.code, f.category, f.year, page)
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

    fun isOwned(id: Long) = owned.value.any { it.typeId == id }

    /** Tick from the search list: add with basic data, then complete it with the details. */
    fun addFromSummary(t: TypeSummary) = viewModelScope.launch {
        val base = newItem(t, composition = null, demonetized = null)
        repo.saveOwned(base)
        refreshOwned().join()
        val detail = runCatching { repo.type(t.id) }.getOrNull()
        refreshCounter()
        if (detail != null) {
            val current = owned.value.firstOrNull { it.typeId == t.id } ?: return@launch
            repo.saveOwned(current.copy(composition = detail.composition, demonetized = detail.demonetized))
            refreshOwned()
        }
    }

    fun addFromDetail(d: TypeDetail) = viewModelScope.launch {
        repo.saveOwned(newItem(d.summary, d.composition, d.demonetized))
        refreshOwned()
    }

    fun save(item: OwnedItem) = viewModelScope.launch {
        repo.saveOwned(item)
        refreshOwned()
    }

    fun remove(typeId: Long) = viewModelScope.launch {
        repo.removeOwned(typeId)
        refreshOwned()
    }

    private fun newItem(t: TypeSummary, composition: String?, demonetized: Boolean?) = OwnedItem(
        typeId = t.id,
        title = t.title,
        category = t.category,
        issuerCode = t.issuerCode,
        issuerName = t.issuerName,
        minYear = t.minYear,
        maxYear = t.maxYear,
        thumb = t.obverseThumb,
        composition = composition,
        demonetized = demonetized,
        quantity = 1,
        grade = "",
        notes = "",
        issueYear = "",
        addedAt = System.currentTimeMillis(),
    )

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
