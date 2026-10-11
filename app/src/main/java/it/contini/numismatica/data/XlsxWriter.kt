package it.contini.numismatica.data

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Small, dependency-free writer for .xlsx files (Office Open XML).
 * Pure JVM code: no Android classes, so it can be unit-tested.
 */
object XlsxWriter {

    /** Cell styles defined in styles.xml (index = position in cellXfs). */
    enum class Style(val index: Int) {
        DEFAULT(0), HEADER(1), WRAP(2), LINK(3), DECIMAL(4), EURO(5), TITLE(6), BOLD(7), INTEGER(8), BOLD_EURO(9)
    }

    sealed class Cell {
        data class Text(val value: String, val style: Style = Style.DEFAULT) : Cell()
        data class Num(val value: Double, val style: Style = Style.DEFAULT) : Cell()
        data class Link(val url: String, val label: String) : Cell()
        data object Empty : Cell()
    }

    data class Column(val header: String, val width: Double)

    data class Sheet(
        val name: String,
        /** Column widths; when [headerRow] is true the names become a styled, frozen header row with filters. */
        val columns: List<Column>,
        val rows: List<List<Cell>>,
        val headerRow: Boolean = true,
    )

    fun text(s: String?, style: Style = Style.DEFAULT): Cell = if (s.isNullOrBlank()) Cell.Empty else Cell.Text(s, style)
    fun num(d: Double?, style: Style = Style.DEFAULT): Cell = if (d == null) Cell.Empty else Cell.Num(d, style)

