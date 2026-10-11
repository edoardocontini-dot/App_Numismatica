package it.contini.numismatica.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.YearMonth

class Repository(context: Context) {
    private val db = Db(context)
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // ---------- settings ----------

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(value) = prefs.edit().putString("api_key", value.trim()).apply()

    /** Requests sent to Numista in the current calendar month (counted on the phone). */
    fun requestsThisMonth(): Int {
        val month = YearMonth.now().toString()
        return if (prefs.getString("req_month", "") == month) prefs.getInt("req_count", 0) else 0
    }

    private fun countRequest() {
        val month = YearMonth.now().toString()
        val current = requestsThisMonth()
        prefs.edit().putString("req_month", month).putInt("req_count", current + 1).apply()
    }

    /** Cache first, network only when needed. */
    private suspend fun fetch(url: String, maxAgeMs: Long): String = withContext(Dispatchers.IO) {
        db.cached(url, maxAgeMs) ?: NumistaApi.get(url, apiKey).also {
            countRequest()
            db.putCache(url, it)
        }
    }

    // ---------- catalogue ----------

    suspend fun search(query: String, issuer: String?, category: String?, year: String?, page: Int): SearchPage {
        val url = NumistaApi.searchUrl(query, issuer, category, year, page)
        val body = fetch(url, maxAgeMs = 14 * DAY)
        return withContext(Dispatchers.Default) { NumistaApi.parseSearch(body) }
    }

    suspend fun type(id: Long): TypeDetail {
        val body = fetch(NumistaApi.typeUrl(id), maxAgeMs = 180 * DAY)
        return withContext(Dispatchers.Default) { NumistaApi.parseType(body) }
    }

    suspend fun issuers(): List<Issuer> {
        val body = fetch(NumistaApi.issuersUrl(), maxAgeMs = 60 * DAY)
        return withContext(Dispatchers.Default) { NumistaApi.parseIssuers(body) }
    }

    // ---------- collection ----------

    suspend fun owned(): List<OwnedItem> = withContext(Dispatchers.IO) { db.allOwned() }

    suspend fun saveOwned(item: OwnedItem) = withContext(Dispatchers.IO) { db.upsertOwned(item) }

    suspend fun removeOwned(typeId: Long) = withContext(Dispatchers.IO) { db.deleteOwned(typeId) }

    // ---------- Excel export ----------

    /**
     * Collects the catalogue details for every owned piece. Uses the phone cache first (any age);
     * only pieces never opened cost a Numista request. If Numista can't be reached the piece
     * is exported with the data already on the phone.
     */
    suspend fun exportEntries(onProgress: (done: Int, total: Int) -> Unit): List<CatalogueExport.Entry> {
        val items = owned()
        var networkOk = true
        return items.mapIndexed { i, item ->
            onProgress(i, items.size)
            val url = NumistaApi.typeUrl(item.typeId)
            val body = withContext(Dispatchers.IO) { db.cachedAnyAge(url) } ?: if (networkOk) {
                runCatching { fetch(url, maxAgeMs = 180 * DAY) }.onFailure { networkOk = false }.getOrNull()
            } else null
            val detail = body?.let { b -> runCatching { NumistaApi.parseType(b) }.getOrNull() }
            CatalogueExport.Entry(item, detail)
        }.also { onProgress(items.size, items.size) }
    }

    // ---------- backup ----------

    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        db.allOwned().forEach {
            arr.put(JSONObject().apply {
                put("type_id", it.typeId)
                put("title", it.title)
                put("category", it.category)
                put("issuer_code", it.issuerCode)
                put("issuer_name", it.issuerName)
                put("min_year", it.minYear ?: JSONObject.NULL)
                put("max_year", it.maxYear ?: JSONObject.NULL)
                put("thumb", it.thumb ?: JSONObject.NULL)
                put("composition", it.composition ?: JSONObject.NULL)
                put("demonetized", it.demonetized ?: JSONObject.NULL)
                put("quantity", it.quantity)
                put("grade", it.grade)
                put("notes", it.notes)
                put("issue_year", it.issueYear)
                put("added_at", it.addedAt)
                put("purchase_price", it.purchasePrice ?: JSONObject.NULL)
                put("purchase_date", it.purchaseDate)
                put("provenance", it.provenance)
            })
        }
        JSONObject().put("app", "numismatica").put("version", 1).put("items", arr).toString(2)
    }

    /** Replaces the whole collection with the backup. Returns the number of pieces imported. */
    suspend fun importJson(json: String): Int = withContext(Dispatchers.IO) {
        val arr = JSONObject(json).optJSONArray("items") ?: throw IllegalArgumentException("File non valido")
        val items = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            OwnedItem(
                typeId = o.optLong("type_id"),
                title = o.str("title") ?: "",
                category = o.str("category") ?: "",
                issuerCode = o.str("issuer_code") ?: "",
                issuerName = o.str("issuer_name") ?: "",
                minYear = o.intOrNull("min_year"),
                maxYear = o.intOrNull("max_year"),
                thumb = o.str("thumb"),
                composition = o.str("composition"),
                demonetized = if (o.has("demonetized") && !o.isNull("demonetized")) o.optBoolean("demonetized") else null,
                quantity = o.optInt("quantity", 1),
                grade = o.str("grade") ?: "",
                notes = o.str("notes") ?: "",
                issueYear = o.str("issue_year") ?: "",
                addedAt = o.optLong("added_at", System.currentTimeMillis()),
                purchasePrice = if (o.has("purchase_price") && !o.isNull("purchase_price")) o.optDouble("purchase_price") else null,
                purchaseDate = o.str("purchase_date") ?: "",
                provenance = o.str("provenance") ?: "",
            )
        }.filter { it.typeId > 0 }
        db.replaceAllOwned(items)
        items.size
    }

    companion object {
        const val DAY = 24L * 60 * 60 * 1000
    }
}
