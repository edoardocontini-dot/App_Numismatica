package it.contini.numismatica.data

import it.contini.numismatica.data.XlsxWriter.Cell
import it.contini.numismatica.data.XlsxWriter.Column
import it.contini.numismatica.data.XlsxWriter.Sheet
import it.contini.numismatica.data.XlsxWriter.Style
import it.contini.numismatica.data.XlsxWriter.num
import it.contini.numismatica.data.XlsxWriter.text
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds the Excel catalogue of the collection.
 * Sheet 1 "Collezione": one row per piece type with catalogue data + personal data.
 * Sheet 2 "Riepilogo": totals by country, type, material and century.
 * Pure JVM code (no Android), unit-tested.
 */
object CatalogueExport {

    data class Entry(val item: OwnedItem, val detail: TypeDetail?)

    private val COLUMNS = listOf(
        Column("N°", 6.0),
        Column("Paese / Emittente", 22.0),
        Column("Tipo", 11.0),
        Column("Denominazione", 34.0),
        Column("Valore nominale", 14.0),
        Column("Valuta", 16.0),
        Column("Anno esemplare", 11.0),
        Column("Periodo di emissione", 14.0),
        Column("Secolo", 12.0),
        Column("Autorità / Sovrano", 22.0),
        Column("Zecca", 22.0),
        Column("Materiale", 20.0),
        Column("Peso (g)", 9.0),
        Column("Diametro / misure (mm)", 11.0),
        Column("Spessore (mm)", 10.0),
        Column("Forma", 12.0),
        Column("Tecnica", 12.0),
        Column("Bordo", 18.0),
        Column("Stato", 12.0),
        Column("Fuori corso dal", 13.0),
        Column("Conservazione", 13.0),
        Column("Quantità", 9.0),
        Column("Prezzo d'acquisto", 13.0),
        Column("Totale pagato", 13.0),
        Column("Data acquisto", 12.0),
        Column("Provenienza", 20.0),
        Column("Note", 36.0),
        Column("Dritto", 40.0),
        Column("Rovescio", 40.0),
        Column("Numista N°", 11.0),
        Column("Scheda Numista", 14.0),
        Column("Foto dritto", 11.0),
        Column("Foto rovescio", 11.0),
        Column("Aggiunto il", 12.0),
    )

    fun write(out: OutputStream, entries: List<Entry>, now: Date = Date()) {
        val sorted = entries.sortedWith(
            compareBy<Entry>({ it.item.issuerName.lowercase(Locale.ITALIAN) }, { it.item.minYear ?: Int.MAX_VALUE }, { it.item.title.lowercase() })
        )
        XlsxWriter.write(out, listOf(collectionSheet(sorted), summarySheet(sorted, now)))
    }

