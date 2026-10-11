package it.contini.numismatica.data

import it.contini.numismatica.data.XlsxWriter.Cell
import it.contini.numismatica.data.XlsxWriter.Column
import it.contini.numismatica.data.XlsxWriter.Sheet
import it.contini.numismatica.data.XlsxWriter.Style
import it.contini.numismatica.data.XlsxWriter.text
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Builds the Excel catalogue of the collection.
 * Sheet 1 "Collezione": one row per piece type with catalogue data + Dad's own data.
 * Sheet 2 "Statistiche": overview and breakdowns (country, century, type, material, status, grade, year added).
 * Pure JVM code (no Android), unit-tested.
 */
object CatalogueExport {

    /** [country] is the unified Italian country name (see CountryIndex); defaults to Numista's issuer name. */
    data class Entry(val item: OwnedItem, val detail: TypeDetail?, val country: String = item.issuerName)

    private val COLUMNS = listOf(
        Column("N°", 6.0),
        Column("Paese", 18.0),
        Column("Emittente", 22.0),
        Column("Tipo", 11.0),
        Column("Tipologia", 18.0),
        Column("Denominazione", 34.0),
        Column("Valore nominale", 14.0),
        Column("Valuta", 16.0),
        Column("Anno esemplare", 13.0),
        Column("Tiratura", 13.0),
        Column("Periodo di emissione", 14.0),
        Column("Secolo", 12.0),
        Column("Autorità / Sovrano", 22.0),
        Column("Ente emittente", 20.0),
        Column("Zecca", 22.0),
        Column("Riferimenti", 16.0),
        Column("Materiale", 20.0),
        Column("Peso (g)", 9.0),
        Column("Diametro / misure (mm)", 11.0),
        Column("Spessore (mm)", 10.0),
        Column("Forma", 12.0),
        Column("Tecnica", 12.0),
        Column("Bordo", 18.0),
        Column("Filigrana", 18.0),
        Column("Stato", 12.0),
        Column("Fuori corso dal", 13.0),
        Column("Conservazione", 13.0),
        Column("Quantità", 9.0),
        Column("Provenienza", 22.0),
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
            compareBy<Entry>(
                { it.country.lowercase(Locale.ITALIAN) },
                { it.item.issuerName.lowercase(Locale.ITALIAN) },
                { it.item.minYear ?: Int.MAX_VALUE },
                { it.item.title.lowercase() },
            )
        )
        XlsxWriter.write(out, listOf(collectionSheet(sorted), statisticsSheet(sorted, now)))
    }

    private fun status(e: Entry): String = when (e.detail?.demonetized ?: e.item.demonetized) {
        true -> "Fuori corso"
        false -> "In corso"
        null -> ""
    }

    private fun material(e: Entry): String = e.detail?.composition ?: e.item.composition ?: ""

    private fun collectionSheet(entries: List<Entry>): Sheet {
        val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.ITALIAN)
        val rows = entries.mapIndexed { i, e ->
            val o = e.item
            val d = e.detail
            listOf(
                Cell.Num((i + 1).toDouble(), Style.INTEGER),
                text(e.country),
                text(o.issuerName),
                text(Categories.label(o.category)),
                text(d?.objectType),
                text(o.title, Style.WRAP),
                text(d?.valueText),
                text(d?.currency),
                text(o.issueYear),
                o.mintage?.let { Cell.Num(it.toDouble(), Style.INTEGER) } ?: Cell.Empty,
                text(yearsLabel(o.minYear, o.maxYear)),
                text(centuryLabel(o.minYear ?: o.maxYear)),
                text(d?.rulers?.joinToString(", "), Style.WRAP),
                text(d?.issuingEntity, Style.WRAP),
                text(d?.mints?.joinToString(", "), Style.WRAP),
                text(d?.references?.joinToString(", "), Style.WRAP),
                text(material(e), Style.WRAP),
                numOrText(d?.weight, Style.DECIMAL),
                numOrText(d?.size, Style.DECIMAL),
                numOrText(d?.thickness, Style.DECIMAL),
                text(d?.shape),
                text(d?.technique),
                text(d?.edge, Style.WRAP),
                text(d?.watermark, Style.WRAP),
                text(status(e)),
                text(italianDate(d?.demonetizationDate)),
                text(o.grade),
                Cell.Num(o.quantity.toDouble(), Style.INTEGER),
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
        check(rows.all { it.size == COLUMNS.size }) { "Excel: row length does not match the header" }
        return Sheet("Collezione", COLUMNS, rows)
    }

    private fun statisticsSheet(entries: List<Entry>, now: Date): Sheet {
        val rows = mutableListOf<List<Cell>>()
        val bars = mutableListOf<String>()
        fun row(vararg cells: Cell) { rows.add(cells.toList()) }
        fun blank() { rows.add(emptyList()) }

        val stamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALIAN).format(now)
        val pieces = entries.sumOf { it.item.quantity }
        val countries = entries.map { it.country }.filter { it.isNotBlank() }.distinct().size
        val centuries = entries.mapNotNull { e -> (e.item.minYear ?: e.item.maxYear)?.let { centuryLabel(it) } }.distinct().size
        val dated = entries.filter { (it.item.minYear ?: it.item.maxYear) != null }
        val oldest = dated.minByOrNull { it.item.minYear ?: it.item.maxYear ?: 0 }
        val newest = dated.maxByOrNull { it.item.maxYear ?: it.item.minYear ?: 0 }

        row(Cell.Text("Statistiche della collezione", Style.TITLE))
        row(Cell.Text("Aggiornate al $stamp · dati di catalogo forniti da Numista", Style.MUTED))
        blank()

        // ---- overview: big figures ----
        row(
            Cell.Num(entries.size.toDouble(), Style.BIG_NUMBER), Cell.Num(pieces.toDouble(), Style.BIG_NUMBER),
            Cell.Num(countries.toDouble(), Style.BIG_NUMBER), Cell.Num(centuries.toDouble(), Style.BIG_NUMBER),
        )
        row(
            Cell.Text("tipi diversi", Style.MUTED), Cell.Text("pezzi in totale", Style.MUTED),
            Cell.Text("paesi / emittenti", Style.MUTED), Cell.Text("secoli rappresentati", Style.MUTED),
        )
        blank()

        row(Cell.Text("In breve", Style.BOLD), Cell.Text("", Style.BOLD), Cell.Text("", Style.BOLD), Cell.Text("", Style.BOLD))
        row(text("Doppioni (pezzi oltre il primo)"), Cell.Num((pieces - entries.size).toDouble(), Style.INTEGER_LEFT))
        row(text("Monete"), Cell.Num(entries.count { it.item.category == Categories.COIN }.toDouble(), Style.INTEGER_LEFT))
        row(text("Banconote"), Cell.Num(entries.count { it.item.category == Categories.BANKNOTE }.toDouble(), Style.INTEGER_LEFT))
        row(text("Esonumia"), Cell.Num(entries.count { it.item.category == Categories.EXONUMIA }.toDouble(), Style.INTEGER_LEFT))
        row(text("Ancora in corso legale"), Cell.Num(entries.count { status(it) == "In corso" }.toDouble(), Style.INTEGER_LEFT))
        row(text("Fuori corso"), Cell.Num(entries.count { status(it) == "Fuori corso" }.toDouble(), Style.INTEGER_LEFT))
        oldest?.let { e ->
            row(text("Pezzo più antico"), text(yearsLabel(e.item.minYear, e.item.maxYear)), text("${e.item.title} · ${e.country}"))
        }
        newest?.let { e ->
            row(text("Pezzo più recente"), text(yearsLabel(e.item.minYear, e.item.maxYear)), text("${e.item.title} · ${e.country}"))
        }
        entries.filter { it.item.mintage != null }.minByOrNull { it.item.mintage ?: Long.MAX_VALUE }?.let { e ->
            val what = listOf(e.item.title, e.item.issueYear).filter { it.isNotBlank() }.joinToString(" · ")
            row(text("Tiratura più bassa"), Cell.Num((e.item.mintage ?: 0L).toDouble(), Style.INTEGER_LEFT), text(what))
        }

        // ---- breakdowns ----
        fun section(title: String, keyOf: (Entry) -> String, order: Comparator<String>? = null, limit: Int = Int.MAX_VALUE) {
            blank()
            row(
                Cell.Text(title, Style.BOLD), Cell.Text("Tipi", Style.BOLD_RIGHT),
                Cell.Text("Pezzi", Style.BOLD_RIGHT), Cell.Text("% pezzi", Style.BOLD_RIGHT),
            )
            val groups = entries.groupBy { keyOf(it).ifBlank { "Non indicato" } }
            val byPieces = compareByDescending<String> { k -> groups.getValue(k).sumOf { it.item.quantity } }.thenBy { it }
            val keys = groups.keys.sortedWith(order ?: byPieces)
            val first = rows.size + 1 // 1-based row number of the first data row
            keys.take(limit).forEach { k ->
                val g = groups.getValue(k)
                val n = g.sumOf { it.item.quantity }
                row(
                    text(k),
                    Cell.Num(g.size.toDouble(), Style.INTEGER),
                    Cell.Num(n.toDouble(), Style.INTEGER),
                    Cell.Num(if (pieces == 0) 0.0 else n.toDouble() / pieces, Style.PERCENT),
                )
            }
            if (keys.size > limit) {
                val rest = keys.drop(limit).flatMap { groups.getValue(it) }
                val n = rest.sumOf { it.item.quantity }
                row(
                    Cell.Text("Altri ${keys.size - limit}", Style.MUTED),
                    Cell.Num(rest.size.toDouble(), Style.INTEGER),
                    Cell.Num(n.toDouble(), Style.INTEGER),
                    Cell.Num(if (pieces == 0) 0.0 else n.toDouble() / pieces, Style.PERCENT),
                )
            }
            val last = rows.size
            if (last >= first) bars += "D$first:D$last"
        }

        val cal = Calendar.getInstance()
        section("Per paese", { it.country })
        section("Per secolo", { centuryLabel(it.item.minYear ?: it.item.maxYear) }, compareBy { centurySortKey(it) })
        section("Per tipo", { Categories.label(it.item.category) })
        section("Per materiale", { material(it) }, limit = 15)
        section("Per stato", { status(it) })
        section("Per conservazione", { it.item.grade }, compareBy { g -> GRADES.indexOf(g).let { i -> if (i < 0) 99 else i } })
        section("Per provenienza", { it.item.provenance.trim() }, limit = 10)
        section("Per anno di inserimento", {
            cal.timeInMillis = it.item.addedAt
            cal.get(Calendar.YEAR).toString()
        }, compareBy { it })

        return Sheet(
            "Statistiche",
            listOf(Column("", 34.0), Column("", 16.0), Column("", 16.0), Column("", 18.0)),
            rows,
            headerRow = false,
            dataBars = bars,
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

    /** Sorts B.C. centuries before A.D. ones, oldest first; "Non indicato" last. */
    private fun centurySortKey(label: String): Int {
        if (label.isBlank() || label == "Non indicato") return Int.MAX_VALUE
        val n = fromRoman(label.substringBefore(' '))
        return if (label.endsWith("a.C.")) -n else n
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