    fun write(out: OutputStream, sheets: List<Sheet>) {
        require(sheets.isNotEmpty())
        ZipOutputStream(out).use { zip ->
            fun put(path: String, content: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", contentTypes(sheets.size))
            put("_rels/.rels", ROOT_RELS)
            put("docProps/app.xml", APP_PROPS)
            put("xl/workbook.xml", workbook(sheets))
            put("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            put("xl/styles.xml", STYLES)
            sheets.forEachIndexed { i, s -> put("xl/worksheets/sheet${i + 1}.xml", sheetXml(s)) }
        }
    }

    // ---------- parts ----------

    private fun contentTypes(n: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        append("""<Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>""")
        for (i in 1..n) {
            append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("</Types>")
    }

    private const val ROOT_RELS =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
            """<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>""" +
            "</Relationships>"

    private const val APP_PROPS =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties">""" +
            "<Application>Numismatica</Application></Properties>"

    private fun workbook(sheets: List<Sheet>) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        append("<sheets>")
        sheets.forEachIndexed { i, s ->
            append("""<sheet name="${esc(safeSheetName(s.name))}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""")
        }
        append("</sheets>")
        val filtered = sheets.withIndex().filter { (_, s) -> s.headerRow && s.columns.isNotEmpty() }
        if (filtered.isNotEmpty()) {
            append("<definedNames>")
            filtered.forEach { (i, s) ->
                val ref = "'${safeSheetName(s.name).replace("'", "''")}'!\$A\$1:\$${col(s.columns.size)}\$${s.rows.size + 1}"
                append("""<definedName name="_xlnm._FilterDatabase" localSheetId="$i" hidden="1">${esc(ref)}</definedName>""")
            }
            append("</definedNames>")
        }
        append("</workbook>")
    }

    private fun workbookRels(n: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..n) {
            append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        }
        append("""<Relationship Id="rId${n + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    private fun sheetXml(s: Sheet) = buildString {
        val firstDataRow = if (s.headerRow) 2 else 1
        val lastRow = s.rows.size + firstDataRow - 1
        val nCols = maxOf(s.columns.size, s.rows.maxOfOrNull { it.size } ?: 0, 1)
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        append("""<dimension ref="A1:${col(nCols)}${maxOf(lastRow, 1)}"/>""")
        append("""<sheetViews><sheetView workbookViewId="0">""")
        if (s.headerRow) {
            append("""<pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>""")
            append("""<selection pane="bottomLeft" activeCell="A2" sqref="A2"/>""")
        }
        append("</sheetView></sheetViews>")
        append("""<sheetFormatPr defaultRowHeight="15"/>""")
        if (s.columns.isNotEmpty()) {
            append("<cols>")
            s.columns.forEachIndexed { i, c ->
                append("""<col min="${i + 1}" max="${i + 1}" width="${c.width}" customWidth="1"/>""")
            }
            append("</cols>")
        }
        append("<sheetData>")
        if (s.headerRow) {
            append("""<row r="1" ht="30" customHeight="1">""")
            s.columns.forEachIndexed { i, c -> appendCell(this, "${col(i + 1)}1", Cell.Text(c.header, Style.HEADER)) }
            append("</row>")
        }
        s.rows.forEachIndexed { r, row ->
            val rowNum = r + firstDataRow
            append("""<row r="$rowNum">""")
            row.forEachIndexed { c, cell -> appendCell(this, "${col(c + 1)}$rowNum", cell) }
            append("</row>")
        }
        append("</sheetData>")
        if (s.headerRow && s.columns.isNotEmpty()) {
            append("""<autoFilter ref="A1:${col(s.columns.size)}${maxOf(lastRow, 1)}"/>""")
        }
        append("""<pageMargins left="0.5" right="0.5" top="0.6" bottom="0.6" header="0.3" footer="0.3"/>""")
        append("""<pageSetup orientation="landscape" fitToWidth="1" fitToHeight="0"/>""")
        append("</worksheet>")
    }

    private fun appendCell(sb: StringBuilder, ref: String, cell: Cell) {
        when (cell) {
            is Cell.Empty -> Unit
            is Cell.Text -> sb.append("""<c r="$ref" t="inlineStr" s="${cell.style.index}"><is><t xml:space="preserve">${esc(cell.value)}</t></is></c>""")
            is Cell.Num -> {
                if (cell.value.isNaN() || cell.value.isInfinite()) return
                sb.append("""<c r="$ref" s="${cell.style.index}"><v>${fmtNum(cell.value)}</v></c>""")
            }
            is Cell.Link -> {
                val url = cell.url.replace("\"", "%22")
                val label = cell.label.replace("\"", "\"\"")
                sb.append("""<c r="$ref" t="str" s="${Style.LINK.index}"><f>${esc("HYPERLINK(\"$url\",\"$label\")")}</f><v>${esc(cell.label)}</v></c>""")
            }
        }
    }

    // ---------- helpers ----------

    /** 1 -> A, 27 -> AA */
    fun col(n: Int): String {
        var x = n
        val sb = StringBuilder()
        while (x > 0) {
            val r = (x - 1) % 26
            sb.insert(0, ('A' + r))
            x = (x - 1) / 26
        }
        return sb.toString()
    }

    private fun fmtNum(d: Double): String =
        if (d == Math.floor(d) && kotlin.math.abs(d) < 1e15) d.toLong().toString() else d.toString()

    private fun safeSheetName(name: String): String =
        name.replace(Regex("[\\[\\]:*?/\\\\]"), " ").take(31).ifBlank { "Foglio" }

    /** XML-escapes text and drops characters that are not allowed in XML 1.0. */
    fun esc(s: String): String {
        val sb = StringBuilder(s.length + 16)
        for (ch in s) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch == '\t' || ch == '\n' || ch == '\r' -> sb.append(ch)
                ch < ' ' || ch == '￾' || ch == '￿' -> Unit
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    // fonts: 0 normal, 1 bold white (header), 2 bold, 3 link, 4 title
    // fills: 0 none, 1 gray125 (required by Excel), 2 dark green (header)
    // borders: 0 none, 1 thin bottom
    private val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
        """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
        """<numFmts count="1"><numFmt numFmtId="164" formatCode="#,##0.00\ &quot;€&quot;"/></numFmts>""" +
        """<fonts count="5">""" +
        """<font><sz val="11"/><name val="Calibri"/><family val="2"/></font>""" +
        """<font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/><family val="2"/></font>""" +
        """<font><b/><sz val="11"/><name val="Calibri"/><family val="2"/></font>""" +
        """<font><u/><sz val="11"/><color rgb="FF0563C1"/><name val="Calibri"/><family val="2"/></font>""" +
        """<font><b/><sz val="16"/><color rgb="FF1F5A4C"/><name val="Calibri"/><family val="2"/></font>""" +
        "</fonts>" +
        """<fills count="3">""" +
        """<fill><patternFill patternType="none"/></fill>""" +
        """<fill><patternFill patternType="gray125"/></fill>""" +
        """<fill><patternFill patternType="solid"><fgColor rgb="FF1F5A4C"/><bgColor indexed="64"/></patternFill></fill>""" +
        "</fills>" +
        """<borders count="2">""" +
        """<border><left/><right/><top/><bottom/><diagonal/></border>""" +
        """<border><left/><right/><top/><bottom style="thin"><color rgb="FFBFBFBF"/></bottom><diagonal/></border>""" +
        "</borders>" +
        """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
        """<cellXfs count="10">""" +
        // 0 default (top aligned)
        """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment vertical="top"/></xf>""" +
        // 1 header
        """<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf>""" +
        // 2 wrap
        """<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf>""" +
        // 3 link
        """<xf numFmtId="0" fontId="3" fillId="0" borderId="0" xfId="0" applyFont="1" applyAlignment="1"><alignment vertical="top"/></xf>""" +
        // 4 decimal
        """<xf numFmtId="2" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyAlignment="1"><alignment vertical="top"/></xf>""" +
        // 5 euro
        """<xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyAlignment="1"><alignment vertical="top"/></xf>""" +
        // 6 title
        """<xf numFmtId="0" fontId="4" fillId="0" borderId="0" xfId="0" applyFont="1"/>""" +
        // 7 bold with bottom border (section heading)
        """<xf numFmtId="0" fontId="2" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1"/>""" +
        // 8 integer
        """<xf numFmtId="1" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyAlignment="1"><alignment vertical="top"/></xf>""" +
        // 9 bold euro (totals)
        """<xf numFmtId="164" fontId="2" fillId="0" borderId="0" xfId="0" applyNumberFormat="1" applyFont="1"/>""" +
        "</cellXfs>" +
        """<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>""" +
        "</styleSheet>"
}