    private fun collectionSheet(entries: List<Entry>): Sheet {
        val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.ITALIAN)
        val rows = entries.mapIndexed { i, e ->
            val o = e.item
            val d = e.detail
            val status = when (d?.demonetized ?: o.demonetized) {
                true -> "Fuori corso"
                false -> "In corso"
                null -> ""
            }
            listOf(
                Cell.Num((i + 1).toDouble(), Style.INTEGER),
                text(o.issuerName),
                text(Categories.label(o.category)),
                text(o.title, Style.WRAP),
                text(d?.valueText),
                text(d?.currency),
                text(o.issueYear),
                text(yearsLabel(o.minYear, o.maxYear)),
                text(centuryLabel(o.minYear ?: o.maxYear)),
                text(d?.rulers?.joinToString(", "), Style.WRAP),
                text(d?.mints?.joinToString(", "), Style.WRAP),
                text(d?.composition ?: o.composition, Style.WRAP),
                numOrText(d?.weight, Style.DECIMAL),
                numOrText(d?.size, Style.DECIMAL),
                numOrText(d?.thickness, Style.DECIMAL),
                text(d?.shape),
                text(d?.technique),
                text(d?.edge, Style.WRAP),
                text(status),
                text(italianDate(d?.demonetizationDate)),
                text(o.grade),
                Cell.Num(o.quantity.toDouble(), Style.INTEGER),
                num(o.purchasePrice, Style.EURO),
                num(o.purchasePrice?.let { it * o.quantity }, Style.EURO),
                text(o.purchaseDate),
                text(o.provenance, Style.WRAP),
                text(o.notes, Style.WRAP),
                text(side(d?.obverseDescription, d?.obverseLettering), Style.WRAP),
                text(side(d?.reverseDescription, d?.reverseLettering), Style.WRAP),
                Cell.Num(o.typeId.toDouble(), Style.INTEGER),
                Cell.Link(d?.url ?: "https://it.numista.com/catalogue/pieces${o.typeId}.html", "Apri scheda"),
                link(d?.obverseImage ?: o.thumb, "Foto"),
                link(d?.reverseImage, "Foto"),
                text(dateFmt.format(Date(o.addedAt))),
            )
        }
        return Sheet("Collezione", COLUMNS, rows)
    }

    private fun summarySheet(entries: List<Entry>, now: Date): Sheet {
        val rows = mutableListOf<List<Cell>>()
        val stamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALIAN).format(now)
        val spent = entries.sumOf { (it.item.purchasePrice ?: 0.0) * it.item.quantity }
        val priced = entries.count { it.item.purchasePrice != null }

        rows.add(listOf(Cell.Text("Catalogo della collezione", Style.TITLE)))
        rows.add(listOf(Cell.Text("Generato il $stamp con l'app Numismatica · dati catalogo forniti da Numista")))
        rows.add(emptyList())
        rows.add(listOf(Cell.Text("Totali", Style.BOLD), Cell.Text("", Style.BOLD)))
        rows.add(listOf(text("Tipi diversi"), Cell.Num(entries.size.toDouble(), Style.INTEGER)))
        rows.add(listOf(text("Pezzi totali"), Cell.Num(entries.sumOf { it.item.quantity }.toDouble(), Style.INTEGER)))
        rows.add(listOf(text("Paesi / emittenti"), Cell.Num(entries.map { it.item.issuerName }.distinct().size.toDouble(), Style.INTEGER)))
        rows.add(listOf(text("Spesa registrata"), Cell.Num(spent, Style.BOLD_EURO), text("su $priced tipi con prezzo indicato")))

        fun section(title: String, keyOf: (Entry) -> String, order: Comparator<String>? = null) {
            rows.add(emptyList())
            rows.add(listOf(
                Cell.Text(title, Style.BOLD), Cell.Text("Tipi", Style.BOLD),
                Cell.Text("Pezzi", Style.BOLD), Cell.Text("Spesa", Style.BOLD),
            ))
            val groups = entries.groupBy { keyOf(it).ifBlank { "Non indicato" } }
            val keys = if (order != null) groups.keys.sortedWith(order)
            else groups.keys.sortedByDescending { k -> groups.getValue(k).sumOf { it.item.quantity } }
            keys.forEach { k ->
                val g = groups.getValue(k)
                rows.add(listOf(
                    text(k),
                    Cell.Num(g.size.toDouble(), Style.INTEGER),
                    Cell.Num(g.sumOf { it.item.quantity }.toDouble(), Style.INTEGER),
                    Cell.Num(g.sumOf { (it.item.purchasePrice ?: 0.0) * it.item.quantity }, Style.EURO),
                ))
            }
        }

        section("Per paese", { it.item.issuerName }, compareBy { it.lowercase(Locale.ITALIAN) })
        section("Per tipo", { Categories.label(it.item.category) })
        section("Per secolo", { centuryLabel(it.item.minYear ?: it.item.maxYear) }, compareBy { centurySortKey(it) })
        section("Per materiale", { it.detail?.composition ?: it.item.composition ?: "" })
        section("Per stato", {
            when (it.detail?.demonetized ?: it.item.demonetized) { true -> "Fuori corso"; false -> "In corso"; null -> "" }
        })
        section("Per conservazione", { it.item.grade }, compareBy { g -> GRADES.indexOf(g).let { i -> if (i < 0) 99 else i } })

        return Sheet(
            "Riepilogo",
            listOf(Column("", 30.0), Column("", 14.0), Column("", 12.0), Column("", 14.0)),
            rows,
            headerRow = false,
        )
    }

    // ---------- helpers ----------

    private fun side(description: String?, lettering: String?): String? =
        listOfNotNull(description, lettering?.let { "Legenda: $it" }).joinToString("\n").ifBlank { null }

    private fun link(url: String?, label: String): Cell = if (url.isNullOrBlank()) Cell.Empty else Cell.Link(url, label)

    /** Numista gives numbers as text ("5.5"); keep them numeric when possible so Excel can sort and sum. */
    private fun numOrText(s: String?, style: Style): Cell {
        if (s.isNullOrBlank()) return Cell.Empty
        val d = s.trim().replace(',', '.').toDoubleOrNull()
        return if (d != null) Cell.Num(d, style) else Cell.Text(s)
    }

    fun centuryLabel(year: Int?): String {
        if (year == null) return ""
        return if (year > 0) "${roman((year - 1) / 100 + 1)} sec." else "${roman((-year - 1) / 100 + 1)} sec. a.C."
    }

    /** Sorts B.C. centuries before A.D. ones, oldest first. */
    private fun centurySortKey(label: String): Int {
        if (label.isBlank() || label == "Non indicato") return Int.MAX_VALUE
        val bc = label.endsWith("a.C.")
        val n = fromRoman(label.substringBefore(' '))
        return if (bc) -n else n
    }

    private val ROMAN = listOf(
        1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC",
        50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I",
    )

    private fun roman(n: Int): String {
        var x = n
        val sb = StringBuilder()
        for ((v, s) in ROMAN) while (x >= v) { sb.append(s); x -= v }
        return sb.toString()
    }

    private fun fromRoman(s: String): Int {
        var i = 0
        var total = 0
        for ((v, r) in ROMAN) while (s.startsWith(r, i)) { total += v; i += r.length }
        return total
    }
}
