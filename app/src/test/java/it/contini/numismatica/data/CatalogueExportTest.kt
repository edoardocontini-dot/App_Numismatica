package it.contini.numismatica.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class CatalogueExportTest {

    private fun item(id: Long, title: String, issuer: String, year: Int?, qty: Int = 1) = OwnedItem(
        typeId = id, title = title, category = Categories.COIN, issuerCode = issuer.lowercase(), issuerName = issuer,
        minYear = year, maxYear = year, thumb = null, composition = null, demonetized = null, quantity = qty,
        grade = "SPL", notes = "Nota con & < > \" caratteri speciali", issueYear = year?.toString() ?: "",
        addedAt = 1_760_000_000_000, provenance = "Mercatino", mintage = if (year == 1958) 24_910_000L else null,
    )

    private fun detail(s: TypeSummary) = TypeDetail(
        summary = s, url = "https://it.numista.com/catalogue/pieces${s.id}.html", valueText = "500 Lire", currency = "Lira italiana",
        composition = "Argento 835‰", weight = "11", size = "29", thickness = "1.9", shape = "Rotonda", technique = "Coniata",
        rulers = listOf("Repubblica Italiana"), demonetized = true, demonetizationDate = "2002-03-01",
        obverseImage = "https://en.numista.com/catalogue/photos/italie/1-original.jpg", reverseImage = null,
        obverseDescription = "Busto femminile", reverseDescription = "Caravelle", obverseLettering = "REPUBBLICA ITALIANA",
        reverseLettering = null, edge = "Iscrizione", mints = listOf("Roma"), comments = null,
    )

    @Test
    fun writesValidWorkbook() {
        val a = item(1, "500 Lire Caravelle", "Italia", 1958, qty = 2)
        val b = item(2, "Denario di Augusto", "Impero romano", -2)
        val c = item(3, "Pezzo senza dettagli", "Francia", null)
        val sa = TypeSummary(1, a.title, "coin", "italie", "Italia", 1958, 1958, null, null)
        val entries = listOf(
            CatalogueExport.Entry(a, detail(sa)),
            CatalogueExport.Entry(b, null),
            CatalogueExport.Entry(c, null),
        )
        val out = File("build/test-output").apply { mkdirs() }
        val f = File(out, "sample-catalogue.xlsx")
        f.outputStream().use { CatalogueExport.write(it, entries) }

        ZipFile(f).use { zip ->
            val names = zip.entries().toList().map { it.name }.toSet()
            assertTrue("xl/workbook.xml" in names)
            assertTrue("xl/worksheets/sheet1.xml" in names)
            assertTrue("xl/worksheets/sheet2.xml" in names)
            val sheet2 = zip.getInputStream(zip.getEntry("xl/worksheets/sheet2.xml")).readBytes().toString(Charsets.UTF_8)
            assertTrue(sheet2.contains("dataBar"))
            assertTrue(!sheet2.contains("Spesa"))
            val sheet1 = zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml")).readBytes().toString(Charsets.UTF_8)
            assertTrue(sheet1.contains("&amp; &lt; &gt; &quot;"))
            assertTrue(sheet1.contains("HYPERLINK"))
        }
    }

    @Test
    fun centuries() {
        assertEquals("XX sec.", CatalogueExport.centuryLabel(1958))
        assertEquals("XXI sec.", CatalogueExport.centuryLabel(2001))
        assertEquals("I sec. a.C.", CatalogueExport.centuryLabel(-2))
        assertEquals("IV sec. a.C.", CatalogueExport.centuryLabel(-336))
        assertEquals("", CatalogueExport.centuryLabel(null))
    }

    @Test
    fun dates() {
        assertEquals("01/03/2002", italianDate("2002-03-01"))
        assertEquals("2002", italianDate("2002-00-00"))
        assertEquals("03/2002", italianDate("2002-03-00"))
        assertEquals("1999", italianDate("1999"))
        assertEquals(null, italianDate(""))
    }

    @Test
    fun columnLetters() {
        assertEquals("A", XlsxWriter.col(1))
        assertEquals("Z", XlsxWriter.col(26))
        assertEquals("AA", XlsxWriter.col(27))
        assertEquals("AH", XlsxWriter.col(34))
    }
}
