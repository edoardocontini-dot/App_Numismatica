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
)

data class Issuer(val code: String, val name: String)

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
val GRADES = listOf("", "B", "MB", "BB", "SPL", "q.FDC", "FDC", "FS (Fondo Specchio)")

fun yearsLabel(minYear: Int?, maxYear: Int?): String = when {
    minYear == null && maxYear == null -> ""
    minYear == maxYear || maxYear == null -> formatYear(minYear!!)
    minYear == null -> formatYear(maxYear!!)
    else -> formatYear(minYear!!) + "–" + formatYear(maxYear!!)
}

private fun formatYear(y: Int): String = if (y < 0) "${-y} a.C." else y.toString()
