package it.contini.numismatica.data

/** One row of a Numista search result. */
data class TypeSummary(
    val id: Long,
    val title: String,
    val category: String,
    val issuerCode: String,
    val issuerName: String,
    val minYear: Int?,
    val maxYear: Int?,
    val obverseThumb: String?,
    val reverseThumb: String?,
)

/** Full details of a coin / banknote type. */
data class TypeDetail(
    val summary: TypeSummary,
    val url: String?,
    val valueText: String?,
    val currency: String?,
    val composition: String?,
    val weight: String?,
    val size: String?,
    val thickness: String?,
    val shape: String?,
    val technique: String?,
    val rulers: List<String>,
    val demonetized: Boolean?,
    val demonetizationDate: String?,
    val obverseImage: String?,
    val reverseImage: String?,
    val obverseDescription: String?,
    val reverseDescription: String?,
    val obverseLettering: String?,
    val reverseLettering: String?,
    val edge: String?,
    val mints: List<String>,
    val comments: String?,
    /** e.g. "Moneta commemorativa", "Banconota regolare" */
    val objectType: String? = null,
    /** e.g. "Banca d'Italia" */
    val issuingEntity: String? = null,
    /** Catalogue numbers, e.g. "KM# 98", "P# 3" */
    val references: List<String> = emptyList(),
    val watermark: String? = null,
)

/** A Numista issuer. Issuers form a tree: level 1 = country / big group, deeper levels = states, cities… */
data class Issuer(val code: String, val name: String, val parentCode: String? = null, val level: Int = 1)

/** One issue of a type: a specific year (and mint mark) with its mintage. */
data class Issue(
    val id: Long,
    val label: String,
    val sortYear: Int?,
    val mintage: Long?,
    val comment: String?,
    /** Catalogue numbers of this specific issue, e.g. "P# 3a" */
    val references: List<String> = emptyList(),
)

data class SearchPage(val total: Int, val items: List<TypeSummary>)

/** A piece in Dad's collection, stored on the phone. */
data class OwnedItem(
    val typeId: Long,
    val title: String,
    val category: String,
    val issuerCode: String,
    val issuerName: String,
    val minYear: Int?,
    val maxYear: Int?,
    val thumb: String?,
    val composition: String?,
    /** null = not known yet */
    val demonetized: Boolean?,
    val quantity: Int,
    val grade: String,
    val notes: String,
    val issueYear: String,
    val addedAt: Long,
    /** Price paid in euro, if he wants to record it. */
    val purchasePrice: Double? = null,
    val purchaseDate: String = "",
    /** Where it came from: dealer, auction, inheritance, change… */
    val provenance: String = "",
    /** Mintage of the chosen year/issue, from Numista. */
    val mintage: Long? = null,
)

object Categories {
    const val COIN = "coin"
    const val BANKNOTE = "banknote"
    const val EXONUMIA = "exonumia"

    fun label(category: String): String = when (category) {
        COIN -> "Moneta"
        BANKNOTE -> "Banconota"
        EXONUMIA -> "Esonumia"
        else -> category
    }
}

/** Common Italian grading scale. */
val GRADES = listOf("", "B", "MB", "q.BB", "BB", "q.SPL", "SPL", "q.FDC", "FDC", "FS (Fondo Specchio)")

/** 25000000 -> "25.000.000" */
fun formatThousands(n: Long): String = String.format(java.util.Locale.ITALIAN, "%,d", n)

fun yearsLabel(minYear: Int?, maxYear: Int?): String = when {
    minYear == null && maxYear == null -> ""
    minYear == maxYear || maxYear == null -> formatYear(minYear!!)
    minYear == null -> formatYear(maxYear!!)
    else -> formatYear(minYear!!) + "–" + formatYear(maxYear!!)
}

private fun formatYear(y: Int): String = if (y < 0) "${-y} a.C." else y.toString()

/** "2002-03-01" -> "01/03/2002"; "2002" stays "2002"; anything else is returned unchanged. */
fun italianDate(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    val m = Regex("""^(-?\d{1,4})-(\d{2})-(\d{2})$""").find(iso.trim()) ?: return iso
    val (y, mo, d) = m.destructured
    return if (mo == "00") y else if (d == "00") "$mo/$y" else "$d/$mo/$y"
}
