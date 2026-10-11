package it.contini.numismatica.data

/** A country as shown in the app: one Italian name, one or more Numista top-level groups. */
data class Country(
    val name: String,
    /** Numista codes of the top-level groups (searching one of them includes all its sub-issuers). */
    val rootCodes: List<String>,
    /** Number of Numista issuers inside this country (all levels). */
    val issuerCount: Int,
)

/**
 * Groups Numista's ~12,000 issuers into countries using Numista's own hierarchy:
 * every issuer belongs to the country of its top-level ancestor
 * (e.g. "Italy", "Italian States", "Italy, Napoleonic Kingdom of" → "Italia").
 * Pure Kotlin, unit-tested.
 */
class CountryIndex(issuers: List<Issuer>) {
    private val byCode: Map<String, Issuer> = issuers.associateBy { it.code }
    private val rootCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Code of the top-level group the issuer belongs to (itself when it is top level). */
    fun rootOf(code: String): String? {
        rootCache[code]?.let { return it }
        var current = byCode[code] ?: return null
        var guard = 0
        while (true) {
            CountryNames.movedTo(current.code)?.let { target ->
                if (byCode.containsKey(target)) {
                    rootCache[code] = target
                    return target
                }
            }
            val parent = current.parentCode?.let { byCode[it] } ?: break
            current = parent
            if (++guard > 20) break // malformed data: stop instead of looping
        }
        rootCache[code] = current.code
        return current.code
    }

    /** Italian country name for a Numista issuer code, or null if the code is unknown. */
    fun countryName(code: String): String? = rootOf(code)?.let { root ->
        CountryNames.italian(root, byCode[root]?.name ?: root)
    }

    /**
     * Where a piece comes from, for display: "Italia" when the issuer is the country itself
     * (or has the same name as it, like Numista's "Italy" inside "Italy"), otherwise
     * "Italia · Italian States".
     */
    fun placeLabel(code: String, issuerName: String): String {
        val root = rootOf(code) ?: return issuerName
        val country = CountryNames.italian(root, byCode[root]?.name ?: root)
        val rootName = byCode[root]?.name
        val ownName = byCode[code]?.name ?: issuerName
        return if (root == code || ownName.equals(rootName, ignoreCase = true) || issuerName.equals(country, ignoreCase = true)) {
            country
        } else {
            "$country · $issuerName"
        }
    }

    /** All countries, alphabetical (Italian collation, ignoring accents). */
    val countries: List<Country> by lazy {
        val counts = HashMap<String, Int>()
        for (issuer in issuers) {
            val root = rootOf(issuer.code) ?: continue
            counts[root] = (counts[root] ?: 0) + 1
        }
        counts.keys
            .groupBy { root -> CountryNames.italian(root, byCode[root]?.name ?: root) }
            .map { (name, roots) ->
                val sorted = roots.sortedByDescending { counts[it] ?: 0 }
                Country(name, sorted, sorted.sumOf { counts[it] ?: 0 })
            }
            .sortedWith(compareBy(collator) { it.name })
    }

    /** Issuers inside a country, in tree order (parents before their children), without the top-level group itself. */
    fun issuersOf(country: Country): List<Issuer> {
        val children = HashMap<String, MutableList<Issuer>>()
        for (i in byCode.values) {
            val p = i.parentCode ?: continue
            children.getOrPut(p) { mutableListOf() }.add(i)
        }
        val roots = country.rootCodes.toSet()
        val out = mutableListOf<Issuer>()
        fun walk(code: String) {
            children[code]?.sortedWith(compareBy(collator) { it.name })?.forEach { child ->
                // skip branches the app moves to another country
                if (rootOf(child.code) !in roots) return@forEach
                out += child
                walk(child.code)
            }
        }
        country.rootCodes.forEach { walk(it) }
        // branches moved here from another country
        byCode.values
            .filter { CountryNames.movedTo(it.code) in roots }
            .sortedWith(compareBy(collator) { it.name })
            .forEach { moved ->
                out += moved
                walk(moved.code)
            }
        return out
    }

    companion object {
        private val collator: java.text.Collator =
            java.text.Collator.getInstance(java.util.Locale.ITALIAN).apply { strength = java.text.Collator.PRIMARY }
    }
}
