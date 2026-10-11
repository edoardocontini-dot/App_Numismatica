package it.contini.numismatica.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** [code] is the HTTP status (0 = network problem). */
class ApiException(message: String, val code: Int = 0) : IOException(message)

/**
 * Minimal client for the Numista API v3 (https://api.numista.com/v3).
 * All calls are blocking: call them from a background dispatcher.
 */
object NumistaApi {
    private const val BASE = "https://api.numista.com/v3"
    /** Numista documents only en / es / fr; "it" is tried first and the app falls back to "en" if refused. */
    const val DEFAULT_LANG = "it"
    const val PAGE_SIZE = 50

    fun searchUrl(query: String, issuer: String?, category: String?, year: String?, page: Int, lang: String): String {
        val params = mutableListOf("lang" to lang, "page" to page.toString(), "count" to PAGE_SIZE.toString())
        if (query.isNotBlank()) params += "q" to query.trim()
        if (!issuer.isNullOrBlank()) params += "issuer" to issuer
        if (!category.isNullOrBlank()) params += "category" to category
        if (!year.isNullOrBlank()) params += "year" to year.trim()
        return "$BASE/types?" + params.joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }
    }

    fun typeUrl(id: Long, lang: String) = "$BASE/types/$id?lang=$lang"

    fun issuesUrl(id: Long, lang: String) = "$BASE/types/$id/issues?lang=$lang"

    fun issuersUrl(lang: String) = "$BASE/issuers?lang=$lang"

    /** Performs a GET and returns the raw body. Throws [ApiException] with an Italian message. */
    fun get(url: String, apiKey: String): String {
        if (apiKey.isBlank()) throw ApiException("Manca la chiave API Numista: inseriscila in Impostazioni.")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Numista-API-Key", apiKey.trim())
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            if (code in 200..299) {
                return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
            val body = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
            val detail = body?.let { runCatching { JSONObject(it).optString("error_message") }.getOrNull() }
                ?.takeIf { it.isNotBlank() }
            throw ApiException(
                when (code) {
                    401 -> "Chiave API non valida. Controllala in Impostazioni."
                    404 -> "Elemento non trovato su Numista."
                    429 -> "Troppe richieste a Numista (limite mensile o richieste troppo ravvicinate). Riprova più tardi."
                    else -> "Errore Numista ($code)" + (detail?.let { ": $it" } ?: "")
                },
                code,
            )
        } catch (e: ApiException) {
            throw e
        } catch (e: IOException) {
            throw ApiException("Connessione non riuscita. Controlla internet e riprova.")
        } finally {
            conn.disconnect()
        }
    }

    // ---------- parsing ----------

    fun parseSearch(json: String): SearchPage {
        val o = JSONObject(json)
        val arr = o.optJSONArray("types") ?: JSONArray()
        val items = (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { parseSummary(it) } }
        return SearchPage(total = o.optInt("count", items.size), items = items)
    }

    fun parseSummary(o: JSONObject): TypeSummary {
        val issuer = o.optJSONObject("issuer")
        return TypeSummary(
            id = o.optLong("id"),
            title = o.str("title") ?: "Senza titolo",
            category = o.str("category") ?: "",
            issuerCode = issuer?.str("code") ?: "",
            issuerName = issuer?.str("name") ?: "",
            minYear = o.intOrNull("min_year"),
            maxYear = o.intOrNull("max_year"),
            obverseThumb = o.str("obverse_thumbnail") ?: o.optJSONObject("obverse")?.str("thumbnail"),
            reverseThumb = o.str("reverse_thumbnail") ?: o.optJSONObject("reverse")?.str("thumbnail"),
        )
    }

    fun parseType(json: String): TypeDetail {
        val o = JSONObject(json)
        val obverse = o.optJSONObject("obverse")
        val reverse = o.optJSONObject("reverse")
        val value = o.optJSONObject("value")
        val demon = o.optJSONObject("demonetization")
        return TypeDetail(
            summary = parseSummary(o),
            url = o.str("url"),
            valueText = value?.str("text"),
            currency = value?.optJSONObject("currency")?.let { it.str("full_name") ?: it.str("name") },
            composition = o.optJSONObject("composition")?.str("text"),
            weight = o.str("weight"),
            size = listOfNotNull(o.str("size"), o.str("size2")).joinToString(" × ").ifBlank { null },
            thickness = o.str("thickness"),
            shape = o.str("shape"),
            technique = o.optJSONObject("technique")?.str("text"),
            rulers = o.optJSONArray("ruler").names("name"),
            demonetized = demon?.let { if (it.has("is_demonetized")) it.optBoolean("is_demonetized") else null },
            demonetizationDate = demon?.str("demonetization_date"),
            obverseImage = obverse?.str("picture"),
            reverseImage = reverse?.str("picture"),
            obverseDescription = obverse?.str("description"),
            reverseDescription = reverse?.str("description"),
            obverseLettering = obverse?.str("lettering"),
            reverseLettering = reverse?.str("lettering"),
            edge = o.optJSONObject("edge")?.str("description"),
            mints = o.optJSONArray("mints").names("name"),
            comments = o.str("comments")?.let { stripHtml(it) }?.takeIf { it.isNotBlank() },
            objectType = o.optJSONObject("object_type")?.str("name"),
            issuingEntity = o.optJSONObject("issuing_entity")?.str("name"),
            references = references(o.optJSONArray("references")),
            watermark = o.optJSONObject("watermark")?.str("description"),
        )
    }

    /** Issues of a type = the individual years / mint marks, with mintage. */
    fun parseIssues(json: String): List<Issue> {
        val trimmed = json.trim()
        val arr = if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed).optJSONArray("issues") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val year = o.intOrNull("year")
            val gregorian = o.intOrNull("gregorian_year")
            val dated = if (o.has("is_dated")) o.optBoolean("is_dated", true) else year != null
            val yearText = when {
                dated && year != null && gregorian != null && gregorian != year -> "$year (${yearsLabel(gregorian, gregorian)})"
                dated && year != null -> yearsLabel(year, year)
                dated && gregorian != null -> yearsLabel(gregorian, gregorian)
                else -> {
                    val range = yearsLabel(o.intOrNull("min_year"), o.intOrNull("max_year"))
                    if (range.isBlank()) "Senza data" else "Senza data ($range)"
                }
            }
            val mint = o.str("mint_letter")
            Issue(
                id = o.optLong("id"),
                label = if (mint != null) "$yearText $mint" else yearText,
                sortYear = gregorian ?: year ?: o.intOrNull("min_year"),
                mintage = o.optString("mintage").trim().toLongOrNull()?.takeIf { it > 0 },
                comment = o.str("comment")?.let { stripHtml(it) }?.takeIf { it.isNotBlank() },
                references = references(o.optJSONArray("references")),
            )
        }.sortedWith(compareBy<Issue>({ it.sortYear ?: Int.MAX_VALUE }, { it.label }))
    }

    fun parseIssuers(json: String): List<Issuer> {
        val arr = JSONObject(json).optJSONArray("issuers") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val code = o.str("code") ?: return@mapNotNull null
            Issuer(
                code = code,
                name = o.str("name") ?: code,
                parentCode = o.optJSONObject("parent")?.str("code"),
                level = o.intOrNull("level") ?: 1,
            )
        }.sortedBy { it.name.lowercase() }
    }

    /** [{"catalogue": {"code": "KM"}, "number": "98"}] -> ["KM# 98"] */
    private fun references(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val r = arr.optJSONObject(i) ?: return@mapNotNull null
            val number = r.str("number") ?: return@mapNotNull null
            val code = r.optJSONObject("catalogue")?.str("code")
            if (code != null) "$code# $number" else number
        }
    }
}

// ---------- JSON helpers (Numista sometimes sends numbers as strings) ----------

internal fun JSONObject.str(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

internal fun JSONObject.intOrNull(key: String): Int? =
    if (!has(key) || isNull(key)) null else optString(key).trim().toIntOrNull()

private fun JSONArray?.names(key: String): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optJSONObject(it)?.str(key) }
}

fun stripHtml(s: String): String =
    s.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("\r\n", "\n")
        .replace(Regex("[ \t]+\n"), "\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
